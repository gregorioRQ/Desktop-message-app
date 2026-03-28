package com.basic_chat.connection_service.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {
    private String type;
    private String messageId;
    private String sender;
    private String recipient;
    private String recipientUserId;
    private byte[] data;

    public static NotificationEvent createNewMessageEvent(String sender, String recipient, String recipientUserId, String messageId, byte[] data) {
        NotificationEvent event = new NotificationEvent();
        event.setType("NEW_MESSAGE");
        event.setMessageId(messageId);
        event.setSender(sender);
        event.setRecipient(recipient);
        event.setRecipientUserId(recipientUserId);
        event.setData(data);
        return event;
    }

    /**
     * Crea un evento de notificación para nuevo mensaje de imagen.
     * 
     * Este método se usa cuando el receptor esta offline y se le notifica
     * que tiene una imagen pendiente de descargar.
     * 
     * @param sender Username del remitente
     * @param receiverUserId UserId del destinatario
     * @param imageData Datos binarios del ImageMessage
     * @return NotificationEvent con tipo NEW_IMAGE_MESSAGE
     */
    public static NotificationEvent createNewImageMessageEvent(String sender, String receiverUserId, byte[] imageData) {
        NotificationEvent event = new NotificationEvent();
        event.setType("NEW_IMAGE_MESSAGE");
        event.setSender(sender);
        event.setRecipientUserId(receiverUserId);
        event.setData(imageData);
        return event;
    }
}
