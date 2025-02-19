package com.applozic.mobicomkit.contact;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.applozic.mobicomkit.api.attachment.FileClientService;
import com.applozic.mobicomkit.api.people.AlGetPeopleTask;
import com.applozic.mobicomkit.broadcast.BroadcastService;
import com.applozic.mobicomkit.cache.MessageSearchCache;
import com.applozic.mobicomkit.channel.service.ChannelService;
import com.applozic.mobicomkit.contact.database.ContactDatabase;
import com.applozic.mobicomkit.listners.AlContactListener;
import com.applozic.mobicommons.ApplozicService;
import com.applozic.mobicommons.commons.image.ImageUtils;
import com.applozic.mobicommons.people.channel.Channel;
import com.applozic.mobicommons.people.contact.Contact;
import com.applozic.mobicommons.task.AlTask;

import java.io.File;
import java.util.Date;
import java.util.List;

/**
 * For {@link Contact} related operations.
 */
public class AppContactService implements BaseContactService {
    //Cleanup: private all
    private static final String TAG = "AppContactService";

    ContactDatabase contactDatabase;
    Context context;
    FileClientService fileClientService;

    public AppContactService(@NonNull Context context) {
        this.context = ApplozicService.getContext(context);
        this.contactDatabase = new ContactDatabase(context);
        this.fileClientService = new FileClientService(context);
    }

    /**
     * Adds a new contact to the local database.
     *
     * @param contact the contact object
     */
    @Override
    public void add(@NonNull Contact contact) {
        contactDatabase.addContact(contact);
    }

    /**
     * Adds a list of contacts to the local database.
     *
     * @param contactList a list of contact objects
     */
    @Override
    public void addAll(@NonNull List<Contact> contactList) {
        for (Contact contact : contactList) {
            upsert(contact);
        }
    }

    /**
     * Add or updated the contact object in the local database.
     *
     * @param contact the contact object
     */
    @Override
    public void upsert(@NonNull Contact contact) {
        if (contactDatabase.getContactById(contact.getUserId()) == null) {
            contactDatabase.addContact(contact);
        } else {
            contactDatabase.updateContact(contact);
        }
    }

    /**
     * Gets the contact with the given user id.
     *
     * <p>Contact is first fetched in an in-memory cache and then the local database.
     * If no contact is found a new one is created, saved and returned.</p>
     *
     * @param contactId the user id of the contact
     * @return the {@link Contact} object
     */
    @Override
    public @NonNull Contact getContactById(@NonNull String contactId) {
        Contact contact;
        contact = MessageSearchCache.getContactById(contactId);
        if (contact == null) {
            contact = contactDatabase.getContactById(contactId);
        }
        if (contact == null) {
            contact = new Contact(contactId);
            upsert(contact);
        }
        return contact;
    }

    /**
     * Gets the list of all contacts save in the local database.
     *
     * @return the list of {@link Contact} objects
     */
    @Override
    public @NonNull List<Contact> getAll() {
        return contactDatabase.getAllContact();
    }

    /**
     * Gets the list of contacts save in the local database, excluding the current logged user.
     *
     * @return the list of {@link Contact} objects
     */
    @Override
    public @NonNull List<Contact> getAllContactListExcludingLoggedInUser() {
        return contactDatabase.getAllContactListExcludingLoggedInUser();
    }

    /**
     * Deletes the contact entry with the given {@link Contact#getUserId()} in the local database.
     *
     * @param contact the contact object to delete. the contact will be identified by the user id
     */
    @Override
    public void deleteContact(@NonNull Contact contact) {
        contactDatabase.deleteContact(contact);
    }

    /**
     * Deletes the contact entry with the given user id in the local database.
     *
     * @param contactId the contact user id
     */
    @Override
    public void deleteContactById(@NonNull String contactId) {
        contactDatabase.deleteContactById(contactId);
    }

    /**
     * Get a list of contacts of the given {@link com.applozic.mobicommons.people.contact.Contact.ContactType}.
     *
     * @param contactType the {@link com.applozic.mobicommons.people.contact.Contact.ContactType}
     * @return the list of contacts
     */
    @Override
    public @NonNull List<Contact> getContacts(@NonNull Contact.ContactType contactType) {
        return contactDatabase.getContacts(contactType);
    }

    /**
     * Checks if contact information for the given userId exists in the local database.
     *
     * @param contactId the user-id
     * @return true/false
     */
    @Override
    public boolean isContactExists(@Nullable String contactId) {
        return contactDatabase.getContactById(contactId) != null;
    }

    /**
     * Updates the contact object in the local database.
     *
     * <p>Non-null/non-empty/non-zero data is taken from the passed contact object and updated.</p>
     *
     * @param contact the {@link Contact} object
     */
    @Override
    public void updateContact(@NonNull Contact contact) {
        contactDatabase.updateContact(contact);
    }

    /**
     * Downloads and saves the profile image for the given contact locally.
     *
     * <p>Image address is taken from {@link Contact#getImageURL()} and the download image's
     * local path is updated in the database. See {@link Contact#getLocalImageUrl()}.</p>
     *
     * @param context the context
     * @param contact the contact object whose profile image needs to be downloaded
     * @return the bitmap of downloaded image
     */
    @Override
    public @Nullable Bitmap downloadContactImage(@NonNull Context context, @Nullable Contact contact) {
        if (contact == null) {
            return null;
        }

        if (TextUtils.isEmpty(contact.getImageURL())) {
            return null;
        }

        Bitmap attachedImage = ImageUtils.getBitMapFromLocalPath(contact.getLocalImageUrl());
        if (attachedImage != null) {
            return attachedImage;
        }

        Bitmap bitmap = fileClientService.downloadBitmap(contact, null);
        if (bitmap != null) {
            File file = FileClientService.getFilePath(contact.getContactIds(), context.getApplicationContext(), "image", true);
            String imageLocalPath = ImageUtils.saveImageToInternalStorage(file, bitmap);
            contact.setLocalImageUrl(imageLocalPath);
        }
        if (!TextUtils.isEmpty(contact.getLocalImageUrl())) {
            updateLocalImageUri(contact);
        }
        return bitmap;
    }

    /**
     * Downloads and saves the group image for the given channel locally.
     *
     * <p>Image address is taken from {@link Channel#getImageUrl()} and the download image's
     * local path is updated in the database. See {@link Channel#getLocalImageUri()}.</p>
     *
     * @param context the context
     * @param channel the channel object whose group image needs to be downloaded
     * @return the bitmap of downloaded image
     */
    @Override
    public @Nullable Bitmap downloadGroupImage(@NonNull Context context, @Nullable Channel channel) {
        if (channel == null) {
            return null;
        }

        if (TextUtils.isEmpty(channel.getImageUrl())) {
            return null;
        }

        Bitmap attachedImage = ImageUtils.getBitMapFromLocalPath(channel.getLocalImageUri());
        if (attachedImage != null) {
            return attachedImage;
        }

        Bitmap bitmap = fileClientService.downloadBitmap(null, channel);
        if (bitmap != null) {
            File file = FileClientService.getFilePath(String.valueOf(channel.getKey()), context.getApplicationContext(), "image", true);
            String imageLocalPath = ImageUtils.saveImageToInternalStorage(file, bitmap);
            channel.setLocalImageUri(imageLocalPath);
        }

        if (!TextUtils.isEmpty(channel.getLocalImageUri())) {
            ChannelService.getInstance(context).updateChannelLocalImageURI(channel.getKey(), channel.getLocalImageUri());
        }
        return bitmap;
    }

    //internal methods >>>

    /**
     * Internal. Do not use.
     *
     * Add the given key-value pair to the contacts metadata in the local database.
     *
     * <p>This value is not sent to the server.</p>
     *
     * @param userId the user id of the contact to update the metadata for
     * @param key the key
     * @param value the value
     */
    @Override
    public void updateMetadataKeyValueForUserId(String userId, String key, String value) {
        contactDatabase.updateMetadataKeyValueForUserId(userId, key, value);
    }

    /** Internal. Do not use **/
    public Contact getContactReceiver(List<String> items, List<String> userIds) {
        if (userIds != null && !userIds.isEmpty()) {
            return getContactById(userIds.get(0));
        } else if (items != null && !items.isEmpty()) {
            return getContactById(items.get(0));
        }

        return null;
    }

    /** Internal. Do not use **/
    @Override
    public void updateConnectedStatus(String contactId, Date date, boolean connected) {
        Contact contact = contactDatabase.getContactById(contactId);
        if (contact != null && contact.isConnected() != connected) {
            contactDatabase.updateConnectedOrDisconnectedStatus(contactId, date, connected);
            BroadcastService.sendUpdateLastSeenAtTimeBroadcast(context, BroadcastService.INTENT_ACTIONS.UPDATE_LAST_SEEN_AT_TIME.toString(), contactId);
        }
    }

    /** Internal. Do not use **/
    @Override
    public void updateUserBlocked(String userId, boolean userBlocked) {
        if (!TextUtils.isEmpty(userId)) {
            contactDatabase.updateUserBlockStatus(userId, userBlocked);
            BroadcastService.sendUpdateLastSeenAtTimeBroadcast(context, BroadcastService.INTENT_ACTIONS.UPDATE_LAST_SEEN_AT_TIME.toString(), userId);
        }
    }

    /** Internal. Do not use **/
    @Override
    public void updateUserBlockedBy(String userId, boolean userBlockedBy) {
        if (!TextUtils.isEmpty(userId)) {
            contactDatabase.updateUserBlockByStatus(userId, userBlockedBy);
            BroadcastService.sendUpdateLastSeenAtTimeBroadcast(context, BroadcastService.INTENT_ACTIONS.UPDATE_LAST_SEEN_AT_TIME.toString(), userId);
        }
    }

    /** Internal. Do not use **/
    @Override
    public void updateLocalImageUri(Contact contact) {
        contactDatabase.updateLocalImageUri(contact);
    }

    //deprecated >>>

    /**
     * @deprecated Duplicate of {@link #isContactExists(String)}.
     *
     * Checks if contact information for the given userId exists in the local database.
     *
     * @param userId the user id
     * @return true/false
     */
    @Deprecated
    @Override
    public boolean isContactPresent(String userId) {
        return contactDatabase.isContactPresent(userId);
    }

    //Cleanup: private
    /**
     * @deprecated Run {@link #getContactById(String)} asynchronously instead.
     */
    @Deprecated
    public void getContactByIdAsync(String userId, AlContactListener contactListener) {
        AlTask.execute(new AlGetPeopleTask(context, userId, null, null, null, contactListener, this, null));
    }

    /**
     * @deprecated Does not always give accurate value.
     */
    @Deprecated
    @Override
    public int getChatConversationCount() {
        return contactDatabase.getChatUnreadCount();
    }

    /**
     * @deprecated Does not always give accurate value.
     */
    @Deprecated
    @Override
    public int getGroupConversationCount() {
        return contactDatabase.getGroupUnreadCount();
    }
}