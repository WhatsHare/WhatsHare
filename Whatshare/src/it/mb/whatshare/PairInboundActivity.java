/**
 * ShareActivity.java Created on 16 Mar 2013 Copyright 2013 Michele Bonazza
 * <michele.bonazza@gmail.com>
 */
package it.mb.whatshare;

import static it.mb.whatshare.MainActivity.CHARACTERS;
import static it.mb.whatshare.MainActivity.CHAR_MAP;
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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
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
public class PairInboundActivity extends Activity {

    /**
     * An asynchronous task to call Google's URL shortener service.
     * 
     * @author Michele Bonazza
     * 
     */
    private class CallGooGl extends AsyncTask<String, Void, Void> {

        private ProgressDialog dialog;
        private DialogFragment resultDialog;

        /*
         * (non-Javadoc)
         * 
         * @see android.os.AsyncTask#onPreExecute()
         */
        @Override
        protected void onPreExecute() {
            dialog = ProgressDialog.show(PairInboundActivity.this,
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
            resultDialog.show(getFragmentManager(), "code");
        }

        /*
         * (non-Javadoc)
         * 
         * @see android.os.AsyncTask#doInBackground(Params[])
         */
        @Override
        protected Void doInBackground(String... params) {
            resultDialog = getPairedDeviceDialog(expand(params[0]));
            return null;
        }

        private Pair<PairedDevice, String> expand(String url) {
            HttpGet get = new HttpGet(String.format(EXPANDER_URL, url));
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
                        return new Pair<PairedDevice, String>(
                                new PairedDevice(decodeId(id),
                                        URLDecoder.decode(model, "UTF-8")),
                                decodeId(assignedID));
                    } else {
                        Utils.debug("Checksum bad");
                    }
                } else {
                    Utils.debug("wrong URL");
                }
            } catch (ClientProtocolException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (JSONException e) {
                // TODO Auto-generated catch block
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

        private DialogFragment getPairedDeviceDialog(
                final Pair<PairedDevice, String> device) {
            return new DialogFragment() {
                public Dialog onCreateDialog(Bundle savedInstanceState) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(
                            PairInboundActivity.this);
                    try {
                        builder.setMessage(getString(R.string.failed_pairing));
                        if (device != null) {
                            assignedID = device.second;
                            savePairing(device);
                            builder.setMessage(String.format(getResources()
                                    .getString(R.string.successful_pairing,
                                            device.first.type)));
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    builder.setPositiveButton(android.R.string.ok,
                            new OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    onNewIntent(getIntent());
                                }
                            });
                    return builder.create();
                }
            };
        }
    }

    private static final String EXPANDER_URL = MainActivity.SHORTENER_URL
            + "&shortUrl=http://goo.gl/%s";
    private static final Pattern EXPANDED_URL = Pattern
            .compile("http://([^/]+)/(\\d+)\\?model\\=([^&]+)&yourid=([a-zA-Z0-9\\-\\_]+)&id=([a-zA-Z0-9\\-\\_]+)");
    private static final int MAX_SHORTENED_URL_LENGTH = 5;
    private static String assignedID;
    private EditText inputCode;
    private List<Integer> randomSeed;

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

    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
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
        showPairingLayout();
    }

    private void showPairingLayout() {
        View view = getLayoutInflater().inflate(R.layout.activity_qrcode, null);
        setContentView(view);
        String paired = getOutboundPaired();
        if (paired != null) {
            ((TextView) findViewById(R.id.qr_instructions)).setText(String
                    .format(getString(R.string.new_outbound_instructions),
                            paired));
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
                    return onSubmitPressed(null);
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

    private void savePairing(Pair<PairedDevice, String> device)
            throws IOException, JSONException {
        FileOutputStream fos = openFileOutput("pairing", Context.MODE_PRIVATE);
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

    /**
     * Called when submit button is pressed.
     * 
     * @param view
     *            the parent view
     * @return <code>true</code> if the soft keyboard should stay where it is
     *         (i.e. if an error happened)
     */
    public boolean onSubmitPressed(View view) {
        String code = inputCode.getText().toString();
        Utils.debug("user submitted %s", code);
        if (code.length() != MAX_SHORTENED_URL_LENGTH) {
            inputCode.setError(getString(R.string.invalidshorturl));
            return true;
        }
        new CallGooGl().execute(code);
        return false;
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
                MainActivity.capitalize(Build.MANUFACTURER), Build.MODEL));
        Utils.debug("stuffing this into the QR code: %s", builder.toString());
        return builder.toString();
    }

}
