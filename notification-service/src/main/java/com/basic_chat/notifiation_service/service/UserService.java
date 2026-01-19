package com.basic_chat.notifiation_service.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.basic_chat.notifiation_service.model.User;
import com.basic_chat.notifiation_service.model.UserCreateEvent;
import com.basic_chat.notifiation_service.repository.UserRepository;

@Service
public class UserService {
   private final UserRepository userRepository;

   public UserService(UserRepository userRepository){
        this.userRepository = userRepository;
   }

   @Transactional
   public void create(UserCreateEvent e){
        Optional<User> u = userRepository.findById(e.getUser_id());
        if(u.isPresent()){
            return;
        }
        try{
            User user = new User();
            user.setId(e.getUser_id());
            userRepository.save(user);
        }catch(Exception ex){
            System.err.println("No se pudo crear el usuario");
            throw ex;
        }    
   }
}
