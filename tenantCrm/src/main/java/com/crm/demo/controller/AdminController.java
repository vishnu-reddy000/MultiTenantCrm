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
@RequestMapping("/admin")
public class AdminController {

    @Autowired private UserRepository    userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private TaskRepository    taskRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;

    // ── Shared helper: inject logged-in user info ─────────────────────────
    private void injectUser(HttpServletRequest request, Model model) {
        String username = (String) request.getAttribute("loggedInUser");
        String role     = (String) request.getAttribute("loggedInRole");
        model.addAttribute("adminName", username != null ? username : "Admin");
        model.addAttribute("adminRole", role     != null ? role     : "ADMIN");
    }

    /**
     * Derive the tenant segment from the logged-in admin's email.
     *
     * Email format:  <prefix>.<tenant>@<domain>
     * Example:       admin.tcs@crm.com  →  "tcs"
     *                admin.amazon@crm.com → "amazon"
     *
     * The segment is the part between the last dot before '@' and the '@'.
     * Falls back to the full local-part if no dot is found.
     *
     * SUPER_ADMIN (whose email has no tenant segment) gets null → sees everyone.
     */
    private String getTenantSegment(HttpServletRequest request) {
        String username = (String) request.getAttribute("loggedInUser");
        String role     = (String) request.getAttribute("loggedInRole");

        // Super-admin sees all tenants
        if ("SUPER_ADMIN".equalsIgnoreCase(role)) return null;

        if (username == null) return null;
        User admin = userRepository.findByUsername(username);
        if (admin == null || admin.getEmail() == null) return null;

        String email     = admin.getEmail();                  // e.g. admin.tcs@crm.com
        String localPart = email.contains("@")
                           ? email.substring(0, email.indexOf('@'))
                           : email;                           // e.g. admin.tcs
        int lastDot = localPart.lastIndexOf('.');
        return lastDot >= 0
               ? localPart.substring(lastDot + 1)            // e.g. "tcs"
               : localPart;
    }

    /** Return tenant-scoped user list (or all users for SUPER_ADMIN). */
    private List<User> getTenantUsers(HttpServletRequest request) {
        String segment = getTenantSegment(request);
        return segment != null
               ? userRepository.findByTenantSegment(segment)
               : userRepository.findAll();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  DASHBOARD
    // ═══════════════════════════════════════════════════════════════════════
    @GetMapping("/dashboard")
    public String dashboard(HttpServletRequest request, Model model) {
        injectUser(request, model);

        String username = (String) request.getAttribute("loggedInUser");
        List<User> allUsers = getTenantUsers(request);
        List<Project> allProjects = projectRepository.findAll();
        List<Task> allTasks = taskRepository.findAll();

        long employees = allUsers.stream().filter(u -> "EMPLOYEE".equalsIgnoreCase(u.getRole())).count();
        long activeProj = allProjects.stream().filter(p -> "active".equalsIgnoreCase(p.getStatus())).count();
        long doneTasks  = allTasks.stream().filter(t -> "done".equalsIgnoreCase(t.getStatus())).count();
        long overdue    = allTasks.stream().filter(t -> "pending".equalsIgnoreCase(t.getStatus())).count();

        model.addAttribute("pageTitle",    "CRM — Admin Dashboard");
        model.addAttribute("pageHeading",  "Dashboard");
        model.addAttribute("pageSubtitle", "Welcome back, " + (username != null ? username : "Admin") + "!");
        model.addAttribute("activePage",   "dashboard");

        model.addAttribute("totalEmployees", employees);
        model.addAttribute("employeeGrowth", "+0%");
        model.addAttribute("activeProjects",  activeProj);
        model.addAttribute("projectGrowth",  "+0%");
        model.addAttribute("tasksDone",       doneTasks);
        model.addAttribute("taskGrowth",     "+0%");
        model.addAttribute("overdueTasks",    overdue);
        model.addAttribute("overdueChange",  "0%");

        model.addAttribute("employeeCount", employees);
        model.addAttribute("projectCount",  allProjects.size());
        model.addAttribute("pendingTasks",  overdue);

        model.addAttribute("notificationCount", 0);
        model.addAttribute("recentActivities", Collections.emptyList());
        return "admin-dashboard";
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  EMPLOYEES
    // ═══════════════════════════════════════════════════════════════════════
    @GetMapping("/employees")
    public String employeesPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        List<User> employees = getTenantUsers(request).stream()
                .filter(u -> "EMPLOYEE".equalsIgnoreCase(u.getRole())).toList();
        long active   = employees.stream().filter(User::isActive).count();
        long inactive = employees.size() - active;

        model.addAttribute("pageTitle",   "CRM — Employees");
        model.addAttribute("pageHeading", "Employees");
        model.addAttribute("activePage",  "employees");
        model.addAttribute("employees",        employees);
        model.addAttribute("totalEmployees",   employees.size());
        model.addAttribute("activeEmployees",  active);
        model.addAttribute("inactiveEmployees",inactive);
        return "add-users";
    }

    @PostMapping("/employees")
    public String addEmployee(@RequestParam String username,
                              @RequestParam String email,
                              @RequestParam String password,
                              @RequestParam String confirmPassword,
                              HttpServletRequest request,
                              RedirectAttributes ra) {
        if (!password.equals(confirmPassword)) {
            ra.addFlashAttribute("errorMessage", "Passwords do not match.");
            ra.addFlashAttribute("showAddForm", true);
            return "redirect:/admin/employees";
        }
        if (userRepository.findByUsernameOrEmail(username, email) != null) {
            ra.addFlashAttribute("errorMessage", "Username or email already exists.");
            ra.addFlashAttribute("showAddForm", true);
            return "redirect:/admin/employees";
        }
        User emp = new User();
        emp.setUsername(username);
        emp.setEmail(email);
        emp.setPassword(passwordEncoder.encode(password));
        emp.setRole("EMPLOYEE");
        emp.setStatus("active");
        userRepository.save(emp);
        ra.addFlashAttribute("successMessage", "Employee '" + username + "' added successfully.");
        return "redirect:/admin/employees";
    }

    @PostMapping("/employees/toggle/{id}")
    public String toggleEmployee(@PathVariable Long id, RedirectAttributes ra) {
        User emp = userRepository.findById(id).orElse(null);
        if (emp != null && "EMPLOYEE".equalsIgnoreCase(emp.getRole())) {
            String newStatus = "active".equalsIgnoreCase(emp.getStatus()) ? "inactive" : "active";
            emp.setStatus(newStatus);
            userRepository.save(emp);
            ra.addFlashAttribute("successMessage", "Employee '" + emp.getUsername() + "' is now " + newStatus + ".");
        }
        return "redirect:/admin/employees";
    }

    @PostMapping("/employees/delete/{id}")
    public String deleteEmployee(@PathVariable Long id, RedirectAttributes ra) {
        User emp = userRepository.findById(id).orElse(null);
        if (emp != null && "EMPLOYEE".equalsIgnoreCase(emp.getRole())) {
            String name = emp.getUsername();
            userRepository.delete(emp);
            ra.addFlashAttribute("successMessage", "Employee '" + name + "' deleted.");
        }
        return "redirect:/admin/employees";
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  PROJECTS
    // ═══════════════════════════════════════════════════════════════════════
    @GetMapping("/projects")
    public String projectsPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        List<Project> projects = projectRepository.findAll();
        long active    = projects.stream().filter(p -> "active".equalsIgnoreCase(p.getStatus())).count();
        long completed = projects.stream().filter(p -> "completed".equalsIgnoreCase(p.getStatus())).count();

        model.addAttribute("pageTitle",   "CRM — Projects");
        model.addAttribute("pageHeading", "Projects");
        model.addAttribute("activePage",  "projects");
        model.addAttribute("projects",          projects);
        model.addAttribute("totalProjects",     projects.size());
        model.addAttribute("activeProjects",    active);
        model.addAttribute("completedProjects", completed);
        return "admin-projects";
    }

    @PostMapping("/projects")
    public String addProject(@RequestParam String name,
                             @RequestParam(required = false) String description,
                             @RequestParam String status,
                             RedirectAttributes ra) {
        Project p = new Project();
        p.setName(name);
        p.setDescription(description);
        p.setStatus(status);
        projectRepository.save(p);
        ra.addFlashAttribute("successMessage", "Project '" + name + "' created.");
        return "redirect:/admin/projects";
    }

    @PostMapping("/projects/delete/{id}")
    public String deleteProject(@PathVariable Long id, RedirectAttributes ra) {
        projectRepository.findById(id).ifPresent(p -> {
            ra.addFlashAttribute("successMessage", "Project '" + p.getName() + "' deleted.");
            projectRepository.delete(p);
        });
        return "redirect:/admin/projects";
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  TASKS
    // ═══════════════════════════════════════════════════════════════════════
    @GetMapping("/tasks")
    public String tasksPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        List<Task> tasks = taskRepository.findAll();
        long done    = tasks.stream().filter(t -> "done".equalsIgnoreCase(t.getStatus())).count();
        long pending = tasks.stream().filter(t -> "pending".equalsIgnoreCase(t.getStatus())).count();

        model.addAttribute("pageTitle",   "CRM — Tasks");
        model.addAttribute("pageHeading", "Tasks");
        model.addAttribute("activePage",  "tasks");
        model.addAttribute("tasks",          tasks);
        model.addAttribute("totalTasks",     tasks.size());
        model.addAttribute("doneTasks",      done);
        model.addAttribute("pendingTaskCount", pending);
        return "admin-tasks";
    }

    @PostMapping("/tasks")
    public String addTask(@RequestParam String title,
                          @RequestParam(required = false) String description,
                          @RequestParam String status,
                          @RequestParam String priority,
                          @RequestParam(required = false) String dueDate,
                          RedirectAttributes ra) {
        Task t = new Task();
        t.setTitle(title);
        t.setDescription(description);
        t.setStatus(status);
        t.setPriority(priority);
        t.setDueDate(dueDate != null && !dueDate.isBlank() ? dueDate : null);
        taskRepository.save(t);
        ra.addFlashAttribute("successMessage", "Task '" + title + "' created.");
        return "redirect:/admin/tasks";
    }

    @PostMapping("/tasks/delete/{id}")
    public String deleteTask(@PathVariable Long id, RedirectAttributes ra) {
        taskRepository.findById(id).ifPresent(t -> {
            ra.addFlashAttribute("successMessage", "Task '" + t.getTitle() + "' deleted.");
            taskRepository.delete(t);
        });
        return "redirect:/admin/tasks";
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  REPORTS
    // ═══════════════════════════════════════════════════════════════════════
    @GetMapping("/reports")
    public String reportsPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        List<User> allUsers = getTenantUsers(request);
        List<Project> allProjects = projectRepository.findAll();
        List<Task> allTasks = taskRepository.findAll();

        model.addAttribute("pageTitle",   "CRM — Reports");
        model.addAttribute("pageHeading", "Reports");
        model.addAttribute("activePage",  "reports");

        model.addAttribute("reportUsers",    allUsers);
        model.addAttribute("reportEmployees",allUsers.stream().filter(u -> "EMPLOYEE".equalsIgnoreCase(u.getRole())).count());
        model.addAttribute("reportProjects", allProjects.size());
        model.addAttribute("reportTasks",    allTasks.size());

        model.addAttribute("roleAdmin",    allUsers.stream().filter(u -> "ADMIN".equalsIgnoreCase(u.getRole())).count());
        model.addAttribute("roleEmployee", allUsers.stream().filter(u -> "EMPLOYEE".equalsIgnoreCase(u.getRole())).count());
        model.addAttribute("roleManager",  allUsers.stream().filter(u -> "MANAGER".equalsIgnoreCase(u.getRole())).count());
        model.addAttribute("roleHr",       allUsers.stream().filter(u -> "HR".equalsIgnoreCase(u.getRole())).count());
        return "admin-reports";
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  ADD USER (existing page — now part of sidebar)
    // ═══════════════════════════════════════════════════════════════════════
    @GetMapping("/add-user")
    public String addUserPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        List<User> users = getTenantUsers(request);
        model.addAttribute("managers",      users);
        model.addAttribute("totalManagers", users.size());
        model.addAttribute("activeCount",   users.stream().filter(u -> "active".equalsIgnoreCase(u.getStatus()) || u.getStatus() == null).count());
        model.addAttribute("roleCount",     users.stream().map(User::getRole).filter(r -> r != null && !r.isBlank()).distinct().count());
        return "add-users";
    }

    @PostMapping("/add-user")
    public String addUser(@RequestParam String email,
                          @RequestParam String username,
                          @RequestParam String password,
                          @RequestParam String confirmPassword,
                          @RequestParam String role,
                          HttpServletRequest request,
                          RedirectAttributes ra) {
        if (!password.equals(confirmPassword)) {
            ra.addFlashAttribute("errorMessage", "Passwords do not match.");
            return "redirect:/admin/add-user";
        }

        // Enforce tenant domain: new user's email must contain the admin's tenant segment
        String segment = getTenantSegment(request);
        if (segment != null && !email.contains("." + segment + "@")) {
            ra.addFlashAttribute("errorMessage",
                    "Email must belong to your tenant domain (e.g. user." + segment + "@crm.com).");
            return "redirect:/admin/add-user";
        }

        if (userRepository.findByUsernameOrEmail(username, email) != null) {
            ra.addFlashAttribute("errorMessage", "Username or email already exists.");
            return "redirect:/admin/add-user";
        }
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        user.setStatus("active");
        userRepository.save(user);
        ra.addFlashAttribute("successMessage", "User '" + username + "' added successfully.");
        return "redirect:/admin/add-user";
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  SETTINGS
    // ═══════════════════════════════════════════════════════════════════════
    @GetMapping("/settings")
    public String settingsPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User admin = userRepository.findByUsername(currentUsername);

        model.addAttribute("pageTitle",   "CRM — Settings");
        model.addAttribute("pageHeading", "Settings");
        model.addAttribute("activePage",  "settings");
        model.addAttribute("adminEmail",  admin != null ? admin.getEmail() : "");
        model.addAttribute("settingsTotalUsers", getTenantUsers(request).size());
        return "admin-settings";
    }

    @PostMapping("/settings/profile")
    public String updateProfile(@RequestParam String username,
                                @RequestParam String email,
                                @RequestParam(required = false) String password,
                                @RequestParam(required = false) String confirmPassword,
                                RedirectAttributes ra) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User admin = userRepository.findByUsername(currentUsername);
        if (admin == null) return "redirect:/admin/settings";

        // Check duplicate (excluding self)
        User existing = userRepository.findByUsernameOrEmail(username, email);
        if (existing != null && !existing.getId().equals(admin.getId())) {
            ra.addFlashAttribute("errorMessage", "Username or email already in use.");
            return "redirect:/admin/settings";
        }

        admin.setUsername(username);
        admin.setEmail(email);
        if (password != null && !password.isBlank()) {
            if (!password.equals(confirmPassword)) {
                ra.addFlashAttribute("errorMessage", "Passwords do not match.");
                return "redirect:/admin/settings";
            }
            admin.setPassword(passwordEncoder.encode(password));
        }
        userRepository.save(admin);
        ra.addFlashAttribute("successMessage", "Profile updated successfully.");
        return "redirect:/admin/settings";
    }
}
