package com.crm.demo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.crm.demo.model.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndReadStatusFalse(Long userId);

    List<Notification> findByUserIdAndReadStatusFalse(Long userId);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Notification n SET n.readStatus = true WHERE n.userId = :userId AND n.readStatus = false")
    int markAllReadForUser(@Param("userId") Long userId);

    void deleteByUserId(Long userId);
}
