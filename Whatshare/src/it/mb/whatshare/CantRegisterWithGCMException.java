/**
 * CantRegisterWithGCMException.java Created on 18 Jun 2013 Copyright 2013
 * Michele Bonazza <emmepuntobi@gmail.com>
 * 
 * Copyright 2013 Michele Bonazza <emmepuntobi@gmail.com> This file is part of WhatsHare.
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
