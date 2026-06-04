package com.crm.demo.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import com.crm.demo.model.User;
import com.crm.demo.repository.UserRepository;
import com.crm.demo.security.JwtUtil;

@Controller
public class LoginController {

	
    @Autowired private UserRepository      userRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    @Autowired private JwtUtil             jwtUtil;

    // ─── Serve the login HTML page ────────────────────────────────────────────────
    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    // ─── REST: authenticate and return JWT ───────────────────────────────────────
    // Called by the login form via fetch() — returns JSON, no redirect
    @PostMapping("/api/auth/login")
    @ResponseBody
    public ResponseEntity<?> authenticate(@RequestBody Map<String, String> body) {

        String username = body.get("username");
        String password = body.get("password");

        if (username == null || password == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Username and password are required."));
        }

        User user = userRepository.findByUsernameOrEmail(username, username);

        if (user != null && passwordEncoder.matches(password, user.getPassword())) {

            // Block inactive users from logging in
            if (!user.isActive()) {
                return ResponseEntity.status(403)
                        .body(Map.of("error", "Your account is inactive. Please contact your administrator."));
            }

            // Block employees/managers/HR if their tenant's admin is inactive
            String role = user.getRole();
            if (role != null && !role.equalsIgnoreCase("SUPER_ADMIN") && !role.equalsIgnoreCase("ADMIN")) {
                String tenantSegment = extractTenantSegment(user.getEmail());
                if (tenantSegment != null && !tenantSegment.isBlank()) {
                    boolean adminInactive = userRepository.findAll().stream()
                            .filter(u -> "ADMIN".equalsIgnoreCase(u.getRole()))
                            .filter(u -> tenantSegment.equals(extractTenantSegment(u.getEmail())))
                            .anyMatch(u -> !u.isActive());
                    if (adminInactive) {
                        return ResponseEntity.status(403)
                                .body(Map.of("error", "Access is currently disabled. Please contact your administrator."));
                    }
                }
            }

            String token = jwtUtil.generateToken(user.getUsername(), user.getRole());

            return ResponseEntity.ok(Map.of(
                    "token",    token,
                    "username", user.getUsername(),
                    "role",     user.getRole(),
                    "redirect", dashboardFor(user.getRole())
            ));
        }

        return ResponseEntity.status(401)
                .body(Map.of("error", "Invalid username or password."));
    }

    // ─── Logout: client just deletes the token from localStorage ─────────────────
    // This endpoint is optional — kept for convenience (e.g. server-side audit log)
    @GetMapping("/logout")
    public String logout() {
        // Nothing to invalidate server-side — token lives only in the browser
        return "redirect:/login";
    }

    // ─── Helper ───────────────────────────────────────────────────────────────────
    private String dashboardFor(String role) {
        if (role == null) return "/login";
        return switch (role.toUpperCase()) {
            case "SUPER_ADMIN" -> "/superadmin";
            case "ADMIN"       -> "/admin/dashboard";
            case "MANAGER"     -> "/manager/dashboard";
            case "HR"          -> "/hr/dashboard";
            case "EMPLOYEE"    -> "/employee/dashboard";
            default            -> "/login";
        };
    }

    /** Extract tenant segment from email: "emp.tcs@crm.com" → "tcs" */
    private String extractTenantSegment(String email) {
        if (email == null || !email.contains("@")) return null;
        String local = email.substring(0, email.indexOf('@'));
        int dot = local.lastIndexOf('.');
        return dot >= 0 ? local.substring(dot + 1) : null;
    }
    
 
    }

