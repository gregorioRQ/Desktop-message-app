package com.basic_chat.chat_service.service;

import java.util.List;

import org.springframework.stereotype.Component;

import com.basic_chat.chat_service.context.SessionContext;
import com.basic_chat.chat_service.handler.WsMessageHandler;
import com.basic_chat.proto.MessagesProto;

@Component
public class WsMessageDispatcher {
    private final List<WsMessageHandler> handlers;

    public WsMessageDispatcher(List<WsMessageHandler> handlers){
        this.handlers = handlers;
    }

    public void dispatch(SessionContext context, MessagesProto.WsMessage message){
        for(WsMessageHandler handler : handlers){
            if(handler.supports(message)){
                try{
                    handler.handle(context, message);
                    return;
                }catch(Exception ex){
                    ex.printStackTrace();
                }
                
            }
        }
        throw new IllegalArgumentException("Tipo de mensaje no soportado");
    }
}
