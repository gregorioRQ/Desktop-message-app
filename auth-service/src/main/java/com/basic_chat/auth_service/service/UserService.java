package com.basic_chat.auth_service.service;

import java.util.Date;
import java.util.Optional;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import com.basic_chat.auth_service.jwt.JwtService;
import com.basic_chat.auth_service.model.AuthResponse;
import com.basic_chat.auth_service.model.Prekey;
import com.basic_chat.auth_service.model.PrekeyBundleDTO;
import com.basic_chat.auth_service.model.Prekeys;
import com.basic_chat.auth_service.model.RequestRegister;
import com.basic_chat.auth_service.model.SignedPreKey;
import com.basic_chat.auth_service.model.User;
import com.basic_chat.auth_service.repository.PreKeyRepository;
import com.basic_chat.auth_service.repository.SignedPreKeyRepository;
import com.basic_chat.auth_service.repository.UserRepository;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PreKeyRepository preKeyRepo;
    private final SignedPreKeyRepository signedPreKeyRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public UserService(UserRepository userRepository, PreKeyRepository preKeyRepository,
            SignedPreKeyRepository signedPreKeyRepository, AuthenticationManager authenticationManager,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.preKeyRepo = preKeyRepository;
        this.signedPreKeyRepository = signedPreKeyRepository;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public void registrarUsuario(RequestRegister request) {
        User user = new User();
        user.setUsername(request.getUsername());
        user.setRegistrationId(request.getRegistrationId());
        user.setDeviceId(request.getDeviceId());
        user.setPublicIdentityKey(request.getIdentityKey());

        Prekey prekey = new Prekey();
        prekey.setRegistrationId(request.getRegistrationId());
        prekey.setPrekey(request.getPreKeyPublic());
        prekey.setPrekeyId(request.getPreKeyId());
        prekey.setTimestamp(new Date().toString());
        prekey.setUsed(false);

        SignedPreKey signedPreKey = new SignedPreKey();
        signedPreKey.setSignedPrekeyPublic(request.getSignedPreKeyPublic());
        signedPreKey.setSignedPreKeySignature(request.getSignedPreKeySignature());
        signedPreKey.setSignedPrekeyId(request.getSignedPreKeyId());
        signedPreKey.setRegistrationId(request.getRegistrationId());
        signedPreKey.setActive(false);
        signedPreKey.setTimestamp(new Date().toString());

        userRepository.save(user);
        preKeyRepo.save(prekey);
        signedPreKeyRepository.save(signedPreKey);

    }

    public AuthResponse loguearUsuario(User us) {

        if (us.getUsername().isEmpty()) {
            throw new IllegalArgumentException("Campo vacio");
        }
        User usFind = userRepository.findByUsername(us.getUsername());
        if (usFind == null) {
            throw new EntityNotFoundException("Usuario no encontrado");
        }

        if (us.getPassword().isEmpty() || !us.getPassword().equals(usFind.getPassword())
                || us.getUsername().isEmpty() || !us.getUsername().equals(usFind.getUsername())) {
            throw new IllegalArgumentException("Credenciales inválidas");
        }
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(us.getUsername(), us.getPassword()));
        UserDetails userDetails = userRepository.findByUsername(us.getUsername());
        String token = jwtService.getToken(userDetails);
        return AuthResponse.builder()
                .token(token)
                .build();
    }

    public PrekeyBundleDTO createPrekeyBundle(String user) {
        User u = userRepository.findByUsername(user);
        Prekey p = preKeyRepo.findByRegistrationId(u.getRegistrationId());
        SignedPreKey s = signedPreKeyRepository.findByRegistrationId(u.getRegistrationId());

        PrekeyBundleDTO dto = new PrekeyBundleDTO();
        dto.setRegistrationId(u.getRegistrationId());
        dto.setDeviceId(u.getDeviceId());
        dto.setIdentityKey(u.getPublicIdentityKey());
        dto.setPreKeyPublic(p.getPrekey());
        dto.setPreKeyId(p.getPrekeyId());
        dto.setSignedPreKeyPublic(s.getSignedPrekeyPublic());
        dto.setSignedPreKeySignature(s.getSignedPreKeySignature());
        dto.setSignedPreKeyId(s.getSignedPrekeyId());
        return dto;

    }

    public User obtenerUsuarioPorId(Long id) {
        User usFind = userRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        return usFind;
    }

    public User findUserByUsername(String username) {
        User usFind = userRepository.findByUsername(username);
        return usFind;
    }
}
