package com.basic_chat.chat_service.security;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * @deprecated Esta clase ya no es necesaria.
 * La validación de tokens JWT es responsabilidad del API Gateway.
 * Se mantiene solo para compatibilidad hacia atrás.
 */
@Deprecated(forRemoval = true)
@Component
@Slf4j
public class JwtValidator {
    // Esta clase ya no realiza operaciones de validación de tokens
    // El API Gateway es responsable de ello
}

