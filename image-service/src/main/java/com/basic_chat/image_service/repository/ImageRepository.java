package com.basic_chat.image_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.basic_chat.image_service.model.Image;

public interface ImageRepository extends JpaRepository<Image, Long> {
    java.util.Optional<Image> findByOriginalFileName(String originalFileName);
}
