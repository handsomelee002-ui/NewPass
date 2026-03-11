package com.gero.newpass.model;

public class FolderData implements ListItem {
    private String id;
    private String name;
    private int sortOrder;

    public FolderData(String id, String name, int sortOrder) {
        this.id = id;
        this.name = name;
        this.sortOrder = sortOrder;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    @Override
    public int getType() {
        return TYPE_FOLDER;
    }
}
