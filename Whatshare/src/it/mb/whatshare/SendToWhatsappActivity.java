package it.mb.whatshare;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import com.google.analytics.tracking.android.EasyTracker;

/**
 * Activity started when tapping on the notification coming from GCM that
 * forwards the shared content to Whatsapp by means of an Intent.
 * 
 * @author Michele Bonazza
 */
public class SendToWhatsappActivity extends Activity {

    private static final String HIDE_MISSING_WHATSAPP_KEY = "hideMissingWhatsappDialog";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        onNewIntent(getIntent());
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onNewIntent(android.content.Intent)
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Utils.debug("SendToWhatsappActivity.onNewIntent()");
        if (intent != null) {
            if (intent.getExtras() != null) {
                String message = intent.getStringExtra("message");
                if (message != null) {
                    if (MainActivity.isWhatsappInstalled(this)) {
                        startActivity(createIntent(message));
                    } else {
                        if (isMissingWhatsappDialogHidden()) {
                            startActivity(createPlainIntent(intent
                                    .getStringExtra("message")));
                        } else {
                            showWhatsappMissingDialog(intent);
                        }
                    }
                } else {
                    Utils.debug("QUE?");
                }
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

    private Intent createIntent(String message) {
        Intent i = new Intent(android.content.Intent.ACTION_SEND);
        i.setPackage(MainActivity.WHATSAPP_PACKAGE);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, message);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        Log.d("main", "this is the message: " + message);
        return i;
    }

    private boolean isMissingWhatsappDialogHidden() {
        SharedPreferences pref = getSharedPreferences("it.mb.whatshare",
                Context.MODE_PRIVATE);
        return pref.getBoolean(HIDE_MISSING_WHATSAPP_KEY, false);
    }

    private Intent createPlainIntent(String message) {
        Intent i = new Intent(android.content.Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, message);
        return i;
    }

    private void showWhatsappMissingDialog(final Intent intent) {
        // @formatter:off
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.whatsapp_not_installed_title))
            .setMessage(getString(R.string.whatsapp_not_installed))
            .setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    startActivity(createPlainIntent(intent.getStringExtra("message")));
                }
            })
            .setNegativeButton(getString(R.string.whatsapp_not_installed_dont_mind), new DialogInterface.OnClickListener() {
                
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    SharedPreferences pref = getSharedPreferences("it.mb.whatshare", Context.MODE_PRIVATE);
                    pref.edit().putBoolean(HIDE_MISSING_WHATSAPP_KEY, true).commit();
                    startActivity(createPlainIntent(intent.getStringExtra("message")));
                }
            })
            .show();
        // @formatter:on
    }

}
