package com.gero.newpass.utilities;

import android.content.Context;
import android.widget.Toast;
import androidx.annotation.StringRes;

public class ToastHelper {
    private static Toast mToast;

    /**
     * Reuses one process-wide toast so repeated validation/auth messages replace
     * the visible text instead of entering Android's toast queue.
     */
    public static synchronized void showToast(Context context, CharSequence text, int duration) {
        Context appContext = context.getApplicationContext();
        if (mToast == null) {
            mToast = Toast.makeText(appContext, text, duration);
        } else {
            mToast.setText(text);
            mToast.setDuration(duration);
        }
        mToast.show();
    }

    /**
     * Reuses one process-wide toast so repeated string-resource messages replace
     * the visible text instead of entering Android's toast queue.
     */
    public static synchronized void showToast(Context context, @StringRes int resId, int duration) {
        showToast(context, context.getString(resId), duration);
    }
}
