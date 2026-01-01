package com.basic_chat.chat_service.handler;

import com.basic_chat.chat_service.context.SessionContext;
import com.basic_chat.proto.MessagesProto;

public interface WsMessageHandler {
    boolean supports(MessagesProto.WsMessage message);
    void handle(SessionContext context, MessagesProto.WsMessage message) throws Exception;
}
