package com.crm.demo.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.crm.demo.model.User;
import com.crm.demo.repository.UserRepository;
import com.crm.demo.service.ProfileUpdateService;

import jakarta.servlet.http.HttpServletResponse;

@Controller
@RequestMapping("/superadmin")
public class SuperAdminController {

    @Autowired private UserRepository        userRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    @Autowired private ProfileUpdateService  profileUpdateService;

    // ── helper: load admins and stats into model ─────────────────────────────
    private List<User> loadAdmins(Model model) {
        List<User> admins = userRepository.findAll().stream()
                .filter(u -> "ADMIN".equalsIgnoreCase(u.getRole()))
                .toList();
        long activeCount = admins.stream().filter(User::isActive).count();
        model.addAttribute("admins",       admins);
        model.addAttribute("totalAdmins",  admins.size());
        model.addAttribute("activeAdmins", activeCount);
        model.addAttribute("todayAdmins",  0);
        return admins;
    }

    // ── Dashboard (default page) ──────────────────────────────────────────────
    @GetMapping
    public String dashboardRoot(Model model) {
        return "redirect:/superadmin/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        loadAdmins(model);

        // ── Analytics data ────────────────────────────────────────────────────
        List<User> allUsers = userRepository.findAll();
        List<User> admins   = allUsers.stream()
                .filter(u -> "ADMIN".equalsIgnoreCase(u.getRole()))
                .collect(Collectors.toList());

        long activeAdmins   = admins.stream().filter(User::isActive).count();
        long inactiveAdmins = admins.size() - activeAdmins;

        // Role distribution across ALL users
        Map<String, Long> roleDistribution = allUsers.stream()
                .collect(Collectors.groupingBy(
                        u -> u.getRole() == null ? "UNKNOWN" : u.getRole().toUpperCase(),
                        Collectors.counting()));

        // Admin growth simulation: bucket admins into 6 groups by ID range
        // (approximates "added over time" without a createdAt column)
        int total = admins.size();
        int[] growthData = new int[6];
        if (total > 0) {
            long minId = admins.stream().mapToLong(User::getId).min().orElse(1);
            long maxId = admins.stream().mapToLong(User::getId).max().orElse(1);
            long range = Math.max(maxId - minId, 1);
            for (User a : admins) {
                int bucket = (int) Math.min(5, (a.getId() - minId) * 6 / range);
                growthData[bucket]++;
            }
        }

        // Status breakdown per "simulated month" (last 6 months labels)
        java.time.LocalDate now = java.time.LocalDate.now();
        String[] monthLabels = new String[6];
        for (int i = 5; i >= 0; i--) {
            monthLabels[5 - i] = now.minusMonths(i)
                    .getMonth().getDisplayName(java.time.format.TextStyle.SHORT,
                            java.util.Locale.ENGLISH);
        }

        // Active vs Inactive per bucket (for stacked bar)
        int[] activePerMonth   = new int[6];
        int[] inactivePerMonth = new int[6];
        if (total > 0) {
            long minId = admins.stream().mapToLong(User::getId).min().orElse(1);
            long maxId = admins.stream().mapToLong(User::getId).max().orElse(1);
            long range = Math.max(maxId - minId, 1);
            for (User a : admins) {
                int bucket = (int) Math.min(5, (a.getId() - minId) * 6 / range);
                if (a.isActive()) activePerMonth[bucket]++;
                else              inactivePerMonth[bucket]++;
            }
        }

        model.addAttribute("activeAdminsCount",   activeAdmins);
        model.addAttribute("inactiveAdminsCount",  inactiveAdmins);
        model.addAttribute("roleDistribution",     roleDistribution);
        model.addAttribute("growthData",           growthData);
        model.addAttribute("monthLabels",          monthLabels);
        model.addAttribute("activePerMonth",       activePerMonth);
        model.addAttribute("inactivePerMonth",     inactivePerMonth);
        model.addAttribute("totalUsers",           allUsers.size());

        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        model.addAttribute("superAdminUser", userRepository.findByUsername(currentUsername));

        return "superadmin-dashboard";
    }

    // ── Admins list page ──────────────────────────────────────────────────────
    @GetMapping("/admins")
    public String adminsPage(Model model) {
        loadAdmins(model);
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        model.addAttribute("superAdminUser", userRepository.findByUsername(currentUsername));
        return "superadmin-admins";
    }

    // ── Add Admin page (GET) ──────────────────────────────────────────────────
    @GetMapping("/add-admin")
    public String addAdminPage(Model model) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        model.addAttribute("superAdminUser", userRepository.findByUsername(currentUsername));
        return "superadmin-add-admin";
    }

    // ── Add Admin (POST) ──────────────────────────────────────────────────────
    @PostMapping("/add-admin")
    public String addAdmin(@RequestParam String email,
                           @RequestParam String username,
                           @RequestParam String password,
                           @RequestParam String confirmPassword,
                           @RequestParam(defaultValue = "active") String status,
                           RedirectAttributes ra) {

        if (!password.equals(confirmPassword)) {
            ra.addFlashAttribute("errorMessage", "Passwords do not match.");
            return "redirect:/superadmin/add-admin";
        }
        if (userRepository.findByUsernameOrEmail(username, email) != null) {
            ra.addFlashAttribute("errorMessage", "Username or email already exists.");
            return "redirect:/superadmin/add-admin";
        }

        User newAdmin = new User();
        newAdmin.setUsername(username);
        newAdmin.setEmail(email);
        newAdmin.setPassword(passwordEncoder.encode(password));
        newAdmin.setRole("ADMIN");
        newAdmin.setStatus(status);
        userRepository.save(newAdmin);

        ra.addFlashAttribute("successMessage", "Admin '" + username + "' added successfully.");
        return "redirect:/superadmin/admins";
    }

    // ── Edit Admin (GET) ──────────────────────────────────────────────────────
    @GetMapping("/edit-admin/{id}")
    public String editAdminPage(@PathVariable Long id, Model model) {
        User admin = userRepository.findById(id).orElse(null);
        if (admin == null || !"ADMIN".equalsIgnoreCase(admin.getRole())) {
            return "redirect:/superadmin/admins";
        }
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        model.addAttribute("superAdminUser", userRepository.findByUsername(currentUsername));
        model.addAttribute("editAdmin", admin);
        return "superadmin-edit-admin";
    }

    // ── Edit Admin (POST) — no password change, only username/email/status ────
    @PostMapping("/edit-admin/{id}")
    public String editAdmin(@PathVariable Long id,
                            @RequestParam String email,
                            @RequestParam String username,
                            @RequestParam(defaultValue = "active") String status,
                            RedirectAttributes ra) {

        User admin = userRepository.findById(id).orElse(null);
        if (admin == null || !"ADMIN".equalsIgnoreCase(admin.getRole())) {
            return "redirect:/superadmin/admins";
        }

        // Check duplicate username/email (excluding current user)
        User existing = userRepository.findByUsernameOrEmail(username, email);
        if (existing != null && !existing.getId().equals(id)) {
            ra.addFlashAttribute("errorMessage", "Username or email already in use.");
            return "redirect:/superadmin/edit-admin/" + id;
        }

        admin.setUsername(username);
        admin.setEmail(email);
        admin.setStatus(status);
        userRepository.save(admin);

        ra.addFlashAttribute("successMessage", "Admin '" + username + "' updated successfully.");
        return "redirect:/superadmin/admins";
    }

    // ── Toggle Admin Status ───────────────────────────────────────────────────
    @PostMapping("/toggle-status/{id}")
    public String toggleStatus(@PathVariable Long id, RedirectAttributes ra) {
        User admin = userRepository.findById(id).orElse(null);
        if (admin != null && "ADMIN".equalsIgnoreCase(admin.getRole())) {
            String newStatus = "active".equalsIgnoreCase(admin.getStatus()) ? "inactive" : "active";
            admin.setStatus(newStatus);
            userRepository.save(admin);
            ra.addFlashAttribute("successMessage",
                "Admin '" + admin.getUsername() + "' is now " + newStatus + ".");
        }
        return "redirect:/superadmin/admins";
    }

    // ── Delete Admin ──────────────────────────────────────────────────────────
    @PostMapping("/delete-admin/{id}")
    public String deleteAdmin(@PathVariable Long id, RedirectAttributes ra) {
        User admin = userRepository.findById(id).orElse(null);
        if (admin != null && "ADMIN".equalsIgnoreCase(admin.getRole())) {
            String name = admin.getUsername();
            userRepository.delete(admin);
            ra.addFlashAttribute("successMessage", "Admin '" + name + "' deleted successfully.");
        }
        return "redirect:/superadmin/admins";
    }

    // ── Views page — removed, redirect to dashboard ──────────────────────────
    @GetMapping("/views")
    public String viewsPage() {
        return "redirect:/superadmin/dashboard";
    }

    // ── Profile page ──────────────────────────────────────────────────────────
    @GetMapping("/profile")
    public String profilePage(Model model) {
        loadAdmins(model);
        String currentUsername = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        User superAdmin = userRepository.findByUsername(currentUsername);
        model.addAttribute("superAdminUser", superAdmin);
        return "superadmin-profile";
    }

    // ── Update Profile (POST) ─────────────────────────────────────────────────
    @PostMapping("/update-profile")
    public String updateProfile(@RequestParam(required = false) String username,
                                @RequestParam(required = false) String email,
                                @RequestParam(required = false) String password,
                                @RequestParam(required = false) String confirmPassword,
                                HttpServletResponse response,
                                RedirectAttributes ra) {

        String currentUsername = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        User superAdmin = userRepository.findByUsername(currentUsername);
        if (superAdmin == null) {
            return "redirect:/superadmin/profile";
        }

        profileUpdateService.updateProfile(superAdmin, username, email, password, confirmPassword, ra, response);
        return "redirect:/superadmin/profile";
    }
}
