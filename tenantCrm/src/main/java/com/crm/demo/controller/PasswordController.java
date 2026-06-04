package com.crm.demo.controller;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.crm.demo.model.PasswordResetToken;
import com.crm.demo.model.User;
import com.crm.demo.repository.PasswordResetTokenRepository;
import com.crm.demo.repository.UserRepository;

@Controller
public class PasswordController {

	@Autowired
	private JavaMailSender mailSender;

	@Autowired
	private PasswordResetTokenRepository tokenRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	// Forgot Password Page
	@GetMapping("/forgot-password")
	public String forgotPasswordPage() {
		return "forgot-password";
	}

	// Process Forgot Password
	@PostMapping("/forgot-password")
	public String processForgotPassword(@RequestParam String email, Model model) {

		User user = userRepository.findByEmail(email);

		// Check if user exists
		if (user == null) {
			model.addAttribute("error", "User not found");
			return "forgot-password";
		}

		// Generate token
		String token = UUID.randomUUID().toString();

		// Delete old token if exists
		Optional<PasswordResetToken> existingToken = tokenRepository.findByUser(user);

		existingToken.ifPresent(tokenRepository::delete);

		// Create new token
		PasswordResetToken resetToken = new PasswordResetToken();

		resetToken.setToken(token);
		resetToken.setUser(user);
		resetToken.setExpiryTime(LocalDateTime.now().plusMinutes(10));

		tokenRepository.save(resetToken);

		// Reset link
		String resetLink = "http://localhost:8080/reset-password?token=" + token;

		// Send email
		SimpleMailMessage message = new SimpleMailMessage();

		message.setFrom("CRM ADMIN <vishnumatamala@gmail.com>");
		message.setTo(user.getEmail());
		message.setSubject("Password Reset Request");

		message.setText("Hello " + user.getUsername() + ",\n\n" + "Click the below link to reset your password:\n\n"
				+ resetLink + "\n\nThis link will expire in 10 minutes.");

		mailSender.send(message);

		model.addAttribute("message", "Password reset link sent to your email");

		return "forgot-password";
	}

	// Reset Password Page
	@GetMapping("/reset-password")
	public String resetPasswordPage(@RequestParam String token, Model model) {

		Optional<PasswordResetToken> opt = tokenRepository.findByToken(token);
		if (opt.isEmpty()) {
			model.addAttribute("error", "Invalid or expired reset link. Please request a new one.");
			return "forgot-password";
		}

		PasswordResetToken resetToken = opt.get();

		// Check token expiry
		if (resetToken.getExpiryTime().isBefore(LocalDateTime.now())) {
			tokenRepository.delete(resetToken);
			model.addAttribute("error", "Reset link has expired. Please request a new one.");
			return "forgot-password";
		}

		model.addAttribute("token", token);

		return "reset-password";
	}

	// Reset Password Logic
	@PostMapping("/reset-password")
	public String resetPassword(@RequestParam String token, @RequestParam String password,
			@RequestParam String confirmPassword, Model model) {

		// Password match validation
		if (!password.equals(confirmPassword)) {

			model.addAttribute("error", "Passwords do not match");

			model.addAttribute("token", token);

			return "reset-password";
		}

		Optional<PasswordResetToken> opt = tokenRepository.findByToken(token);
		if (opt.isEmpty()) {
			model.addAttribute("error", "Invalid or expired reset link. Please request a new one.");
			return "forgot-password";
		}

		PasswordResetToken resetToken = opt.get();

		// Check expiry again
		if (resetToken.getExpiryTime().isBefore(LocalDateTime.now())) {
			tokenRepository.delete(resetToken);
			model.addAttribute("error", "Reset link has expired. Please request a new one.");
			return "forgot-password";
		}

		User user = resetToken.getUser();

		// Encode password
		String encodedPassword = passwordEncoder.encode(password);

		user.setPassword(encodedPassword);

		userRepository.save(user);

		// Delete token after reset
		tokenRepository.delete(resetToken);

		return "redirect:/login";
	}
}