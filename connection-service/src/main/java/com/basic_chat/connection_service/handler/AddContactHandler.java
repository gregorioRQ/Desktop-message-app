package com.basic_chat.connection_service.handler;

import org.springframework.stereotype.Component;

import com.basic_chat.connection_service.service.RabbitMQProducerService;
import com.basic_chat.proto.MessagesProto;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class AddContactHandler implements ConnectionWsMessageHandler {

    private final RabbitMQProducerService rabbitMQProducerService;

    public AddContactHandler(RabbitMQProducerService rabbitMQProducerService) {
        this.rabbitMQProducerService = rabbitMQProducerService;
    }

    @Override
    public boolean supports(MessagesProto.WsMessage message) {
        return message.hasAddContactRequest();
    }

    @Override
    public void handle(String sender, MessagesProto.WsMessage message) {
        MessagesProto.AddContactRequest request = message.getAddContactRequest();
        String contactUsername = request.getContactUsername();

        log.info("Procesando solicitud de agregar contacto de {} a {}", sender, contactUsername);

        String eventJson = String.format(
            "{\"sender\": \"%s\", \"contact_username\": \"%s\"}",
            sender,
            contactUsername
        );

        rabbitMQProducerService.sendContactEventJson(eventJson);
        log.info("Evento de contacto enviado a notification-service: {}", eventJson);
    }
}