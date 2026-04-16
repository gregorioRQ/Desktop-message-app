package com.pola.event.handler;

import com.pola.event.SseEvent;
import com.pola.event.SseEventHandler;
import com.pola.event.SseEventType;
import com.pola.service.ContactService;

/**
 * Handler para eventos de presencia (ONLINE/OFFLINE).
 * Procesa eventos SSE de tipo PRESENCE y actualiza el estado online
 * de los contactos en el ContactService.
 */
public class PresenceEventHandler implements SseEventHandler {

    private final ContactService contactService;

    public PresenceEventHandler(ContactService contactService) {
        this.contactService = contactService;
    }

    @Override
    public boolean canHandle(SseEventType type) {
        return type == SseEventType.PRESENCE;
    }

    @Override
    public void handle(SseEvent event) {
        if (event == null) {
            return;
        }

        String message = event.getData();

        try {
            String type = extractJsonField(message, "type");
            String userId = extractJsonField(message, "userId");
            String username = extractJsonField(message, "username");

            if (type == null || userId == null) {
                System.err.println("[PresenceEventHandler] Evento de presencia inválido: " + message);
                return;
            }

            boolean isOnline = "ONLINE".equals(type);

            System.out.println("[PresenceEventHandler] Evento recibido: " + type + " para usuario: " + username);

            // Actualizar el estado online del contacto
            if (contactService != null) {
                contactService.setContactOnlineByUsername(username, isOnline);
                System.out.println("[PresenceEventHandler] Contacto " + (isOnline ? "conectado" : "desconectado") + ": " + username);
            }

        } catch (Exception e) {
            System.err.println("[PresenceEventHandler] Error procesando evento: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Extrae un campo de un string JSON simple.
     * @param json El string JSON
     * @param field El nombre del campo a extraer
     * @return El valor del campo o null si no se encuentra
     */
    private String extractJsonField(String json, String field) {
        String searchKey = "\"" + field + "\":\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1) {
            return null;
        }
        int start = keyIndex + searchKey.length();
        int end = json.indexOf("\"", start);
        return end > start ? json.substring(start, end) : null;
    }
}
