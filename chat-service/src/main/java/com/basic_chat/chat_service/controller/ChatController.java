package com.basic_chat.chat_service.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import com.basic_chat.proto.MensajeProto;
import com.basic_chat.proto.PaqueteDatos;
import com.basic_chat.proto.PaqueteDatos.Tipo;

@Controller
public class ChatController {
private final SimpMessageSendingOperations messagingTemplate;

    // Spring inyecta SimpMessagingTemplate (que ahora existe gracias a WebSocketConfig)
    public ChatController(SimpMessageSendingOperations messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Recibe mensajes del cliente. 
     * @MessageMapping("/chat.sendMessage") mapea a /app/chat.sendMessage
     */
    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload PaqueteDatos chatMessage) {
        System.out.println("Mensaje recibido de: " + chatMessage.getUsuarioId());
        System.out.println("Contenido: " + chatMessage.getContenido());
        
        // Ejemplo de uso de las enumeraciones generadas:
        if (chatMessage.getTipo() == Tipo.CHAT) {
            // Reenvía el mensaje a todos los suscriptores del topic /topic/public
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
        } else if (chatMessage.getTipo() == Tipo.LOGIN) {
            System.out.println("Usuario " + chatMessage.getUsuarioId() + " ha iniciado sesión.");
        }
    }


    // Ejemplo de creación de un nuevo PaqueteDatos para enviar
    public PaqueteDatos createAlert(String userId, String message) {
        return PaqueteDatos.newBuilder()
                .setUsuarioId(userId)
                .setContenido(message)
                .setTimestamp(System.currentTimeMillis())
                .setTipo(Tipo.ALERTA)
                .build();
    }
}
