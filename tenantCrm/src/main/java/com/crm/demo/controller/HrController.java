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
import java.util.List;

@Controller
@RequestMapping("/hr")
public class HrController {

    @Autowired private UserRepository        userRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;

    private void injectUser(HttpServletRequest request, Model model) {
        String username = (String) request.getAttribute("loggedInUser");
        model.addAttribute("adminName", username != null ? username : "HR User");
        model.addAttribute("adminRole", "HR");
    }

    private void injectStats(Model model) {
        List<User> employees = userRepository.findAll().stream()
                .filter(u -> "EMPLOYEE".equalsIgnoreCase(u.getRole())).toList();
        long active   = employees.stream().filter(User::isActive).count();
        long inactive = employees.size() - active;

        model.addAttribute("employees",         employees);
        model.addAttribute("totalEmployees",    employees.size());
        model.addAttribute("activeEmployees",   active);
        model.addAttribute("inactiveEmployees", inactive);
        model.addAttribute("newHires",          0);
        model.addAttribute("onLeaveToday",      0);
        model.addAttribute("openPositions",     0);
        model.addAttribute("leaveRequests",     Collections.emptyList());
        model.addAttribute("attendanceMonth",   "May 2026");
        model.addAttribute("presentPercent",    "0%");
        model.addAttribute("absentPercent",     "0%");
        model.addAttribute("wfhPercent",        "0%");
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpServletRequest request, Model model) {
        injectUser(request, model);
        injectStats(model);
        return "hr-dashboard";
    }

    @GetMapping("/employees")
    public String employeesPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        injectStats(model);
        return "hr-employees";
    }

    @GetMapping("/recruitment")
    public String recruitmentPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        injectStats(model);
        return "hr-recruitment";
    }

    @GetMapping("/attendance")
    public String attendancePage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        injectStats(model);
        return "hr-attendance";
    }

    @GetMapping("/leaves")
    public String leavesPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        injectStats(model);
        return "hr-leaves";
    }

    @GetMapping("/settings")
    public String settingsPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        injectStats(model);
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User hr = userRepository.findByUsername(currentUsername);
        model.addAttribute("hrEmail", hr != null ? hr.getEmail() : "");
        return "hr-settings";
    }

    @PostMapping("/settings/profile")
    public String updateProfile(@RequestParam String username,
                                @RequestParam String email,
                                @RequestParam(required = false) String password,
                                @RequestParam(required = false) String confirmPassword,
                                RedirectAttributes ra) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User hr = userRepository.findByUsername(currentUsername);
        if (hr == null) return "redirect:/hr/settings";

        User existing = userRepository.findByUsernameOrEmail(username, email);
        if (existing != null && !existing.getId().equals(hr.getId())) {
            ra.addFlashAttribute("errorMessage", "Username or email already in use.");
            return "redirect:/hr/settings";
        }
        hr.setUsername(username);
        hr.setEmail(email);
        if (password != null && !password.isBlank()) {
            if (!password.equals(confirmPassword)) {
                ra.addFlashAttribute("errorMessage", "Passwords do not match.");
                return "redirect:/hr/settings";
            }
            hr.setPassword(passwordEncoder.encode(password));
        }
        userRepository.save(hr);
        ra.addFlashAttribute("successMessage", "Profile updated successfully.");
        return "redirect:/hr/settings";
    }
}
