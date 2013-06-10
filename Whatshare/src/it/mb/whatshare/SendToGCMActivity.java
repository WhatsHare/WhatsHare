package it.mb.whatshare;

import it.mb.whatshare.MainActivity.PairedDevice;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.OptionalDataException;
import java.io.UnsupportedEncodingException;
import java.util.Locale;
import java.util.Scanner;
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
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Pair;

/**
 * Activity called when sharing content from this device to be sent to the
 * Android device where Whatsapp is installed through GCM.
 * 
 * @author Michele Bonazza
 */
public class SendToGCMActivity extends Activity {

    private class CallGCM extends AsyncTask<String, Void, Void> {

        /*
         * (non-Javadoc)
         * 
         * @see android.os.AsyncTask#doInBackground(Params[])
         */
        @Override
        protected Void doInBackground(String... params) {
            String text = params[0];
            String assignedID = PairInboundActivity
                    .getAssignedID(SendToGCMActivity.this);
            HttpPost post = new HttpPost(GCM_URL);
            try {
                Utils.debug(String
                        .format("{\"registration_ids\": [\"%s\"], \"data\": {\"message\": \"%s\", \"sender\": \"%s\"}}",
                                outboundDevice.name, JSONObject.quote(text),
                                JSONObject.quote(assignedID)));
                post.setEntity(new StringEntity(
                        String.format(
                                "{\"registration_ids\": [\"%s\"], \"data\": {\"message\": %s, \"sender\": %s}}",
                                outboundDevice.name, JSONObject.quote(text),
                                JSONObject.quote(assignedID))));
                post.setHeader("Content-Type", "application/json");
                post.setHeader("Authorization", "key=" + MainActivity.API_KEY);
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
    private String registrationID = "";
    private PairedDevice outboundDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if ("".equals(registrationID)) {
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
                    dialog = ProgressDialog.show(SendToGCMActivity.this,
                            getResources().getString(R.string.please_wait),
                            getResources()
                                    .getString(R.string.wait_registration));
                }

                @Override
                protected Void doInBackground(Void... params) {
                    registrationID = GCMIntentService.getRegistrationID();
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
                    onNewIntent(getIntent());
                }
            }.execute();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onNewIntent(android.content.Intent)
     */
    @Override
    protected void onNewIntent(final Intent intent) {
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
        showNoPairedDeviceDialog();
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
            return new Pair<PairedDevice, String>(new PairedDevice(name, type),
                    assignedID);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void showNoPairedDeviceDialog() {
        new DialogFragment() {
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                AlertDialog.Builder builder = new AlertDialog.Builder(
                        SendToGCMActivity.this);
                builder.setMessage(getString(R.string.no_paired_device));
                builder.setPositiveButton(android.R.string.ok,
                        new OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                Intent i = new Intent(SendToGCMActivity.this,
                                        PairInboundActivity.class);
                                startActivity(i);
                            }
                        });
                return builder.create();
            }
        }.show(getFragmentManager(), "no paired device");
    }

    @SuppressWarnings("deprecation")
    private void shareViaGCM(Intent intent) {
        String subject = intent.getExtras().getString(Intent.EXTRA_SUBJECT);
        String text = intent.getExtras().getString(Intent.EXTRA_TEXT);
        if (mustIncludeSubject(subject, text)) {
            text = subject + " - " + text;
        }
        int sharedWhat = text.contains("http://") ? R.string.link
                : R.string.selection;
        Utils.debug("sharing with %s this: '%s'", outboundDevice.type, text);
        new CallGCM().execute(text);
        String title = getString(R.string.app_name);
        PendingIntent notificationIntent = PendingIntent.getActivity(this, 0,
                new Intent(), 0);
        Notification notification = null;
        // @formatter:off
        Notification.Builder builder = new Notification.Builder(this)
                .setSmallIcon(R.drawable.notification_icon, 0)
                .setContentTitle(title)
                .setContentText(String.format(getString(R.string.share_success), getString(sharedWhat), outboundDevice.type))
                .setContentIntent(notificationIntent);
        // @formatter:on
        if (Build.VERSION.SDK_INT > 15) {
            notification = buildForJellyBean(builder);
        } else {
            notification = builder.getNotification();
        }
        notification.flags |= Notification.FLAG_AUTO_CANCEL;
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                .notify(0, notification);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private Notification buildForJellyBean(Notification.Builder builder) {
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
