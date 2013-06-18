/**
 * PatchedDialogFragment.java Created on 18 Jun 2013 Copyright 2013 Michele
 * Bonazza <michele.bonazza@gmail.com>
 */
package it.mb.whatshare;

import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;

/**
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

}
