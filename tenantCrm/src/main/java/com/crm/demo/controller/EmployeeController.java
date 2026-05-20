package com.crm.demo.controller;

import com.crm.demo.model.Attendance;
import com.crm.demo.model.User;
import com.crm.demo.repository.AttendanceRepository;
import com.crm.demo.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/employee")
public class EmployeeController {

    @Autowired private UserRepository        userRepository;
    @Autowired private AttendanceRepository  attendanceRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;

    // ── helpers ───────────────────────────────────────────────────────────

    private void injectUser(HttpServletRequest request, Model model) {
        String username = (String) request.getAttribute("loggedInUser");
        model.addAttribute("employeeName", username != null ? username : "Employee");
        model.addAttribute("employeeRole", "Employee");
    }

    private User getCurrentEmployee() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username);
    }

    private String getTenantSegment(User user) {
        if (user == null || user.getEmail() == null) return "";
        try {
            String email = user.getEmail();
            String local = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
            int dot = local.lastIndexOf('.');
            return dot >= 0 ? local.substring(dot + 1) : local;
        } catch (Exception e) { return ""; }
    }

    private void injectStats(Model model) {
        model.addAttribute("activeProjectsCount", 0);
        model.addAttribute("completedTasks",       0);
        model.addAttribute("attendanceRate",      "0%");
        model.addAttribute("pendingLeaves",        0);
        model.addAttribute("attendanceMonth",     "May 2026");
        model.addAttribute("presentDays",          0);
        model.addAttribute("absentDays",           0);
        model.addAttribute("leaveDays",            0);
        model.addAttribute("attendancePercent",    0);
        model.addAttribute("lastCheckin",         "—");
        model.addAttribute("myProjects",           Collections.emptyList());
        model.addAttribute("pendingTasks",         Collections.emptyList());
        model.addAttribute("leaveRequests",        Collections.emptyList());
    }

    // ── pages ─────────────────────────────────────────────────────────────

    @GetMapping("/dashboard")
    public String dashboard(HttpServletRequest request, Model model) {
        injectUser(request, model);
        injectStats(model);
        return "employee-dashboard";
    }

    @GetMapping("/projects")
    public String projectsPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        injectStats(model);
        return "employee-projects";
    }

    @GetMapping("/tasks")
    public String tasksPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        injectStats(model);
        return "employee-tasks";
    }

    // ── ATTENDANCE ────────────────────────────────────────────────────────

    @GetMapping("/attendance")
    public String attendancePage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        injectStats(model);

        User emp = getCurrentEmployee();
        LocalDate today = LocalDate.now();

        // Personal attendance history (newest first)
        List<Attendance> records = emp != null
                ? attendanceRepository.findByUserOrderByDateDesc(emp)
                : Collections.emptyList();

        // Today's record — drives button state
        Optional<Attendance> todayOpt = emp != null
                ? attendanceRepository.findByUserAndDate(emp, today)
                : Optional.empty();

        boolean punchedIn  = todayOpt.isPresent();
        boolean punchedOut = todayOpt.map(a -> a.getCheckOut() != null).orElse(false);

        // Personal stats
        long presentCount = records.stream()
                .filter(a -> "present".equalsIgnoreCase(a.getStatus()) || "late".equalsIgnoreCase(a.getStatus()))
                .count();
        long lateCount = records.stream()
                .filter(a -> "late".equalsIgnoreCase(a.getStatus()))
                .count();

        model.addAttribute("attendanceRecords", records);
        model.addAttribute("todayRecord",       todayOpt.orElse(null));
        model.addAttribute("punchedIn",         punchedIn);
        model.addAttribute("punchedOut",        punchedOut);
        model.addAttribute("totalRecords",      records.size());
        model.addAttribute("presentCount",      presentCount);
        model.addAttribute("lateCount",         lateCount);

        return "employee-attendance";
    }

    @PostMapping("/attendance/punch-in")
    public String punchIn(RedirectAttributes ra) {
        User emp = getCurrentEmployee();
        if (emp == null) return "redirect:/employee/attendance";

        LocalDate today = LocalDate.now();
        if (attendanceRepository.findByUserAndDate(emp, today).isPresent()) {
            ra.addFlashAttribute("errorMessage", "You have already punched in today.");
            return "redirect:/employee/attendance";
        }

        LocalTime now    = LocalTime.now();
        String   status  = now.isAfter(LocalTime.of(9, 30)) ? "late" : "present";
        String   tenant  = getTenantSegment(emp);

        Attendance att = new Attendance();
        att.setUser(emp);
        att.setDate(today);
        att.setCheckIn(now);
        att.setStatus(status);
        att.setTenantSegment(tenant);
        attendanceRepository.save(att);

        ra.addFlashAttribute("successMessage",
                "Punched in at " + String.format("%02d:%02d", now.getHour(), now.getMinute()) + ".");
        return "redirect:/employee/attendance";
    }

    @PostMapping("/attendance/punch-out")
    public String punchOut(RedirectAttributes ra) {
        User emp = getCurrentEmployee();
        if (emp == null) return "redirect:/employee/attendance";

        LocalDate today = LocalDate.now();
        Optional<Attendance> opt = attendanceRepository.findByUserAndDate(emp, today);

        if (opt.isEmpty()) {
            ra.addFlashAttribute("errorMessage", "You haven't punched in today.");
            return "redirect:/employee/attendance";
        }
        Attendance att = opt.get();
        if (att.getCheckOut() != null) {
            ra.addFlashAttribute("errorMessage", "You have already punched out today.");
            return "redirect:/employee/attendance";
        }

        LocalTime now = LocalTime.now();
        att.setCheckOut(now);
        attendanceRepository.save(att);

        ra.addFlashAttribute("successMessage",
                "Punched out at " + String.format("%02d:%02d", now.getHour(), now.getMinute()) + ".");
        return "redirect:/employee/attendance";
    }

    // ── other pages ───────────────────────────────────────────────────────

    @GetMapping("/leaves")
    public String leavesPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        injectStats(model);
        return "employee-leaves";
    }

    @GetMapping("/settings")
    public String settingsPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        injectStats(model);
        User emp = getCurrentEmployee();
        model.addAttribute("employeeEmail", emp != null ? emp.getEmail() : "");
        return "employee-settings";
    }

    @PostMapping("/settings/profile")
    public String updateProfile(@RequestParam String username,
                                @RequestParam String email,
                                @RequestParam(required = false) String password,
                                @RequestParam(required = false) String confirmPassword,
                                RedirectAttributes ra) {
        User emp = getCurrentEmployee();
        if (emp == null) return "redirect:/employee/settings";

        User existing = userRepository.findByUsernameOrEmail(username, email);
        if (existing != null && !existing.getId().equals(emp.getId())) {
            ra.addFlashAttribute("errorMessage", "Username or email already in use.");
            return "redirect:/employee/settings";
        }
        emp.setUsername(username);
        emp.setEmail(email);
        if (password != null && !password.isBlank()) {
            if (!password.equals(confirmPassword)) {
                ra.addFlashAttribute("errorMessage", "Passwords do not match.");
                return "redirect:/employee/settings";
            }
            emp.setPassword(passwordEncoder.encode(password));
        }
        userRepository.save(emp);
        ra.addFlashAttribute("successMessage", "Profile updated successfully.");
        return "redirect:/employee/settings";
    }
}
