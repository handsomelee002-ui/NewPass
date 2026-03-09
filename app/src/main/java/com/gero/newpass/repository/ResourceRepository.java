package com.gero.newpass.repository;

import android.content.Context;


import androidx.annotation.StringRes;

import com.gero.newpass.R;

public class ResourceRepository {
    private final Context context;

    public ResourceRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    public String getString(@StringRes int resId) {
        return context.getString(resId);
    }
}
