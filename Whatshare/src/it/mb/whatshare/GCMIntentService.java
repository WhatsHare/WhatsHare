/**
 * GCMIntentService.java Created on 19 Feb 2013 Copyright 2013 Michele Bonazza
 * <michele.bonazza@gmail.com>
 */
package it.mb.whatshare;

import it.mb.whatshare.MainActivity.PairedDevice;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.StreamCorruptedException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;

import com.google.android.gcm.GCMBaseIntentService;
import com.google.android.gcm.GCMRegistrar;

/**
 * Issues notifications to the system whenever messages come from GCM.
 * 
 * @author Michele Bonazza
 */
public class GCMIntentService extends GCMBaseIntentService {

    /**
     * Lock to synchronize with {@link GCMIntentService}.
     */
    private static final Lock REGISTRATION_LOCK = new ReentrantLock();
    private static final String PROJECT_SERVER_ID = "213874322054";
    private static final String UNKNOWN_GCM_ERROR = "UNKNOWN_GCM_ERROR";
    private static final Map<String, Integer> ERROR_CODES = new HashMap<String, Integer>() {
        private static final long serialVersionUID = 649852390172936228L;

        {
            put("ACCOUNT_MISSING", R.string.account_missing);
            put("TOO_MANY_REGISTRATIONS", R.string.too_many_registrations);
            put("INVALID_SENDER", R.string.invalid_sender);
            put("PHONE_REGISTRATION_ERROR", R.string.phone_registration_error);
            put(UNKNOWN_GCM_ERROR, R.string.unknown_gcm_error);
        }
    };

    /**
     * Condition that is released when {@link GCMIntentService} is done with the
     * registration process.
     */
    private static final Condition REGISTERED = REGISTRATION_LOCK
            .newCondition();

    private static AtomicInteger counter = new AtomicInteger();
    private static String registrationID = "";
    private static int errorID = -1;

    private Set<String> senderWhitelist = new HashSet<String>();
    private long lastCheckedWhitelist;

    /**
     * Creates a new intent service for the application.
     */
    public GCMIntentService() {
        super("213874322054");
    }

    /**
     * Registers the device with the GCM server.
     * 
     * @param activity
     *            the calling activity
     */
    static void registerWithGCM(Context activity) {
        if ("".equals(registrationID)) {
            GCMRegistrar.checkDevice(activity);
            GCMRegistrar.checkManifest(activity);
            // register using the project ID
            GCMRegistrar.register(activity, PROJECT_SERVER_ID);
        }
    }

    /**
     * Returns the registration ID for this device, waiting for GCM to give this
     * device one if it hasn't done so before.
     * 
     * @return the registration ID
     * @throws CantRegisterWithGCMException
     *             in case an error message is received from GCM when trying to
     *             register this device
     */
    static String getRegistrationID() throws CantRegisterWithGCMException {
        if ("".equals(registrationID)) {
            try {
                REGISTRATION_LOCK.lock();
                // check again within lock
                if ("".equals(registrationID) && errorID != -1) {
                    REGISTERED.await();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                REGISTRATION_LOCK.unlock();
                if (errorID != -1) {
                    int error = errorID;
                    errorID = -1;
                    throw new CantRegisterWithGCMException(error);
                }
            }
        }
        return registrationID;
    }

    private void readWhitelist() {
        File whitelist = new File(getFilesDir(),
                MainActivity.INBOUND_DEVICES_FILENAME);
        // if whitelist doesn't exist, don't bother
        long lastModified = whitelist.exists() ? whitelist.lastModified()
                : Long.MIN_VALUE;
        if (lastCheckedWhitelist < lastModified) {
            FileInputStream fis = null;
            try {
                fis = openFileInput(MainActivity.INBOUND_DEVICES_FILENAME);
                ObjectInputStream ois = new ObjectInputStream(fis);
                Object read = ois.readObject();
                @SuppressWarnings("unchecked")
                List<PairedDevice> devices = (ArrayList<PairedDevice>) read;
                senderWhitelist = new HashSet<String>();
                for (PairedDevice device : devices) {
                    senderWhitelist.add(String.valueOf(device.name.hashCode()));
                }
            } catch (FileNotFoundException e) {
                // it's ok, no whitelist, all messages are rejected
            } catch (StreamCorruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                // TODO here the error should be notified I guess
                e.printStackTrace();
            } finally {
                if (fis != null)
                    try {
                        fis.close();
                    } catch (IOException e) {
                        // can't do much...
                        e.printStackTrace();
                    }
            }
            // whatever happens, don't keep checking for nothing
            lastCheckedWhitelist = lastModified;
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.google.android.gcm.GCMBaseIntentService#onError(android.content.Context
     * , java.lang.String)
     */
    @Override
    protected void onError(Context arg0, String arg1) {
        try {
            REGISTRATION_LOCK.lock();
            Utils.debug("Cannot register: %s", arg1);
            registrationID = "";
            errorID = ERROR_CODES.get(UNKNOWN_GCM_ERROR);
            if (arg1 != null) {
                Integer error = ERROR_CODES.get(arg1);
                if (error != null)
                    errorID = error;
            }
            REGISTERED.signalAll();
        } finally {
            REGISTRATION_LOCK.unlock();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.google.android.gcm.GCMBaseIntentService#onRecoverableError(android
     * .content.Context, java.lang.String)
     */
    @Override
    protected boolean onRecoverableError(Context context, String errorId) {
        return super.onRecoverableError(context, errorId);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.google.android.gcm.GCMBaseIntentService#onMessage(android.content
     * .Context, android.content.Intent)
     */
    @Override
    protected void onMessage(Context arg0, Intent arg1) {
        Bundle bundle = arg1.getExtras();
        String sender = bundle.getString("sender");
        Utils.debug("new incoming message from %s: %s", sender,
                bundle.getString("message"));
        readWhitelist();
        if (senderWhitelist.contains(sender)) {
            generateNotification(arg0, bundle.getString("message"));
        }
    }

    /**
     * Creates a notification informing the user that new content is to be
     * shared.
     * 
     * @param context
     *            the current application context
     * @param message
     *            the message to be shared
     */
    @SuppressWarnings("deprecation")
    public void generateNotification(Context context, String message) {
        // @formatter:off
        String title = context.getString(R.string.whatshare);
        // setAction is called so filterEquals() always returns false for all
        // our intents
        Intent whatshareIntent = new Intent(context,
                SendToWhatsappActivity.class)
                .putExtra("message", message)
                .setAction(String.valueOf(System.currentTimeMillis()))
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        
        PendingIntent intent = PendingIntent.getActivity(context, 0,
                whatshareIntent, Intent.FLAG_ACTIVITY_NEW_TASK);
        
        Notification notification = null;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.notification_icon, 0)
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(intent)
                .setDefaults(Notification.DEFAULT_ALL);
        // @formatter:on

        if (Build.VERSION.SDK_INT > 15) {
            notification = buildForJellyBean(builder);
        } else {
            notification = builder.getNotification();
        }
        notification.flags |= Notification.FLAG_AUTO_CANCEL;

        ((NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE)).notify(
                counter.incrementAndGet(), notification);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private Notification buildForJellyBean(NotificationCompat.Builder builder) {
        return builder.build();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.google.android.gcm.GCMBaseIntentService#onRegistered(android.content
     * .Context, java.lang.String)
     */
    @Override
    protected void onRegistered(Context arg0, String arg1) {
        try {
            REGISTRATION_LOCK.lock();
            Utils.debug("Now app is registered with ID %s", arg1);
            registrationID = arg1;
            REGISTERED.signalAll();
        } finally {
            REGISTRATION_LOCK.unlock();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.google.android.gcm.GCMBaseIntentService#onUnregistered(android.content
     * .Context, java.lang.String)
     */
    @Override
    protected void onUnregistered(Context arg0, String arg1) {
        // nothing to do here

    }

}
