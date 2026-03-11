package com.gero.newpass.model;

public class UserData implements ListItem {
    private String id;
    private String name;
    private String email;
    private String password;
    private Integer folderId;
    private int sortOrder;

    public UserData(String id, String name, String email, String password, Integer folderId, int sortOrder) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.password = password;
        this.folderId = folderId;
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Integer getFolderId() {
        return folderId;
    }
    
    public void setFolderId(Integer folderId) {
        this.folderId = folderId;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    @Override
    public int getType() {
        return TYPE_PASSWORD;
    }
}
