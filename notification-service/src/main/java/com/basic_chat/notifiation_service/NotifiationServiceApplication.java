package com.basic_chat.notifiation_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class NotifiationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(NotifiationServiceApplication.class, args);
	}

}
