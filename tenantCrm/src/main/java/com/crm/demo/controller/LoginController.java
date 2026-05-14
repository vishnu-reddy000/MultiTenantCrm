package com.crm.demo.controller;

import org.springframework.beans.factory.annotation.Autowired;
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

	@GetMapping("/login")
	public String loginPage() {
		return "login";
	}

	// Login Process
	@PostMapping("/login")
	public String loginUser(@RequestParam String username, @RequestParam String password, Model model) {

		User user = userRepository.findByUsername(username);

		if (user != null && user.getPassword().equals(password)) {

			return "superadmin";
		}

		model.addAttribute("error", "Invalid Username or Password");

		return "login";
	}
}