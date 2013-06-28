/**
 * FragmentUtils.java Created on 13 Jun 2013 Copyright 2013 Michele Bonazza
 * <emmepuntobi@gmail.com>
 * 
 * Copyright 2013 Michele Bonazza <emmepuntobi@gmail.com> This file is part of
 * WhatsHare.
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
import java.text.SimpleDateFormat;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.json.JSONException;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.Pair;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Dialogs used throughout the app.
 * 
 * <p>
 * The initial purpose of this class was to have a single point where to
 * dynamically switch between fragments from the support package and fragments
 * from the standard library. Anyway, because of the awesome design decision to
 * force activities to extend {@link FragmentActivity} for fragments to work,
 * it's impossible to switch between old-fashioned fragments and the new ones
 * (you can't have different flavors of {@link MainActivity} in the manifest
 * according to the API level).
 * 
 * <p>
 * If and when this app will drop support for API &lt;11, all activities can
 * stop to extend {@link FragmentActivity}, and all calls to
 * {@link FragmentActivity#getSupportFragmentManager()} can be replaced with
 * {@link Activity#getFragmentManager()}.
 * 
 * @author Michele Bonazza
 */
@SuppressLint("ValidFragment")
public class Dialogs {

    /**
     * Shows a dialog informing the user that the QR code she's taken a picture
     * of is not valid.
     * 
     * @param activity
     *            the caller activity
     */
    public static void onQRFail(final FragmentActivity activity) {
        DialogFragment failDialog = new PatchedDialogFragment() {
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                return getBuilder(activity)
                        .setMessage(R.string.qr_code_fail)
                        .setPositiveButton(R.string.qr_code_retry,
                                new OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        // do nothing
                                    }
                                }).create();
            }
        };
        failDialog.show(activity.getSupportFragmentManager(), "fail");
    }

    /**
     * Shows a dialog that informs the user of the result of a pairing action,
     * and saves the newly paired outbound device if the operation was
     * successful.
     * 
     * <p>
     * Once the user taps on the OK button,
     * {@link Activity#startActivity(Intent)} is called to get back to the
     * {@link MainActivity}.
     * 
     * @param device
     *            the outbound device to be paired together with the ID (name)
     *            chosen by the user for the new device
     * @param activity
     *            the caller activity
     */
    public static void onPairingOutbound(
            final Pair<PairedDevice, String> device,
            final FragmentActivity activity) {
        new PatchedDialogFragment() {
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                AlertDialog.Builder builder = getBuilder(activity);
                try {
                    builder.setMessage(getString(R.string.failed_pairing));
                    if (device != null) {
                        PairOutboundActivity.setAssignedID(device.second);
                        PairOutboundActivity.savePairing(device, activity);
                        builder.setMessage(getResources().getString(
                                R.string.successful_pairing, device.first.type));
                    }
                } catch (IOException e) {
                    // TODO let user know
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                builder.setPositiveButton(android.R.string.ok,
                        new OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                // back to main screen
                                startActivity(new Intent(activity,
                                        MainActivity.class));
                            }
                        });
                return builder.create();
            }
        }.show(activity.getSupportFragmentManager(), "code");
    }

    /**
     * Shows a dialog informing the user about what went wrong while trying to
     * registrate her device with GCM.
     * 
     * @param errorCode
     *            the error message's resource ID within <tt>strings.xml</tt>
     * @param activity
     *            the caller activity
     * @param finishActivityOnOk
     *            whether the call comes from an activity with no UI that
     *            requires clicks on ok (or back) to call
     *            {@link Activity#finish()} on the caller activity
     */
    public static void onRegistrationError(final int errorCode,
            final FragmentActivity activity, final boolean finishActivityOnOk) {
        new PatchedDialogFragment() {
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                AlertDialog.Builder builder;
                if (finishActivityOnOk) {
                    builder = getNoUiBuilder(activity);
                } else {
                    builder = getBuilder(activity);
                }
                builder.setMessage(getString(R.string.gcm_registration_error,
                        getString(errorCode)));
                builder.setPositiveButton(android.R.string.ok,
                        new OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                // just hide dialog
                            }
                        });
                Dialog dialog = builder.create();
                dialog.setCanceledOnTouchOutside(!finishActivityOnOk);
                return dialog;
            }
        }.show(activity.getSupportFragmentManager(), "gcm_error");
    }

    /**
     * Shows a dialog containing the code received by goo.gl if any, or an error
     * message stating that the pairing operation failed.
     * 
     * @param googl
     *            the pairing code retrieved by goo.gl
     * @param activity
     *            the caller activity
     */
    public static void onObtainPairingCode(final String googl,
            final MainActivity activity) {
        new PatchedDialogFragment() {
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                AlertDialog.Builder builder = getBuilder(activity);
                if (googl != null) {
                    View layout = View.inflate(getContext(),
                            R.layout.pairing_code_dialog, null);
                    TextView message = (TextView) layout
                            .findViewById(R.id.pairingCode);
                    Typeface typeFace = Typeface.createFromAsset(
                            activity.getAssets(), "fonts/PTM55FT.ttf");
                    message.setTypeface(typeFace);
                    message.setText(googl);
                    builder.setView(layout);
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
                                                    activity.getInboundDevicesCount(),
                                                    activity.getInboundDevicesCount());
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
        }.show(activity.getSupportFragmentManager(), "resultCode");
    }

    /**
     * Shows a dialog to get an ID (name) for the inbound device being paired
     * and starts a new {@link CallGooGlInbound} action if the chosen name is
     * valid and not in use.
     * 
     * @param deviceType
     *            the model of the device being paired as suggested by the
     *            device itself
     * @param sharedSecret
     *            the keys used when encrypting the message between devices
     * @param activity
     *            the caller activity
     */
    public static void promptForInboundID(final String deviceType,
            final int[] sharedSecret, final MainActivity activity) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {

            @Override
            public void run() {
                DialogFragment prompt = new PatchedDialogFragment() {
                    public Dialog onCreateDialog(Bundle savedInstanceState) {
                        AlertDialog.Builder builder = getBuilder(activity);
                        final EditText input = new EditText(getContext());
                        input.setInputType(InputType.TYPE_CLASS_TEXT);
                        input.setText(deviceType);
                        input.setSelection(deviceType.length());
                        // @formatter:off 
                        builder.setTitle(R.string.device_id_chooser_title)
                            .setView(input)
                            .setPositiveButton(android.R.string.ok, null);
                        // @formatter:on
                        final AlertDialog alertDialog = builder.create();
                        alertDialog
                                .setOnShowListener(new DialogInterface.OnShowListener() {

                                    @Override
                                    public void onShow(DialogInterface dialog) {
                                        Button b = alertDialog
                                                .getButton(AlertDialog.BUTTON_POSITIVE);
                                        b.setOnClickListener(new View.OnClickListener() {

                                            @Override
                                            public void onClick(View view) {
                                                input.setError(null);
                                                String deviceId = input
                                                        .getText().toString();
                                                if (!Pattern
                                                        .matches(
                                                                MainActivity.VALID_DEVICE_ID,
                                                                deviceId)) {
                                                    if (deviceId.length() < 1) {
                                                        setError(
                                                                input,
                                                                R.string.at_least_one_char,
                                                                activity);
                                                    } else {
                                                        setError(
                                                                input,
                                                                R.string.wrong_char,
                                                                activity);
                                                    }
                                                } else if (!activity
                                                        .isValidChoice(deviceId)) {
                                                    setError(
                                                            input,
                                                            R.string.id_already_in_use,
                                                            activity);
                                                } else {
                                                    new CallGooGlInbound(
                                                            activity, deviceId,
                                                            deviceType)
                                                            .execute(sharedSecret);
                                                    ((InputMethodManager) activity
                                                            .getSystemService(Context.INPUT_METHOD_SERVICE))
                                                            .hideSoftInputFromWindow(
                                                                    input.getWindowToken(),
                                                                    0);
                                                    alertDialog.dismiss();
                                                }
                                            }
                                        });
                                    }
                                });
                        return alertDialog;
                    }
                };
                prompt.show(activity.getSupportFragmentManager(), "chooseName");
            }
        });
    }

    /**
     * Sad workaround.
     * 
     * See http://stackoverflow.com/a/7350315/1159164
     * 
     * @param input
     *            the EditText to set the error to
     * @param errorID
     *            the ID of the error in <tt>strings.xml</tt>
     * @param activity
     *            the enclosing activity
     */
    private static void
            setError(EditText input, int errorID, Activity activity) {
        String errorMsg = activity.getResources().getString(errorID);
        if (Build.VERSION.SDK_INT < 11) {
            ForegroundColorSpan fgcspan = new ForegroundColorSpan(Color.BLACK);
            SpannableStringBuilder ssbuilder = new SpannableStringBuilder(
                    errorMsg);
            ssbuilder.setSpan(fgcspan, 0, errorMsg.length(), 0);
            input.setError(ssbuilder);
        } else {
            input.setError(errorMsg);
        }
    }

    /**
     * Asks the user for confirmation of a delete outbound device operation.
     * 
     * <p>
     * If the user confirms the operation, the current outbound device is
     * deleted.
     * 
     * @param outboundDevice
     *            the device being removed
     * @param activity
     *            the caller activity
     */
    public static void confirmRemoveOutbound(final PairedDevice outboundDevice,
            final MainActivity activity) {
        new PatchedDialogFragment() {
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                // @formatter:off
                    AlertDialog.Builder builder = getBuilder(activity)
                        .setMessage(
                            getResources().getString(
                                    R.string.remove_outbound_paired_message,
                                    outboundDevice.type))
                        .setPositiveButton(
                            android.R.string.ok, new OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    activity.deleteOutboundDevice();
                                }
                            })
                        .setNegativeButton(android.R.string.cancel, null);
                    // @formatter:on
                return builder.create();
            }
        }.show(activity.getSupportFragmentManager(), "removeInbound");
    }

    /**
     * Shows a dialog telling the user what to do before the QR code scanner is
     * displayed, and starts the QR code activity.
     * 
     * @param activity
     *            the caller activity
     */
    public static void pairInboundInstructions(final FragmentActivity activity) {
        DialogFragment dialog = new PatchedDialogFragment() {
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                AlertDialog.Builder builder = getBuilder(activity);
                View layout = View.inflate(getContext(),
                        R.layout.pair_inbound_instructions, null);
                builder.setView(layout);
                ((TextView) layout.findViewById(R.id.instructions))
                        .setText(getResources().getString(
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
                                        MainActivity.QR_CODE_SCANNED);
                            }
                        });
                return builder.create();
            }
        };
        dialog.show(activity.getSupportFragmentManager(), "instruction");
    }

    /**
     * Asks the user for confirmation of a delete inbound device operation.
     * 
     * <p>
     * If the user confirms the operation, the device is removed from the list
     * of paired devices.
     * 
     * @param deviceToBeUnpaired
     *            the device to be removed from the list of paired devices
     * @param activity
     *            the caller activity
     */
    public static void confirmUnpairInbound(
            final PairedDevice deviceToBeUnpaired, final MainActivity activity) {
        new PatchedDialogFragment() {
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                // @formatter:off
                AlertDialog.Builder builder = getBuilder(activity)
                    .setMessage(
                        getResources().getString(
                                R.string.remove_inbound_paired_message,
                                deviceToBeUnpaired.name))
                    .setPositiveButton(
                        android.R.string.ok, new OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                activity.removePaired();
                            }
                        })
                    .setNegativeButton(android.R.string.cancel, null);
                // @formatter:on
                return builder.create();
            }
        }.show(activity.getSupportFragmentManager(), "removeInbound");
    }

    /**
     * Asks the user for confirmation of a delete all inbound devices operation.
     * 
     * <p>
     * If the user confirms the operation, all devices are removed from the list
     * of inbound paired devices.
     * 
     * @param activity
     *            the caller activity
     */
    public static void confirmUnpairAllInbound(final MainActivity activity) {
        new PatchedDialogFragment() {
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                // @formatter:off
                AlertDialog.Builder builder = getBuilder(activity)
                    .setMessage(
                        getResources().getString(
                                R.string.delete_all_inbound_confirm))
                    .setPositiveButton(
                        android.R.string.ok, new OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                activity.deleteAllInbound();
                            }
                        })
                    .setNegativeButton(android.R.string.cancel, null);
                // @formatter:on
                return builder.create();
            }
        }.show(activity.getSupportFragmentManager(), "removeAllInbound");
    }

    /**
     * Shows a dialog informing the user that no outbound device is currently
     * configured, and takes the user to the {@link PairOutboundActivity}.
     * 
     * @param activity
     *            the caller activity
     */
    public static void noPairedDevice(final FragmentActivity activity) {
        new PatchedDialogFragment() {
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                AlertDialog.Builder builder = getNoUiBuilder(activity);
                builder.setMessage(getString(R.string.no_paired_device));
                builder.setPositiveButton(android.R.string.ok,
                        new OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                Intent i = new Intent(activity,
                                        PairOutboundActivity.class);
                                startActivity(i);
                            }
                        });
                Dialog dialog = builder.create();
                dialog.setCanceledOnTouchOutside(false);
                return dialog;
            }
        }.show(activity.getSupportFragmentManager(), "no paired device");
    }

    /**
     * Shows a dialog informing the user that the action she's trying to perform
     * requires an Internet connection which is not available at the moment.
     * 
     * @param activity
     *            the caller activity
     * @param whyItsNeeded
     *            the resource ID within <tt>strings.xml</tt> that explains to
     *            the user why an Internet connection is needed by the operation
     * @param finishActivityOnOk
     *            whether the caller activity must be {@link Activity#finish()}
     *            'ed after the user hides the dialog
     */
    public static void noInternetConnection(final FragmentActivity activity,
            final int whyItsNeeded, final boolean finishActivityOnOk) {
        new PatchedDialogFragment() {
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                AlertDialog.Builder builder;
                if (finishActivityOnOk) {
                    builder = getNoUiBuilder(activity);
                } else {
                    builder = getBuilder(activity);
                }
                builder.setMessage(getString(R.string.no_internet_connection,
                        getString(whyItsNeeded)));
                Dialog dialog = builder.create();
                dialog.setCanceledOnTouchOutside(!finishActivityOnOk);
                return dialog;
            }
        }.show(activity.getSupportFragmentManager(), "no_internet");
    }

    /**
     * Shows a dialog informing the user that Whatsapp is not installed on the
     * device, and gives an option to hide the dialog in the future.
     * 
     * @param activity
     *            the caller activity
     * @param intent
     *            the intent containing the content to be shared
     */
    public static void whatsappMissing(final SendToWhatsappActivity activity,
            final Intent intent) {
        // @formatter:off
        DialogFragment dialog = new PatchedDialogFragment() {
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                AlertDialog.Builder builder = getNoUiBuilder(activity)
                .setTitle(activity.getString(R.string.whatsapp_not_installed_title))
                .setMessage(activity.getString(R.string.whatsapp_not_installed))
                .setPositiveButton(activity.getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                    
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        activity.startActivity(SendToAppActivity.createPlainIntent(intent.getStringExtra("message")));
                    }
                })
                .setNegativeButton(activity.getString(R.string.whatsapp_not_installed_dont_mind), new DialogInterface.OnClickListener() {
                    
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SharedPreferences pref = activity.getSharedPreferences("it.mb.whatshare", Context.MODE_PRIVATE);
                        pref.edit().putBoolean(SendToWhatsappActivity.HIDE_MISSING_WHATSAPP_KEY, true).commit();
                        activity.startActivity(SendToAppActivity.createPlainIntent(intent.getStringExtra("message")));
                    }
                });
                Dialog dialog = builder.create();
                dialog.setCanceledOnTouchOutside(false);
                return dialog;
            }
        };
        dialog.show(activity.getSupportFragmentManager(), "whatsapp_missing");
        // @formatter:on
    }

    /**
     * Shows the about screen.
     * 
     * @param activity
     *            the caller activity
     */
    public static void showAbout(final FragmentActivity activity) {
        DialogFragment dialog = new PatchedDialogFragment() {
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                AlertDialog.Builder builder = getBuilder(activity);
                View layout = View.inflate(getContext(), R.layout.about, null);
                String version = "alpha";
                try {
                    version = activity.getPackageManager().getPackageInfo(
                            activity.getPackageName(), 0).versionName;
                } catch (NameNotFoundException e) {
                    e.printStackTrace();
                }
                ((TextView) layout.findViewById(R.id.title)).setText(activity
                        .getResources().getString(R.string.title_about_dialog,
                                version));
                ((TextView) layout.findViewById(R.id.build_date))
                        .setText(activity.getResources().getString(
                                R.string.build_date_about,
                                getBuildDate(activity)));
                // make links clickable
                ((TextView) layout.findViewById(R.id.description))
                        .setMovementMethod(LinkMovementMethod.getInstance());
                builder.setView(layout);
                return builder.create();
            }
        };
        dialog.show(activity.getSupportFragmentManager(), "about");
    }

    private static String getBuildDate(final Activity activity) {
        String buildDate = "";
        ZipFile zf = null;
        try {
            ApplicationInfo ai = activity.getPackageManager()
                    .getApplicationInfo(activity.getPackageName(), 0);
            zf = new ZipFile(ai.sourceDir);
            ZipEntry ze = zf.getEntry("classes.dex");
            long time = ze.getTime();
            buildDate = SimpleDateFormat.getInstance().format(
                    new java.util.Date(time));

        } catch (Exception e) {} finally {
            if (zf != null) {
                try {
                    zf.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return buildDate;
    }
}
