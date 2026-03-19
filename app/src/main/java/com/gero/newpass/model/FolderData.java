package com.gero.newpass.model;

public class FolderData implements ListItem {
    private String id;
    private String name;
    private Integer parentFolderId;
    private int sortOrder;

    public FolderData(String id, String name, Integer parentFolderId, int sortOrder) {
        this.id = id;
        this.name = name;
        this.parentFolderId = parentFolderId;
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

    public Integer getParentFolderId() {
        return parentFolderId;
    }

    public void setParentFolderId(Integer parentFolderId) {
        this.parentFolderId = parentFolderId;
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
