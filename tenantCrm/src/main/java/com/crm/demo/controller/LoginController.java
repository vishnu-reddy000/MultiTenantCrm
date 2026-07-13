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
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.servlet.http.HttpServletRequest;

import com.crm.demo.model.User;
import com.crm.demo.repository.UserRepository;
import com.crm.demo.security.JwtUtil;
import com.crm.demo.security.SessionManager;

@Controller
public class LoginController {

    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_MANAGER = "MANAGER";
    private static final String ROLE_EMPLOYEE = "EMPLOYEE";
    private static final String ROLE_HR = "HR";
    private static final String ATTR_ERROR = "error";
    private static final String REDIRECT_PREFIX = "redirect:";
    private static final String REDIRECT_SLASH = "redirect:/";
    private static final String REDIRECT_LOGIN = "redirect:/login";
    private static final String PATH_MEETINGS = "meetings";

    @Autowired private UserRepository      userRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    @Autowired private JwtUtil             jwtUtil;
    @Autowired private SessionManager      sessionManager;

    // ─── Serve the login HTML page ────────────────────────────────────────────────
    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    // ─── REST: authenticate and return JWT ───────────────────────────────────────
    // Called by the login form via fetch() — returns JSON, no redirect
    @PostMapping("/api/auth/login")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> authenticate(@RequestBody Map<String, String> body) {

        var username = body.get("username");
        var password = body.get("password");
        var force = Boolean.parseBoolean(body.get("force"));

        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of(ATTR_ERROR, "Username and password are required."));
        }

        var user = userRepository.findByUsernameOrEmail(username, username).orElse(null);

        if (user != null && passwordEncoder.matches(password, user.getPassword())) {

            // Block inactive users from logging in
            if (!user.isActive()) {
                return ResponseEntity.status(403)
                        .body(Map.of(ATTR_ERROR, "Your account is inactive. Please contact your administrator."));
            }

            // Block employees/managers/HR if their tenant's admin is inactive
            if (isTenantAdminInactive(user)) {
                return ResponseEntity.status(403)
                        .body(Map.of(ATTR_ERROR, "Access is currently disabled. Please contact your administrator."));
            }

            // Check if user is already logged in
            if (!force && sessionManager.isUserLoggedIn(user.getUsername())) {
                return ResponseEntity.status(409)
                        .body(Map.of(
                            "alreadyLoggedIn", true,
                            ATTR_ERROR, "Your account is already active on another device. Do you want to continue and sign out from the previous session?"
                        ));
            }

            if (force) {
                sessionManager.invalidateSession(user.getUsername());
            }

            var token = jwtUtil.generateToken(user.getUsername(), user.getRole());

            // Register the active session
            sessionManager.registerSession(user.getUsername(), token);

            return ResponseEntity.ok(Map.of(
                    "token",    token,
                    "username", user.getUsername(),
                    "role",     user.getRole(),
                    "redirect", dashboardFor(user.getRole())
            ));
        }

        return ResponseEntity.status(401)
                .body(Map.of(ATTR_ERROR, "Invalid username or password."));
    }

    // ─── REST Logout: clear server-side session ──────────────────────────────────
    @PostMapping("/api/auth/logout")
    @ResponseBody
    public ResponseEntity<Void> apiLogout(HttpServletRequest request) {
        var authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            var token = authHeader.substring(7);
            if (jwtUtil.isValid(token)) {
                var username = jwtUtil.extractUsername(token);
                sessionManager.invalidateSession(username);
            }
        }
        return ResponseEntity.ok().build();
    }

    // ─── Logout: clear server-side session and cookie ────────────────────────────
    @GetMapping("/logout")
    public String logout(HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            var username = auth.getName();
            sessionManager.invalidateSession(username);
        }
        
        // Clear cookie
        var cookie = new jakarta.servlet.http.Cookie("jwt_token", "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        
        return REDIRECT_LOGIN;
    }

    @GetMapping({"/dashboard", "/tasks", "/meetings", "/leaves", "/leave", "/teams", "/team", "/performance", "/reports", "/attendance", "/calendar"})
    public String handleLegacyRedirects(HttpServletRequest request) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return REDIRECT_LOGIN;
        }
        var username = auth.getName();
        var user = userRepository.findByUsername(username);
        if (user == null) {
            return REDIRECT_LOGIN;
        }

        var role = user.getRole() != null ? user.getRole().toUpperCase() : "";
        var path = request.getRequestURI().toLowerCase();

        return resolveLegacyRedirect(role, path);
    }

    private String resolveLegacyRedirect(String role, String path) {
        String cleanPath = getCleanPathSuffix(path);
        if (cleanPath == null) {
            return REDIRECT_PREFIX + dashboardFor(role);
        }
        
        // Handle special exceptions first
        if (PATH_MEETINGS.equals(cleanPath) && ROLE_ADMIN.equalsIgnoreCase(role)) {
            return "redirect:/admin/schedule-meeting";
        }
        if ("teams".equals(cleanPath)) {
            if (ROLE_MANAGER.equalsIgnoreCase(role)) return "redirect:/manager/team";
            if (ROLE_HR.equalsIgnoreCase(role)) return "redirect:/hr/teams";
            return REDIRECT_PREFIX + dashboardFor(role);
        }
        
        // Standard paths
        boolean allowed = switch (cleanPath) {
            case "tasks", "reports", "calendar" -> hasRole(role, ROLE_EMPLOYEE, ROLE_MANAGER, ROLE_HR, ROLE_ADMIN);
            case PATH_MEETINGS, "leaves", "performance", "attendance" -> hasRole(role, ROLE_EMPLOYEE, ROLE_MANAGER, ROLE_HR);
            default -> false;
        };
        
        if (allowed) {
            return REDIRECT_SLASH + role.toLowerCase() + "/" + cleanPath;
        }
        
        return REDIRECT_PREFIX + dashboardFor(role);
    }

    private boolean hasRole(String role, String... targetRoles) {
        if (role == null) return false;
        for (var r : targetRoles) {
            if (r.equalsIgnoreCase(role)) return true;
        }
        return false;
    }

    private String getCleanPathSuffix(String path) {
        if (path == null) return null;
        if (path.endsWith("/tasks")) return "tasks";
        if (path.endsWith("/" + PATH_MEETINGS)) return PATH_MEETINGS;
        if (path.endsWith("/leaves") || path.endsWith("/leave")) return "leaves";
        if (path.endsWith("/teams") || path.endsWith("/team")) return "teams";
        if (path.endsWith("/performance")) return "performance";
        if (path.endsWith("/reports")) return "reports";
        if (path.endsWith("/attendance")) return "attendance";
        if (path.endsWith("/calendar")) return "calendar";
        return null;
    }

    // ─── Helper ───────────────────────────────────────────────────────────────────
    private String dashboardFor(String role) {
        if (role == null) return "/login";
        return switch (role.toUpperCase()) {
            case ROLE_SUPER_ADMIN -> "/superadmin";
            case ROLE_ADMIN       -> "/admin/dashboard";
            case ROLE_MANAGER     -> "/manager/dashboard";
            case ROLE_HR          -> "/hr/dashboard";
            case ROLE_EMPLOYEE    -> "/employee/dashboard";
            default            -> "/login";
        };
    }

    private boolean isTenantAdminInactive(User user) {
        var role = user.getRole();
        if (role != null && !role.equalsIgnoreCase(ROLE_SUPER_ADMIN) && !role.equalsIgnoreCase(ROLE_ADMIN)) {
            var tenantSegment = extractTenantSegment(user.getEmail());
            if (tenantSegment != null && !tenantSegment.isBlank()) {
                return userRepository.findAll().stream()
                        .filter(u -> ROLE_ADMIN.equalsIgnoreCase(u.getRole()))
                        .filter(u -> tenantSegment.equals(extractTenantSegment(u.getEmail())))
                        .anyMatch(u -> !u.isActive());
            }
        }
        return false;
    }

    /** Extract tenant segment from email: "emp.tcs@crm.com" → "tcs" */
    private String extractTenantSegment(String email) {
        if (email == null || !email.contains("@")) return null;
        var local = email.substring(0, email.indexOf('@'));
        var dot = local.lastIndexOf('.');
        return dot >= 0 ? local.substring(dot + 1) : null;
    }
    
  
    }

