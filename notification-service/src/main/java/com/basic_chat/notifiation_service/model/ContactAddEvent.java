package com.basic_chat.notifiation_service.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContactAddEvent {
    private String from;
    // private String fromUsername;
    private String to;
}
