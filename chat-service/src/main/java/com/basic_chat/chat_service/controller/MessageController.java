package com.basic_chat.chat_service.controller;

import java.util.List;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.basic_chat.chat_service.client.RestClient;
import com.basic_chat.chat_service.handler.ChatWebSocketHandler;
import com.basic_chat.chat_service.models.Message;
import com.basic_chat.chat_service.models.MessageDTO;
import com.basic_chat.chat_service.models.MessageSeenEvent;
import com.basic_chat.chat_service.models.MessageSeenRequest;
import com.basic_chat.chat_service.models.MessageSentEvent;
import com.basic_chat.chat_service.service.MessageService;
import com.basic_chat.proto.PaqueteDatos;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/message")
public class MessageController {
    private final MessageService messageService;
    private final RabbitTemplate rabbitTemplate;
    private final RestClient restClient;
    private final ChatWebSocketHandler webSocketHandler;

    public MessageController(MessageService messageService, RabbitTemplate rabbitTemplate, RestClient restClient,
            ChatWebSocketHandler webSocketHandler) {
        this.messageService = messageService;
        this.rabbitTemplate = rabbitTemplate;
        this.restClient = restClient;
        this.webSocketHandler = webSocketHandler;
    }
    @PostMapping
    public void sendMessage(@RequestBody MessageDTO dto) {
        //messageService.saveMessage(dto);
        
        PaqueteDatos chatMessage = PaqueteDatos.newBuilder()
            .setUsuarioId(dto.getSender())
            .setContenido(dto.getContent())
            .setTimestamp(System.currentTimeMillis())
            .setTipo(PaqueteDatos.Tipo.CHAT)
            .build();
            
        //webSocketHandler.broadcast(chatMessage);
    }
    /* 
    @PostMapping("/send-with-image")
    public ResponseEntity<String> sendMessageWithImage(@RequestParam("msg") String msg,
            @RequestParam("file") MultipartFile file) {
        try {
            // Convertir el JSON del mensaje a un objeto Message
            ObjectMapper objectMapper = new ObjectMapper();
            MessageDTO message = objectMapper.readValue(msg, MessageDTO.class);

            // Enviar la imagen al servicio de imágenes con el senderId
            String imageUrl = restClient.sendImage(file, message.getReceiver());
            message.setImageUrl(imageUrl);

            // envia el evento de mensaje enviado a la cola
            rabbitTemplate.convertAndSend("message.sent",
                    new MessageSentEvent(message.getSender(), message.getReceiver()));

            // Guardar el mensaje en la base de datos
            messageService.saveMessage(message);
            return new ResponseEntity<>("Mensaje enviado con imagen", HttpStatus.OK);
        } catch (Exception e) {
            System.out.println("Error al enviar el mensaje: " + e.getMessage());
            return new ResponseEntity<>("Error al enviar el mensaje", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }*/

    /* 
    @GetMapping("/messages-unread/{username}")
    public ResponseEntity<List<MessageDTO>> getUnreadMessages(@PathVariable String username) {
        return new ResponseEntity<>(messageService.getUnreadMessages(username), HttpStatus.OK);
    }
    */
    /* 
    @GetMapping("/{userId}")
    public List<MessageDTO> getMessages(@PathVariable String userId) {
        List<ChatMessage> chatMessages = messageService.findByToUserId(userId);
        return chatMessages.stream()
                .map(chatMessage -> new MessageDTO(
                        chatMessage.getFromUserId(),
                        chatMessage.getToUserId(),
                        chatMessage.getContent()
                ))
                .toList();
    }
*/

/* 
    @PostMapping("/read")
    public ResponseEntity<String> markReadBatch(@RequestBody MessageSeenRequest req) {
        // validación básica: receiver no nulo y lista no vacía
        if (req.getReceiver() == null || req.getMessageIds() == null || req.getMessageIds().isEmpty()) {
            return ResponseEntity.badRequest().body("receiver y messageIds requeridos");
        }
        MessageSeenEvent event = new MessageSeenEvent();
        event.setReceiver(req.getReceiver());

        rabbitTemplate.convertAndSend("message.read", req);
        messageService.markRead(req.getMessageIds(), req.getReceiver());

        return ResponseEntity.ok("Marcados " + req.getMessageIds().size() + " mensajes como leídos");
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @DeleteMapping("/delete")
    public ResponseEntity<String> deleteMessage(@RequestParam Long messageId, @RequestParam String receiver) {
        messageService.deleteMessage(messageId, receiver);
        return ResponseEntity.ok("Mensaje eliminado correctamente");
    }

    @DeleteMapping("/delete-all")
    public ResponseEntity<String> deleteAllMessages(@RequestParam String username) {
        messageService.deleteAllMessages(username);
        return ResponseEntity.ok("Todos los mensajes eliminados correctamente");
    }

    @DeleteMapping("/delete-between")
    public ResponseEntity<String> deleteAllMessagesBetweenUsers(@RequestParam String sender,
            @RequestParam String receiver) {
        messageService.deleteAllMessagesBetweenUsers(sender, receiver);
        return ResponseEntity.ok("Todos los mensajes entre " + sender + " y " + receiver + " eliminados correctamente");
    }
*/
}