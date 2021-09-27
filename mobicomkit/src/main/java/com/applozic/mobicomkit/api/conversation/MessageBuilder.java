package com.applozic.mobicomkit.api.conversation;

import android.content.Context;

import com.applozic.mobicomkit.api.people.ChannelInfo;
import com.applozic.mobicomkit.exception.ApplozicException;
import com.applozic.mobicomkit.listners.MediaUploadProgressHandler;
import com.applozic.mobicommons.people.channel.Channel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Helper class to send a {@link Message}.
 *
 * <p><b>To send a message to a one-to-one conversation:</b></p>
 * <code>
 *     MessageBuilder messageBuilder = new MessageBuilder(context)
 *                 .setMessage("Hello user123!")
 *                 .setTo("user123");
 *
 *     messageBuilder.send();
 * </code>
 *
 * <p><b>To send a message to a group conversation:</b></p>
 * <p>- First create a {@link com.applozic.mobicommons.people.channel.Channel} using {@link com.applozic.mobicomkit.channel.service.ChannelService#createChannelWithResponse(ChannelInfo)}. Have the {@link Channel#getKey()} or {@link Channel#getClientGroupId()} handy.</p>
 * <p>- Then refer to the following code:</p>
 * <code>
 *     MessageBuilder messageBuilder = new MessageBuilder(context)
 *                 .setMessage("Hello channel!")
 *                 //.setGroupId(123455); //use this or setClientGroupId
 *                 .setClientGroupId("group123");
 *
 *     messageBuilder.send();
 * </code>
 *
 * <p><b>To send a message with an attachment:</b></p>
 * <code>
 *     MessageBuilder messageBuilder = new MessageBuilder(context)
 *                 .setMessage("Hello! Here is a picture.")
 *                 .setTo("user123")
 *                 //.setClientGroupId("group123") //attachments can also be sent to groups
 *                 .setContentType(Message.ContentType.ATTACHMENT.getValue())
 *                 .setFilePath("local/path/to/image.jpg");
 *
 *         messageBuilder.send(new MediaUploadProgressHandler() {
 *             @Override
 *             public void onUploadStarted(ApplozicException e, String oldMessageKey) {
 *
 *             }
 *
 *             @Override
 *             public void onProgressUpdate(int percentage, ApplozicException e, String oldMessageKey) {
 *
 *             }
 *
 *             @Override
 *             public void onCancelled(ApplozicException e, String oldMessageKey) {
 *
 *             }
 *
 *             @Override
 *             public void onCompleted(ApplozicException e, String oldMessageKey) {
 *
 *             }
 *
 *             @Override
 *             public void onSent(Message message, String oldMessageKey) {
 *                  //message.getFileMetas() will get you remote URLs for the uploaded attachment
 *             }
 *         });
 * </code>
 *
 * <p>To receive messages, refer to {@link com.applozic.mobicomkit.listners.ApplozicUIListener}.</p>
 */
public class MessageBuilder {

    private Message message;
    private Context context;

    public MessageBuilder(Context context) {
        this.message = new Message();
        this.context = context;
    }

    /**
     * Set the user-id of the receiver.
     */
    public MessageBuilder setTo(String to) {
        message.setTo(to);
        return this;
    }

    /**
     * Set the message text.
     */
    public MessageBuilder setMessage(String message) {
        this.message.setMessage(message);
        return this;
    }

    /**
     * Set the message {@link com.applozic.mobicomkit.api.conversation.Message.MessageType type}.
     */
    public MessageBuilder setType(Short type) {
        message.setType(type);
        return this;
    }

    /**
     * Set the local path to the message attachment file.
     *
     * <p>Images, video, audio, documents are supported.</p>
     */
    public MessageBuilder setFilePath(String filePath) {
        List<String> pathList = new ArrayList<>();
        pathList.add(filePath);
        message.setFilePaths(pathList);
        return this;
    }

    /**
     * Set the message {@link com.applozic.mobicomkit.api.conversation.Message.ContentType content type}.
     */
    public MessageBuilder setContentType(short contentType) {
        message.setContentType(contentType);
        return this;
    }

    /**
     * Set the {@link Channel#getKey() group-id} (also called key/channel key) of the {@link com.applozic.mobicommons.people.channel.Channel} to send this message to.
     *
     * This is generated by Applozic.
     */
    public MessageBuilder setGroupId(Integer groupId) {
        message.setGroupId(groupId);
        return this;
    }

    /**
     * Set the {@link com.applozic.mobicommons.people.channel.Channel#setClientGroupId(String) client-group-id} of the {@link com.applozic.mobicommons.people.channel.Channel} to send this message to.
     *
     * This is set by you when creating a {@link Channel}.
     */
    public MessageBuilder setClientGroupId(String clientGroupId) {
        message.setClientGroupId(clientGroupId);
        return this;
    }

    /**
     * Set any custom key-value data for the message.
     */
    public MessageBuilder setMetadata(Map<String, String> metadata) {
        message.setMetadata(metadata);
        return this;
    }

    /**
     * Alternatively you can also directly set a {@link Message} object to send.
     */
    public MessageBuilder setMessageObject(Message message) {
        this.message = message;
        return this;
    }

    /**
     * Returns the message object to be sent.
     */
    public Message getMessageObject() {
        return message;
    }

    /**
     * Sends the created message.
     */
    public void send() {
        new MobiComConversationService(context).sendMessageWithHandler(message, null);
    }

    /**
     * Sends the created message with attachment progress and success callbacks.
     */
    public void send(MediaUploadProgressHandler handler) {
        new MobiComConversationService(context).sendMessageWithHandler(message, handler);
    }
}
