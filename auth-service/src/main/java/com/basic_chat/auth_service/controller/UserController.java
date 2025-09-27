package com.basic_chat.auth_service.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.basic_chat.auth_service.model.PreKeyRequest;
import com.basic_chat.auth_service.model.Prekey;
import com.basic_chat.auth_service.model.PrekeyBundleDTO;
import com.basic_chat.auth_service.model.RequestRegister;
import com.basic_chat.auth_service.model.SignedPreKey;
import com.basic_chat.auth_service.model.SignedPreKeyRequest;
import com.basic_chat.auth_service.model.User;
import com.basic_chat.auth_service.service.SignedPreKeyService;
import com.basic_chat.auth_service.service.PrekeyService;
import com.basic_chat.auth_service.service.UserService;

@RestController
@RequestMapping("/auth")
public class UserController {
    private final UserService userService;
    private final SignedPreKeyService signedPreKeyServicekey;
    private final PrekeyService preKeyService;

    public UserController(UserService userService, SignedPreKeyService signedPreKeyService,
            PrekeyService prekeyService) {
        this.userService = userService;
        this.signedPreKeyServicekey = signedPreKeyService;
        this.preKeyService = prekeyService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> registrarUsuario(@RequestBody RequestRegister request) {
        if (request == null) {
            return ResponseEntity.badRequest().body("Usuario no puede ser nulo");
        }

        try {
            userService.registrarUsuario(request);
            return ResponseEntity.ok("hecho");
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            return ResponseEntity.status(500).body("Error al registrar el usuario");
        }
    }

    @PostMapping("/login")
    public void loguearUsuario(@RequestBody User us) {
        try {
            userService.loguearUsuario(us);
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }

    }

    @GetMapping("/{id}")
    public boolean userExist(@PathVariable Long id) {
        try {
            userService.obtenerUsuarioPorId(id);
            return true;
        } catch (Exception ex) {
            System.out.println("El usuario con el ID " + id + " no existe");
            return false;
        }
    }

    /*
     * Este metodo recibe la SignedPreKeyPublica del usuario y
     * la guarda en la base de datos.
     */
    @PostMapping("/users/key/signedprekey")
    public ResponseEntity<String> uploadSignedPreKey(@RequestBody SignedPreKeyRequest request) {
        signedPreKeyServicekey.saveSignedKey(request);
        return ResponseEntity.ok("Datos recibidos");
    }

    /*
     * Sirve para que otros clientes obtengan las claves publicas de un usuario
     * al iniciar un chat.
     */

    @GetMapping("/prekeybundle/{username}")
    public ResponseEntity<PrekeyBundleDTO> getPrekeyBundle(@PathVariable("username") String user) {
        if (user == null || user.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            PrekeyBundleDTO bundle = userService.createPrekeyBundle(user);
            if (bundle == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(bundle);
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /*
     * Es para que los clientes suban un lote de sus prekeys.
     */
    @PostMapping("/users/key/prekeys")
    public ResponseEntity<String> uploadPrekeys(@RequestBody PreKeyRequest request) {
        preKeyService.savePreKey(request);
        return new ResponseEntity<String>("Data recibida", HttpStatus.OK);
    };

    /*
     * Para que los clientes soliciten Prekeys de un destinatario
     * (el servidor las tiene que consumir para evitar que se reusen).
     */
    @GetMapping("/users/{userId}/prekeys/batch")
    public void getPrekeys() {
    };
}
