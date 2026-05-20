package com.crm.demo.controller;

import com.crm.demo.model.Attendance;
import com.crm.demo.model.Project;
import com.crm.demo.model.Task;
import com.crm.demo.model.User;
import com.crm.demo.repository.AttendanceRepository;
import com.crm.demo.repository.ProjectRepository;
import com.crm.demo.repository.TaskRepository;
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
	private BCryptPasswordEncoder passwordEncoder;

	// =========================
	// COMMON STATS METHOD
	// =========================
	private void injectStats(HttpServletRequest request, Model model) {

		// Logged-in manager username
		String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();

		User manager = userRepository.findByUsername(currentUsername);

		// Safety check
		if (manager == null) {
			return;
		}

		model.addAttribute("managerName", manager.getUsername());
		model.addAttribute("managerEmail", manager.getEmail());

		// Example:
		// manager.tcs@crm.com

		String email = manager.getEmail();

		// Extract company/tenant
		String tenantSegment = email.split("\\.")[1].split("@")[0];

		// Fetch only same company employees
		List<User> team = userRepository.findEmployeesByTenant(tenantSegment);

		List<Project> projects = projectRepository.findAll();

		List<Task> tasks = taskRepository.findAll();

		long active = team.stream().filter(User::isActive).count();

		long inactive = team.size() - active;

		long done = tasks.stream().filter(t -> "done".equalsIgnoreCase(t.getStatus())).count();

		long pending = tasks.stream().filter(t -> "pending".equalsIgnoreCase(t.getStatus())).count();

		long activeP = projects.stream().filter(p -> "active".equalsIgnoreCase(p.getStatus())).count();

		long completedP = projects.stream().filter(p -> "completed".equalsIgnoreCase(p.getStatus())).count();

		// =========================
		// MODEL ATTRIBUTES
		// =========================

		model.addAttribute("teamMembers", team);
		model.addAttribute("teamCount", team.size());
		model.addAttribute("activeTeam", active);
		model.addAttribute("inactiveTeam", inactive);

		model.addAttribute("projects", projects);
		model.addAttribute("totalProjects", projects.size());
		model.addAttribute("activeProjects", activeP);
		model.addAttribute("completedProjects", completedP);
		model.addAttribute("projectCount", projects.size());

		model.addAttribute("tasks", tasks);
		model.addAttribute("totalTasks", tasks.size());
		model.addAttribute("doneTasks", done);
		model.addAttribute("pendingTaskCount", pending);
		model.addAttribute("taskCount", tasks.size());

		model.addAttribute("overdueTasks", pending);

		model.addAttribute("notificationCount", 0);

		model.addAttribute("pendingTaskList", Collections.emptyList());
	}

	// =========================
	// DASHBOARD PAGE
	// =========================
	@GetMapping("/dashboard")
	public String dashboard(HttpServletRequest request, Model model) {

		injectStats(request, model);

		return "manager-dashboard";
	}

	// =========================
	// TEAM PAGE
	// =========================
	@GetMapping("/team")
	public String teamPage(HttpServletRequest request, Model model) {

		injectStats(request, model);

		return "manager-team";
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
	public String attendancePage(HttpServletRequest request, Model model) {
		injectStats(request, model);

		User manager = getCurrentManager();
		String tenant = getTenantSegment(manager);

		// All attendance records for this tenant
		List<Attendance> records =
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