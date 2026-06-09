package com.crm.demo.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.crm.demo.model.Notification;
import com.crm.demo.model.User;
import com.crm.demo.repository.UserRepository;
import com.crm.demo.service.NotificationService;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired private NotificationService notificationService;
    @Autowired private UserRepository userRepository;

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username);
    }

    @GetMapping
    public ResponseEntity<?> list() {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        List<Map<String, Object>> items = notificationService.getRecentForUser(user.getId())
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "notifications", items,
                "unreadCount", notificationService.getUnreadCount(user.getId())
        ));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<?> unreadCount() {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        return ResponseEntity.ok(Map.of("unreadCount", notificationService.getUnreadCount(user.getId())));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<?> markRead(@PathVariable Long id) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        boolean ok = notificationService.markAsRead(id, user.getId());
        if (!ok) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("unreadCount", notificationService.getUnreadCount(user.getId())));
    }

    @PostMapping("/read-all")
    public ResponseEntity<?> markAllRead() {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        notificationService.markAllAsRead(user.getId());
        return ResponseEntity.ok(Map.of("unreadCount", 0));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteOne(@PathVariable Long id) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        boolean ok = notificationService.deleteNotification(id, user.getId());
        if (!ok) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of(
                "unreadCount", notificationService.getUnreadCount(user.getId()),
                "deleted", true
        ));
    }

    @DeleteMapping("/clear-all")
    public ResponseEntity<?> deleteAll() {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        notificationService.deleteAllForUser(user.getId());
        return ResponseEntity.ok(Map.of("unreadCount", 0, "deleted", true));
    }

    private Map<String, Object> toDto(Notification n) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", n.getId());
        m.put("title", n.getTitle());
        m.put("message", n.getMessage());
        m.put("type", n.getType());
        m.put("link", n.getLink());
        m.put("read", n.isRead());
        m.put("createdAt", n.getCreatedAt() != null ? n.getCreatedAt().toString() : null);
        return m;
    }
}
