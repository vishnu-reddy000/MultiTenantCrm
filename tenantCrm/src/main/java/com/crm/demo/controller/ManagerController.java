package com.crm.demo.controller;

import com.crm.demo.model.Project;
import com.crm.demo.model.Task;
import com.crm.demo.model.User;
import com.crm.demo.repository.ProjectRepository;
import com.crm.demo.repository.TaskRepository;
import com.crm.demo.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;
import java.util.List;

@Controller
@RequestMapping("/manager")
public class ManagerController {

    @Autowired private UserRepository        userRepository;
    @Autowired private ProjectRepository     projectRepository;
    @Autowired private TaskRepository        taskRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;

    private void injectUser(HttpServletRequest request, Model model) {
        String username = (String) request.getAttribute("loggedInUser");
        model.addAttribute("managerName", username != null ? username : "Manager");
    }

    private void injectStats(Model model) {
        List<User> team = userRepository.findAll().stream()
                .filter(u -> "EMPLOYEE".equalsIgnoreCase(u.getRole())).toList();
        List<Project> projects = projectRepository.findAll();
        List<Task> tasks = taskRepository.findAll();
        long active   = team.stream().filter(User::isActive).count();
        long inactive = team.size() - active;
        long done     = tasks.stream().filter(t -> "done".equalsIgnoreCase(t.getStatus())).count();
        long pending  = tasks.stream().filter(t -> "pending".equalsIgnoreCase(t.getStatus())).count();
        long activeP  = projects.stream().filter(p -> "active".equalsIgnoreCase(p.getStatus())).count();
        long completedP = projects.stream().filter(p -> "completed".equalsIgnoreCase(p.getStatus())).count();

        model.addAttribute("teamMembers",      team);
        model.addAttribute("teamCount",        team.size());
        model.addAttribute("activeTeam",       active);
        model.addAttribute("inactiveTeam",     inactive);
        model.addAttribute("projects",         projects);
        model.addAttribute("totalProjects",    projects.size());
        model.addAttribute("activeProjects",   activeP);
        model.addAttribute("completedProjects",completedP);
        model.addAttribute("projectCount",     projects.size());
        model.addAttribute("tasks",            tasks);
        model.addAttribute("totalTasks",       tasks.size());
        model.addAttribute("doneTasks",        done);
        model.addAttribute("pendingTaskCount", pending);
        model.addAttribute("taskCount",        tasks.size());
        model.addAttribute("overdueTasks",     pending);
        model.addAttribute("notificationCount", 0);
        model.addAttribute("pendingTaskList",  Collections.emptyList());
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpServletRequest request, Model model) {
        injectUser(request, model);
        injectStats(model);
        return "manager-dashboard";
    }

    @GetMapping("/team")
    public String teamPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        injectStats(model);
        return "manager-team";
    }

    @GetMapping("/projects")
    public String projectsPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        injectStats(model);
        return "manager-projects";
    }

    @GetMapping("/tasks")
    public String tasksPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        injectStats(model);
        return "manager-tasks";
    }

    @GetMapping("/reports")
    public String reportsPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        injectStats(model);
        return "manager-reports";
    }

    @GetMapping("/settings")
    public String settingsPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        injectStats(model);
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User manager = userRepository.findByUsername(currentUsername);
        model.addAttribute("managerEmail", manager != null ? manager.getEmail() : "");
        return "manager-settings";
    }

    @PostMapping("/settings/profile")
    public String updateProfile(@RequestParam String username,
                                @RequestParam String email,
                                @RequestParam(required = false) String password,
                                @RequestParam(required = false) String confirmPassword,
                                RedirectAttributes ra) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User manager = userRepository.findByUsername(currentUsername);
        if (manager == null) return "redirect:/manager/settings";

        User existing = userRepository.findByUsernameOrEmail(username, email);
        if (existing != null && !existing.getId().equals(manager.getId())) {
            ra.addFlashAttribute("errorMessage", "Username or email already in use.");
            return "redirect:/manager/settings";
        }
        manager.setUsername(username);
        manager.setEmail(email);
        if (password != null && !password.isBlank()) {
            if (!password.equals(confirmPassword)) {
                ra.addFlashAttribute("errorMessage", "Passwords do not match.");
                return "redirect:/manager/settings";
            }
            manager.setPassword(passwordEncoder.encode(password));
        }
        userRepository.save(manager);
        ra.addFlashAttribute("successMessage", "Profile updated successfully.");
        return "redirect:/manager/settings";
    }
}
