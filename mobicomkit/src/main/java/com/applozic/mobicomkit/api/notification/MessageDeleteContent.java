package com.applozic.mobicomkit.api.notification;

import java.util.List;

public class MessageDeleteContent {

    private List<String> deleteKeyStrings;
    private String contactNumber;

    public List<String> getDeleteKeyStrings() {
        return deleteKeyStrings;
    }

    public void setDeleteKeyStrings(List<String> deleteKeyStrings) {
        this.deleteKeyStrings = deleteKeyStrings;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber;
    }
}
