package com.applozic.mobicomkit.api.conversation;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.applozic.mobicomkit.ApplozicClient;
import com.applozic.mobicomkit.api.MobiComKitConstants;
import com.applozic.mobicomkit.api.account.user.MobiComUserPreference;
import com.applozic.mobicomkit.api.account.user.UserService;
import com.applozic.mobicomkit.api.attachment.FileClientService;
import com.applozic.mobicomkit.api.conversation.database.MessageDatabaseService;
import com.applozic.mobicomkit.api.conversation.selfdestruct.DisappearingMessageTask;
import com.applozic.mobicomkit.api.mention.MentionHelper;
import com.applozic.mobicomkit.api.notification.VideoCallNotificationHelper;
import com.applozic.mobicomkit.broadcast.BroadcastService;
import com.applozic.mobicomkit.channel.service.ChannelService;
import com.applozic.mobicomkit.contact.AppContactService;
import com.applozic.mobicomkit.contact.BaseContactService;
import com.applozic.mobicomkit.contact.database.ContactDatabase;
import com.applozic.mobicomkit.feed.ApiResponse;
import com.applozic.mobicomkit.sync.SyncMessageFeed;
import com.applozic.mobicommons.ApplozicService;
import com.applozic.mobicommons.commons.core.utils.Utils;
import com.applozic.mobicommons.people.channel.Channel;
import com.applozic.mobicommons.people.contact.Contact;
import com.applozic.mobicommons.personalization.PersonalizedMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;

/**
 * Created by devashish on 24/3/15.
 */
public class MobiComMessageService {

    //Cleanup: all private
    public static final long DELAY = 60000L;
    private static final String TAG = "MobiComMessageService";
    public static Map<String, Uri> map = new HashMap<String, Uri>();
    public static Map<String, Message> mtMessages = new LinkedHashMap<String, Message>();
    protected Context context;
    protected MobiComConversationService conversationService;
    protected MessageDatabaseService messageDatabaseService;
    protected MessageClientService messageClientService;
    protected Class messageIntentServiceClass;
    protected BaseContactService baseContactService;
    protected UserService userService;
    protected FileClientService fileClientService;
    private boolean isHideActionMessage;
    private Short loggedInUserRole;
    private String loggedInUserId;

    //Cleanup: remove 2nd parameter
    public MobiComMessageService(Context context, Class messageIntentServiceClass) {
        this.context = ApplozicService.getContext(context);
        this.messageDatabaseService = new MessageDatabaseService(context);
        this.messageClientService = new MessageClientService(context);
        this.conversationService = new MobiComConversationService(context);
        this.messageIntentServiceClass = messageIntentServiceClass;
        //Todo: this can be changed to DeviceContactService for device contacts usage.
        this.baseContactService = new AppContactService(context);
        fileClientService = new FileClientService(context);
        this.userService = UserService.getInstance(context);
        isHideActionMessage = ApplozicClient.getInstance(context).isActionMessagesHidden();
        loggedInUserRole = MobiComUserPreference.getInstance(context).getUserRoleType();
        loggedInUserId = MobiComUserPreference.getInstance(context).getUserId();
    }

    //Cleanup: private, MessageSendTimer is not used
    public Message processMessage(final Message messageToProcess, String tofield, int index) {
        if (Message.MetaDataType.HIDDEN.getValue().equals(messageToProcess.getMetaDataValueForKey(Message.MetaDataType.KEY.getValue()))) {
            MessageWorker.enqueueWork(context, messageToProcess, null, null);
            return null;
        } else if (Message.MetaDataType.PUSHNOTIFICATION.getValue().equals(messageToProcess.getMetaDataValueForKey(Message.MetaDataType.KEY.getValue()))) {
            BroadcastService.sendNotificationBroadcast(context, messageToProcess, index);
            MessageWorker.enqueueWork(context, messageToProcess, null, null);
            return null;
        }

        Message message = prepareMessage(messageToProcess, tofield);
        //download contacts in advance.
        if (message.getGroupId() != null) {
            Channel channel = ChannelService.getInstance(context).getChannelInfo(message.getGroupId());
            if (channel == null) {
                return null;
            }
        }
        if (message.getContentType() == Message.ContentType.CONTACT_MSG.getValue()) {
            fileClientService.loadContactsvCard(message);
        }
        try {
            List<String> messageKeys = new ArrayList<>();
            if (message.getMetadata() != null && message.getMetaDataValueForKey(Message.MetaDataType.AL_REPLY.getValue()) != null && !messageDatabaseService.isMessagePresent(message.getMetaDataValueForKey(Message.MetaDataType.AL_REPLY.getValue()))) {
                messageKeys.add(message.getMetaDataValueForKey(Message.MetaDataType.AL_REPLY.getValue()));
            }
            if (messageKeys != null && messageKeys.size() > 0) {
                Message[] replyMessageList = conversationService.getMessageListByKeyList(messageKeys);
                if (replyMessageList != null) {
                    Message replyMessage = replyMessageList[0];
                    if (replyMessage != null) {
                        if (replyMessage.hasAttachment() && !(replyMessage.getContentType() == Message.ContentType.TEXT_URL.getValue())) {
                            conversationService.setFilePathifExist(replyMessage);
                        }
                        if (replyMessage.getContentType() == Message.ContentType.CONTACT_MSG.getValue()) {
                            fileClientService.loadContactsvCard(replyMessage);
                        }
                        replyMessage.setReplyMessage(Message.ReplyMessage.HIDE_MESSAGE.getValue());
                        messageDatabaseService.createMessage(replyMessage);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (message.getType().equals(Message.MessageType.MT_INBOX.getValue())) {
            addMTMessage(message, index);
        } else if (message.getType().equals(Message.MessageType.MT_OUTBOX.getValue())) {
            BroadcastService.sendMessageUpdateBroadcast(context, BroadcastService.INTENT_ACTIONS.SYNC_MESSAGE.toString(), message);
            messageDatabaseService.createMessage(message);
            if (!message.getCurrentId().equals(BroadcastService.currentUserId)) {
                MobiComUserPreference.getInstance(context).setNewMessageFlag(true);
            }
            if (message.isVideoNotificationMessage()) {
                Utils.printLog(context, TAG, "Got notifications for Video call...");
                VideoCallNotificationHelper helper = new VideoCallNotificationHelper(context);
                helper.handleVideoCallNotificationMessages(message);
            }
        }
        Utils.printLog(context, TAG, "processing message: " + message);
        return message;
    }

    //Cleanup: private
    public Message prepareMessage(Message messageToProcess, String tofield) {
        Message message = new Message(messageToProcess);
        message.setMessageId(messageToProcess.getMessageId());
        message.setKeyString(messageToProcess.getKeyString());
        message.setPairedMessageKeyString(messageToProcess.getPairedMessageKeyString());

        if (message.getMessage() != null && PersonalizedMessage.isPersonalized(message.getMessage())) {
            Contact contact = null;
            if (message.getGroupId() == null) {
                contact = baseContactService.getContactById(tofield);
            }
            if (contact != null) {
                message.setMessage(PersonalizedMessage.prepareMessageFromTemplate(message.getMessage(), contact));
            }
        }
        return message;
    }

    //Cleanup: private
    public Contact addMTMessage(Message message, int index) {
        MobiComUserPreference userPreferences = MobiComUserPreference.getInstance(context);
        Contact receiverContact = null;
        message.processContactIds(context);

        String currentId = message.getCurrentId();
        if (message.getGroupId() == null) {
            receiverContact = baseContactService.getContactById(message.getContactIds());
        }

        if (message.getMessage() != null && PersonalizedMessage.isPersonalized(message.getMessage()) && receiverContact != null) {
            message.setMessage(PersonalizedMessage.prepareMessageFromTemplate(message.getMessage(), receiverContact));
        }

        if (isHideActionMessage && message.isActionMessage()) {
            message.setHidden(true);
        }

        messageDatabaseService.createMessage(message);

        //Check if we are........container is already opened...don't send broadcast
        boolean isContainerOpened;
        if (message.getConversationId() != null && BroadcastService.isContextBasedChatEnabled()) {
            if (BroadcastService.currentConversationId == null) {
                BroadcastService.currentConversationId = message.getConversationId();
            }
            isContainerOpened = (currentId.equals(BroadcastService.currentUserId) && message.getConversationId().equals(BroadcastService.currentConversationId));
        } else {
            isContainerOpened = currentId.equals(BroadcastService.currentUserId);
        }
        if (message.isVideoNotificationMessage()) {
            Utils.printLog(context, TAG, "Got notifications for Video call...");
            BroadcastService.sendMessageUpdateBroadcast(context, BroadcastService.INTENT_ACTIONS.SYNC_MESSAGE.toString(), message);

            VideoCallNotificationHelper helper = new VideoCallNotificationHelper(context);
            helper.handleVideoCallNotificationMessages(message);

        } else if (message.isVideoCallMessage()) {
            BroadcastService.sendMessageUpdateBroadcast(context, BroadcastService.INTENT_ACTIONS.SYNC_MESSAGE.toString(), message);
            VideoCallNotificationHelper.buildVideoCallNotification(context, message, index);
        } else if (!isContainerOpened) {

            if (message.isConsideredForCount() && !message.hasHideKey()) {
                if (message.getTo() != null && message.getGroupId() == null && !message.isHidden()) {
                    messageDatabaseService.updateContactUnreadCount(message.getTo());
                    BroadcastService.sendMessageUpdateBroadcast(context, BroadcastService.INTENT_ACTIONS.SYNC_MESSAGE.toString(), message);
                    Contact contact = new ContactDatabase(context).getContactById(message.getTo());
                    if (contact != null && !contact.isNotificationMuted()) {
                        sendNotification(message, index);
                    }
                }
                if (message.getGroupId() != null && !Message.GroupMessageMetaData.FALSE.getValue().equals(message.getMetaDataValueForKey(Message.GroupMessageMetaData.KEY.getValue()))) {
                    if (!Message.ContentType.CHANNEL_CUSTOM_MESSAGE.getValue().equals(message.getContentType()) && !message.isHidden()) {
                        messageDatabaseService.updateChannelUnreadCount(message.getGroupId());
                    }
                    BroadcastService.sendMessageUpdateBroadcast(context, BroadcastService.INTENT_ACTIONS.SYNC_MESSAGE.toString(), message);
                    Channel currentChannel = ChannelService.getInstance(context).getChannelInfo(message.getGroupId());

                    if (currentChannel != null && (!currentChannel.isNotificationMuted() || MentionHelper.isLoggedInUserMentionedInChannelMessage(context, message))) {
                        sendNotification(message, index);
                    }
                }
                MobiComUserPreference.getInstance(context).setNewMessageFlag(true);
            } else {
                BroadcastService.sendMessageUpdateBroadcast(context, BroadcastService.INTENT_ACTIONS.SYNC_MESSAGE.toString(), message);
            }
        } else {
            BroadcastService.sendMessageUpdateBroadcast(context, BroadcastService.INTENT_ACTIONS.SYNC_MESSAGE.toString(), message);
        }

        return receiverContact;
    }

    //Cleanup: private
    public void sendNotification(Message message, int index) {
        if (message.isHidden()) {
            return;
        }

        BroadcastService.sendNotificationBroadcast(context, message, index);
        Intent intent = new Intent(MobiComKitConstants.APPLOZIC_UNREAD_COUNT);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    //Cleanup: private
    public void processOpenGroupAttachmentMessage(Message message) {
        processMessage(message, message.getTo(), 0);
    }

    /**
     * Will sync data from the respective last sync times:
     * 1) Messages
     * 2) Channels
     * 3) Channel metadata
     * 4) Group of two block
     * 5) Delivered message status
     * 6) Contact details
     */
    public synchronized void syncMessages() {
        final MobiComUserPreference userpref = MobiComUserPreference.getInstance(context);
        boolean syncChannel = false;
        boolean syncChannelForMetadata = false;
        boolean syncGroupOfTwoForBlockList = false;

        Utils.printLog(context, TAG, "Starting syncMessages for lastSyncTime: " + userpref.getLastSyncTime());
        SyncMessageFeed syncMessageFeed = messageClientService.getMessageFeed(userpref.getLastSyncTime(), false);
        if (syncMessageFeed == null) {
            return;
        }
        if (syncMessageFeed != null && syncMessageFeed.getMessages() != null) {
            Utils.printLog(context, TAG, "Got sync response " + syncMessageFeed.getMessages().size() + " messages.");
            processUserDetailFromMessages(syncMessageFeed.getMessages());
        }
        // if regIdInvalid in syncrequest, tht means device reg with c2dm is no
        // more valid, do it again and make the sync request again
        if (syncMessageFeed != null && syncMessageFeed.isRegIdInvalid()
                && Utils.hasFroyo()) {
            Utils.printLog(context, TAG, "Going to call GCM device registration");
            //Todo: Replace it with mobicomkit gcm registration
            // C2DMessaging.register(context);
        }
        if (syncMessageFeed != null && syncMessageFeed.getMessages() != null) {
            List<Message> messageList = syncMessageFeed.getMessages();

            for (int i = messageList.size() - 1; i >= 0; i--) {
                if (Message.ContentType.CHANNEL_CUSTOM_MESSAGE.getValue().equals(messageList.get(i).getContentType())) {
                    if (messageList.get(i).isGroupMetaDataUpdated()) {
                        syncChannelForMetadata = true;
                    } else {
                        syncChannel = true;
                    }
                    //Todo: fix this, what if there are mulitple messages.
                    ChannelService.isUpdateTitle = true;
                }
                if (Message.ContentType.BLOCK_NOTIFICATION_IN_GROUP.getValue().equals(messageList.get(i).getContentType())) {
                    syncGroupOfTwoForBlockList = true;
                }
                processMessage(messageList.get(i), messageList.get(i).getTo(), ((messageList.size() - 1) - i));
                MobiComUserPreference.getInstance(context).setLastInboxSyncTime(messageList.get(i).getCreatedAtTime());
            }

            if (syncChannel) {
                ChannelService.getInstance(context).syncChannels(false);
            }
            if (syncChannelForMetadata) {
                ChannelService.getInstance(context).syncChannels(true);
            }
            if (syncGroupOfTwoForBlockList) {
                UserService.getInstance(context).processSyncUserBlock();
            }
            updateDeliveredStatus(syncMessageFeed.getDeliveredMessageKeys());
            userpref.setLastSyncTime(String.valueOf(syncMessageFeed.getLastSyncTime()));
        }
    }

    //Cleanup: default
    public synchronized void syncMessageForMetadataUpdate() {
        final MobiComUserPreference userpref = MobiComUserPreference.getInstance(context);
        SyncMessageFeed syncMessageFeed = messageClientService.getMessageFeed(userpref.getLastSyncTimeForMetadataUpdate(), true);

        Utils.printLog(context, TAG, "\nStarting syncMessages for metadata update for lastSyncTime: " + userpref.getLastSyncTimeForMetadataUpdate());
        if (syncMessageFeed != null && syncMessageFeed.getMessages() != null) {
            userpref.setLastSyncTimeForMetadataUpdate(String.valueOf(syncMessageFeed.getLastSyncTime()));
            List<Message> messageList = syncMessageFeed.getMessages();
            for (final Message message : messageList) {
                if (message != null) {
                    messageDatabaseService.replaceExistingMessage(message);
                    BroadcastService.updateMessageMetadata(context, message.getKeyString(), BroadcastService.INTENT_ACTIONS.MESSAGE_METADATA_UPDATE.toString(), message.getGroupId() == null ? message.getTo() : null, message.getGroupId(), false, message.getMetadata());
                }
            }
        }
    }

    //Cleanup: remove this method, unnecessary
    public MessageInfoResponse getMessageInfoResponse(String messageKey) {
        MessageInfoResponse messageInfoResponse = messageClientService.getMessageInfoList(messageKey);
        return messageInfoResponse;

    }

    private void updateDeliveredStatus(List<String> deliveredMessageKeys) {
        if (deliveredMessageKeys == null) {
            return;
        }
        for (String messageKey : deliveredMessageKeys) {
            messageDatabaseService.updateMessageDeliveryReportForContact(messageKey, false);
            Message message = messageDatabaseService.getMessage(messageKey);
            if (message != null) {
                BroadcastService.sendMessageUpdateBroadcast(context, BroadcastService.INTENT_ACTIONS.MESSAGE_DELIVERY.toString(), message);
            }
        }
    }

    //Cleanup: default
    public boolean isMessagePresent(String key) {
        return messageDatabaseService.isMessagePresent(key);
    }

    //Cleanup: private
    public void processUserDetailFromMessages(List<Message> messages) {
        try {
            if (!ApplozicClient.getInstance(context).isHandleDisplayName()) {
                return;
            }
            Set<String> userIds = new HashSet<String>();
            for (Message msg : messages) {
                if (!baseContactService.isContactPresent(msg.getContactIds())) {
                    userIds.add(msg.getContactIds());
                }
            }

            if (userIds.isEmpty()) {
                return;
            }
            userService.processUserDetails(userIds);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //Cleanup: default
    public synchronized void updateDeliveryStatusForContact(String contactId, boolean markRead) {
        int rows = messageDatabaseService.updateMessageDeliveryReportForContact(contactId, markRead);
        Utils.printLog(context, TAG, "Updated delivery report of " + rows + " messages for contactId: " + contactId);

        if (rows > 0) {
            String action = markRead ? BroadcastService.INTENT_ACTIONS.MESSAGE_READ_AND_DELIVERED_FOR_CONTECT.toString() :
                    BroadcastService.INTENT_ACTIONS.MESSAGE_DELIVERY_FOR_CONTACT.toString();
            BroadcastService.sendDeliveryReportForContactBroadcast(context, action, contactId);
        }
    }

    //Cleanup: default
    public synchronized void updateDeliveryStatus(String key, boolean markRead) {
        //Todo: Check if this is possible? In case the delivery report reaches before the sms is reached, then wait for the sms.
        Utils.printLog(context, TAG, "Got the delivery report for key: " + key);
        String keyParts[] = key.split((","));
        Message message = messageDatabaseService.getMessage(keyParts[0]);
        if (message != null && (message.getStatus() != Message.Status.DELIVERED_AND_READ.getValue())) {
            message.setDelivered(Boolean.TRUE);

            if (markRead) {
                message.setStatus(Message.Status.DELIVERED_AND_READ.getValue());
            } else {
                message.setStatus(Message.Status.DELIVERED.getValue());
            }
            //Todo: Server need to send the contactNumber of the receiver in case of group messaging and update
            //delivery report only for that number
            messageDatabaseService.updateMessageDeliveryReportForContact(keyParts[0], null, markRead);
            String action = markRead ? BroadcastService.INTENT_ACTIONS.MESSAGE_READ_AND_DELIVERED.toString() :
                    BroadcastService.INTENT_ACTIONS.MESSAGE_DELIVERY.toString();
            BroadcastService.sendMessageUpdateBroadcast(context, action, message);
            if (message.getTimeToLive() != null && message.getTimeToLive() != 0) {
                Timer timer = new Timer();
                timer.schedule(new DisappearingMessageTask(context, new MobiComConversationService(context), message), message.getTimeToLive() * 60 * 1000);
            }
        } else if (message == null) {
            Utils.printLog(context, TAG, "Message is not present in table, keyString: " + keyParts[0]);
        }
        map.remove(key);
        mtMessages.remove(key);
    }

    //Cleanup: remove, unnecessary
    public ApiResponse getUpdateMessageMetadata(String key, Map<String, String> metadata) {
        return messageClientService.updateMessageMetadata(key, metadata);
    }

    //Cleanup: remove
    public void createEmptyMessage(Contact contact) {
        Message sms = new Message();
        MobiComUserPreference userPreferences = MobiComUserPreference.getInstance(context);
        sms.setTo(contact.getContactNumber());
        sms.setCreatedAtTime(0L);
        sms.setStoreOnDevice(Boolean.TRUE);
        sms.setSendToDevice(Boolean.FALSE);
        sms.setType(Message.MessageType.MT_OUTBOX.getValue());
        sms.setDeviceKeyString(userPreferences.getDeviceKeyString());
        sms.setSource(Message.Source.MT_MOBILE_APP.getValue());
        messageDatabaseService.createMessage(sms);
    }

    //Cleanup: can be removed (is public)
    //Cleanup: remove
    public String getMessageDeleteForAllResponse(String messageKey, boolean deleteForAll) throws Exception {
        return messageClientService.getMessageDeleteForAllResponse(messageKey, deleteForAll);
    }

    //Cleanup: try to make default
    public synchronized void syncMessageDataAndSendBroadcastFor(Message message) {

        if (!baseContactService.isContactPresent(message.getContactIds())) {
            userService.processUserDetails(message.getContactIds());
        }
        Channel channel = ChannelService.getInstance(context).getChannelInfo(message.getGroupId());
        if (channel == null) {
            return;
        }
        if (message.hasAttachment()) {
            processOpenGroupAttachmentMessage(message);
        } else {
            BroadcastService.sendMessageUpdateBroadcast(context, BroadcastService.INTENT_ACTIONS.SYNC_MESSAGE.toString(), message);
        }
    }
}
