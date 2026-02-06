package com.basic_chat.profile_service.exception;

public class TokenNotFounException extends RuntimeException{

    public TokenNotFounException(String message) {
        super(message);
    }

    public TokenNotFounException(String message, Throwable cause) {
        super(message, cause);
    }
    
}
