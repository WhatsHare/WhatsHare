/**
 * PatchedDialogFragment.java Created on 18 Jun 2013 Copyright 2013 Michele
 * Bonazza <michele.bonazza@gmail.com>
 */
package it.mb.whatshare;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;

/**
 * A {@link DialogFragment} that uses
 * {@link FragmentTransaction#commitAllowingStateLoss()} instead of
 * {@link FragmentTransaction#commit()} to
 * {@link FragmentTransaction#show(android.support.v4.app.Fragment)} the dialog
 * fragment.
 * 
 * <p>
 * This class is used to overcome <a
 * href="https://code.google.com/p/android/issues/detail?id=23761">this bug</a>
 * in the support package.
 * 
 * @author Michele Bonazza
 * 
 */
public class PatchedDialogFragment extends DialogFragment {

    /*
     * (non-Javadoc)
     * 
     * @see android.support.v4.app.DialogFragment#show(android.support.v4.app.
     * FragmentManager, java.lang.String)
     */
    @Override
    public void show(FragmentManager manager, String tag) {
        FragmentTransaction transaction = manager.beginTransaction();
        transaction.add(this, tag);
        transaction.commitAllowingStateLoss();
    }

    /**
     * Returns a builder that will create a dialog that can only be canceled by
     * clicking OK or by pressing back.
     * 
     * <p>
     * Whenever the dialog is dismissed, {@link Activity#finish()} is called on
     * the argument <tt>activity</tt>.
     * 
     * <p>
     * The builder has already the correct theme set (for making it look ok even
     * on wonderful Android pre-v11), so {@link Builder#getContext()} can be
     * used to inflate layouts with.
     * 
     * @param activity
     *            the caller activity
     * @return a builder to be used to create a dialog with
     */
    protected Builder getNoUiBuilder(final FragmentActivity activity) {
        // @formatter:off
        return new AlertDialog.Builder(new ContextThemeWrapper(
                activity, R.style.DialogTheme))
            .setCancelable(false)
            .setOnKeyListener(new DialogInterface.OnKeyListener() {
                        @Override
                        public boolean onKey(DialogInterface dialog,
                                int keyCode, KeyEvent event) {
                            if (keyCode == KeyEvent.KEYCODE_BACK
                                    && event.getAction() == KeyEvent.ACTION_UP
                                    && !event.isCanceled()) {
                                dialog.cancel();
                                activity.finish();
                                return true;
                            }
                            return false;
                        }
                    })
            .setPositiveButton(android.R.string.ok,
                        new OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                activity.finish();
                            }
                        });
        // @formatter:on
    }

    /**
     * Returns a builder that will create a dialog that is hidden when clicking
     * ok or outside of it.
     * 
     * <p>
     * The builder has already the correct theme set (for making it look ok even
     * on wonderful Android pre-v11), so {@link Builder#getContext()} can be
     * used to inflate layouts with.
     * 
     * @param activity
     *            the caller activity
     * @return a builder to be used to create a dialog with
     */
    protected Builder getBuilder(final FragmentActivity activity) {
        // @formatter:off
       return new AlertDialog.Builder(new ContextThemeWrapper(
                activity, R.style.DialogTheme))
           .setPositiveButton(android.R.string.ok,
               new OnClickListener() {

                   @Override
                   public void onClick(DialogInterface dialog,
                           int which) {
                       //  just hide dialog
                   }
               });
       // @formatter:on
    }

}
