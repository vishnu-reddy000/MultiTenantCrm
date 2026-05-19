package com.crm.demo.controller;

import com.crm.demo.model.User;
import com.crm.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/superadmin")
public class SuperAdminController {

    @Autowired private UserRepository        userRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;

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
        return "superadmin-dashboard";
    }

    // ── Admins list page ──────────────────────────────────────────────────────
    @GetMapping("/admins")
    public String adminsPage(Model model) {
        loadAdmins(model);
        return "superadmin-admins";
    }

    // ── Add Admin page (GET) — redirect to admins ──
    @GetMapping("/add-admin")
    public String addAdminPage() {
        return "redirect:/superadmin/admins";
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
            ra.addFlashAttribute("showAddForm", true);
            return "redirect:/superadmin/admins";
        }
        if (userRepository.findByUsernameOrEmail(username, email) != null) {
            ra.addFlashAttribute("errorMessage", "Username or email already exists.");
            ra.addFlashAttribute("showAddForm", true);
            return "redirect:/superadmin/admins";
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
        loadAdmins(model);
        model.addAttribute("editAdmin", admin);
        return "superadmin-admins";
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

    // ── Views page ────────────────────────────────────────────────────────────
    @GetMapping("/views")
    public String viewsPage(Model model) {
        loadAdmins(model);
        return "superadmin-views";
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
    public String updateProfile(@RequestParam String username,
                                @RequestParam String email,
                                @RequestParam(required = false) String password,
                                @RequestParam(required = false) String confirmPassword,
                                RedirectAttributes ra) {

        String currentUsername = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        User superAdmin = userRepository.findByUsername(currentUsername);
        if (superAdmin == null) {
            return "redirect:/superadmin/profile";
        }

        superAdmin.setUsername(username);
        superAdmin.setEmail(email);

        if (password != null && !password.isBlank()) {
            if (!password.equals(confirmPassword)) {
                ra.addFlashAttribute("errorMessage", "Passwords do not match.");
                return "redirect:/superadmin/profile";
            }
            superAdmin.setPassword(passwordEncoder.encode(password));
        }

        userRepository.save(superAdmin);
        ra.addFlashAttribute("successMessage", "Profile updated successfully.");
        return "redirect:/superadmin/profile";
    }
}
