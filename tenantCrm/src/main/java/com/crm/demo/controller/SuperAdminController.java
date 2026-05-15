package com.crm.demo.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.crm.demo.model.User;
import com.crm.demo.repository.UserRepository;

import java.util.List;

@Controller
@RequestMapping("/superadmin")
public class SuperAdminController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    // ─── DASHBOARD ────────────────────────────────────────────────────────────────
    @GetMapping
    public String dashboard(Model model) {
        List<User> admins = userRepository.findAll()
                .stream()
                .filter(u -> "ADMIN".equalsIgnoreCase(u.getRole()))
                .toList();

        model.addAttribute("admins", admins);
        model.addAttribute("totalAdmins", admins.size());
        model.addAttribute("activeAdmins", admins.size()); // extend later if you add an active flag
        model.addAttribute("todayAdmins", 0);              // extend later with createdAt tracking
        return "superadmin";
    }

    // ─── ADD ADMIN ────────────────────────────────────────────────────────────────
    @PostMapping("/add-admin")
    public String addAdmin(@RequestParam String email,
                           @RequestParam String username,
                           @RequestParam String password,
                           @RequestParam String confirmPassword,
                           Model model) {

        // Reload admin list for the view regardless of outcome
        List<User> admins = userRepository.findAll()
                .stream()
                .filter(u -> "ADMIN".equalsIgnoreCase(u.getRole()))
                .toList();
        model.addAttribute("admins", admins);
        model.addAttribute("totalAdmins", admins.size());
        model.addAttribute("activeAdmins", admins.size());
        model.addAttribute("todayAdmins", 0);

        // Validate passwords match
        if (!password.equals(confirmPassword)) {
            model.addAttribute("errorMessage", "Passwords do not match.");
            return "superadmin";
        }

        // Check for duplicate username or email
        if (userRepository.findByUsernameOrEmail(username, email) != null) {
            model.addAttribute("errorMessage", "Username or email already exists.");
            return "superadmin";
        }

        // Create and save the new admin with a BCrypt-hashed password
        User newAdmin = new User();
        newAdmin.setUsername(username);
        newAdmin.setEmail(email);
        newAdmin.setPassword(passwordEncoder.encode(password));  // ← BCrypt hash
        newAdmin.setRole("ADMIN");

        userRepository.save(newAdmin);

        model.addAttribute("successMessage", "Admin '" + username + "' added successfully.");
        return "superadmin";
    }
}
