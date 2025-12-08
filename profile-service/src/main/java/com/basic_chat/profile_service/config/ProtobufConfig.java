package com.basic_chat.profile_service.config;

import java.util.List;
import com.basic_chat.profile_service.converter.ProtobufHttpMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


// Configuración de protobuf y seguridad
@Configuration
public class ProtobufConfig implements WebMvcConfigurer{
    /**
     * Registra el conversor de Protobuf para que Spring lo use automáticamente
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.add(new ProtobufHttpMessageConverter());
    }
    
    /**
     * Bean para encriptar contraseñas con BCrypt
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
