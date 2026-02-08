package com.basic_chat.profile_service.service;

import com.basic_chat.proto.LoginProto.LoginRequest;
import com.basic_chat.proto.LoginProto.LoginResponse;
import com.basic_chat.proto.LogoutProto.LogoutRequest;
import com.basic_chat.proto.LogoutProto.LogoutResponse;
import com.basic_chat.proto.RegisterProto.RegisterRequest;
import com.basic_chat.proto.RegisterProto.RegisterResponse;

/**
 * Interface para el servicio de registro
 * Principio SOLID: Dependency Inversion - Los controladores dependen de esta abstracción
 */
public interface ProfileService {
    RegisterResponse registerUser(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    LogoutResponse logout(LogoutRequest request);
}
