/**
 * PairOutboundActivity.java Created on 16 Mar 2013 Copyright 2013 Michele
 * Bonazza <emmepuntobi@gmail.com>
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

import static it.mb.whatshare.CallGooGlInbound.CHARACTERS;
import static it.mb.whatshare.CallGooGlInbound.CHAR_MAP;
import it.mb.whatshare.MainActivity.PairedDevice;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OptionalDataException;
import java.io.PrintStream;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.text.InputFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.google.analytics.tracking.android.EasyTracker;
import com.google.analytics.tracking.android.GoogleAnalytics;
import com.google.analytics.tracking.android.Tracker;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

/**
 * Activity displayed when pairing this device so that it can share stuff to a
 * paired Android device on which Whatsapp is installed.
 * 
 * @author Michele Bonazza
 * 
 */
public class PairOutboundActivity extends FragmentActivity {

    /**
     * The name of the file that keeps reference of the device (which has
     * Whatsapp installed) used to send messages to.
     */
    public static final String PAIRING_FILE_NAME = "pairing";

    /**
     * An asynchronous task to call Google's URL shortener service in order to
     * retrieve information stored within the expanded URL by the outbound
     * device (the one where Whatsapp is installed).
     * 
     * @author Michele Bonazza
     */
    private class CallGooGlOutbound extends AsyncTask<String, Void, Void> {

        private ProgressDialog dialog;
        private Pair<PairedDevice, String> pairedPair;

        /*
         * (non-Javadoc)
         * 
         * @see android.os.AsyncTask#onPreExecute()
         */
        @Override
        protected void onPreExecute() {
            dialog = ProgressDialog.show(PairOutboundActivity.this,
                    getResources().getString(R.string.please_wait),
                    getResources().getString(R.string.wait_message));
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
            Dialogs.onPairingOutbound(pairedPair, PairOutboundActivity.this);
        }

        /*
         * (non-Javadoc)
         * 
         * @see android.os.AsyncTask#doInBackground(Params[])
         */
        @Override
        protected Void doInBackground(String... params) {
            pairedPair = expand(params[0]);
            return null;
        }

        private Pair<PairedDevice, String> expand(String url) {
            HttpGet get = new HttpGet(String.format(
                    EXPANDER_URL,
                    PairOutboundActivity.this.getResources().getString(
                            R.string.android_shortener_key), url));
            String response;
            try {
                response = new DefaultHttpClient().execute(get,
                        new BasicResponseHandler());
                String longUrl = new JSONObject(response).getString("longUrl");
                Utils.debug("response is %s", longUrl);
                Matcher matcher = EXPANDED_URL.matcher(longUrl);
                if (matcher.matches()) {
                    String domain = matcher.group(1);
                    int sum = Integer.parseInt(matcher.group(2));
                    String model = matcher.group(3);
                    String assignedID = matcher.group(4);
                    String id = matcher.group(5);
                    Utils.debug(
                            "domain=%s, sum=%d, model=%s, assignedID=%s, id=%s",
                            domain, sum, model, assignedID, id);
                    if (checksum(domain, sum)) {
                        Utils.debug("Checksum ok!");
                        tracker.sendEvent("googl", "parse_expanded", "ok", 0L);
                        return new Pair<PairedDevice, String>(
                                new PairedDevice(decodeId(id),
                                        URLDecoder.decode(model, "UTF-8")),
                                decodeId(assignedID));
                    } else {
                        tracker.sendEvent("googl", "parse_expanded",
                                "bad_checksum", 0L);
                        Utils.debug("Checksum bad");
                    }
                } else {
                    tracker.sendEvent("googl", "parse_expanded", "wrong_url",
                            0L);
                    Utils.debug("wrong URL");
                }
            } catch (ClientProtocolException e) {
                e.printStackTrace();
            } catch (IOException e) {
                tracker.sendEvent("googl", "parse_expanded",
                        "ioexception " + e.getMessage(), 0L);
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return null;
        }

        private boolean checksum(String toCheck, int toMatch) {
            int sum = 0;
            for (int i = 0; i < toCheck.length(); i++) {
                sum += CHAR_MAP.get(toCheck.charAt(i));
            }
            return sum == toMatch;
        }

        private String decodeId(String id) {
            StringBuilder decoded = new StringBuilder();
            for (int i = 0; i < id.length(); i++) {
                int c = CHAR_MAP.get(id.charAt(i));
                int index = (c - randomSeed.get(i % randomSeed.size()))
                        % CHARACTERS.length;
                if (index < 0) {
                    index = CHARACTERS.length + index;
                }
                decoded.append(CHARACTERS[index]);
            }
            return decoded.toString();
        }
    }

    private static final String EXPANDER_URL = MainActivity.SHORTENER_URL
            + "&shortUrl=http://goo.gl/%s";
    private static final Pattern EXPANDED_URL = Pattern
            .compile("http://([^/]+)/(\\d+)\\?model\\=([^&]+)&yourid=([a-zA-Z0-9\\-\\_]+)&id=([a-zA-Z0-9\\-\\_]+)");
    private static final int MAX_SHORTENED_URL_LENGTH = 6;
    private static String assignedID;
    private EditText inputCode;
    private List<Integer> randomSeed;
    private Tracker tracker;
    private boolean keepKeyboardVisible;

    /**
     * Returns the ID that the currently paired outbound device has given to
     * this device and must thus be included in messages sent to that device.
     * 
     * @param context
     *            the current activity to access file with
     * @return the ID to be included in all messages sent to the currently
     *         paired outbound device, <code>null</code> if no device is
     *         currently paired in outbound
     */
    static String getAssignedID(ContextWrapper context) {
        if (assignedID == null) {
            try {
                Pair<PairedDevice, String> paired = SendToGCMActivity
                        .loadOutboundPairing(context);
                assignedID = paired.second;
            } catch (OptionalDataException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return assignedID;
    }

    /**
     * Sets the ID (name) chosen by the user for the device that is currently
     * being paired as the outbound device.
     * 
     * @param assignedID
     *            the user-chosen device ID (human readable name)
     */
    static void setAssignedID(String assignedID) {
        PairOutboundActivity.assignedID = assignedID;
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Utils.checkDebug(this);
        onNewIntent(getIntent());
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onNewIntent(android.content.Intent)
     */
    @Override
    protected void onNewIntent(Intent intent) {
        tracker = GoogleAnalytics.getInstance(this).getDefaultTracker();
        showPairingLayout();
    }

    private void showPairingLayout() {
        View view = getLayoutInflater().inflate(R.layout.activity_qrcode, null);
        setContentView(view);
        String paired = getOutboundPaired();
        if (paired != null) {
            ((TextView) findViewById(R.id.qr_instructions)).setText(getString(
                    R.string.new_outbound_instructions, paired));
        }
        inputCode = (EditText) findViewById(R.id.inputCode);

        inputCode.setFilters(new InputFilter[] { new InputFilter() {
            /*
             * (non- Javadoc )
             * 
             * @see android .text. InputFilter # filter( java .lang.
             * CharSequence , int, int, android .text. Spanned , int, int)
             */
            @Override
            public CharSequence filter(CharSequence source, int start, int end,
                    Spanned dest, int dstart, int dend) {
                if (source instanceof SpannableStringBuilder) {
                    SpannableStringBuilder sourceAsSpannableBuilder = (SpannableStringBuilder) source;
                    for (int i = end - 1; i >= start; i--) {
                        char currentChar = source.charAt(i);
                        if (!Character.isLetterOrDigit(currentChar)) {
                            sourceAsSpannableBuilder.delete(i, i + 1);
                        }
                    }
                    return source;
                } else {
                    StringBuilder filteredStringBuilder = new StringBuilder();
                    for (int i = 0; i < end; i++) {
                        char currentChar = source.charAt(i);
                        if (Character.isLetterOrDigit(currentChar)) {
                            filteredStringBuilder.append(currentChar);
                        }
                    }
                    return filteredStringBuilder.toString();
                }
            }
        }, new InputFilter.LengthFilter(MAX_SHORTENED_URL_LENGTH) });

        inputCode.setOnEditorActionListener(new OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId,
                    KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    onSubmitPressed(null);
                    return keepKeyboardVisible;
                }
                return false;
            }
        });

        final ImageView qrWrapper = (ImageView) findViewById(R.id.qr_code);
        qrWrapper.getViewTreeObserver().addOnGlobalLayoutListener(
                new OnGlobalLayoutListener() {

                    private boolean createdQRCode = false;

                    @Override
                    public void onGlobalLayout() {
                        if (!createdQRCode) {
                            try {
                                Bitmap qrCode = generateQRCode(
                                        generateRandomSeed(),
                                        getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT ? qrWrapper
                                                .getHeight() : qrWrapper
                                                .getWidth());
                                if (qrCode != null) {
                                    qrWrapper.setImageBitmap(qrCode);
                                }
                                createdQRCode = true;
                            } catch (WriterException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
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

    private String getOutboundPaired() {
        try {
            Pair<PairedDevice, String> paired = SendToGCMActivity
                    .loadOutboundPairing(this);
            if (paired != null) {
                assignedID = paired.second;
                return paired.first.type;
            }
        } catch (OptionalDataException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            // it's ok
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Saves the argument <tt>device</tt> as the (only) configured outbound
     * device.
     * 
     * <p>
     * If <tt>device</tt> is <code>null</code>, the currently configured device
     * is deleted.
     * 
     * @param device
     *            the device to be stored, or <code>null</code> if the current
     *            association must be discarded
     * @param context
     *            the application's context (used to open the association file
     *            with)
     * @throws IOException
     *             in case something is wrong with the file
     * @throws JSONException
     *             in case something is wrong with the argument <tt>device</tt>
     */
    public static void savePairing(Pair<PairedDevice, String> device,
            Context context) throws IOException, JSONException {
        if (device == null) {
            Utils.debug("deleting outbound device... %s",
                    context.deleteFile(PAIRING_FILE_NAME) ? "success" : "fail");
        } else {
            FileOutputStream fos = context.openFileOutput(PAIRING_FILE_NAME,
                    Context.MODE_PRIVATE);
            // @formatter:off
        JSONObject json = new JSONObject()
                                .put("name", device.first.name)
                                .put("type", device.first.type)
                                .put("assignedID", device.second);
        // @formatter:on
            PrintStream writer = new PrintStream(fos);
            writer.append(json.toString());
            writer.flush();
            writer.close();
        }
    }

    /**
     * Called when submit button below the QR code is pressed or after the
     * pairing code typed by the user has been submitted.
     * 
     * <p>
     * This method also decides whether the soft keyboard should be kept visible
     * (in case the pairing code typed by the user is not valid).
     * 
     * @param view
     *            the parent view
     */
    public void onSubmitPressed(View view) {
        String code = inputCode.getText().toString();
        Utils.debug("user submitted %s", code);
        if (code.length() > MAX_SHORTENED_URL_LENGTH) {
            inputCode.setError(getString(R.string.invalidshorturl));
            keepKeyboardVisible = true;
        } else {
            keepKeyboardVisible = false;
        }
        new CallGooGlOutbound().execute(code);
    }

    private Bitmap generateQRCode(String dataToEncode, int imageViewSize)
            throws WriterException {
        MultiFormatWriter writer = new MultiFormatWriter();
        BitMatrix matrix = writer.encode(dataToEncode, BarcodeFormat.QR_CODE,
                imageViewSize, imageViewSize);
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            int offset = y * width;
            for (int x = 0; x < width; x++) {
                pixels[offset + x] = matrix.get(x, y) ? 0xff000000 : 0xffffffff;
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height,
                Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }

    private String generateRandomSeed() {
        StringBuilder builder = new StringBuilder();
        String whitespace = "";
        Random gen = new Random();
        // I know, it's 4 (half) bytes so I could stuff them into a single int,
        // but I'm lazy and er.. it's open to future larger keys?
        randomSeed = new ArrayList<Integer>();
        for (int i = 0; i < 4; i++) {
            builder.append(whitespace);
            int nextInt = gen.nextInt(128);
            builder.append(nextInt);
            randomSeed.add(nextInt);
            whitespace = " ";
        }
        builder.append(String.format(" %s %s",
                Utils.capitalize(Build.MANUFACTURER), Build.MODEL));
        Utils.debug("stuffing this into the QR code: %s", builder.toString());
        return builder.toString();
    }

}
