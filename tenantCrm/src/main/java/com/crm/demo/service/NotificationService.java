package com.crm.demo.service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.crm.demo.model.LeaveRequest;
import com.crm.demo.model.Meeting;
import com.crm.demo.model.Notification;
import com.crm.demo.model.Task;
import com.crm.demo.model.Team;
import com.crm.demo.model.User;
import com.crm.demo.repository.NotificationRepository;
import com.crm.demo.repository.TeamRepository;
import com.crm.demo.repository.UserRepository;
import java.util.HashMap;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private SimpMessagingTemplate messagingTemplate;

    public Notification notify(User user, String title, String message, String type, String link) {
        if (user == null || user.getId() == null) return null;
        try {
            Notification n = new Notification();
            n.setUserId(user.getId());
            n.setTitle(title);
            n.setMessage(message);
            n.setType(type);
            n.setLink(link);
            Notification saved = notificationRepository.save(n);

            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            messagingTemplate.convertAndSend("/topic/notifications/" + user.getId(), toDto(saved));
                        } catch (Exception e) {
                            log.error("Failed to send WebSocket notification to user {}: {}", user.getUsername(), e.getMessage());
                        }
                    }
                });
            } else {
                try {
                    messagingTemplate.convertAndSend("/topic/notifications/" + user.getId(), toDto(saved));
                } catch (Exception e) {
                    log.error("Failed to send WebSocket notification to user {}: {}", user.getUsername(), e.getMessage());
                }
            }

            return saved;
        } catch (Exception e) {
            log.error("Failed to save notification for user {}: {}", user.getUsername(), e.getMessage());
            return null;
        }
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

    public void notifyByUsername(String username, String title, String message, String type, String link) {
        if (username == null || username.isBlank()) return;
        User user = userRepository.findByUsername(username.trim());
        if (user != null) {
            notify(user, title, message, type, link);
        }
    }

    public void notifyUsersInTenantByRole(String tenant, String role, String title, String message, String type, String link) {
        if (tenant == null || tenant.isBlank() || role == null) return;
        userRepository.findByTenantSegment(tenant).stream()
                .filter(u -> role.equalsIgnoreCase(u.getRole()))
                .forEach(u -> notify(u, title, message, type, link));
    }

    public void notifyAllInTenant(String tenant, String title, String message, String type, String link) {
        if (tenant == null || tenant.isBlank()) return;
        userRepository.findByTenantSegment(tenant).stream()
                .filter(u -> u.getRole() != null && !"SUPER_ADMIN".equalsIgnoreCase(u.getRole()))
                .forEach(u -> notify(u, title, message, type, linkFor(u, link)));
    }

    public List<Notification> getRecentForUser(Long userId) {
        return notificationRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId);
    }

    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadStatusFalse(userId);
    }

    @Transactional
    public boolean markAsRead(Long notificationId, Long userId) {
        return notificationRepository.findByIdAndUserId(notificationId, userId)
                .map(n -> {
                    n.setRead(true);
                    notificationRepository.save(n);
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllReadForUser(userId);
    }

    @Transactional
    public boolean deleteNotification(Long notificationId, Long userId) {
        return notificationRepository.findByIdAndUserId(notificationId, userId)
                .map(n -> {
                    notificationRepository.delete(n);
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public void deleteAllForUser(Long userId) {
        notificationRepository.deleteByUserId(userId);
    }

    public void notifyMeetingParticipants(Meeting meeting) {
        if (meeting == null) return;
        String participants = meeting.getParticipants();
        if (participants == null || participants.isBlank()) return;

        String scheduledBy = meeting.getScheduledBy() != null ? meeting.getScheduledBy() : "";
        String dateStr = meeting.getMeetingDate() != null ? meeting.getMeetingDate().toString() : "TBD";

        Arrays.stream(participants.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty() && !name.equalsIgnoreCase(scheduledBy))
                .forEach(username -> {
                    User user = userRepository.findByUsername(username);
                    if (user == null) return;
                    notify(user,
                            "New Meeting Scheduled",
                            "You have been invited to \"" + meeting.getTitle() + "\" on " + dateStr + ".",
                            "MEETING",
                            meetingsLink(user));
                });

        // Broadcast to all users in the tenant
        User creator = userRepository.findByUsername(scheduledBy);
        if (creator != null) {
            String tenant = getTenantSegment(creator);
            sendLiveUpdateToTenant(tenant, "MEETING", "Meeting Scheduled", "Meeting: " + meeting.getTitle(), "/meetings");
        }
    }

    public void notifyTaskAssigned(User employee, String assignerName, String taskTitle) {
        if (employee == null) return;
        notify(employee,
                "New Task Assigned",
                assignerName + " assigned you: \"" + taskTitle + "\"",
                "TASK",
                tasksLink(employee));
        sendLiveUpdateToTenant(getTenantSegment(employee), "TASK", "New Task Assigned", assignerName + " assigned you a task", "/tasks");
    }

    public void notifyTaskSubmittedForReview(User employee, Task task) {
        notifyTaskStatusUpdated(employee, task, "done");
    }

    public void notifyTaskStatusUpdated(User employee, Task task, String status) {
        if (employee == null || task == null) return;
        String tenant = getTenantSegment(employee);
        Map<Long, User> managers = new LinkedHashMap<>();

        if (task.getCreatedBy() != null && !task.getCreatedBy().isBlank()) {
            User creator = userRepository.findByUsername(task.getCreatedBy().trim());
            addTaskStatusRecipient(managers, creator, employee, task);
        }

        List<Team> teams = teamRepository.findByMemberAndTenant(employee, tenant);
        for (Team team : teams) {
            User manager = team.getManager();
            addTaskStatusRecipient(managers, manager, employee, task);
        }

        String displayStatus = displayTaskStatus(status != null ? status : task.getStatus());
        boolean reviewReady = "done".equalsIgnoreCase(status != null ? status : task.getStatus());
        String title = reviewReady ? "Task Ready for Review" : "Task Status Updated";
        String message = reviewReady
                ? employee.getUsername() + " updated \"" + task.getTitle() + "\" to Done and submitted it for your review."
                : employee.getUsername() + " updated \"" + task.getTitle() + "\" to " + displayStatus + ".";

        for (User manager : managers.values()) {
            notify(manager,
                    title,
                    message,
                    "TASK",
                    tasksLink(manager));
        }
        sendLiveUpdateToTenant(tenant, "TASK", title, message, "/tasks");
    }

    private void addTaskStatusRecipient(Map<Long, User> recipients, User manager, User employee, Task task) {
        if (manager == null || manager.getId() == null) return;
        if (employee != null && manager.getId().equals(employee.getId())) return;
        if (task != null && task.getTenantSegment() != null && !task.getTenantSegment().isBlank()
                && !task.getTenantSegment().equals(getTenantSegment(manager))) {
            return;
        }
        recipients.putIfAbsent(manager.getId(), manager);
    }

    private String displayTaskStatus(String status) {
        if ("in-progress".equalsIgnoreCase(status)) return "In Progress";
        if ("done".equalsIgnoreCase(status)) return "Done";
        return "Pending";
    }

    public void notifyTaskVerified(User employee, String managerName, String taskTitle,
                                   String action, String reason) {
        if (employee == null) return;
        if ("approve".equalsIgnoreCase(action)) {
            notify(employee,
                    "Task Approved",
                    managerName + " approved your completed task \"" + taskTitle + "\".",
                    "TASK",
                    tasksLink(employee));
        } else if ("reject".equalsIgnoreCase(action)) {
            String msg = managerName + " returned \"" + taskTitle + "\" for rework.";
            if (reason != null && !reason.isBlank()) {
                msg += " Feedback: " + reason.trim();
            }
            notify(employee, "Task Returned", msg, "TASK", tasksLink(employee));
        } else if ("reopen".equalsIgnoreCase(action)) {
            notify(employee,
                    "Task Reopened",
                    managerName + " reopened \"" + taskTitle + "\". Please continue working on it.",
                    "TASK",
                    tasksLink(employee));
        }
        sendLiveUpdateToTenant(getTenantSegment(employee), "TASK", "Task Verified", managerName + " verified task: " + taskTitle, "/tasks");
    }

    public void notifyLeaveSubmitted(LeaveRequest leave) {
        if (leave == null || leave.getTenantSegment() == null) return;
        String employeeName = leave.getEmployeeName() != null ? leave.getEmployeeName() : "An employee";
        String period = leave.getFromDate() + " to " + leave.getToDate();
        notifyUsersInTenantByRole(
                leave.getTenantSegment(),
                "HR",
                "New Leave Request",
                employeeName + " submitted " + leave.getType() + " leave (" + period + ").",
                "LEAVE",
                "/hr/leaves");
        sendLiveUpdateToTenant(leave.getTenantSegment(), "LEAVE", "Leave Request Submitted", employeeName + " submitted leave request", "/leaves");
    }

    public void notifyLeaveReviewed(User employee, String status, String leaveType,
                                    LocalDate from, LocalDate to, String reviewer) {
        if (employee == null) return;
        String period = from + " to " + to;
        if ("Approved".equalsIgnoreCase(status)) {
            notify(employee,
                    "Leave Request Approved",
                    "Your " + leaveType + " leave (" + period + ") was approved by " + reviewer + ".",
                    "LEAVE",
                    leavesLink(employee));
        } else if ("Rejected".equalsIgnoreCase(status)) {
            notify(employee,
                    "Leave Request Rejected",
                    "Your " + leaveType + " leave (" + period + ") was rejected by " + reviewer + ".",
                    "LEAVE",
                    leavesLink(employee));
        }
        sendLiveUpdateToTenant(getTenantSegment(employee), "LEAVE", "Leave Request Reviewed", "Leave request was " + status.toLowerCase(), "/leaves");
    }

    public void notifyTeamAdded(User employee, String teamName) {
        if (employee == null) return;
        notify(employee,
                "Added to Team",
                "You have been added to team \"" + teamName + "\".",
                "TEAM",
                dashboardLink(employee));
        sendLiveUpdateToTenant(getTenantSegment(employee), "TEAM", "Team Updated", "Employee added to team " + teamName, "/teams");
    }

    public void notifyManagerAssigned(User manager, String teamName) {
        if (manager == null) return;
        notify(manager,
                "Team Assignment",
                "You have been assigned as manager of team \"" + teamName + "\".",
                "TEAM",
                teamLink(manager));
        sendLiveUpdateToTenant(getTenantSegment(manager), "TEAM", "Team Updated", "Manager assigned to team " + teamName, "/teams");
    }

    public void notifyPerformanceReview(User employee, String reviewer, String reviewMonth, int rating) {
        if (employee == null) return;
        notify(employee,
                "Performance Review Updated",
                reviewer + " submitted your performance review for " + reviewMonth
                        + " (Rating: " + rating + "/5).",
                "PERFORMANCE",
                performanceLink(employee));
        sendLiveUpdateToTenant(getTenantSegment(employee), "PERFORMANCE", "Performance Review Updated", reviewer + " updated performance review", "/performance");
    }

    public void notifyReportReceived(User recipient, String senderName, String reportTitle) {
        if (recipient == null) return;
        notify(recipient,
                "New Report Received",
                senderName + " sent you a report: \"" + reportTitle + "\".",
                "REPORT",
                reportsLink(recipient));
        sendLiveUpdateToTenant(getTenantSegment(recipient), "REPORT", "Report Received", "Report received from " + senderName, "/reports");
    }

    public void notifyHolidayAdded(String tenant, String holidayName, String date) {
        notifyAllInTenant(
                tenant,
                "Holiday Announced",
                holidayName + " on " + date + " has been added to the company calendar.",
                "HOLIDAY",
                "calendar");
    }

    public void sendLiveUpdate(User recipient, String type, String title, String message, String link) {
        if (recipient == null || recipient.getId() == null) return;
        try {
            notify(recipient, title, message, type, link);
        } catch (Exception e) {
            log.error("Failed to save and send live update to user {}: {}", recipient.getUsername(), e.getMessage());
        }
    }

    public void sendLiveUpdateToTenant(String tenant, String type, String title, String message, String link) {
        if (tenant == null || tenant.isBlank()) return;
        userRepository.findByTenantSegment(tenant).stream()
                .filter(u -> u.getRole() != null && !"SUPER_ADMIN".equalsIgnoreCase(u.getRole()))
                .forEach(u -> sendLiveUpdate(u, type, title, message, link));
    }

    public void sendLiveUpdateToTenantRole(String tenant, String role, String type, String title, String message, String link) {
        if (tenant == null || tenant.isBlank() || role == null) return;
        userRepository.findByTenantSegment(tenant).stream()
                .filter(u -> role.equalsIgnoreCase(u.getRole()))
                .forEach(u -> sendLiveUpdate(u, type, title, message, link));
    }

    public void notifyAttendanceUpdated(User employee, String action) {
        if (employee == null) return;
        String tenant = getTenantSegment(employee);
        
        // Notify all HR users in the same tenant
        sendLiveUpdateToTenantRole(tenant, "HR", "ATTENDANCE", 
            "Attendance Updated", 
            employee.getUsername() + " performed " + action, 
            "/hr/attendance");

        // Notify all Manager users in the same tenant
        sendLiveUpdateToTenantRole(tenant, "MANAGER", "ATTENDANCE", 
            "Attendance Updated", 
            employee.getUsername() + " performed " + action, 
            "/manager/attendance");

        // Notify the entire tenant for instant UI update
        sendLiveUpdateToTenant(tenant, "ATTENDANCE", "Attendance Updated", employee.getUsername() + " performed " + action, "/attendance");
    }

    public void notifyAttendanceModified(User employee, String reviewerName) {
        if (employee == null) return;
        sendLiveUpdate(employee, "ATTENDANCE", 
            "Attendance Record Updated", 
            reviewerName + " updated your attendance record.", 
            "/employee/attendance");
        sendLiveUpdateToTenant(getTenantSegment(employee), "ATTENDANCE", "Attendance Updated", reviewerName + " updated attendance record.", "/attendance");
    }

    public void notifyEmployeeManagementChanged(String tenant, String action, String employeeName) {
        sendLiveUpdateToTenantRole(tenant, "HR", "EMPLOYEE", 
            "Employee List Updated", 
            "Employee " + employeeName + " was " + action, 
            "/hr/employees");
        sendLiveUpdateToTenantRole(tenant, "MANAGER", "EMPLOYEE", 
            "Employee List Updated", 
            "Employee " + employeeName + " was " + action, 
            "/manager/team");
        sendLiveUpdateToTenant(tenant, "EMPLOYEE", "Employee List Updated", "Employee " + employeeName + " was " + action, "/employees");
    }

    private String linkFor(User user, String page) {
        return switch (page) {
            case "calendar" -> calendarLink(user);
            case "dashboard" -> dashboardLink(user);
            default -> dashboardLink(user);
        };
    }

    private String dashboardLink(User user) {
        String role = roleOf(user);
        return switch (role) {
            case "EMPLOYEE" -> "/employee/dashboard";
            case "MANAGER" -> "/manager/dashboard";
            case "HR" -> "/hr/dashboard";
            case "ADMIN" -> "/admin/dashboard";
            case "SUPER_ADMIN" -> "/superadmin/dashboard";
            default -> "/login";
        };
    }

    private String tasksLink(User user) {
        return switch (roleOf(user)) {
            case "EMPLOYEE" -> "/employee/tasks";
            case "MANAGER" -> "/manager/tasks";
            case "HR" -> "/hr/tasks";
            case "ADMIN" -> "/admin/tasks";
            default -> dashboardLink(user);
        };
    }

    private String leavesLink(User user) {
        return switch (roleOf(user)) {
            case "EMPLOYEE" -> "/employee/leaves";
            case "MANAGER" -> "/manager/leave";
            case "HR" -> "/hr/leaves";
            default -> dashboardLink(user);
        };
    }

    private String meetingsLink(User user) {
        return switch (roleOf(user)) {
            case "EMPLOYEE" -> "/employee/meetings";
            case "MANAGER" -> "/manager/meetings";
            case "HR" -> "/hr/meetings";
            case "ADMIN" -> "/admin/schedule-meeting";
            default -> dashboardLink(user);
        };
    }

    private String reportsLink(User user) {
        return switch (roleOf(user)) {
            case "EMPLOYEE" -> "/employee/reports";
            case "MANAGER" -> "/manager/reports";
            case "HR" -> "/hr/reports";
            case "ADMIN" -> "/admin/reports";
            default -> dashboardLink(user);
        };
    }

    private String calendarLink(User user) {
        return switch (roleOf(user)) {
            case "EMPLOYEE" -> "/employee/calendar";
            case "MANAGER" -> "/manager/calendar";
            case "HR" -> "/hr/calendar";
            case "ADMIN" -> "/admin/calendar";
            default -> dashboardLink(user);
        };
    }

    private String performanceLink(User user) {
        return switch (roleOf(user)) {
            case "EMPLOYEE" -> "/employee/performance";
            case "MANAGER" -> "/manager/performance";
            case "HR" -> "/hr/performance";
            default -> dashboardLink(user);
        };
    }

    private String teamLink(User user) {
        return "MANAGER".equals(roleOf(user)) ? "/manager/team" : dashboardLink(user);
    }

    private String roleOf(User user) {
        return user != null && user.getRole() != null ? user.getRole().toUpperCase() : "";
    }

    private String getTenantSegment(User user) {
        if (user == null || user.getEmail() == null) return "";
        String email = user.getEmail();
        String localPart = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        int lastDot = localPart.lastIndexOf('.');
        return lastDot >= 0 ? localPart.substring(lastDot + 1) : localPart;
    }
}
