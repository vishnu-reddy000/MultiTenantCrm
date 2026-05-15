package com.crm.demo.controller;

import com.crm.demo.model.User;
import com.crm.demo.repository.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@Controller
@RequestMapping("/manager")
public class ManagerController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    // DASHBOARD
    @GetMapping("/dashboard")
    public String dashboard(Model model) {

        populateCommonModel(model);

        model.addAttribute("activePage", "dashboard");
        model.addAttribute("pageHeading", "Dashboard");
        model.addAttribute("pageSubtitle",
                "Here's what's happening with your team today.");

        model.addAttribute("pendingTaskList",
                Collections.emptyList());

        return "manager";
    }

    // ADD USER PAGE
    @GetMapping("/add")
    public String addUserPage(Model model) {

        List<User> users = userRepository.findAll();

        model.addAttribute("managers", users);
        model.addAttribute("totalManagers", users.size());

        return "add-users";
    }

    // SAVE USER
    @PostMapping("/add")
    public String addUser(

            @RequestParam String email,
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            @RequestParam String role,
            Model model

    ) {

        List<User> users = userRepository.findAll();

        model.addAttribute("managers", users);
        model.addAttribute("totalManagers", users.size());

        // PASSWORD CHECK
        if (!password.equals(confirmPassword)) {

            model.addAttribute(
                    "errorMessage",
                    "Passwords do not match."
            );

            return "add-users";
        }

        // DUPLICATE CHECK
        if (userRepository.findByUsernameOrEmail(username, email) != null) {

            model.addAttribute(
                    "errorMessage",
                    "Username or email already exists."
            );

            return "add-users";
        }

        // SAVE USER
        User user = new User();

        user.setUsername(username);
        user.setEmail(email);

        user.setPassword(
                passwordEncoder.encode(password)
        );

        user.setRole(role);

        userRepository.save(user);

        model.addAttribute(
                "successMessage",
                "User added successfully."
        );

        return "add-users";
    }

    // COMMON MODEL
    private void populateCommonModel(Model model) {

        model.addAttribute("managerName", "Manager");

        model.addAttribute("notificationCount", 0);

        model.addAttribute("teamCount", 0);

        model.addAttribute("projectCount", 0);

        model.addAttribute("taskCount", 0);

        model.addAttribute("overdueTasks", 0);

        model.addAttribute("teamGrowth", "+0%");

        model.addAttribute("projectGrowth", "+0%");

        model.addAttribute("taskGrowth", "+0%");

        model.addAttribute("overdueChange", "0%");

        model.addAttribute(
                "teamMembers",
                Collections.emptyList()
        );
    }
}