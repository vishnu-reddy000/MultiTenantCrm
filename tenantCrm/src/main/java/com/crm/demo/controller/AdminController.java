package com.crm.demo.controller;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.crm.demo.model.Meeting;
import com.crm.demo.model.Project;
import com.crm.demo.model.Task;
import com.crm.demo.model.User;
import com.crm.demo.repository.MeetingRepository;
import com.crm.demo.repository.ProjectRepository;
import com.crm.demo.repository.TaskRepository;
import com.crm.demo.repository.UserRepository;
import com.crm.demo.service.ProfileUpdateService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/admin")
public class AdminController {

	@Autowired
	private MeetingRepository meetingRepository;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private TaskRepository taskRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private BCryptPasswordEncoder passwordEncoder;

	@Autowired
	private ProfileUpdateService profileUpdateService;

	// =========================================================
	// COMMON USER DETAILS
	// =========================================================

	private void injectUser(HttpServletRequest request, Model model) {
		String username = (String) request.getAttribute("loggedInUser");
		String role     = (String) request.getAttribute("loggedInRole");
		model.addAttribute("adminName", username != null ? username : "Admin");
		model.addAttribute("adminRole", role     != null ? role     : "ADMIN");
	}

	/** Resolve the current admin's tenant segment from their email. */
	private String getTenantSegment(String username) {
		if (username == null) return "";
		User user = userRepository.findByUsername(username);
		if (user == null || user.getEmail() == null) return "";
		String email = user.getEmail();
		try {
			String local = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
			int dot = local.lastIndexOf('.');
			return dot >= 0 ? local.substring(dot + 1) : local;
		} catch (Exception e) {
			return "";
		}
	}

	// =========================================================
	// DASHBOARD
	// =========================================================

	@GetMapping("/dashboard")
	public String dashboard(HttpServletRequest request, Model model) {
		injectUser(request, model);

		String username = (String) request.getAttribute("loggedInUser");
		String tenant   = getTenantSegment(username);

		List<User>    employees = userRepository.findEmployeesByTenant(tenant);
		List<Project> projects  = projectRepository.findAll();
		List<Task>    tasks     = taskRepository.findAll();

		long activeProjects = projects.stream().filter(p -> "active".equalsIgnoreCase(p.getStatus())).count();
		long tasksDone      = tasks.stream().filter(t -> "done".equalsIgnoreCase(t.getStatus())).count();
		long pendingTasks   = tasks.stream().filter(t -> "pending".equalsIgnoreCase(t.getStatus())).count();

		model.addAttribute("totalEmployees",  employees.size());
		model.addAttribute("employeeCount",   employees.size());
		model.addAttribute("employeeGrowth",  "+0%");
		model.addAttribute("activeProjects",  activeProjects);
		model.addAttribute("projectCount",    activeProjects);
		model.addAttribute("projectGrowth",   "+0%");
		model.addAttribute("tasksDone",       tasksDone);
		model.addAttribute("taskGrowth",      "+0%");
		model.addAttribute("overdueTasks",    pendingTasks);
		model.addAttribute("overdueChange",   "0%");
		model.addAttribute("pendingTasks",    pendingTasks);
		model.addAttribute("recentActivities", java.util.Collections.emptyList());

		return "admin-dashboard";
	}

	// =========================================================
	// EMPLOYEES — LIST PAGE  (sidebar "Employees" lands here)
	// =========================================================

	@GetMapping("/employees")
	public String employeesPage(HttpServletRequest request, Model model) {
		injectUser(request, model);

		String username = (String) request.getAttribute("loggedInUser");
		String tenant   = getTenantSegment(username);

		List<User> users = userRepository.findByTenantSegment(tenant).stream()
				.filter(u -> !"ADMIN".equalsIgnoreCase(u.getRole())
						  && !"SUPER_ADMIN".equalsIgnoreCase(u.getRole()))
				.toList();

		long active   = users.stream().filter(User::isActive).count();
		long inactive = users.size() - active;

		model.addAttribute("managers",          users);
		model.addAttribute("totalManagers",     users.size());
		model.addAttribute("totalEmployees",    users.size());
		model.addAttribute("activeEmployees",   active);
		model.addAttribute("inactiveEmployees", inactive);
		return "add-users";
	}

	// =========================================================
	// EMPLOYEES — ADD FORM PAGE
	// =========================================================

	@GetMapping("/add-employee")
	public String addEmployeePage(HttpServletRequest request, Model model) {
		injectUser(request, model);
		return "admin-add-employee";
	}

	// =========================================================
	// EMPLOYEES — ADD USER (POST)
	// =========================================================

	@GetMapping("/add-user")
	public String addUserRedirect() {
		return "redirect:/admin/employees";
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
			return "redirect:/admin/add-employee";
		}

		if (userRepository.findByEmail(email) != null) {
			ra.addFlashAttribute("errorMessage", "Email already in use.");
			return "redirect:/admin/add-employee";
		}

		if (userRepository.findByUsername(username) != null) {
			ra.addFlashAttribute("errorMessage", "Username already in use.");
			return "redirect:/admin/add-employee";
		}

		User user = new User();
		user.setEmail(email);
		user.setUsername(username);
		user.setPassword(passwordEncoder.encode(password));
		user.setRole(role.toUpperCase());
		user.setStatus("active");
		userRepository.save(user);

		ra.addFlashAttribute("successMessage", "Employee added successfully.");
		return "redirect:/admin/employees";
	}

	// =========================================================
	// EMPLOYEES — TOGGLE STATUS
	// =========================================================

	@PostMapping("/toggle-user/{id}")
	public String toggleUser(@PathVariable Long id, RedirectAttributes ra) {
		User user = userRepository.findById(id).orElse(null);
		if (user != null
				&& !"ADMIN".equalsIgnoreCase(user.getRole())
				&& !"SUPER_ADMIN".equalsIgnoreCase(user.getRole())) {
			String newStatus = "active".equalsIgnoreCase(user.getStatus()) ? "inactive" : "active";
			user.setStatus(newStatus);
			userRepository.save(user);
			ra.addFlashAttribute("successMessage", user.getUsername() + " is now " + newStatus + ".");
		}
		return "redirect:/admin/employees";
	}

	// =========================================================
	// EMPLOYEES — DELETE
	// =========================================================

	@PostMapping("/delete-employee/{id}")
	public String deleteEmployee(@PathVariable Long id, RedirectAttributes ra) {
		User user = userRepository.findById(id).orElse(null);
		if (user != null
				&& !"ADMIN".equalsIgnoreCase(user.getRole())
				&& !"SUPER_ADMIN".equalsIgnoreCase(user.getRole())) {
			String name = user.getUsername();
			userRepository.delete(user);
			ra.addFlashAttribute("successMessage", "Employee '" + name + "' deleted.");
		}
		return "redirect:/admin/employees";
	}

	// =========================================================
	// EMPLOYEES — EDIT
	// =========================================================

	@GetMapping("/edit-employee/{id}")
	public String editEmployeePage(@PathVariable Long id, HttpServletRequest request, Model model,
	                               RedirectAttributes ra) {
		injectUser(request, model);
		User emp = userRepository.findById(id).orElse(null);
		if (emp == null
				|| "ADMIN".equalsIgnoreCase(emp.getRole())
				|| "SUPER_ADMIN".equalsIgnoreCase(emp.getRole())) {
			ra.addFlashAttribute("errorMessage", "User not found or cannot be edited.");
			return "redirect:/admin/employees";
		}
		model.addAttribute("employee", emp);
		return "edit-employee";
	}

	@PostMapping("/edit-employee/{id}")
	public String updateEmployee(@PathVariable Long id,
	                             @RequestParam String username,
	                             @RequestParam String email,
	                             @RequestParam String role,
	                             @RequestParam(required = false) String password,
	                             @RequestParam(required = false) String confirmPassword,
	                             HttpServletRequest request,
	                             RedirectAttributes ra) {
		User emp = userRepository.findById(id).orElse(null);
		if (emp == null
				|| "ADMIN".equalsIgnoreCase(emp.getRole())
				|| "SUPER_ADMIN".equalsIgnoreCase(emp.getRole())) {
			ra.addFlashAttribute("errorMessage", "User not found or cannot be edited.");
			return "redirect:/admin/employees";
		}
		// Block promoting to ADMIN / SUPER_ADMIN
		if ("ADMIN".equalsIgnoreCase(role) || "SUPER_ADMIN".equalsIgnoreCase(role)) {
			ra.addFlashAttribute("errorMessage", "You cannot assign that role.");
			return "redirect:/admin/edit-employee/" + id;
		}
		// Check duplicate (excluding self)
		User existing = userRepository.findByUsernameOrEmail(username, email);
		if (existing != null && !existing.getId().equals(emp.getId())) {
			ra.addFlashAttribute("errorMessage", "Username or email already in use.");
			return "redirect:/admin/edit-employee/" + id;
		}
		if (password != null && !password.isBlank()) {
			if (!password.equals(confirmPassword)) {
				ra.addFlashAttribute("errorMessage", "Passwords do not match.");
				return "redirect:/admin/edit-employee/" + id;
			}
			emp.setPassword(passwordEncoder.encode(password));
		}
		emp.setUsername(username);
		emp.setEmail(email);
		emp.setRole(role);
		userRepository.save(emp);
		ra.addFlashAttribute("successMessage", "'" + username + "' updated successfully.");
		return "redirect:/admin/employees";
	}

	// =========================================================
	// PROJECTS
	// =========================================================

	@GetMapping("/projects")
	public String projectsPage(HttpServletRequest request, Model model) {
		injectUser(request, model);

		List<Project> projects = projectRepository.findAll();
		long active    = projects.stream().filter(p -> "active".equalsIgnoreCase(p.getStatus())).count();
		long completed = projects.stream().filter(p -> "completed".equalsIgnoreCase(p.getStatus())).count();

		model.addAttribute("projects",          projects);
		model.addAttribute("totalProjects",     projects.size());
		model.addAttribute("activeProjects",    active);
		model.addAttribute("completedProjects", completed);

		return "admin-projects";
	}

	@PostMapping("/projects")
	public String createProject(@RequestParam String name,
	                            @RequestParam(required = false) String description,
	                            @RequestParam String status,
	                            RedirectAttributes ra) {
		Project p = new Project();
		p.setName(name);
		p.setDescription(description);
		p.setStatus(status);
		projectRepository.save(p);
		ra.addFlashAttribute("successMessage", "Project created successfully.");
		return "redirect:/admin/projects";
	}

	@PostMapping("/projects/delete/{id}")
	public String deleteProject(@PathVariable Long id, RedirectAttributes ra) {
		projectRepository.deleteById(id);
		ra.addFlashAttribute("successMessage", "Project deleted.");
		return "redirect:/admin/projects";
	}

	// =========================================================
	// TASKS
	// =========================================================

	@GetMapping("/tasks")
	public String tasksPage(HttpServletRequest request, Model model) {
		injectUser(request, model);

		List<Task> tasks = taskRepository.findAll();
		long done    = tasks.stream().filter(t -> "done".equalsIgnoreCase(t.getStatus())).count();
		long pending = tasks.stream().filter(t -> "pending".equalsIgnoreCase(t.getStatus())).count();

		model.addAttribute("tasks",            tasks);
		model.addAttribute("totalTasks",       tasks.size());
		model.addAttribute("doneTasks",        done);
		model.addAttribute("pendingTaskCount", pending);

		return "admin-tasks";
	}

	@PostMapping("/tasks")
	public String createTask(@RequestParam String title,
	                         @RequestParam(required = false) String description,
	                         @RequestParam String priority,
	                         @RequestParam String status,
	                         @RequestParam(required = false) String dueDate,
	                         RedirectAttributes ra) {
		Task t = new Task();
		t.setTitle(title);
		t.setDescription(description);
		t.setPriority(priority);
		t.setStatus(status);
		t.setDueDate(dueDate);
		taskRepository.save(t);
		ra.addFlashAttribute("successMessage", "Task created successfully.");
		return "redirect:/admin/tasks";
	}

	@PostMapping("/tasks/delete/{id}")
	public String deleteTask(@PathVariable Long id, RedirectAttributes ra) {
		taskRepository.deleteById(id);
		ra.addFlashAttribute("successMessage", "Task deleted.");
		return "redirect:/admin/tasks";
	}

	// =========================================================
	// REPORTS
	// =========================================================

	@GetMapping("/reports")
	public String reportsPage(HttpServletRequest request, Model model) {
		injectUser(request, model);

		String username = (String) request.getAttribute("loggedInUser");
		String tenant   = getTenantSegment(username);

		List<User>    allUsers  = userRepository.findByTenantSegment(tenant);
		List<Project> projects  = projectRepository.findAll();
		List<Task>    tasks     = taskRepository.findAll();

		long roleAdmin    = allUsers.stream().filter(u -> "ADMIN".equalsIgnoreCase(u.getRole())).count();
		long roleEmployee = allUsers.stream().filter(u -> "EMPLOYEE".equalsIgnoreCase(u.getRole())).count();
		long roleManager  = allUsers.stream().filter(u -> "MANAGER".equalsIgnoreCase(u.getRole())).count();
		long roleHr       = allUsers.stream().filter(u -> "HR".equalsIgnoreCase(u.getRole())).count();

		model.addAttribute("reportUsers",     allUsers);
		model.addAttribute("reportEmployees", allUsers.size());
		model.addAttribute("reportProjects",  projects.size());
		model.addAttribute("reportTasks",     tasks.size());
		model.addAttribute("roleAdmin",       roleAdmin);
		model.addAttribute("roleEmployee",    roleEmployee);
		model.addAttribute("roleManager",     roleManager);
		model.addAttribute("roleHr",          roleHr);

		return "admin-reports";
	}

	// =========================================================
	// CALENDAR
	// =========================================================

	@GetMapping("/calendar")
	public String calendarPage(HttpServletRequest request, Model model) {
		injectUser(request, model);
		return "calendar";
	}

	// =========================================================
	// SETTINGS
	// =========================================================

	@GetMapping("/settings")
	public String settingsPage(HttpServletRequest request, Model model) {
		injectUser(request, model);

		String username = (String) request.getAttribute("loggedInUser");
		User admin = userRepository.findByUsername(username);

		model.addAttribute("adminEmail",         admin != null ? admin.getEmail() : "");
		model.addAttribute("settingsTotalUsers", userRepository.count());

		return "admin-settings";
	}

	@PostMapping("/settings/profile")
	public String updateProfile(@RequestParam(required = false) String username,
	                            @RequestParam(required = false) String email,
	                            @RequestParam(required = false) String password,
	                            @RequestParam(required = false) String confirmPassword,
	                            HttpServletResponse response,
	                            RedirectAttributes ra) {

		String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
		User admin = userRepository.findByUsername(currentUsername);

		if (admin == null) {
			return "redirect:/admin/settings";
		}

		profileUpdateService.updateProfile(admin, username, email, password, confirmPassword, ra, response);
		return "redirect:/admin/settings";
	}

	// =========================================================
	// SCHEDULE MEETING — GET PAGE
	// =========================================================

	/**
	 * Returns upcoming meetings (today + future) where the admin is a participant
	 * or the creator/host who scheduled the meeting.
	 * Excludes today's meetings that have already ended.
	 */
	private List<Meeting> getUpcomingMeetingsForUser(String tenant, String username) {
		List<Meeting> all = meetingRepository
				.findUpcomingMeetingsForUserOrHost(tenant, username, LocalDate.now());
		LocalDate today = LocalDate.now();
		LocalTime now   = LocalTime.now();
		return all.stream().filter(m -> {
			if (!m.getMeetingDate().equals(today)) return true;
			if (m.getMeetingTime() == null) return true;
			int dur = (m.getDuration() != null) ? m.getDuration() : 0;
			return !m.getMeetingTime().plusMinutes(dur).isBefore(now);
		}).toList();
	}

	private List<Meeting> getPastMeetingsForUser(String tenant, String username) {
		List<Meeting> all = meetingRepository.findAllMeetingsForUserOrHost(tenant, username);
		LocalDate today = LocalDate.now();
		LocalTime now   = LocalTime.now();
		return all.stream().filter(m -> {
			if (m.getMeetingDate() == null) return false;
			if (m.getMeetingDate().isAfter(today)) return false;
			if (m.getMeetingDate().isBefore(today)) return true;
			if (m.getMeetingTime() == null) return false;
			int dur = (m.getDuration() != null) ? m.getDuration() : 0;
			return m.getMeetingTime().plusMinutes(dur).isBefore(now);
		}).toList();
	}

	@GetMapping("/schedule-meeting")
	public String scheduleMeetingPage(HttpServletRequest request, Model model) {
		injectUser(request, model);
		String username = (String) request.getAttribute("loggedInUser");
		String tenant   = getTenantSegment(username);

		model.addAttribute("meetingForm", new Meeting());
		model.addAttribute("upcomingMeetings", getUpcomingMeetingsForUser(tenant, username != null ? username : ""));
		model.addAttribute("pastMeetings", getPastMeetingsForUser(tenant, username != null ? username : ""));
		model.addAttribute("tenantUsers",
				userRepository.findByTenantSegment(tenant).stream()
						.filter(u -> !"ADMIN".equalsIgnoreCase(u.getRole())
								  && !"SUPER_ADMIN".equalsIgnoreCase(u.getRole()))
						.toList());
		model.addAttribute("activePage", "schedule-meeting");
		return "admin-scheduleMeeting";
	}

	// =========================================================
	// SCHEDULE MEETING — SAVE
	// =========================================================

	@PostMapping("/schedule-meeting")
	public String scheduleMeeting(@Valid @ModelAttribute("meetingForm") Meeting meetingForm,
	                              BindingResult result,
	                              HttpServletRequest request,
	                              Model model,
	                              RedirectAttributes ra) {

		String username = (String) request.getAttribute("loggedInUser");
		String tenant   = getTenantSegment(username);

		if (result.hasErrors()) {
			injectUser(request, model);
			model.addAttribute("upcomingMeetings", getUpcomingMeetingsForUser(tenant, username != null ? username : ""));
			model.addAttribute("pastMeetings", getPastMeetingsForUser(tenant, username != null ? username : ""));
			model.addAttribute("tenantUsers",
					userRepository.findByTenantSegment(tenant).stream()
							.filter(u -> !"ADMIN".equalsIgnoreCase(u.getRole())
									  && !"SUPER_ADMIN".equalsIgnoreCase(u.getRole()))
							.toList());
			model.addAttribute("errorMessage", "Please fix the errors below.");
			model.addAttribute("activePage", "schedule-meeting");
			return "admin-scheduleMeeting";
		}

		meetingForm.setTenantSegment(tenant);		meetingForm.setScheduledBy(username != null ? username : "");		meetingRepository.save(meetingForm);
		ra.addFlashAttribute("successMessage", "Meeting scheduled successfully.");
		return "redirect:/admin/schedule-meeting";
	}

	// =========================================================
	// SCHEDULE MEETING — EDIT PAGE
	// =========================================================

	@GetMapping("/schedule-meeting/edit/{id}")
	public String editMeetingPage(@PathVariable Long id,
	                              HttpServletRequest request,
	                              Model model,
	                              RedirectAttributes ra) {

		injectUser(request, model);
		String username = (String) request.getAttribute("loggedInUser");
		String tenant   = getTenantSegment(username);

		Meeting meeting = meetingRepository.findById(id).orElse(null);
		if (meeting == null) {
			ra.addFlashAttribute("errorMessage", "Meeting not found.");
			return "redirect:/admin/schedule-meeting";
		}

		model.addAttribute("meetingForm", meeting);
		model.addAttribute("upcomingMeetings", getUpcomingMeetingsForUser(tenant, username != null ? username : ""));
		model.addAttribute("pastMeetings", getPastMeetingsForUser(tenant, username != null ? username : ""));
		model.addAttribute("tenantUsers",
				userRepository.findByTenantSegment(tenant).stream()
						.filter(u -> !"ADMIN".equalsIgnoreCase(u.getRole())
								  && !"SUPER_ADMIN".equalsIgnoreCase(u.getRole()))
						.toList());
		model.addAttribute("activePage", "schedule-meeting");
		return "admin-scheduleMeeting";
	}

	// =========================================================
	// SCHEDULE MEETING — UPDATE
	// =========================================================

	@PostMapping("/schedule-meeting/edit/{id}")
	public String updateMeeting(@PathVariable Long id,
	                            @Valid @ModelAttribute("meetingForm") Meeting meetingForm,
	                            BindingResult result,
	                            HttpServletRequest request,
	                            Model model,
	                            RedirectAttributes ra) {

		String username = (String) request.getAttribute("loggedInUser");
		String tenant   = getTenantSegment(username);

		if (result.hasErrors()) {
			injectUser(request, model);
			model.addAttribute("upcomingMeetings", getUpcomingMeetingsForUser(tenant, username != null ? username : ""));			model.addAttribute("pastMeetings", getPastMeetingsForUser(tenant, username != null ? username : ""));			model.addAttribute("tenantUsers",
					userRepository.findByTenantSegment(tenant).stream()
						.filter(u -> !"ADMIN".equalsIgnoreCase(u.getRole())
							&& !"SUPER_ADMIN".equalsIgnoreCase(u.getRole()))
						.toList());
			model.addAttribute("activePage", "schedule-meeting");
			return "admin-scheduleMeeting";
		}

		Meeting existing = meetingRepository.findById(id).orElse(null);
		if (existing == null) {
			ra.addFlashAttribute("errorMessage", "Meeting not found.");
			return "redirect:/admin/schedule-meeting";
		}

		existing.setTitle(meetingForm.getTitle());
		existing.setMeetingDate(meetingForm.getMeetingDate());
		existing.setMeetingTime(meetingForm.getMeetingTime());
		existing.setDuration(meetingForm.getDuration());
		existing.setMeetingType(meetingForm.getMeetingType());
		existing.setLocation(meetingForm.getLocation());
		existing.setParticipants(meetingForm.getParticipants());
		existing.setAgenda(meetingForm.getAgenda());
		existing.setSendNotification(meetingForm.isSendNotification());
		meetingRepository.save(existing);

		ra.addFlashAttribute("successMessage", "Meeting updated successfully.");
		return "redirect:/admin/schedule-meeting";
	}

	// =========================================================
	// SCHEDULE MEETING — DELETE
	// =========================================================

	@PostMapping("/schedule-meeting/delete/{id}")
	public String deleteMeeting(@PathVariable Long id, RedirectAttributes ra) {
		Meeting meeting = meetingRepository.findById(id).orElse(null);
		if (meeting == null) {
			ra.addFlashAttribute("errorMessage", "Meeting not found.");
		} else {
			meetingRepository.delete(meeting);
			ra.addFlashAttribute("successMessage", "Meeting deleted successfully.");
		}
		return "redirect:/admin/schedule-meeting";
	}
}
