package it.mb.whatshare;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;

/**
 * Activity that displays the logo, web site and license information.
 * 
 * @author Michele Bonazza
 */
public class AboutActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.about, menu);
        return true;
    }

}
