package com.pola.event.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pola.event.SseEvent;
import com.pola.event.SseEventHandler;
import com.pola.event.SseEventType;
import com.pola.service.ContactService;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler para eventos de lista de contactos online.
 * Procesa eventos SSE de tipo CONTACT_LIST que se reciben cuando
 * el usuario se conecta al notification-service.
 * 
 * El formato esperado del evento es:
 * {
 *   "contacts": [
 *     {"userId": "abc", "username": "john"},
 *     {"userId": "def", "username": "jane"}
 *   ]
 * }
 * 
 * Este handler realiza las siguientes acciones:
 * 1. Marca todos los contactos existentes como offline (reset)
 * 2. Extrae la lista de contactos online del JSON
 * 3. Marca solo esos contactos como online
 */
public class ContactListEventHandler implements SseEventHandler {

    private final ContactService contactService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ContactListEventHandler(ContactService contactService) {
        this.contactService = contactService;
    }

    @Override
    public boolean canHandle(SseEventType type) {
        return type == SseEventType.CONTACT_LIST;
    }

    @Override
    public void handle(SseEvent event) {
        if (event == null) {
            System.err.println("[ContactListEventHandler] Evento nulo recibido");
            return;
        }

        String jsonData = event.getData();
        System.out.println("[ContactListEventHandler] Recibida lista de contactos online");

        try {
            // Parsear el JSON
            JsonNode rootNode = objectMapper.readTree(jsonData);
            JsonNode contactsArray = rootNode.get("contacts");
            
            if (contactsArray == null || !contactsArray.isArray()) {
                System.err.println("[ContactListEventHandler] Formato JSON inválido: falta array 'contacts'");
                return;
            }

            // Paso 1: Resetear todos los contactos a offline
            System.out.println("[ContactListEventHandler] Resetando todos los contactos a offline");
            contactService.resetAllContactsOffline();

            // Paso 2: Extraer usernames de contactos online
            List<String> onlineUsernames = new ArrayList<>();
            for (JsonNode contactNode : contactsArray) {
                String username = contactNode.get("username").asText();
                if (username != null && !username.isEmpty()) {
                    onlineUsernames.add(username);
                }
            }

            // Paso 3: Marcar los contactos online
            if (!onlineUsernames.isEmpty()) {
                System.out.println("[ContactListEventHandler] Marcando " + onlineUsernames.size() + 
                        " contactos como online: " + onlineUsernames);
                contactService.setContactsOnline(onlineUsernames);
            } else {
                System.out.println("[ContactListEventHandler] No hay contactos online en este momento");
            }

            System.out.println("[ContactListEventHandler] Lista de contactos online procesada exitosamente");

        } catch (Exception e) {
            System.err.println("[ContactListEventHandler] Error procesando lista de contactos: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
