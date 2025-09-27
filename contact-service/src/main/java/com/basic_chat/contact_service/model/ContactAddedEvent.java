package com.basic_chat.contact_service.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContactAddedEvent {
    private Long fromUserId;
    // private String fromUsername;
    private Long toUserId;

}
