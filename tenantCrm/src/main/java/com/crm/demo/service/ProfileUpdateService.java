package com.crm.demo.service;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.crm.demo.model.User;
import com.crm.demo.repository.UserRepository;
import com.crm.demo.security.JwtUtil;

import jakarta.servlet.http.HttpServletResponse;

@Service
public class ProfileUpdateService {

    @Autowired private UserRepository userRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    public boolean updateProfile(User user,
                                 String username,
                                 String email,
                                 String password,
                                 String confirmPassword,
                                 RedirectAttributes ra,
                                 HttpServletResponse response) {
        if (user == null) {
            return false;
        }

        String newUsername = clean(username);
        String newEmail = clean(email);
        boolean changed = false;

        if (newUsername != null && !newUsername.equals(user.getUsername())) {
            User existing = userRepository.findByUsername(newUsername);
            if (isAnotherUser(existing, user)) {
                ra.addFlashAttribute("errorMessage", "Username already in use.");
                return false;
            }
            user.setUsername(newUsername);
            changed = true;
        }

        if (newEmail != null && !newEmail.equalsIgnoreCase(nullToEmpty(user.getEmail()))) {
            User existing = userRepository.findByEmail(newEmail);
            if (isAnotherUser(existing, user)) {
                ra.addFlashAttribute("errorMessage", "Email already in use.");
                return false;
            }
            user.setEmail(newEmail);
            changed = true;
        }

        if (password != null && !password.isBlank()) {
            if (!Objects.equals(password, confirmPassword)) {
                ra.addFlashAttribute("errorMessage", "Passwords do not match.");
                return false;
            }

            if (user.getPassword() == null || !passwordEncoder.matches(password, user.getPassword())) {
                user.setPassword(passwordEncoder.encode(password));
                changed = true;
            }
        }

        if (changed) {
            userRepository.save(user);
            ra.addFlashAttribute("successMessage", "Profile updated successfully.");
        } else {
            ra.addFlashAttribute("successMessage", "No changes to update.");
        }

        refreshBrowserAuth(user, ra, response);
        return true;
    }

    private void refreshBrowserAuth(User user, RedirectAttributes ra, HttpServletResponse response) {
        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());

        ra.addFlashAttribute("newJwtToken", token);
        ra.addFlashAttribute("newJwtUsername", user.getUsername());
        ra.addFlashAttribute("newJwtRole", user.getRole());

        ResponseCookie cookie = ResponseCookie.from("jwt_token", token)
                .path("/")
                .sameSite("Strict")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private boolean isAnotherUser(User existing, User current) {
        return existing != null && !existing.getId().equals(current.getId());
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
