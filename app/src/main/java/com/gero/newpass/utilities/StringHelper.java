package com.gero.newpass.utilities;

import java.util.Arrays;

public class StringHelper {
    private static char[] sharedString;

    public static synchronized void setSharedString(String value) {
        clearSharedString();
        sharedString = value == null ? null : value.toCharArray();
    }

    public static synchronized String getSharedString() {
        return sharedString == null ? null : new String(sharedString);
    }

    public static synchronized void clearSharedString() {
        if (sharedString != null) {
            Arrays.fill(sharedString, '\0');
            sharedString = null;
        }
    }
}
