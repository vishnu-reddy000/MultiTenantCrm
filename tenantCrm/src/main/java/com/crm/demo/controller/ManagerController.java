package com.crm.demo.controller;
import org.springframework.web.bind.annotation.RequestParam;
import com.crm.demo.model.Attendance;
import com.crm.demo.model.Project;
import com.crm.demo.model.Task;
import com.crm.demo.model.Team;
import com.crm.demo.model.User;
import com.crm.demo.repository.AttendanceRepository;
import com.crm.demo.repository.ProjectRepository;
import com.crm.demo.repository.TaskRepository;
import com.crm.demo.repository.TeamRepository;
import com.crm.demo.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/manager")
public class ManagerController {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private TaskRepository taskRepository;

	@Autowired
	private AttendanceRepository attendanceRepository;

	@Autowired
	private TeamRepository teamRepository;

	@Autowired
	private BCryptPasswordEncoder passwordEncoder;

	// =========================
	// COMMON STATS METHOD
	// =========================
	private void injectStats(Model model) {

		// Logged-in manager username
		String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();

		User manager = userRepository.findByUsername(currentUsername);

		// Safety check
		if (manager == null) {
			model.addAttribute("managerName", "Manager");
			model.addAttribute("managerEmail", "");
			model.addAttribute("teamMembers", Collections.emptyList());
			model.addAttribute("teamCount", 0);
			model.addAttribute("activeTeam", 0);
			model.addAttribute("inactiveTeam", 0);
			model.addAttribute("myTeam", null);
			model.addAttribute("myTeamName", "—");
			model.addAttribute("projects", Collections.emptyList());
			model.addAttribute("totalProjects", 0);
			model.addAttribute("activeProjects", 0);
			model.addAttribute("completedProjects", 0);
			model.addAttribute("projectCount", 0);
			model.addAttribute("tasks", Collections.emptyList());
			model.addAttribute("totalTasks", 0);
			model.addAttribute("doneTasks", 0);
			model.addAttribute("pendingTaskCount", 0);
			model.addAttribute("taskCount", 0);
			model.addAttribute("overdueTasks", 0);
			model.addAttribute("notificationCount", 0);
			model.addAttribute("pendingTaskList", Collections.emptyList());
			return;
		}

		model.addAttribute("managerName", manager.getUsername());
		model.addAttribute("managerEmail", manager.getEmail());

		// ── Load team assigned to this manager ────────────────────────────
		Team myTeam = teamRepository.findByManagerWithMembers(manager).orElse(null);
		List<User> teamMembers = myTeam != null ? myTeam.getMembers() : Collections.emptyList();

		long active   = teamMembers.stream().filter(User::isActive).count();
		long inactive = teamMembers.size() - active;

		model.addAttribute("myTeam",      myTeam);
		model.addAttribute("myTeamName",  myTeam != null ? myTeam.getName() : "No Team Assigned");
		model.addAttribute("teamMembers", teamMembers);
		model.addAttribute("teamCount",   teamMembers.size());
		model.addAttribute("activeTeam",  active);
		model.addAttribute("inactiveTeam",inactive);

		// ── Projects & Tasks ──────────────────────────────────────────────
		List<Project> projects = projectRepository.findAll();
		List<Task>    tasks    = taskRepository.findAll();

		long done    = tasks.stream().filter(t -> "done".equalsIgnoreCase(t.getStatus())).count();
		long pending = tasks.stream().filter(t -> "pending".equalsIgnoreCase(t.getStatus())).count();
		long activeP = projects.stream().filter(p -> "active".equalsIgnoreCase(p.getStatus())).count();
		long completedP = projects.stream().filter(p -> "completed".equalsIgnoreCase(p.getStatus())).count();

		model.addAttribute("projects",          projects);
		model.addAttribute("totalProjects",     projects.size());
		model.addAttribute("activeProjects",    activeP);
		model.addAttribute("completedProjects", completedP);
		model.addAttribute("projectCount",      projects.size());

		model.addAttribute("tasks",             tasks);
		model.addAttribute("totalTasks",        tasks.size());
		model.addAttribute("doneTasks",         done);
		model.addAttribute("pendingTaskCount",  pending);
		model.addAttribute("taskCount",         tasks.size());
		model.addAttribute("overdueTasks",      pending);
		model.addAttribute("notificationCount", 0);
		model.addAttribute("pendingTaskList",   Collections.emptyList());
	}

	// =========================
	// DASHBOARD PAGE
	// =========================
	@GetMapping("/dashboard")
	public String dashboard(Model model) {

		injectStats(model);

		return "manager-dashboard";
	}

	// =========================
	// TEAM PAGE
	// =========================
	@GetMapping("/team")
	public String teamPage(Model model) {

		injectStats(model);

		return "manager-team";
	}

	// =========================
	// PROJECTS PAGE
	// =========================
	@GetMapping("/projects")
	public String projectsPage(Model model) {

		injectStats(model);

		return "manager-projects";
	}

	// =========================
	// TASKS PAGE
	// =========================
	@GetMapping("/tasks")
	public String tasksPage(Model model) {

		injectStats(model);

		return "manager-tasks";
	}

	// =========================
	// REPORTS PAGE
	// =========================
	@GetMapping("/reports")
	public String reportsPage(Model model) {

		injectStats(model);

		return "manager-reports";
	}

	// =========================
	// CALENDAR PAGE (read-only)
	// =========================
	@GetMapping("/calendar")
	public String calendarPage(Model model) {
		injectStats(model);
		return "manager-calendar";
	}

	// =========================
	// SETTINGS PAGE
	// =========================
	@GetMapping("/settings")
	public String settingsPage(Model model) {

		injectStats(model);

		return "manager-settings";
	}

	// =========================
	// UPDATE PROFILE
	// =========================
	@PostMapping("/settings/profile")
	public String updateProfile(@RequestParam String username,
								@RequestParam String email,
								@RequestParam(required = false) String password,
								@RequestParam(required = false) String confirmPassword,
								RedirectAttributes ra) {

		String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
		User manager = userRepository.findByUsername(currentUsername);

		if (manager == null) {
			return "redirect:/manager/settings";
		}

		// Check if username or email already exists (excluding current user)
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

	// ═══════════════════════════════════════════════════════════════════════
	//  ATTENDANCE PAGE
	// ═══════════════════════════════════════════════════════════════════════

	/** Helper: resolve current manager + tenant segment */
	private User getCurrentManager() {
		String username = SecurityContextHolder.getContext().getAuthentication().getName();
		return userRepository.findByUsername(username);
	}

	private String getTenantSegment(User manager) {
		if (manager == null || manager.getEmail() == null) return "";
		String email = manager.getEmail();
		try {
			return email.split("\\.")[1].split("@")[0];
		} catch (Exception e) {
			return "";
		}
	}

	@GetMapping("/attendance")
	public String attendancePage(Model model) {
		injectStats(model);

		User manager = getCurrentManager();
		if (manager == null) {
			return "redirect:/manager/dashboard";
		}

		String tenant = getTenantSegment(manager);

		// All attendance records for this tenant
		List<Attendance> records = tenant.isEmpty() ? 
			Collections.emptyList() :
			attendanceRepository.findByTenantSegmentOrderByDateDescCheckInDesc(tenant);

		// Today's record for the manager (drives punch-in / punch-out button state)
		LocalDate today = LocalDate.now();
		Optional<Attendance> todayOpt = attendanceRepository.findByUserAndDate(manager, today);

		boolean punchedIn  = todayOpt.isPresent();
		boolean punchedOut = todayOpt.map(a -> a.getCheckOut() != null).orElse(false);

		// Stats
		long presentToday = records.stream()
				.filter(a -> today.equals(a.getDate()) && "present".equalsIgnoreCase(a.getStatus()))
				.count();
		long totalToday = records.stream().filter(a -> today.equals(a.getDate())).count();

		model.addAttribute("attendanceRecords", records);
		model.addAttribute("todayRecord",       todayOpt.orElse(null));
		model.addAttribute("punchedIn",         punchedIn);
		model.addAttribute("punchedOut",        punchedOut);
		model.addAttribute("presentToday",      presentToday);
		model.addAttribute("totalToday",        totalToday);
		model.addAttribute("totalRecords",      records.size());

		return "manager-attendance";
	}

	/** Punch In */
	@PostMapping("/attendance/punch-in")
	public String punchIn(RedirectAttributes ra) {
		User manager = getCurrentManager();
		if (manager == null) return "redirect:/manager/attendance";

		String tenant = getTenantSegment(manager);
		LocalDate today = LocalDate.now();

		// Prevent duplicate punch-in
		if (attendanceRepository.findByUserAndDate(manager, today).isPresent()) {
			ra.addFlashAttribute("errorMessage", "You have already punched in today.");
			return "redirect:/manager/attendance";
		}

		LocalTime now = LocalTime.now();
		// Mark "late" if after 09:30
		String status = now.isAfter(LocalTime.of(9, 30)) ? "late" : "present";

		Attendance att = new Attendance();
		att.setUser(manager);
		att.setDate(today);
		att.setCheckIn(now);
		att.setStatus(status);
		att.setTenantSegment(tenant);
		attendanceRepository.save(att);

		ra.addFlashAttribute("successMessage",
				"Punched in at " + String.format("%02d:%02d", now.getHour(), now.getMinute()) + ".");
		return "redirect:/manager/attendance";
	}

	/** Punch Out */
	@PostMapping("/attendance/punch-out")
	public String punchOut(RedirectAttributes ra) {
		User manager = getCurrentManager();
		if (manager == null) return "redirect:/manager/attendance";

		LocalDate today = LocalDate.now();
		Optional<Attendance> opt = attendanceRepository.findByUserAndDate(manager, today);

		if (opt.isEmpty()) {
			ra.addFlashAttribute("errorMessage", "You haven't punched in today.");
			return "redirect:/manager/attendance";
		}

		Attendance att = opt.get();
		if (att.getCheckOut() != null) {
			ra.addFlashAttribute("errorMessage", "You have already punched out today.");
			return "redirect:/manager/attendance";
		}

		LocalTime now = LocalTime.now();
		att.setCheckOut(now);
		attendanceRepository.save(att);

		ra.addFlashAttribute("successMessage",
				"Punched out at " + String.format("%02d:%02d", now.getHour(), now.getMinute()) + ".");
		return "redirect:/manager/attendance";
	}
}