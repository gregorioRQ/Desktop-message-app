package com.basic_chat.connection_service.service;

import com.basic_chat.connection_service.models.NotificationEvent;
import com.basic_chat.connection_service.models.RoutedMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MessageRouterService {

    private final SessionRegistryService sessionRegistryService;
    private final RabbitMQProducerService rabbitMQProducerService;
    private final String instanceId;

    public MessageRouterService(
            SessionRegistryService sessionRegistryService,
            RabbitMQProducerService rabbitMQProducerService,
            @Value("${connection.service.instance.id}") String instanceId) {
        this.sessionRegistryService = sessionRegistryService;
        this.rabbitMQProducerService = rabbitMQProducerService;
        this.instanceId = instanceId;
    }

    /**
     * Enruta un mensaje al destinatario según su estado de conexión.
     * 
     * Este método determina la ruta del mensaje en tres escenarios:
     * 1. Usuario conectado en esta instancia → Envío directo por WebSocket
     * 2. Usuario conectado en otra instancia → Encolar en RabbitMQ (message.sent.{instanceId})
     * 3. Usuario offline → Encolar en cola offline (message.offline) + Notificar a notification-service
     * 
     * IMPORTANTE: Este método recibe el nombre de usuario (username), pero Redis almacena
     * la información de conexión usando el userId. Por eso se convierte username -> userId
     * antes de consultar la instancia de conexión.
     * 
     * @param sender Username del remitente
     * @param recipient Username del destinatario
     * @param messageData Datos binarios del mensaje (WsMessage protobuf)
     */
    public void routeMessage(String sender, String recipient, byte[] messageData) {
        // Convertir username a userId para consultar Redis correctamente
        // Redis guarda: user:name:{username} -> userId
        // Y también: user:{userId}:connectionInstance -> instanceId
        String recipientUserId = sessionRegistryService.getUserIdByUsername(recipient);

        if (recipientUserId == null) {
            // El usuario no existe o no está registrado en el sistema
            log.info("Usuario {} no encontrado en el sistema, enviando a cola offline", recipient);
            rabbitMQProducerService.sendToOfflineQueue(new RoutedMessage(sender, recipient, messageData, null));
            // No hay userId disponible, no se puede notificar
            return;
        }

        // Obtener la instancia donde está conectado el destinatario
        String recipientInstance = sessionRegistryService.getConnectionInstance(recipientUserId);

        if (recipientInstance == null) {
            // Usuario offline - no hay instancia registrada en Redis
            log.info("Destinatario {} no está conectado, encolando mensaje en cola offline", recipient);
            rabbitMQProducerService.sendToOfflineQueue(new RoutedMessage(sender, recipient, messageData, null));
            // Publicar evento de notificación para notification-service (con userId)
            publishNotificationEvent(sender, recipient, recipientUserId, null, messageData);
            return;
        }

        if (recipientInstance.equals(instanceId)) {
            // Usuario conectado en esta instancia - envío directo por WebSocket
            log.info("Destinatario {} está en esta instancia {}, enviando directamente", recipient, instanceId);
            sessionRegistryService.sendToUserByUsername(recipient, messageData);
        } else {
            // Usuario conectado en otra instancia - enviar a esa instancia via RabbitMQ
            log.info("Destinatario {} está en instancia {}, encolando mensaje", recipient, recipientInstance);
            rabbitMQProducerService.sendToQueue(recipientInstance, new RoutedMessage(sender, recipient, messageData, recipientInstance));
        }
    }

    /**
     * Publica un evento de notificación para notification-service.
     * 
     * Este método se llama cuando:
     * - El usuario destinatario está offline
     * - El usuario destinatario no existe en el sistema
     * 
     * El evento se publica en la cola message.notification para que
     * notification-service pueda enviar una notificación SSE al cliente.
     * 
     * @param sender Username del remitente
     * @param recipient Username del destinatario
     * @param recipientUserId UserId del destinatario
     * @param messageId ID del mensaje (puede ser null)
     * @param messageData Datos binarios del mensaje
     */
    private void publishNotificationEvent(String sender, String recipient, String recipientUserId, String messageId, byte[] messageData) {
        try {
            NotificationEvent notificationEvent = NotificationEvent.createNewMessageEvent(
                    sender, recipient, recipientUserId, messageId, messageData);
            rabbitMQProducerService.sendToNotificationQueue(notificationEvent);
            log.info("Evento de notificación publicado para {} (userId: {}, remitente: {})", 
                    recipient, recipientUserId, sender);
        } catch (Exception e) {
            log.error("Error al publicar evento de notificación para {}: {}", recipient, e.getMessage());
        }
    }

    /**
     * Envía un estado de entrega (DELIVERED/READ) al destinatario si está conectado.
     * 
     * Este método solo envía el estado si el destinatario está conectado en la instancia
     * actual. Si está en otra instancia, el estado se maneja en esa instancia.
     * 
     * @param recipient Username del destinatario
     * @param statusMessage Datos binarios del mensaje de estado (WsMessage con DeliveryStatus)
     */
    public void broadcastDeliveryStatus(String recipient, byte[] statusMessage) {
        // Convertir username a userId para consultar correctamente
        String recipientUserId = sessionRegistryService.getUserIdByUsername(recipient);
        
        if (recipientUserId == null) {
            log.info("Usuario {} no encontrado para estado de entrega", recipient);
            return;
        }
        
        String recipientInstance = sessionRegistryService.getConnectionInstance(recipientUserId);

        if (recipientInstance != null && recipientInstance.equals(instanceId)) {
            // Solo enviar si está en esta instancia
            sessionRegistryService.sendToUserByUsername(recipient, statusMessage);
        }
    }

    /**
     * Enruta la notificación de eliminación de mensaje según el estado del receptor.
     * 
     * Este método determina la ruta de la notificación en tres escenarios:
     * 1. Receptor conectado en esta instancia → Enviar notificación directamente por WebSocket
     * 2. Receptor conectado en otra instancia → Encolar a la cola de la instancia del receptor
     * 3. Receptor offline → Encolar a message.offline para que chat-service guarde la eliminación pendiente
     * 
     * @param sender Username del usuario que eliminó el mensaje
     * @param recipient Username del destinatario del mensaje
     * @param messageId ID del mensaje eliminado
     * @param messageData Datos binarios del mensaje de notificación (WsMessage con DeleteMessageRequest)
     */
    public void routeDeletionNotification(String sender, String recipient, String messageId, byte[] messageData) {
        log.info("Enrutando notificación de eliminación - mensaje: {}, de: {}, para: {}", messageId, sender, recipient);
        
        // Convertir username a userId para consultar Redis
        String recipientUserId = sessionRegistryService.getUserIdByUsername(recipient);
        
        if (recipientUserId == null) {
            // El usuario no existe o no está registrado en el sistema
            log.info("Usuario {} no encontrado en el sistema, guardando eliminación como pendiente", recipient);
            rabbitMQProducerService.sendToOfflineQueue(
                    new RoutedMessage(sender, recipient, messageData, null));
            return;
        }
        
        // Obtener la instancia donde está conectado el destinatario
        String recipientInstance = sessionRegistryService.getConnectionInstance(recipientUserId);
        
        if (recipientInstance == null) {
            // Receptor offline - guardar eliminación como pendiente
            log.info("Receptor {} está offline, encolando para guardado pendiente", recipient);
            rabbitMQProducerService.sendToOfflineQueue(
                    new RoutedMessage(sender, recipient, messageData, null));
            return;
        }
        
        if (recipientInstance.equals(instanceId)) {
            // Receptor online en esta instancia - enviar notificación directamente por WebSocket
            log.info("Receptor {} está en esta instancia {}, enviando notificación directamente", 
                    recipient, instanceId);
            sessionRegistryService.sendToUserByUsername(recipient, messageData);
        } else {
            // Receptor online en otra instancia - encolar a la cola de esa instancia
            log.info("Receptor {} está en instancia {}, encolando notificación", 
                    recipient, recipientInstance);
            rabbitMQProducerService.sendToQueue(recipientInstance, 
                    new RoutedMessage(sender, recipient, messageData, recipientInstance));
        }
    }

    /**
     * Enruta una solicitud de eliminación de historial de chat al chat-service.
     * 
     * Este método envía la solicitud de eliminación de historial a la cola correspondiente
     * de RabbitMQ para que chat-service procese la eliminación del historial.
     * 
     * @param sender Username del usuario que solicita la eliminación del historial
     * @param recipient Username del destinatario cuyo historial se eliminará
     * @param messageData Datos binarios de la solicitud (WsMessage con ClearHistoryRequest)
     */
    public void routeClearHistoryRequest(String sender, String recipient, byte[] messageData) {
        log.info("Enrutando solicitud de eliminación de historial entre {} y {}", sender, recipient);
        
        // Convertir username a userId para consultar Redis
        String recipientUserId = sessionRegistryService.getUserIdByUsername(recipient);
        
        if (recipientUserId == null) {
            // El usuario no existe - enviar a cola offline
            log.info("Usuario {} no encontrado, enviando solicitud a cola offline", recipient);
            rabbitMQProducerService.sendToOfflineQueue(
                    new RoutedMessage(sender, recipient, messageData, null));
            return;
        }
        
        String recipientInstance = sessionRegistryService.getConnectionInstance(recipientUserId);
        
        if (recipientInstance == null) {
            // Receptor offline
            log.info("Receptor {} está offline, encolando solicitud de historial", recipient);
            rabbitMQProducerService.sendToOfflineQueue(
                    new RoutedMessage(sender, recipient, messageData, null));
            return;
        }
        
        if (recipientInstance.equals(instanceId)) {
            // Receptor online en esta instancia
            log.info("Receptor {} está en esta instancia, enviando solicitud directamente", recipient);
            sessionRegistryService.sendToUserByUsername(recipient, messageData);
        } else {
            // Receptor online en otra instancia
            log.info("Receptor {} está en instancia {}, encolando solicitud", recipient, recipientInstance);
            rabbitMQProducerService.sendToQueue(recipientInstance, 
                    new RoutedMessage(sender, recipient, messageData, recipientInstance));
        }
    }

    /**
     * Enruta solicitudes de bloqueo/desbloqueo de contacto al chat-service para procesamiento.
     * 
     * Este método realiza dos acciones en paralelo:
     * 1. Envía la solicitud a chat-service (cola offline) para procesar la lógica de ContactBlock:
     *    - BlockContactRequest: registra el bloqueo en la tabla contact_blocks
     *    - UnblockContactRequest: elimina el registro de contact_blocks
     * 2. Envía la notificación directamente al destinatario según su estado de conexión:
     *    - Online en esta instancia: WebSocket directo
     *    - Online en otra instancia: RabbitMQ a la instancia correspondiente
     *    - Offline: No se envía notificación directa (se entrega cuando se conecte)
     * 
     * Este enfoque de "segundo plano" asegura que:
     * - La tabla contact_blocks siempre se actualice correctamente
     * - El destinatario reciba la notificación para actualizar su DB local
     * 
     * @param sender Username del usuario que envía la solicitud (quien bloquea/desbloquea)
     * @param recipient Username del destinatario (quien será bloqueado/desbloqueado)
     * @param messageData Datos binarios del mensaje (WsMessage con BlockContactRequest o UnblockContactRequest)
     */
    public void routeBlockUnblockToChatService(String sender, String recipient, byte[] messageData) {
        log.info("[BLOCK_UNBLOCK_ROUTE] Iniciando enrutamiento de solicitud block/unblock - De: {}, Para: {}", sender, recipient);
        
        // Convertir username a userId para consultar Redis
        String recipientUserId = sessionRegistryService.getUserIdByUsername(recipient);
        
        // Paso 1: Enviar siempre a chat-service para procesar ContactBlock
        // Esto asegura que el bloqueo/desbloqueo se registre permanentemente
        if (recipientUserId == null) {
            // Usuario no existe o no está registrado - enviar a cola offline
            log.info("[BLOCK_UNBLOCK_ROUTE] Usuario {} no encontrado, enviando a cola offline para procesamiento", recipient);
            rabbitMQProducerService.sendToOfflineQueue(new RoutedMessage(sender, recipient, messageData, null));
        } else {
            // Usuario existe - enviar a la cola de la instancia donde está conectado
            // Si está offline, recipientInstance será null y sendToOffline lo maneja
            String recipientInstance = sessionRegistryService.getConnectionInstance(recipientUserId);
            
            if (recipientInstance == null) {
                // Usuario offline - enviar a cola offline
                log.info("[BLOCK_UNBLOCK_ROUTE] Usuario {} está offline, enviando a cola offline para procesamiento", recipient);
                rabbitMQProducerService.sendToOfflineQueue(new RoutedMessage(sender, recipient, messageData, null));
            } else {
                // Usuario está en alguna instancia - enviar a esa instancia
                // chat-service de esa instancia procesará el ContactBlock
                log.info("[BLOCK_UNBLOCK_ROUTE] Enviando a chat-service instancia {} para procesamiento de ContactBlock", recipientInstance);
                rabbitMQProducerService.sendToQueue(recipientInstance, new RoutedMessage(sender, recipient, messageData, recipientInstance));
            }
        }
        
        // Paso 2: Enviar notificación directa al destinatario (si está online)
        // Esto permite que el cliente actualice su DB local
        if (recipientUserId != null) {
            String recipientInstance = sessionRegistryService.getConnectionInstance(recipientUserId);
            
            if (recipientInstance != null) {
                // Usuario online - enviar notificación directa
                if (recipientInstance.equals(instanceId)) {
                    // Online en esta instancia - WebSocket directo
                    log.info("[BLOCK_UNBLOCK_ROUTE] Destinatario {} online en esta instancia, enviando notificación directa", recipient);
                    sessionRegistryService.sendToUserByUsername(recipient, messageData);
                } else {
                    // Online en otra instancia - enviar via RabbitMQ
                    log.info("[BLOCK_UNBLOCK_ROUTE] Destinatario {} online en instancia {}, enviando notificación", recipient, recipientInstance);
                    rabbitMQProducerService.sendToQueue(recipientInstance, new RoutedMessage(sender, recipient, messageData, recipientInstance));
                }
            } else {
                // Usuario offline - no envía notificación directa
                // Se entregara cuando se conecte via los pending
                log.info("[BLOCK_UNBLOCK_ROUTE] Destinatario {} offline, notificación se entregara al conectarse", recipient);
            }
        } else {
            // Usuario no existe - igual procesar en chat-service para caso de registro tardio
            log.info("[BLOCK_UNBLOCK_ROUTE] Usuario {} no encontrado en sistema, enviando a chat-service para registro", recipient);
            rabbitMQProducerService.sendToOfflineQueue(new RoutedMessage(sender, recipient, messageData, null));
        }
        
        log.info("[BLOCK_UNBLOCK_ROUTE] Enrutamiento de block/unblock completado - De: {}, Para: {}", sender, recipient);
    }
}
