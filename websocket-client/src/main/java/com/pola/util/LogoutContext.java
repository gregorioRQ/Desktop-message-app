package com.pola.util;

import com.pola.repository.TokenRepository;
import com.pola.service.ContactService;
import com.pola.service.MessageService;
import com.pola.service.WebSocketService;
import com.pola.view.ViewManager;

/**
 * Objeto de Parámetros para el LogoutHandler.
 * Agrupa todas las dependencias necesarias para el proceso de cierre de sesión,
 * solucionando el code smell "Long Parameter List".
 */
public class LogoutContext {
    private final WebSocketService webSocketService;
    private final MessageService messageService;
    private final ContactService contactService;
    private final ViewManager viewManager;
    private final TokenRepository tokenRepository;

    public LogoutContext(WebSocketService webSocketService, MessageService messageService, ContactService contactService, ViewManager viewManager, TokenRepository tokenRepository) {
        this.webSocketService = webSocketService;
        this.messageService = messageService;
        this.contactService = contactService;
        this.viewManager = viewManager;
        this.tokenRepository = tokenRepository;
    }

    public WebSocketService getWebSocketService() {
        return webSocketService;
    }

    public MessageService getMessageService() {
        return messageService;
    }

    public ContactService getContactService() {
        return contactService;
    }

    public ViewManager getViewManager() {
        return viewManager;
    }

    public TokenRepository getTokenRepository() {
        return tokenRepository;
    }
}