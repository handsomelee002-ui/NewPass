package com.gero.newpass.model;

public interface ListItem {
    int TYPE_FOLDER = 0;
    int TYPE_PASSWORD = 1;

    int getType();
}
