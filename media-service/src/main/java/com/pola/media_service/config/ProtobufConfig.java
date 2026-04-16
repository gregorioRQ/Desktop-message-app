package com.pola.media_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.protobuf.ProtobufHttpMessageConverter;

@Configuration
public class ProtobufConfig {
    /**
     * Registra el conversor de Protobuf para que Spring lo use automáticamente
     * El conversor toma los bytes crudos y los serializa a un objto y viseversa
     */
    @Bean
    ProtobufHttpMessageConverter protobufHttpMessageConverter() {
        return new ProtobufHttpMessageConverter();
    }
}