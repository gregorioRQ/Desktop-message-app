package com.basic_chat.profile_service.converter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;

import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

import com.google.protobuf.Message;

/**
 * Conversor HTTP para mensajes Protobuf
 * Permite que Spring Boot lea y escriba automáticamente mensajes Protobuf
 */
public class ProtobufHttpMessageConverter extends AbstractHttpMessageConverter<Message>{

    private static final MediaType PROTOBUF  = new MediaType("application", "x-protobuf");

    public ProtobufHttpMessageConverter(){
        super(PROTOBUF);
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return Message.class.isAssignableFrom(clazz);
    }

    @Override
    protected Message readInternal(Class<? extends Message> clazz, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        try {
            Method parseFromMethod = clazz.getMethod("parseFrom", InputStream.class);
            return (Message) parseFromMethod.invoke(null, inputMessage.getBody());
        } catch (Exception e) {
            throw new HttpMessageNotReadableException(
                    "Error al deserializar mensaje Protobuf: " + e.getMessage(), e, inputMessage);
        }
    }

    @Override
    protected void writeInternal(Message message, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        try (OutputStream out = outputMessage.getBody()) {
            message.writeTo(out);
        }
    }

}
