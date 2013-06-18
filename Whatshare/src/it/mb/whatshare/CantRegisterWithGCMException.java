/**
 * CantRegisterWithGCMException.java Created on 18 Jun 2013 Copyright 2013
 * Michele Bonazza <michele.bonazza@gmail.com>
 */
package it.mb.whatshare;

/**
 * Thrown upon receipt of an error message from GCM.
 * 
 * @author Michele Bonazza
 * 
 */
public class CantRegisterWithGCMException extends Exception {

    private static final long serialVersionUID = -3160414477732020594L;
    private final int messageID;

    /**
     * Creates a new exception.
     * 
     * @param messageID
     *            the ID in use within <tt>strings.xml</tt> for the error
     *            message
     */
    public CantRegisterWithGCMException(int messageID) {
        this.messageID = messageID;
    }

    /**
     * Returns the ID to be used to retrieve the error message to be displayed
     * to the user.
     * 
     * @return the ID of the resource string to be retrieved from
     *         <tt>strings.xml</tt>
     */
    public int getMessageID() {
        return messageID;
    }

}
