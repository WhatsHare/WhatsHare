package it.mb.whatshare;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OptionalDataException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ListFragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.Pair;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The only activity: it can be created when tapping on a notification (and an
 * Intent is routed to this activity, which in turn creates a new Whatsapp
 * activity) or by tapping the app's icon, in which case the pairing screen is
 * displayed.
 * 
 * @author Michele Bonazza
 * 
 */
public class MainActivity extends Activity {

    /**
     * The file name of the list containing all inbound devices.
     */
    public static final String INBOUND_DEVICES_FILENAME = "inbound";

    /**
     * If set, all network requests are routed to localhost (on port 80) upon
     * failure (to have a log of all requests that failed). An HTTP server must
     * be running (for instance, echo_server.py in the extras_not_in_apk folder)
     */
    public static final boolean DEBUG_FAILED_REQUESTS = false;

    /**
     * The IP of the server that logs failed requests in case
     * {@link #DEBUG_FAILED_REQUESTS} is set.
     */
    public static final String DEBUG_FAILED_REQUESTS_SERVER = "http://192.168.0.8";

    /**
     * A device paired with this one to share stuff on whatsapp.
     * 
     * @author Michele Bonazza
     */
    static class PairedDevice implements Serializable {
        /**
         * 
         */
        private static final long serialVersionUID = 7662420062946826108L;
        /**
         * The name set for the device by the user, must be unique so it's used
         * as this device's ID.
         */
        final String name;
        /**
         * The device's type as specified by the device itself (e.g. 'Chrome
         * Whatshare Extension').
         */
        final String type;

        /**
         * Creates a new device.
         * 
         * @param name
         *            the name set for the device by the user, must be unique so
         *            it's used as this device's ID.
         * @param type
         *            the device's type as specified by the device itself (e.g.
         *            'Chrome Whatshare Extension')
         */
        PairedDevice(String name, String type) {
            this.name = name;
            this.type = type;
        }

        public String toString() {
            return new StringBuilder(name).append(", type(").append(type)
                    .append(")").toString();
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            return result;
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            PairedDevice other = (PairedDevice) obj;
            if (name == null) {
                if (other.name != null)
                    return false;
            } else if (!name.equals(other.name))
                return false;
            return true;
        }
    }

    /**
     * The list fragment containing all inbound devices.
     * 
     * @author Michele Bonazza
     * 
     */
    public static class InboundDevicesList extends ListFragment {

        /*
         * (non-Javadoc)
         * 
         * @see
         * android.app.ListFragment#onCreateView(android.view.LayoutInflater,
         * android.view.ViewGroup, android.os.Bundle)
         */
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            return inflater.inflate(R.layout.inbound_devices_fragment,
                    container);
        }

        /*
         * (non-Javadoc)
         * 
         * @see android.app.Fragment#onAttach(android.app.Activity)
         */
        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            setListAdapter(((MainActivity) activity).getListAdapter());
        }

        /*
         * (non-Javadoc)
         * 
         * @see android.app.Fragment#onActivityCreated(android.os.Bundle)
         */
        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
            registerForContextMenu(getListView());
        }

    }

    /**
     * An asynchronous task to call Google's URL shortener service.
     * 
     * @author Michele Bonazza
     * 
     */
    private class CallGooGl extends AsyncTask<int[], Void, Void> {

        private static final int RETRY_COUNT = 3;
        private static final long RETRY_SLEEP_TIME = 1000L;
        private ProgressDialog dialog;
        private DialogFragment resultDialog;

        /*
         * (non-Javadoc)
         * 
         * @see android.os.AsyncTask#onPreExecute()
         */
        @Override
        protected void onPreExecute() {
            dialog = ProgressDialog.show(MainActivity.this, getResources()
                    .getString(R.string.please_wait),
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
        protected Void doInBackground(int[]... params) {
            registrationID = GCMIntentService.getRegistrationID();
            String encodedID = encrypt(params[0], registrationID.toCharArray());
            String encodedAssignedID = encrypt(params[0],
                    String.valueOf(deviceToBePaired.name.hashCode())
                            .toCharArray());
            Utils.debug(
                    "shortening, this device's encodedID is %s, paired devices's"
                            + " encodedAssignedID is %s", encodedID,
                    encodedAssignedID);
            String googl = shorten(encodedID, encodedAssignedID);
            if (googl != null) {
                googl = googl.substring(googl.lastIndexOf('/') + 1);
                try {
                    saveInboundPairing(deviceToBePaired);
                } catch (OptionalDataException e) {
                    e.printStackTrace();
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    MainActivity.this.getListAdapter().notifyDataSetChanged();
                }
            });
            resultDialog = getCodeDialog(googl);
            return null;
        }

        private String shorten(String encodedID, String encodedAssignedID) {
            HttpPost post = new HttpPost(SHORTENER_URL);
            String shortURL = null;
            int tries = 0;
            try {
                post.setEntity(new StringEntity(String.format(
                        "{\"longUrl\": \"%s\"}",
                        getURL(encodedID, encodedAssignedID))));
                post.setHeader("Content-Type", "application/json");
                DefaultHttpClient client = new DefaultHttpClient();
                client.setHttpRequestRetryHandler(new DefaultHttpRequestRetryHandler(
                        0, false));
                String response = null;
                while (response == null && tries < RETRY_COUNT) {
                    try {
                        response = client.execute(post,
                                new BasicResponseHandler());
                    } catch (IOException e) {
                        // maybe just try again...
                        tries++;
                        Utils.debug("attempt %d failed... waiting", tries);
                        try {
                            // life is too short for exponential backoff
                            Thread.sleep(RETRY_SLEEP_TIME * tries);
                        } catch (InterruptedException e1) {
                            e1.printStackTrace();
                        }
                    }
                }
                Utils.debug("response is %s", response);
                if (response != null) {
                    JSONObject jsonResponse = new JSONObject(response);
                    shortURL = jsonResponse.getString("id");
                } else if (DEBUG_FAILED_REQUESTS) {
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

        private void debugPost(HttpPost post, HttpClient client) {
            post.setURI(URI.create(DEBUG_FAILED_REQUESTS_SERVER));
            try {
                client.execute(post, new BasicResponseHandler());
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }

        private DialogFragment getCodeDialog(final String googl) {
            return new RetainedDialogFragment() {
                public Dialog onCreateDialog(Bundle savedInstanceState) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(
                            MainActivity.this);
                    if (googl != null) {
                        builder.setMessage(String.format(getResources()
                                .getString(R.string.code_dialog), googl));
                    } else {
                        builder.setMessage(getResources().getString(
                                R.string.code_dialog_fail));
                    }
                    builder.setPositiveButton(android.R.string.ok,
                            new OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    if (googl != null) {
                                        Resources res = getResources();
                                        String howManyTotal = res
                                                .getQuantityString(
                                                        R.plurals.added_device,
                                                        adapter.getCount(),
                                                        adapter.getCount());
                                        Toast.makeText(
                                                getActivity(),
                                                res.getString(
                                                        R.string.device_paired,
                                                        howManyTotal),
                                                Toast.LENGTH_LONG).show();
                                    }
                                }
                            });
                    return builder.create();
                }
            };
        }
    }

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
    /**
     * The name of Whatsapp's package.
     */
    static final String WHATSAPP_PACKAGE = "com.whatsapp";
    /**
     * The API key for the Android app.
     */
    static final String API_KEY = "AIzaSyDrxJwFE70m3yXgUCkao1YSmVx5K_KDfCg";
    /**
     * Where Google shortener is at.
     */
    static final String SHORTENER_URL = "https://www.googleapis.com/urlshortener/v1/url?key="
            + API_KEY;
    private static final String VALID_DEVICE_ID = "[A-Za-z0-9\\-\\_\\s]{1,30}";
    private static final int QR_CODE_SCANNED = 0;
    private static final int SHARED_SECRET_SIZE = 4;
    private String registrationID = "";
    private PairedDevice outboundDevice, deviceToBeUnpaired, deviceToBePaired;
    private List<PairedDevice> inboundDevices = new ArrayList<PairedDevice>();
    private ArrayAdapter<String> adapter;

    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onNewIntent(android.content.Intent)
     */
    @Override
    protected void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        // app was started through launcher
        updateLayout();
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onCreateContextMenu(android.view.ContextMenu,
     * android.view.View, android.view.ContextMenu.ContextMenuInfo)
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.add(R.string.unpair);
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        deviceToBeUnpaired = inboundDevices.get(info.position);
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onContextItemSelected(android.view.MenuItem)
     */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        new RetainedDialogFragment() {
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                // @formatter:off
                AlertDialog.Builder builder = new AlertDialog.Builder(
                        MainActivity.this)
                    .setMessage(
                        getResources().getString(
                                R.string.remove_inbound_paired_message,
                                deviceToBeUnpaired.name))
                    .setPositiveButton(
                        android.R.string.ok, new OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                removePaired(deviceToBeUnpaired);
                                deviceToBeUnpaired = null;
                            }
                        })
                    .setNegativeButton(android.R.string.cancel, null);
                // @formatter:on
                return builder.create();
            }
        }.show(getFragmentManager(), "removeInbound");

        return super.onContextItemSelected(item);
    }

    private void removePaired(PairedDevice device) {
        Utils.debug("removing %s... success? %s", device.name,
                inboundDevices.remove(device));
        try {
            writePairedInboundFile(inboundDevices);
        } catch (IOException e) {
            // TODO should notify the user
            e.printStackTrace();
        }
        BaseAdapter listAdapter = getListAdapter();
        listAdapter.notifyDataSetChanged();
    }

    private void updateLayout() {
        if (outboundDevice == null) {
            try {
                Pair<PairedDevice, String> paired = SendToGCMActivity
                        .loadOutboundPairing(this);
                if (paired != null)
                    outboundDevice = paired.first;
            } catch (OptionalDataException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (FileNotFoundException e) {
                // it's ok
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (!isWhatsappInstalled(this) && outboundDevice == null) {
            // show the QR code by default on tablets with no whatsapp installed
            showOutboundConfiguration();
        } else {
            TextView outboundView = (TextView) findViewById(R.id.outbound_device);
            final boolean outboundConfigured = outboundDevice != null;
            outboundView.setOnLongClickListener(new OnLongClickListener() {

                @Override
                public boolean onLongClick(View v) {
                    if (outboundConfigured) {
                        showRemoveOutboundDialog();
                    } else {
                        showOutboundConfiguration();
                    }
                    return true;
                }
            });
            if (outboundDevice != null) {
                outboundView.setText(outboundDevice.type);
            } else {
                outboundView.setText(R.string.no_device);
            }
            if (!isWhatsappInstalled(this)) {
                // no inbound device can ever be configured
                int[] toRemove = { R.id.inbound, R.id.inboundDevices };
                for (int item : toRemove) {
                    View view = findViewById(item);
                    ((ViewGroup) view.getParent()).removeView(view);
                }
            }
        }
    }

    private void showRemoveOutboundDialog() {
        new RetainedDialogFragment() {
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                // @formatter:off
                AlertDialog.Builder builder = new AlertDialog.Builder(
                        MainActivity.this)
                    .setMessage(
                        getResources().getString(
                                R.string.remove_outbound_paired_message,
                                outboundDevice.name))
                    .setPositiveButton(
                        android.R.string.ok, new OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                try {
                                    PairOutboundActivity.savePairing(null, getApplicationContext());
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                                outboundDevice = null;
                                updateLayout();
                            }
                        })
                    .setNegativeButton(android.R.string.cancel, null);
                // @formatter:on
                return builder.create();
            }
        }.show(getFragmentManager(), "removeInbound");
    }

    /**
     * Called when the add new inbound device button is pressed.
     * 
     * @param v
     *            the button
     */
    public void onAddInboundClicked(View v) {
        onNewInboundDevicePressed();
    }

    /**
     * Called when the add new outbound device button is pressed.
     * 
     * @param v
     *            the button
     */
    public void onAddOutboundClicked(View v) {
        onNewOutboundDevicePressed();
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.menu_delete_saved_inbound:
            onDeleteSavedInboundPressed();
            break;
        }
        return true;
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_main, menu);
        return true;
    }

    private void onDeleteSavedInboundPressed() {
        Utils.debug("deleting inbound devices... %s", getApplicationContext()
                .deleteFile(INBOUND_DEVICES_FILENAME) ? "success" : "fail");
        BaseAdapter listAdapter = getListAdapter();
        listAdapter.notifyDataSetChanged();
    }

    private void showOutboundConfiguration() {
        Intent i = new Intent(this, PairOutboundActivity.class);
        startActivity(i);
    }

    /**
     * Called when the pair new device menu item (inbound) is pressed.
     */
    public void onNewInboundDevicePressed() {
        DialogFragment dialog = new RetainedDialogFragment() {
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                AlertDialog.Builder builder = new AlertDialog.Builder(
                        MainActivity.this);
                builder.setMessage(getResources().getString(
                        R.string.new_inbound_instructions));
                builder.setPositiveButton(android.R.string.ok,
                        new OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                Intent intent = new Intent(
                                        "com.google.zxing.client.android.SCAN");
                                intent.putExtra(
                                        "com.google.zxing.client.android.SCAN.SCAN_MODE",
                                        "QR_CODE_MODE");
                                getActivity().startActivityForResult(intent,
                                        QR_CODE_SCANNED);
                            }
                        });
                return builder.create();
            }
        };
        dialog.show(getFragmentManager(), "instruction");
    }

    /**
     * Called when the pair new device menu item (outbound) is pressed.
     */
    public void onNewOutboundDevicePressed() {
        showOutboundConfiguration();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // start the registration process if needed
        GCMIntentService.registerWithGCM(this);
        View menu = getLayoutInflater().inflate(R.layout.menu, null);
        setContentView(menu);
        onNewIntent(getIntent());
    }

    /**
     * Checks whether Whatsapp is installed on this device.
     * 
     * @param activity
     *            the calling activity
     * @return <code>true</code> if a whatsapp installation is found locally
     */
    static boolean isWhatsappInstalled(Context activity) {
        boolean installed = false;
        try {
            activity.getPackageManager().getPackageInfo(WHATSAPP_PACKAGE,
                    PackageManager.GET_ACTIVITIES);
            installed = true;
        } catch (PackageManager.NameNotFoundException e) {
            // not installed
        }
        return installed;
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onActivityResult(int, int,
     * android.content.Intent)
     */
    @Override
    protected void
            onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == QR_CODE_SCANNED) {
            if (resultCode == RESULT_OK) {
                String result = data.getStringExtra("SCAN_RESULT");
                try {
                    String[] keys = result.split(" ");
                    if (keys.length < SHARED_SECRET_SIZE)
                        throw new NumberFormatException();
                    int[] sharedSecret = new int[SHARED_SECRET_SIZE];
                    for (int i = 0; i < SHARED_SECRET_SIZE; i++) {
                        sharedSecret[i] = Integer.valueOf(keys[i]);
                    }
                    String space = "";
                    StringBuilder deviceName = new StringBuilder();
                    for (int i = SHARED_SECRET_SIZE; i < keys.length; i++) {
                        deviceName.append(space);
                        deviceName.append(keys[i]);
                        space = " ";
                    }
                    promptUserForID(deviceName.toString(), sharedSecret);
                } catch (NumberFormatException e) {
                    DialogFragment failDialog = new RetainedDialogFragment() {
                        public Dialog onCreateDialog(Bundle savedInstanceState) {
                            return new AlertDialog.Builder(getActivity())
                                    .setMessage(R.string.qr_code_fail)
                                    .setPositiveButton(R.string.qr_code_retry,
                                            new OnClickListener() {

                                                @Override
                                                public void onClick(
                                                        DialogInterface dialog,
                                                        int which) {
                                                    // do nothing
                                                }
                                            }).create();
                        }
                    };
                    failDialog.show(getFragmentManager(), "fail");
                }
            }
        }
    }

    private void promptUserForID(final String deviceName,
            final int[] sharedSecret) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(deviceName);
        // @formatter:off
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
            .setTitle(R.string.device_id_chooser_title)
            .setView(input)
            .setPositiveButton(android.R.string.ok, null);
        // @formatter:on
        final AlertDialog alertDialog = builder.create();
        alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {

            @Override
            public void onShow(DialogInterface dialog) {
                Button b = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                b.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {
                        input.setError(null);
                        String deviceId = input.getText().toString();
                        if (!Pattern.matches(VALID_DEVICE_ID, deviceId)) {
                            if (deviceId.length() < 1) {
                                input.setError(getResources().getString(
                                        R.string.at_least_one_char));
                            } else {
                                input.setError(getResources().getString(
                                        R.string.wrong_char));
                            }
                        } else if (!isValidChoice(deviceId)) {
                            input.setError(getResources().getString(
                                    R.string.id_already_in_use));
                        } else {
                            deviceToBePaired = new PairedDevice(deviceId,
                                    deviceName);
                            new CallGooGl().execute(sharedSecret);
                            ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                                    .hideSoftInputFromWindow(
                                            input.getWindowToken(), 0);
                            alertDialog.dismiss();
                        }
                    }
                });
            }
        });
        alertDialog.show();
    }

    private boolean isValidChoice(String deviceID) {
        int hashed = deviceID.hashCode();
        for (PairedDevice device : inboundDevices) {
            if (hashed == device.name.hashCode()) {
                return false;
            }
        }
        return true;
    }

    private String encrypt(int[] sharedSecret, char[] content) {
        for (int i = 0; i < content.length; i++) {
            int charIndex = CHAR_MAP.get(content[i]);
            int secret = sharedSecret[i % SHARED_SECRET_SIZE];
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
                    String.format("%s %s", capitalize(Build.MANUFACTURER),
                            Build.MODEL), "UTF-8").replaceAll("\\+", "%20"));
            builder.append("&yourid=");
            builder.append(deviceAssignedID);
            builder.append("&id=");
            builder.append(encodedId);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return builder.toString();
    }

    /**
     * Capitalizes a string.
     * 
     * @param s
     *            the string to be capitalized
     * @return the capitalized string
     */
    static String capitalize(String s) {
        if (s == null || s.length() == 0)
            return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @SuppressWarnings("unchecked")
    private List<PairedDevice> loadInboundPairing()
            throws OptionalDataException, ClassNotFoundException, IOException {
        FileInputStream fis = openFileInput(INBOUND_DEVICES_FILENAME);
        ObjectInputStream ois = new ObjectInputStream(fis);
        Object read = ois.readObject();
        fis.close();
        return (ArrayList<PairedDevice>) read;
    }

    private void saveInboundPairing(PairedDevice newDevice)
            throws OptionalDataException, ClassNotFoundException, IOException {
        List<PairedDevice> alreadyPaired = new ArrayList<PairedDevice>();
        try {
            alreadyPaired = loadInboundPairing();
        } catch (FileNotFoundException e) {
            // it's ok, no file has been saved yet
        }
        alreadyPaired.add(newDevice);
        writePairedInboundFile(alreadyPaired);
        deviceToBePaired = null;
    }

    private void writePairedInboundFile(List<PairedDevice> pairedDevices)
            throws IOException {
        FileOutputStream fos = openFileOutput(INBOUND_DEVICES_FILENAME,
                Context.MODE_PRIVATE);
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(pairedDevices);
        fos.close();
    }

    private ArrayAdapter<String> getListAdapter() {
        List<String> deviceNames = new ArrayList<String>();
        try {
            if (isWhatsappInstalled(this)) {
                inboundDevices = loadInboundPairing();
                if (inboundDevices == null)
                    inboundDevices = new ArrayList<PairedDevice>();
                Utils.debug("%d device(s)", inboundDevices.size());
                for (PairedDevice device : inboundDevices) {
                    deviceNames.add(device.name);
                }
            }
        } catch (OptionalDataException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            // it's ok
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (adapter == null) {
            adapter = new ArrayAdapter<String>(this,
                    android.R.layout.simple_list_item_1, deviceNames);
        } else {
            adapter.clear();
            adapter.addAll(deviceNames);
        }
        return adapter;
    }

}
