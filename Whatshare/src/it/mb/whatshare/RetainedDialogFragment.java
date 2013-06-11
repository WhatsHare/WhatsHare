/**
 * RetainedDialogFragment.java Created on 11 Jun 2013 Copyright 2013 Michele
 * Bonazza <michele.bonazza@gmail.com>
 */
package it.mb.whatshare;

import android.app.DialogFragment;

/**
 * A dialog fragment that doesn't disappear on screen rotations.
 * 
 * @author Michele Bonazza
 * 
 */
public class RetainedDialogFragment extends DialogFragment {

    /**
     * Creates a new fragment that won't be dismissed on screen orientation
     * changes.
     */
    public RetainedDialogFragment() {
        setRetainInstance(true);
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.app.DialogFragment#onDestroyView()
     */
    @Override
    public void onDestroyView() {
        if (getDialog() != null && getRetainInstance()) {
            // this is a workaround for a compatibility library bug
            getDialog().setDismissMessage(null);
        }
        super.onDestroyView();
    }

}
