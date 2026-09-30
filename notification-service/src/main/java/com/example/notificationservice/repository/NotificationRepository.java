package com.example.notificationservice.repository;

import com.example.notificationservice.model.Notification;
import com.example.notificationservice.model.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByIdDesc(Long userId);

    List<Notification> findByStatusInOrderByIdAsc(Collection<NotificationStatus> statuses);

    List<Notification> findByStatusInAndAttemptsLessThanOrderByIdAsc(Collection<NotificationStatus> statuses, int attempts);
}
