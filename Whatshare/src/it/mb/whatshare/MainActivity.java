/**
 * MainActivity.java Created on 10 Mar 2013
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
import android.text.Html;
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
import android.widget.Toast;

import com.google.analytics.tracking.android.EasyTracker;
import com.google.analytics.tracking.android.ExceptionReporter;
import com.google.analytics.tracking.android.GoogleAnalytics;
import com.google.analytics.tracking.android.Tracker;

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
     * The key used to set the intent type according to which content is either
     * shared directly through the paired device's WhatsApp or after showing an
     * app picker dialog.
     */
    public static final String INTENT_TYPE_EXTRA = "type";
    /**
     * String added to the message sent through GCM in case the content should
     * be shared directly via Whatsapp bypassing the app choice dialog on the
     * receiver's side.
     */
    public static final String SHARE_VIA_WHATSAPP_EXTRA = "w";

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
         * The name set for the device by the user.
         */
        String name;
        /**
         * The device's type as specified by the device itself (e.g. 'Chrome
         * Whatshare Extension').
         */
        final String type;

        /**
         * The unique ID for the device
         */
        final String id;

        /**
         * Creates a new device.
         * 
         * @param ID
         *            the ID for the device
         * @param name
         *            the name set for the device by the user
         * @param type
         *            the device's type as specified by the device itself (e.g.
         *            'Chrome Whatshare Extension')
         */
        PairedDevice(String ID, String name, String type) {
            this.id = ID;
            this.name = name;
            this.type = type;
        }

        /**
         * Returns a new value to be used as ID for a device.
         * 
         * @return an ID to be used to identify a device
         */
        static String getNextID() {
            return String.valueOf(System.currentTimeMillis());
        }

        public String toString() {
            return new StringBuilder(name).append(", type(").append(type)
                    .append(")").toString();
        }

        /**
         * Renames this device.
         * 
         * @param newName
         *            the new name for this device
         */
        public void rename(String newName) {
            this.name = newName;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((id == null) ? 0 : id.hashCode());
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            result = prime * result + ((type == null) ? 0 : type.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            PairedDevice other = (PairedDevice) obj;
            if (id == null) {
                if (other.id != null)
                    return false;
            } else if (!id.equals(other.id))
                return false;
            if (name == null) {
                if (other.name != null)
                    return false;
            } else if (!name.equals(other.name))
                return false;
            if (type == null) {
                if (other.type != null)
                    return false;
            } else if (!type.equals(other.type))
                return false;
            return true;
        }

    }

    /**
     * The name of Whatsapp's package.
     */
    static final String WHATSAPP_PACKAGE = "com.whatsapp";
    /**
     * Where Google shortener is at.
     */
    static final String SHORTENER_URL = "https://www.googleapis.com/urlshortener/v1/url?key=%s";
    /**
     * The number of random keys used to encrypt messages with.
     */
    static final int SHARED_SECRET_SIZE = 4;
    /**
     * The regex used to filter names chosen by the user for inbound devices.
     */
    static final String VALID_DEVICE_NAME = "[A-Za-z0-9\\-\\_\\s]{1,30}";
    /**
     * The code used by {@link #onActivityResult(int, int, Intent)} to detect
     * incoming replies from the QR code capture activity.
     */
    static final int QR_CODE_SCANNED = 0;

    private PairedDevice outboundDevice, deviceSelectedContextMenu;
    private List<PairedDevice> inboundDevices = new ArrayList<PairedDevice>();
    private ArrayAdapter<String> adapter;
    private Tracker tracker;
    private GoogleAnalytics analytics;

    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onCreateContextMenu(android.view.ContextMenu,
     * android.view.View, android.view.ContextMenu.ContextMenuInfo)
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.context_menu_devices, menu);
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        deviceSelectedContextMenu = inboundDevices.get(info.position);
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
        switch (item.getItemId()) {
        case R.id.context_devices_rename:
            tracker.sendEvent("ui", "button_press", "rename", 0L);
            Dialogs.promptForNewDeviceName(deviceSelectedContextMenu, this);
            break;
        case R.id.context_devices_unpair:
            tracker.sendEvent("ui", "button_press", "unpair", 0L);
            Dialogs.confirmUnpairInbound(deviceSelectedContextMenu, this);
            break;
        }
        return super.onContextItemSelected(item);
    }

    /**
     * Removes the currently selected device from the list of inbound devices.
     */
    void removePaired() {
        if (deviceSelectedContextMenu != null) {
            Utils.debug("removePaired(): removing %s... success? %s",
                    deviceSelectedContextMenu.name,
                    inboundDevices.remove(deviceSelectedContextMenu));
            deviceSelectedContextMenu = null;
            writePairedInboundFile(inboundDevices);
            BaseAdapter listAdapter = getListAdapter();
            listAdapter.notifyDataSetChanged();
        } else {
            Utils.debug("removePaired(): no device is currently set to be unpaired");
        }
    }

    /**
     * Renames the currently selected device.
     */
    void onSelectedDeviceRenamed() {
        if (deviceSelectedContextMenu != null) {
            Utils.debug("renamePaired(): renamed %s to %s",
                    deviceSelectedContextMenu.type,
                    deviceSelectedContextMenu.name);
            deviceSelectedContextMenu = null;
            writePairedInboundFile(inboundDevices);
            BaseAdapter listAdapter = getListAdapter();
            listAdapter.notifyDataSetChanged();
        } else {
            Utils.debug("renamePaired(): no device is currently set to be renamed");
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
        case R.id.menu_tell_friends:
            tracker.sendEvent("ui", "button_press", "tell_friends", 0L);
            onTellFriendsSelected();
            break;
        case R.id.about:
            tracker.sendEvent("ui", "button_press", "about", 0L);
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
        tracker.sendEvent("ui", "button_press", "delete_all_inbound", 0L);
        Dialogs.confirmUnpairAllInbound(this);
    }

    private void onTellFriendsSelected() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/html");
        intent.putExtra(Intent.EXTRA_SUBJECT,
                getResources().getString(R.string.email_subject));
        intent.putExtra(Intent.EXTRA_TEXT,
                Html.fromHtml(getResources().getString(R.string.email_body)));
        startActivity(Intent.createChooser(intent,
                getResources().getString(R.string.email_intent_msg)));
    }

    /**
     * Removes all inbound paired devices.
     */
    public void deleteAllInbound() {
        if (!getApplicationContext().deleteFile(INBOUND_DEVICES_FILENAME)) {
            if (!inboundDevices.isEmpty()) {
                Toast.makeText(this,
                        R.string.delete_all_inbound_fail_notification,
                        Toast.LENGTH_SHORT).show();
                return;
            }
        }

        inboundDevices = new ArrayList<PairedDevice>();
        BaseAdapter listAdapter = getListAdapter();
        listAdapter.notifyDataSetChanged();
        Toast.makeText(this, R.string.delete_all_inbound_notification,
                Toast.LENGTH_SHORT).show();
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
            tracker.sendEvent("ui", "button_press", "add_inbound", 0L);
            Dialogs.pairInboundInstructions(this);
        } else {
            tracker.sendEvent("ui", "button_press", "add_inbound", -1L);
            Dialogs.noInternetConnection(this, R.string.no_internet_pairing,
                    false);
        }
    }

    /**
     * Called when the pair new device menu item (outbound) is pressed.
     */
    public void onNewOutboundDevicePressed() {
        if (Utils.isConnectedToTheInternet(this)) {
            tracker.sendEvent("ui", "button_press", "add_outbound", 0L);
            showOutboundConfiguration();
        } else {
            tracker.sendEvent("ui", "button_press", "add_outbound", -1L);
            Dialogs.noInternetConnection(this, R.string.no_internet_pairing,
                    false);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Utils.checkDebug(this);
        analytics = GoogleAnalytics.getInstance(this);
        tracker = analytics.getTracker(getResources().getString(
                R.string.ga_trackingId));

        Thread.UncaughtExceptionHandler uncaughtExceptionHandler = Thread
                .getDefaultUncaughtExceptionHandler();
        if (uncaughtExceptionHandler instanceof ExceptionReporter) {
            ExceptionReporter exceptionReporter = (ExceptionReporter) uncaughtExceptionHandler;
            exceptionReporter
                    .setExceptionParser(new AnalyticsExceptionParser());
        }

        analytics.setDefaultTracker(tracker);
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
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

                    tracker.sendEvent("qr", "result", "scan_ok", 0L);
                    Dialogs.promptForInboundName(deviceName.toString(),
                            sharedSecret, this);
                } catch (NumberFormatException e) {
                    tracker.sendEvent("qr", "result", "scan_fail", 0L);
                    Dialogs.onQRFail(this);
                }
            } else if (resultCode == RESULT_CANCELED) {
                tracker.sendEvent("qr", "result", "scan_canceled", 0L);
            }
        }
    }

    /**
     * Returns whether the argument <tt>deviceID</tt> is valid.
     * 
     * @param deviceID
     *            the ID to be checked
     * @return <code>true</code> if <tt>deviceID</tt> is a non-empty string that
     *         matches {@link #VALID_DEVICE_NAME} and is not in use for any
     *         other inbound device.
     */
    public boolean isValidChoice(String deviceID) {
        if (deviceID == null || deviceID.isEmpty()
                || !deviceID.matches(VALID_DEVICE_NAME))
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
            // it's ok, no inbound device has been configured
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
