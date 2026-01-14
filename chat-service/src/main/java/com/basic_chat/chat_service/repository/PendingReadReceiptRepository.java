package com.basic_chat.chat_service.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.basic_chat.chat_service.models.PendingReadReceipt;

public interface PendingReadReceiptRepository extends JpaRepository<PendingReadReceipt, Long> {
    List<PendingReadReceipt> findByReceiptRecipient(String receiptRecipient);
}

