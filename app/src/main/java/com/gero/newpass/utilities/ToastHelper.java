package com.gero.newpass.utilities;

import android.content.Context;
import android.widget.Toast;
import androidx.annotation.StringRes;

public class ToastHelper {
    private static Toast mToast;

    /**
     * Shows a toast message, instantly cancelling any currently visible toast
     * to prevent Android from queuing up "toast spam".
     */
    public static void showToast(Context context, CharSequence text, int duration) {
        if (mToast != null) {
            mToast.cancel();
        }
        mToast = Toast.makeText(context, text, duration);
        mToast.show();
    }

    /**
     * Shows a toast message from a string resource, instantly cancelling any
     * currently visible toast to prevent Android from queuing up "toast spam".
     */
    public static void showToast(Context context, @StringRes int resId, int duration) {
        if (mToast != null) {
            mToast.cancel();
        }
        mToast = Toast.makeText(context, resId, duration);
        mToast.show();
    }
}
