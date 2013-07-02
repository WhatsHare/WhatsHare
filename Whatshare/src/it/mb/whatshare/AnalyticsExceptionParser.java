/**
 * AnalyticsExceptionParser.java Created on 2 Jul 2013 Copyright 2013 Michele
 * Bonazza <michele.bonazza@gmail.com>
 */
package it.mb.whatshare;

import java.io.PrintWriter;
import java.io.StringWriter;

import com.google.analytics.tracking.android.ExceptionParser;

/**
 * Overrides Analytics suboptimal exception tracking system, which only logs the
 * first line in the stack trace.
 * 
 * @author Michele Bonazza
 * 
 */
public class AnalyticsExceptionParser implements ExceptionParser {

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.google.analytics.tracking.android.ExceptionParser#getDescription(
     * java.lang.String, java.lang.Throwable)
     */
    @Override
    public String getDescription(String arg0, Throwable arg1) {
        // @formatter:off
        StringWriter writer = new StringWriter();
        arg1.printStackTrace(new PrintWriter(writer));
        return new StringBuilder("Thread: ")
                    .append(arg0)
                    .append(", Exception: ")
                    .append(writer.toString())
                    .toString();
    }

}
