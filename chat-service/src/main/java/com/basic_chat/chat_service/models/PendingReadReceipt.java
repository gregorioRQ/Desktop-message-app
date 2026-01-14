package com.basic_chat.chat_service.models;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pending_read_receipts")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PendingReadReceipt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String messageId;
    private String receiptRecipient; // The user to be notified (original sender)
    private String reader; // The user who read the message
}
