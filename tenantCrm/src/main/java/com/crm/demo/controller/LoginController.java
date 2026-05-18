package com.crm.demo.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.crm.demo.model.User;
import com.crm.demo.repository.UserRepository;

@Controller
public class LoginController {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private BCryptPasswordEncoder passwordEncoder;

	// ─── LOGIN PAGE
	// ───────────────────────────────────────────────────────────────
	@GetMapping("/login")
	public String loginPage() {
		return "login";
	}

	// ─── LOGIN PROCESS
	// ────────────────────────────────────────────────────────────
	@PostMapping("/login")
	public String loginUser(@RequestParam String username, @RequestParam String password, Model model) {

		// Find user by username OR email
		User user = userRepository.findByUsernameOrEmail(username, username);

		// Verify user exists and BCrypt hash matches the submitted password
		if (user != null && passwordEncoder.matches(password, user.getPassword())) {

			String role = user.getRole();

			if ("SUPER_ADMIN".equalsIgnoreCase(role)) {
				return "redirect:/superadmin";
			} else if ("ADMIN".equalsIgnoreCase(role)) {
				return "redirect:/admin/dashboard";
			} else if ("MANAGER".equalsIgnoreCase(role)) {
				return "redirect:/manager/dashboard";
			} else if ("HR".equalsIgnoreCase(role)) {
				return "redirect:/hr/dashboard";
			} else if ("EMPLOYEE".equalsIgnoreCase(role)) {
				return "redirect:/employee/dashboard";
			} else {
				model.addAttribute("error", "You do not have permission to access this panel.");
				return "login";
			}
		}

		// Wrong credentials
		model.addAttribute("error", "Invalid Username or Password");
		return "login";
	}

	// ─── LOGOUT
	// ───────────────────────────────────────────────────────────────────
	@GetMapping("/logout")
	public String logout() {
		return "redirect:/login";
	}
}
