package it.mb.whatshare;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Dummy activity that only forwards requests to {@link SendToGCMActivity}.
 * 
 * <p>
 * The purpose of this class is just to add an entry in Android's app choice
 * dialog when sharing something from another app.
 * 
 * @author Michele Bonazza
 */
public class SendToGCMAppChoiceActivity extends Activity {

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
        Intent newIntent = new Intent(this, SendToGCMActivity.class);
        newIntent.putExtras(intent);
        newIntent.putExtra(MainActivity.INTENT_TYPE_EXTRA, "c");
        startActivity(newIntent);
        finish();
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onBackPressed()
     */
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Utils.debug("sendGCM onback!");
        finish();
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onResume()
     */
    @Override
    protected void onResume() {
        // TODO Auto-generated method stub
        super.onResume();
        Utils.debug("sendGCM onResume!");
    }
}
