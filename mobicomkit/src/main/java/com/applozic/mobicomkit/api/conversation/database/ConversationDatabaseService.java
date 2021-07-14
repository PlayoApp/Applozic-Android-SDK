package com.applozic.mobicomkit.api.conversation.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import com.applozic.mobicomkit.database.MobiComDatabaseHelper;
import com.applozic.mobicommons.ApplozicService;
import com.applozic.mobicommons.commons.core.utils.Utils;
import com.applozic.mobicommons.people.channel.Channel;
import com.applozic.mobicommons.people.channel.Conversation;
import com.applozic.mobicommons.people.contact.Contact;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by sunil on 12/2/16.
 */
//ApplozicInternal: default
public class ConversationDatabaseService {

    private static final String TAG = "ConversationDatabase";
    private static ConversationDatabaseService conversationDatabaseService;
    private MobiComDatabaseHelper dbHelper;
    private Context context;

    private ConversationDatabaseService(Context context) {
        this.context = ApplozicService.getContext(context);
        this.dbHelper = MobiComDatabaseHelper.getInstance(context);
    }

    public static synchronized ConversationDatabaseService getInstance(Context context) {
        if (conversationDatabaseService == null) {
            conversationDatabaseService = new ConversationDatabaseService(ApplozicService.getContext(context));
        }
        return conversationDatabaseService;
    }

    //ApplozicInternal: private
    public static List<Conversation> getConversationList(Cursor cursor) {
        List<Conversation> conversationList = new ArrayList<Conversation>();
        cursor.moveToFirst();
        if (cursor.getCount() > 0) {
            do {
                conversationList.add(getConversation(cursor));
            } while (cursor.moveToNext());
        }
        return conversationList;
    }

    //ApplozicInternal: private
    public static Conversation getConversation(Cursor cursor) {
        Conversation conversation = new Conversation();
        conversation.setId(cursor.getInt(cursor.getColumnIndex(MobiComDatabaseHelper.KEY)));
        conversation.setGroupId(cursor.getInt(cursor.getColumnIndex(MobiComDatabaseHelper.CHANNEL_KEY)));
        String topicId = cursor.getString(cursor.getColumnIndex(MobiComDatabaseHelper.TOPIC_ID));
        if (!TextUtils.isEmpty(topicId)) {
            conversation.setTopicId(topicId);
        }
        String topicDetail = cursor.getString(cursor.getColumnIndex(MobiComDatabaseHelper.TOPIC_DETAIL));
        if (!TextUtils.isEmpty(topicDetail)) {
            conversation.setTopicDetail(topicDetail);
        }
        String userId = cursor.getString(cursor.getColumnIndex(MobiComDatabaseHelper.USERID));
        if (!TextUtils.isEmpty(userId)) {
            conversation.setUserId(userId);
        }
        conversation.setTopicLocalImageUri(cursor.getString(cursor.getColumnIndex(MobiComDatabaseHelper.TOPIC_LOCAL_IMAGE_URL)));
        return conversation;
    }

    //ApplozicInternal: default
    public void addConversation(Conversation conversation) {
        try {
            ContentValues contentValues = prepareConversationValue(conversation);
            long rowsUpdated = dbHelper.getWritableDatabase().insert(MobiComDatabaseHelper.CONVERSATION, null, contentValues);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            dbHelper.close();
        }
    }

    //ApplozicInternal: private
    public ContentValues prepareConversationValue(Conversation conversation) {
        ContentValues contentValues = new ContentValues();
        if (conversation != null) {
            if (conversation.getId() != null) {
                contentValues.put(MobiComDatabaseHelper.KEY, conversation.getId());
            }
            if (!TextUtils.isEmpty(conversation.getTopicId())) {
                contentValues.put(MobiComDatabaseHelper.TOPIC_ID, conversation.getTopicId());
            }
            if (conversation.getGroupId() != null) {
                contentValues.put(MobiComDatabaseHelper.CHANNEL_KEY, conversation.getGroupId());
            }
            if (!TextUtils.isEmpty(conversation.getUserId())) {
                contentValues.put(MobiComDatabaseHelper.USERID, conversation.getUserId());
            }
            if (!TextUtils.isEmpty(conversation.getTopicDetail())) {
                contentValues.put(MobiComDatabaseHelper.TOPIC_DETAIL, conversation.getTopicDetail());
            }
            if (!TextUtils.isEmpty(conversation.getTopicLocalImageUri())) {
                contentValues.put(MobiComDatabaseHelper.TOPIC_LOCAL_IMAGE_URL, conversation.getTopicLocalImageUri());
            }
        }
        return contentValues;
    }

    //ApplozicInternal: default
    public Conversation getConversationByConversationId(final Integer conversationId) {
        Conversation conversation = null;
        SQLiteDatabase database = dbHelper.getReadableDatabase();

        String conversationParameters = "";
        List<String> structuredNameParamsList = new ArrayList<>();

        conversationParameters += "key = ? ";
        structuredNameParamsList.add(String.valueOf(conversationId));

        Cursor cursor = database.query(MobiComDatabaseHelper.CONVERSATION, null, conversationParameters, structuredNameParamsList.toArray(new String[structuredNameParamsList.size()]), null, null, null);

        if (cursor.moveToFirst()) {
            conversation = getConversation(cursor);
            cursor.close();
        }

        dbHelper.close();
        return conversation;
    }

    //ApplozicInternal: private
    public Conversation getConversationByTopicId(final String topicId, Context context) {
        if (TextUtils.isEmpty(topicId)) {
            return null;
        }
        Conversation conversation = null;
        SQLiteDatabase database = MobiComDatabaseHelper.getInstance(context).getReadableDatabase();
        String conversationParameters = "";
        List<String> structuredNameParamsList = new ArrayList<>();

        conversationParameters += MobiComDatabaseHelper.TOPIC_ID + "= ? ";
        structuredNameParamsList.add(topicId);

        Cursor cursor = database.query(MobiComDatabaseHelper.CONVERSATION, null, conversationParameters, structuredNameParamsList.toArray(new String[structuredNameParamsList.size()]), null, null, null);

        if (cursor.moveToFirst()) {
            conversation = getConversation(cursor);
        }
        if (cursor != null) {
            cursor.close();
        }

        dbHelper.close();
        return conversation;
    }

    //ApplozicInternal: default
    public List<Conversation> getConversationList(final Channel channel, final Contact contact) {
        List<Conversation> conversation = null;
        SQLiteDatabase database = dbHelper.getReadableDatabase();

        String conversationParameters = "";
        List<String> structuredNameParamsList = new ArrayList<>();

        if (channel != null) {
            conversationParameters += "channelKey = ? ";
            structuredNameParamsList.add(String.valueOf(channel.getKey()));
        } else {
            conversationParameters += "userId = ? ";
            structuredNameParamsList.add(contact.getContactIds());
        }
        Cursor cursor = database.query(MobiComDatabaseHelper.CONVERSATION, null, conversationParameters, structuredNameParamsList.toArray(new String[structuredNameParamsList.size()]), null, null, "key desc");

        if (cursor.moveToFirst()) {
            conversation = getConversationList(cursor);
        }
        if (cursor != null) {
            cursor.close();
        }
        return conversation;
    }

    //ApplozicInternal: default
    public boolean isConversationPresent(Integer conversationId) {
        SQLiteDatabase database = dbHelper.getWritableDatabase();
        Cursor cursor = database.rawQuery(
                "SELECT COUNT(*) FROM conversation WHERE key=?", new String[]{String.valueOf(conversationId)});
        boolean present = false;
        if (cursor.moveToFirst()) {
            present = cursor.getInt(0) > 0;
            cursor.close();
        }
        dbHelper.close();
        return present;
    }

    //ApplozicInternal: default
    public void updateConversation(Conversation conversation) {
        try {
            ContentValues contentValues = prepareConversationValue(conversation);
            dbHelper.getWritableDatabase().update(MobiComDatabaseHelper.CONVERSATION, contentValues, MobiComDatabaseHelper.KEY + "=?", new String[]{String.valueOf(conversation.getId())});

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //ApplozicInternal: default
    public void deleteConversation(String userId) {
        int deletedRows = dbHelper.getWritableDatabase().delete(MobiComDatabaseHelper.CONVERSATION, MobiComDatabaseHelper.USERID + "=?", new String[]{userId});
        Utils.printLog(context,TAG, "Delete no of conversation:" + deletedRows);
    }

    //ApplozicInternal: default
    public Integer isConversationExit(String userId, String topicId) {
        Conversation conversation = null;
        SQLiteDatabase database = dbHelper.getReadableDatabase();

        String conversationParameters = "";
        List<String> structuredNameParamsList = new ArrayList<>();

        conversationParameters += "userId = ? ";
        structuredNameParamsList.add(userId);

        conversationParameters += " and topicId = ? ";
        structuredNameParamsList.add(topicId);

        Cursor cursor = database.query(MobiComDatabaseHelper.CONVERSATION, null, conversationParameters, structuredNameParamsList.toArray(new String[structuredNameParamsList.size()]), null, null, null);

        if (cursor.moveToFirst()) {
            conversation = getConversation(cursor);
            cursor.close();
            return conversation.getId();
        }
        if (cursor != null) {
            cursor.close();
        }
        return null;
    }

    //ApplozicInternal: default
    public void updateTopicLocalImageUri(String imageUri, Integer conversationId) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(MobiComDatabaseHelper.TOPIC_LOCAL_IMAGE_URL, imageUri);
        int updatedRow = dbHelper.getWritableDatabase().update(MobiComDatabaseHelper.CONVERSATION, contentValues, MobiComDatabaseHelper.KEY + "=?", new String[]{String.valueOf(conversationId)});
    }
}
