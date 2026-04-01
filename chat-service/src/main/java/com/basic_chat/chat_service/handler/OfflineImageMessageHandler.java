package com.basic_chat.chat_service.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.basic_chat.chat_service.models.ImageMessage;
import com.basic_chat.chat_service.repository.ImageMessageRepository;
import com.basic_chat.proto.MessagesProto;

import lombok.extern.slf4j.Slf4j;

/**
 * Handler offline para mensajes de imagen (ImageMessage).
 * 
 * Cuando el destinatario está offline, guarda el ImageMessage en la tabla image_messages
 * con delivered=false para ser entregado cuando se conecte. El cliente receptor podra
 * usar la informacion del ImageMessage (especialmente la URL completa) para
 * mostrar la UI de descarga.
 * 
 * La imagen ya esta almacenada en media-service, aqui solo se guarda la referencia
 * para que el cliente receptor pueda ver que tiene una imagen pendiente.
 */
@Component
@Slf4j
public class OfflineImageMessageHandler implements OfflineMessageHandler {

    private final ImageMessageRepository imageMessageRepository;

    public OfflineImageMessageHandler(ImageMessageRepository imageMessageRepository) {
        this.imageMessageRepository = imageMessageRepository;
    }

    /**
     * Verifica si este handler puede procesar el mensaje.
     * 
     * @param message Mensaje protobuf recibido
     * @return true si el mensaje contiene un ImageMessage
     */
    @Override
    public boolean supports(MessagesProto.WsMessage message) {
        return message.hasImageMessage();
    }

    /**
     * Procesa el mensaje de imagen cuando el destinatario esta offline.
     * 
     * Guarda el ImageMessage en la tabla image_messages con delivered=false.
     * El cliente receptor recibira este mensaje cuando se conecte y podra usar
     * la URL completa para descargar la imagen.
     * 
     * @param message Mensaje protobuf que contiene ImageMessage
     * @param recipient Username del destinatario
     */
    @Override
    @Transactional
    public void handleOffline(MessagesProto.WsMessage message, String recipient) throws Exception {
        MessagesProto.ImageMessage protoImageMessage = message.getImageMessage();
        
        ImageMessage entity = new ImageMessage();
        entity.setMediaId(protoImageMessage.getMediaId());
        entity.setSenderId(protoImageMessage.getSenderId());
        entity.setReceiverId(protoImageMessage.getReceiverId());
        entity.setFullImageUrl(protoImageMessage.getFullImageUrl());
        entity.setOriginalWidth(protoImageMessage.getOriginalWidth());
        entity.setOriginalHeight(protoImageMessage.getOriginalHeight());
        entity.setFileSize(protoImageMessage.getFileSize());
        entity.setTimestamp(protoImageMessage.getTimestamp());
        entity.setDelivered(false);
        
        imageMessageRepository.save(entity);
        
        log.info("Mensaje de imagen offline guardado en tabla image_messages - de: {}, para: {}, mediaId: {}", 
            protoImageMessage.getSenderId(), protoImageMessage.getReceiverId(), protoImageMessage.getMediaId());
    }
}
