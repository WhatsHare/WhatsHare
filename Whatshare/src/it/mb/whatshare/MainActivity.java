package it.mb.whatshare;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OptionalDataException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Pair;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.google.analytics.tracking.android.EasyTracker;

/**
 * The only activity: it can be created when tapping on a notification (and an
 * Intent is routed to this activity, which in turn creates a new Whatsapp
 * activity) or by tapping the app's icon, in which case the pairing screen is
 * displayed.
 * 
 * @author Michele Bonazza
 * 
 */
public class MainActivity extends FragmentActivity {

    /**
     * The file name of the list containing all inbound devices.
     */
    public static final String INBOUND_DEVICES_FILENAME = "inbound";

    /**
     * If set, all network requests are routed to localhost (on port 80) upon
     * failure (to have a log of all requests that failed). An HTTP server must
     * be running (for instance, using netcat or the echo_server.py script in
     * the extras_not_in_apk folder)
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
    /**
     * The number of random keys used to encrypt messages with.
     */
    static final int SHARED_SECRET_SIZE = 4;
    /**
     * The regex used to filter names chosen by the user for inbound devices.
     */
    static final String VALID_DEVICE_ID = "[A-Za-z0-9\\-\\_\\s]{1,30}";
    /**
     * The code used by {@link #onActivityResult(int, int, Intent)} to detect
     * incoming replies from the QR code capture activity.
     */
    static final int QR_CODE_SCANNED = 0;
    private PairedDevice outboundDevice, deviceToBeUnpaired;
    private List<PairedDevice> inboundDevices = new ArrayList<PairedDevice>();
    private ArrayAdapter<String> adapter;

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
     * @see android.app.Activity#onResume()
     */
    @Override
    protected void onResume() {
        super.onResume();
        updateLayout();
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onContextItemSelected(android.view.MenuItem)
     */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        Dialogs.confirmUnpairInbound(deviceToBeUnpaired, this);
        return super.onContextItemSelected(item);
    }

    /**
     * Removes the currently selected device from the list of inbound devices.
     */
    void removePaired() {
        if (deviceToBeUnpaired != null) {
            Utils.debug("removePaired(): removing %s... success? %s",
                    deviceToBeUnpaired.name,
                    inboundDevices.remove(deviceToBeUnpaired));
            deviceToBeUnpaired = null;
            writePairedInboundFile(inboundDevices);
            BaseAdapter listAdapter = getListAdapter();
            listAdapter.notifyDataSetChanged();
        } else {
            Utils.debug("removePaired(): no device is currently set to be unpaired");
        }
    }

    /**
     * Refreshes the layout of this activity, also reloading configured devices
     * from files.
     */
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
        final TextView outboundView = (TextView) findViewById(R.id.outbound_device);
        if (outboundDevice != null) {
            outboundView.setText(outboundDevice.type);
        } else {
            outboundView.setText(R.string.no_device);
        }
        final boolean outboundConfigured = outboundDevice != null;
        outboundView.setOnLongClickListener(new OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                if (outboundConfigured) {
                    Dialogs.confirmRemoveOutbound(outboundDevice,
                            MainActivity.this);
                } else {
                    showOutboundConfiguration();
                }
                return true;
            }
        });
    }

    /**
     * Removes the currently configured outbound device and updates both the
     * current layout and the configuration file.
     */
    void deleteOutboundDevice() {
        outboundDevice = null;
        try {
            PairOutboundActivity.savePairing(null, this);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        updateLayout();
    }

    /**
     * Returns the number of currently paired inbound devices.
     * 
     * @return the number of currently paired inbound devices
     */
    int getInboundDevicesCount() {
        return adapter.getCount();
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
        case R.id.about:
            Dialogs.showAbout(this);
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
        inboundDevices = new ArrayList<PairedDevice>();
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
        if (Utils.isConnectedToTheInternet(this)) {
            Dialogs.pairInboundInstructions(this);
        } else {
            Dialogs.noInternetConnection(this, R.string.no_internet_pairing);
        }
    }

    /**
     * Called when the pair new device menu item (outbound) is pressed.
     */
    public void onNewOutboundDevicePressed() {
        if (Utils.isConnectedToTheInternet(this)) {
            showOutboundConfiguration();
        } else {
            Dialogs.noInternetConnection(this, R.string.no_internet_pairing);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // start the registration process if needed
        GCMIntentService.registerWithGCM(this);
        View menu = getLayoutInflater().inflate(R.layout.menu, null);
        setContentView(menu);
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
                    Dialogs.promptForInboundID(deviceName.toString(),
                            sharedSecret, this);
                } catch (NumberFormatException e) {
                    Dialogs.onQRFail(this);
                }
            }
        }
    }

    /**
     * Returns whether the argument <tt>deviceID</tt> is valid.
     * 
     * @param deviceID
     *            the ID to be checked
     * @return <code>true</code> if <tt>deviceID</tt> is a non-empty string that
     *         matches {@link #VALID_DEVICE_ID} and is not in use for any other
     *         inbound device.
     */
    public boolean isValidChoice(String deviceID) {
        if (deviceID == null || deviceID.isEmpty()
                || !deviceID.matches(VALID_DEVICE_ID))
            return false;
        int hashed = deviceID.hashCode();
        for (PairedDevice device : inboundDevices) {
            if (hashed == device.name.hashCode()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a list of all devices currently configured as inbound devices by
     * loading it from the configuration file.
     * 
     * @return a (potentially empty) list of currently paired inbound devices
     */
    @SuppressWarnings("unchecked")
    List<PairedDevice> loadInboundPairing() {
        FileInputStream fis = null;
        try {
            fis = openFileInput(INBOUND_DEVICES_FILENAME);
            ObjectInputStream ois = new ObjectInputStream(fis);
            Object read = ois.readObject();
            return (ArrayList<PairedDevice>) read;
        } catch (FileNotFoundException e) {
            // it's ok, no inbound devices have been configured
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            if (fis != null)
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }
        return new ArrayList<PairedDevice>();

    }

    /**
     * Reloads the list of configured inbound devices from file and updates the
     * list in the UI.
     */
    void refreshInboundDevicesList() {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                getListAdapter().notifyDataSetChanged();
            }
        });
    }

    /**
     * Loads the list of configured inbound devices from the configuration file
     * and creates an {@link ArrayAdapter} wrapping them.
     * 
     * @return a new {@link ArrayAdapter} that contains all currently configured
     *         inbound devices (potentially none)
     */
    ArrayAdapter<String> getListAdapter() {
        List<String> deviceNames = new ArrayList<String>();
        inboundDevices = loadInboundPairing();
        Utils.debug("%d device(s)", inboundDevices.size());
        for (PairedDevice device : inboundDevices) {
            deviceNames.add(device.name);
        }
        if (adapter == null) {
            adapter = new ArrayAdapter<String>(this,
                    android.R.layout.simple_list_item_1, deviceNames);
        } else {
            adapter.clear();
            for (String deviceName : deviceNames) {
                adapter.add(deviceName);
            }
        }
        return adapter;
    }

    /**
     * Overwrites the current configuration file for outbound devices storing
     * the argument list of devices.
     * 
     * @param pairedDevices
     *            the new list of devices to be saved to disk
     */
    void writePairedInboundFile(List<PairedDevice> pairedDevices) {
        FileOutputStream fos = null;
        try {
            fos = openFileOutput(INBOUND_DEVICES_FILENAME, Context.MODE_PRIVATE);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(pairedDevices);
        } catch (IOException e) {
            // TODO should notify the user
            e.printStackTrace();
        } finally {
            if (fos != null)
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }
    }

}
