package com.basic_chat.notifiation_service.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.basic_chat.notifiation_service.model.User;
import com.basic_chat.notifiation_service.model.UserCreateEvent;
import com.basic_chat.notifiation_service.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class UserService {
   private final UserRepository userRepository;

   public UserService(UserRepository userRepository){
        this.userRepository = userRepository;
   }

   @Transactional
   public void create(UserCreateEvent e){
        if(e == null){
            log.warn("Evento recibido fue nulo no se creara el contacto");
            return;
        }
        try{
            Optional<User> u = userRepository.findById(e.getUser_id());
            if(u.isPresent()){
                log.debug("El usuario: {} ya existe", e.getUser_id());
                return;
            }
            log.debug("Registrando nuevo usuario en la db, userId: {}", e.getUser_id());
            User user = new User();
            user.setId(e.getUser_id());
            userRepository.save(user);
            log.info("Nuevo usuario registrado con exito");
        }catch(Exception ex){
            log.error("No se pudo registrar el usuario error: {}", ex);
          
        }    
   }
}
