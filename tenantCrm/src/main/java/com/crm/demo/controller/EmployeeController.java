package com.crm.demo.controller;

import com.crm.demo.model.User;
import com.crm.demo.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;

@Controller
@RequestMapping("/employee")
public class EmployeeController {

    @Autowired private UserRepository        userRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;

    private void injectUser(HttpServletRequest request, Model model) {
        String username = (String) request.getAttribute("loggedInUser");
        model.addAttribute("employeeName", username != null ? username : "Employee");
        model.addAttribute("employeeRole", "Employee");
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

    @GetMapping("/attendance")
    public String attendancePage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        injectStats(model);
        return "employee-attendance";
    }

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
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User emp = userRepository.findByUsername(currentUsername);
        model.addAttribute("employeeEmail", emp != null ? emp.getEmail() : "");
        return "employee-settings";
    }

    @PostMapping("/settings/profile")
    public String updateProfile(@RequestParam String username,
                                @RequestParam String email,
                                @RequestParam(required = false) String password,
                                @RequestParam(required = false) String confirmPassword,
                                RedirectAttributes ra) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User emp = userRepository.findByUsername(currentUsername);
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
