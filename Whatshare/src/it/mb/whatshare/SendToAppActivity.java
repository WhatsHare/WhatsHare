/**
 * SendToAppActivity.java Created on 16 Jun 2013 Copyright 2013 Michele Bonazza
 * <emmepuntobi@gmail.com>
 * 
 * Copyright 2013 Michele Bonazza <emmepuntobi@gmail.com> This file is part of WhatsHare.
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
