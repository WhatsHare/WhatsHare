package it.mb.whatshare;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.google.analytics.tracking.android.EasyTracker;

/**
 * Activity started when tapping on the notification coming from GCM that
 * forwards the shared content to Whatsapp by means of an Intent.
 * 
 * @author Michele Bonazza
 */
public class SendToAppActivity extends Activity {

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
        Utils.debug("SendToAppActivity.onNewIntent()");
        if (intent != null) {
            if (intent.getExtras() != null) {
                String message = intent.getStringExtra("message");
                if (message != null) {
                    startActivity(createPlainIntent(intent
                            .getStringExtra("message")));
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

    /**
     * Creates a share Intent that contains the argument <tt>message</tt> as
     * {@link Intent#EXTRA_TEXT}.
     * 
     * @param message
     *            the message to be stored by the returned intent
     * @return a share intent of type <tt>text/plain</tt> with the argument
     *         <tt>message</tt> stored as <tt>EXTRA_TEXT</tt>
     */
    public static Intent createPlainIntent(String message) {
        Intent i = new Intent(android.content.Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, message);
        return i;
    }

}
