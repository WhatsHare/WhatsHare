/**
 * SendToGCMActivity.java Created on 25 Mar 2013 Copyright 2013 Michele Bonazza
 * <emmepuntobi@gmail.com>
 * 
 * his file is part of WhatsHare.
 * 
 * WhatsHare is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * Foobar is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * WhatsHare. If not, see <http://www.gnu.org/licenses/>.
 */
package it.mb.whatshare;

import it.mb.whatshare.MainActivity.PairedDevice;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.OptionalDataException;
import java.io.UnsupportedEncodingException;
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.NotificationCompat;
import android.util.Pair;

import com.google.analytics.tracking.android.EasyTracker;
import com.google.analytics.tracking.android.GoogleAnalytics;
import com.google.analytics.tracking.android.Tracker;

/**
 * Activity called when sharing content from this device to be sent to the
 * Android device where Whatsapp is installed through GCM.
 * 
 * @author Michele Bonazza
 */
public class SendToGCMActivity extends FragmentActivity {

    private class CallGCM extends AsyncTask<String, Void, Void> {

        /*
         * (non-Javadoc)
         * 
         * @see android.os.AsyncTask#doInBackground(Params[])
         */
        @Override
        protected Void doInBackground(String... params) {
            String text = params[0];
            String type = params[1];
            String assignedID = PairOutboundActivity
                    .getAssignedID(SendToGCMActivity.this);
            HttpPost post = new HttpPost(GCM_URL);
            try {
                Utils.debug(String
                        .format("{\"registration_ids\": [\"%s\"], \"data\": {\"message\": \"%s\", \"sender\": \"%s\", \"type\": \"%s\"}}",
                                outboundDevice.name, JSONObject.quote(text),
                                JSONObject.quote(assignedID), type));
                post.setEntity(new StringEntity(
                        String.format(
                                "{\"delay_while_idle\": false, \"registration_ids\": [\"%s\"], \"data\": {\"message\": %s, \"sender\": %s, \"type\": \"%s\"}}",
                                outboundDevice.name, JSONObject.quote(text),
                                JSONObject.quote(assignedID), type)));
                post.setHeader("Content-Type", "application/json");
                post.setHeader(
                        "Authorization",
                        "key="
                                + SendToGCMActivity.this.getResources()
                                        .getString(
                                                R.string.android_shortener_key));
                String response = new DefaultHttpClient().execute(post,
                        new BasicResponseHandler());
                Utils.debug("response is %s", response);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (ClientProtocolException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

    }

    private static final String GCM_URL = "https://android.googleapis.com/gcm/send";
    private static final Pattern FLIPBOARD_PATTERN = Pattern
            .compile("\\w+\\:(.+)");
    private static AtomicInteger notificationCounter = new AtomicInteger();
    private String registrationID = "";
    private int registrationError = -1;
    private PairedDevice outboundDevice;
    private Tracker tracker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tracker = GoogleAnalytics.getInstance(this).getDefaultTracker();
        if ("".equals(registrationID)) {
            if (!Utils.isConnectedToTheInternet(this)) {
                Dialogs.noInternetConnection(this,
                        R.string.no_internet_sending, true);
            } else {
                GCMIntentService.registerWithGCM(this);
                new AsyncTask<Void, Void, Void>() {

                    private ProgressDialog dialog;

                    /*
                     * (non-Javadoc)
                     * 
                     * @see android.os.AsyncTask#onPreExecute()
                     */
                    @Override
                    protected void onPreExecute() {
                        dialog = ProgressDialog.show(
                                SendToGCMActivity.this,
                                getResources().getString(R.string.please_wait),
                                getResources().getString(
                                        R.string.wait_registration));
                    }

                    @Override
                    protected Void doInBackground(Void... params) {
                        try {
                            registrationID = GCMIntentService
                                    .getRegistrationID();
                        } catch (CantRegisterWithGCMException e) {
                            registrationError = e.getMessageID();
                        }
                        return null;
                    }

                    /*
                     * (non-Javadoc)
                     * 
                     * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
                     */
                    @Override
                    protected void onPostExecute(Void result) {
                        super.onPostExecute(result);
                        dialog.dismiss();
                        if (registrationError != -1) {
                            Dialogs.onRegistrationError(registrationError,
                                    SendToGCMActivity.this, true);
                        } else {
                            onNewIntent(getIntent());
                        }
                    }
                }.execute();
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onStart()
     */
    @Override
    protected void onStart() {
        super.onStart();
        EasyTracker.getInstance().activityStart(this);
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onStop()
     */
    @Override
    protected void onStop() {
        super.onStop();
        EasyTracker.getInstance().activityStop(this);
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onNewIntent(android.content.Intent)
     */
    @Override
    protected void onNewIntent(final Intent intent) {
        tracker = GoogleAnalytics.getInstance(this).getDefaultTracker();
        if (intent.hasExtra(Intent.EXTRA_TEXT)) {
            if (!Utils.isConnectedToTheInternet(this)) {
                Dialogs.noInternetConnection(this,
                        R.string.no_internet_sending, true);
            } else {
                // send to paired device if any
                try {
                    if (outboundDevice == null) {
                        Pair<PairedDevice, String> paired = loadOutboundPairing(this);
                        if (paired != null)
                            outboundDevice = paired.first;
                    }
                    if (outboundDevice != null) {
                        // share with other device
                        shareViaGCM(intent);
                        finish();
                        return;
                    }
                } catch (OptionalDataException e) {
                    e.printStackTrace();
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                // can't load paired device from file
                tracker.sendEvent("intent", "send_to_gcm", "no_paired_device",
                        0L);
                Dialogs.noPairedDevice(this);
            }
        } else {
            // user clicked on the notification
            notificationCounter.set(0);
            finish();
        }
    }

    /**
     * Loads the ID and name of the paired device in use to share content when
     * Whatsapp is not installed on this device.
     * 
     * @param activity
     *            the calling activity
     * @return the device loaded from file if any is configured,
     *         <code>null</code> otherwise
     * @throws OptionalDataException
     * @throws ClassNotFoundException
     * @throws IOException
     */
    static Pair<PairedDevice, String> loadOutboundPairing(Context activity)
            throws OptionalDataException, ClassNotFoundException, IOException {
        FileInputStream fis = activity.openFileInput("pairing");
        Scanner scanner = new Scanner(fis).useDelimiter("\\Z");
        JSONObject json;
        try {
            json = new JSONObject(scanner.next());
            String name = json.getString("name");
            String type = json.getString("type");
            String assignedID = json.getString("assignedID");
            return new Pair<PairedDevice, String>(new PairedDevice(assignedID,
                    name, type), assignedID);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void shareViaGCM(Intent intent) {
        String subject = intent.getExtras().getString(Intent.EXTRA_SUBJECT);
        String text = intent.getExtras().getString(Intent.EXTRA_TEXT);
        String type = intent.getExtras().getString(
                MainActivity.INTENT_TYPE_EXTRA);
        if (type == null)
            type = MainActivity.SHARE_VIA_WHATSAPP_EXTRA;
        if (mustIncludeSubject(subject, text)) {
            text = subject + " - " + text;
        }
        int sharedWhat = text.contains("http://") ? R.string.link
                : R.string.selection;
        Utils.debug("sharing with %s this: '%s'", outboundDevice.type, text);
        new CallGCM().execute(text, type);
        tracker.sendEvent("gcm", "share", sharedWhat == R.string.link ? "link"
                : "text", 0L);
        showNotification(sharedWhat,
                MainActivity.SHARE_VIA_WHATSAPP_EXTRA.equals(type));
    }

    @SuppressWarnings("deprecation")
    private void showNotification(int sharedWhat, boolean sharedViaWhatsapp) {
        String title = getString(R.string.whatshare);
        Intent onNotificationDiscarded = new Intent(this,
                SendToGCMActivity.class);
        PendingIntent notificationIntent = PendingIntent.getActivity(this, 0,
                onNotificationDiscarded, 0);
        Notification notification = null;
        int notificationIcon = sharedViaWhatsapp ? R.drawable.notification_icon
                : R.drawable.whatshare_logo_notification;
        int notificationNumber = notificationCounter.incrementAndGet();
        String content = getString(R.string.share_success,
                getString(sharedWhat), outboundDevice.type);
        // @formatter:off
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setSmallIcon(notificationIcon, 0)
                .setContentTitle(title)
                .setContentText(content)
                .setTicker(content)
                .setContentIntent(notificationIntent)
                .setDeleteIntent(PendingIntent.getActivity(this, 0, onNotificationDiscarded, 0))
                .setNumber(notificationNumber);
        // @formatter:on
        if (Build.VERSION.SDK_INT > 15) {
            notification = buildForJellyBean(builder);
        } else {
            notification = builder.getNotification();
        }
        notification.flags |= Notification.FLAG_AUTO_CANCEL;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        // cancel previous notification to clean up garbage in the status bar
        nm.cancel(notificationNumber - 1);
        nm.notify(notificationNumber, notification);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private Notification buildForJellyBean(NotificationCompat.Builder builder) {
        builder.setPriority(Notification.PRIORITY_MAX);
        return builder.build();
    }

    private boolean mustIncludeSubject(String subject, String text) {
        if (subject == null)
            return false;
        subject = subject.trim().toLowerCase(Locale.getDefault());
        text = text.trim().toLowerCase(Locale.getDefault());
        if (subject.length() == 0 || subject.equals(text)) {
            return false;
        }
        // test for flipboard
        Matcher matcher = FLIPBOARD_PATTERN.matcher(subject);
        if (matcher.matches() && matcher.groupCount() == 1) {
            String testIncluded = matcher.group(1).trim();
            Utils.debug("testing if '%s' contains '%s'", text, testIncluded);
            return !text.contains(testIncluded);
        }
        return true;
    }
}
