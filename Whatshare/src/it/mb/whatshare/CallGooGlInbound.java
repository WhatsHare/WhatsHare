/**
 * CallGooGlInbound.java Created on 13 Jun 2013
 * 
 * Copyright 2013 Michele Bonazza <emmepuntobi@gmail.com>
 * 
 * This file is part of WhatsHare.
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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.Build;

/**
 * An asynchronous task to call Google's URL shortener service in order to get a
 * pairing code for a new inbound device.
 * 
 * @author Michele Bonazza
 */
class CallGooGlInbound extends AsyncTask<int[], Void, Void> {

    /**
     * All valid characters for URLs used by this app.
     */
    static final char[] CHARACTERS = new char[] { '0', '1', '2', '3', '4', '5',
            '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i',
            'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v',
            'w', 'x', 'y', 'z', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I',
            'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V',
            'W', 'X', 'Y', 'Z', '-', '_' };
    /**
     * Map that indexes every valid character by its position in the map itself.
     */
    static final HashMap<Character, Integer> CHAR_MAP = new HashMap<Character, Integer>() {
        private static final long serialVersionUID = 1599960929705590053L;
        {
            for (int i = 0; i < CHARACTERS.length; i++) {
                put(CHARACTERS[i], i);
            }
        }
    };

    private static final int RETRY_COUNT = 3;
    private static final long RETRY_SLEEP_TIME = 1000L;

    private final MainActivity mainActivity;
    private ProgressDialog dialog;
    private PairedDevice deviceToBePaired;
    private String googl;
    private String registrationID = "";
    private int registrationError = -1;

    /**
     * Creates a new task that will call goo.gl to create a new pairing code for
     * the argument device.
     * 
     * @param mainActivity
     *            the caller activity
     * @param deviceName
     *            the name chosen by the user for the device being paired
     * @param deviceType
     *            the model of the device, as suggested by the device itself
     */
    CallGooGlInbound(MainActivity mainActivity, String deviceName,
            String deviceType) {
        this.mainActivity = mainActivity;
        deviceToBePaired = new PairedDevice(PairedDevice.getNextID(),
                deviceName, deviceType);
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.os.AsyncTask#onPreExecute()
     */
    @Override
    protected void onPreExecute() {
        dialog = ProgressDialog.show(
                this.mainActivity,
                this.mainActivity.getResources()
                        .getString(R.string.please_wait),
                this.mainActivity.getResources().getString(
                        R.string.wait_message));
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
            Dialogs.onRegistrationError(registrationError, mainActivity, false);
        } else {
            Dialogs.onObtainPairingCode(googl, mainActivity);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.os.AsyncTask#doInBackground(Params[])
     */
    @Override
    protected Void doInBackground(int[]... params) {
        try {
            registrationID = GCMIntentService.getRegistrationID();
            String encodedID = encrypt(params[0], registrationID.toCharArray());
            String encodedAssignedID = encrypt(params[0],
                    String.valueOf(deviceToBePaired.id.hashCode())
                            .toCharArray());
            Utils.debug(
                    "shortening, this device's encodedID is %s, paired devices's"
                            + " encodedAssignedID is %s", encodedID,
                    encodedAssignedID);

            googl = shorten(encodedID, encodedAssignedID);
            if (googl != null) {
                googl = googl.substring(googl.lastIndexOf('/') + 1);
                saveInboundPairing(deviceToBePaired);
            }

            mainActivity.refreshInboundDevicesList();
        } catch (CantRegisterWithGCMException e) {
            registrationError = e.getMessageID();
        }
        return null;
    }

    private String shorten(String encodedID, String encodedAssignedID) {
        HttpPost post = new HttpPost(String.format(
                MainActivity.SHORTENER_URL,
                mainActivity.getResources().getString(
                        R.string.android_shortener_key)));
        String shortURL = null;
        int tries = 1;
        try {
            post.setEntity(new StringEntity(String.format(
                    "{\"longUrl\": \"%s\"}",
                    getURL(encodedID, encodedAssignedID))));
            post.setHeader("Content-Type", "application/json");

            long timeout = RETRY_SLEEP_TIME * tries;
            HttpParams params = new BasicHttpParams();
            DefaultHttpClient client = updateTimeout(params, timeout);

            String response = null;
            while (response == null && tries < RETRY_COUNT) {
                try {
                    response = client.execute(post, new BasicResponseHandler());
                } catch (IOException e) {
                    // maybe just try again...
                    Utils.debug("attempt %d failed... waiting", tries);
                    try {
                        // life is too short for exponential backoff
                        Thread.sleep(timeout);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                    tries++;
                    timeout = RETRY_SLEEP_TIME * tries;
                    client = updateTimeout(params, timeout);
                }
            }
            Utils.debug("response is %s", response);

            if (response != null) {
                JSONObject jsonResponse = new JSONObject(response);
                shortURL = jsonResponse.getString("id");
            } else if (MainActivity.DEBUG_FAILED_REQUESTS) {
                Utils.debug("attempt %d failed, giving up", RETRY_COUNT);
                debugPost(post, client);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return shortURL;
    }

    private DefaultHttpClient updateTimeout(HttpParams params, long timeout) {
        HttpConnectionParams.setConnectionTimeout(params, (int) timeout);
        HttpConnectionParams.setSoTimeout(params, (int) timeout * 3);
        DefaultHttpClient client = new DefaultHttpClient(params);
        client.setHttpRequestRetryHandler(new DefaultHttpRequestRetryHandler(0,
                false));
        return client;
    }

    private void debugPost(HttpPost post, HttpClient client) {
        post.setURI(URI.create(MainActivity.DEBUG_FAILED_REQUESTS_SERVER));
        try {
            client.execute(post, new BasicResponseHandler());
        } catch (IOException e1) {
            e1.printStackTrace();
        }
    }

    private String encrypt(int[] sharedSecret, char[] content) {
        for (int i = 0; i < content.length; i++) {
            int charIndex = CHAR_MAP.get(content[i]);
            int secret = sharedSecret[i % MainActivity.SHARED_SECRET_SIZE];
            content[i] = CHARACTERS[(charIndex + secret) % CHARACTERS.length];
        }
        return new String(content);
    }

    private static String getURL(String encodedId, String deviceAssignedID) {
        StringBuilder builder = new StringBuilder("http://");
        Random generator = new Random();
        int sum = 0;
        for (int i = 0; i < 8; i++) {
            char rand = CHARACTERS[generator.nextInt(CHARACTERS.length)];
            builder.append(rand);
            // no idea why they set lowercase for domain names...
            sum += CHAR_MAP.get(Character.toLowerCase(rand));
        }
        builder.append("/");
        builder.append(sum);
        builder.append("?model=");
        try {
            builder.append(URLEncoder.encode(
                    String.format("%s %s",
                            Utils.capitalize(Build.MANUFACTURER), Build.MODEL),
                    "UTF-8").replaceAll("\\+", "%20"));
            builder.append("&yourid=");
            builder.append(deviceAssignedID);
            builder.append("&id=");
            builder.append(encodedId);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return builder.toString();
    }

    private void saveInboundPairing(PairedDevice newDevice) {
        List<PairedDevice> alreadyPaired = new ArrayList<PairedDevice>();
        alreadyPaired = mainActivity.loadInboundPairing();
        alreadyPaired.add(newDevice);
        mainActivity.writePairedInboundFile(alreadyPaired);
        deviceToBePaired = null;
    }
}