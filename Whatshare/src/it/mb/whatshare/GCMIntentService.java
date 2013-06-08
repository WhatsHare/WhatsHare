/**
 * GCMIntentService.java Created on 19 Feb 2013 Copyright 2013 Michele Bonazza
 * <michele.bonazza@gmail.com>
 */
package it.mb.whatshare;

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

    /**
     * Condition that is released when {@link GCMIntentService} is done with the
     * registration process.
     */
    private static final Condition REGISTERED = REGISTRATION_LOCK
            .newCondition();

    private static AtomicInteger counter = new AtomicInteger();

    private static String registrationID = "";

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
     */
    static String getRegistrationID() {
        if ("".equals(registrationID)) {
            try {
                REGISTRATION_LOCK.lock();
                // check again within lock
                if ("".equals(registrationID)) {
                    REGISTERED.await();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                REGISTRATION_LOCK.unlock();
            }
        }
        return registrationID;
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
        // TODO Auto-generated method stub

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
        Utils.debug("new incoming message from %s", bundle.getString("sender"));
        generateNotification(arg0, bundle.getString("message"));

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
        String title = context.getString(R.string.app_name);
        Intent whatshareIntent = new Intent(context,
                SendToWhatsappActivity.class);
        whatshareIntent.putExtra("message", message);
        PendingIntent intent = PendingIntent.getActivity(context, 0,
                whatshareIntent, Intent.FLAG_ACTIVITY_NEW_TASK);
        whatshareIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        Notification notification = null;
        // @formatter:off
        Notification.Builder builder = new Notification.Builder(context)
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
    private Notification buildForJellyBean(Notification.Builder builder) {
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
