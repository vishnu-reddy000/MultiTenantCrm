package com.crm.demo.controller;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.springframework.validation.BindingResult;

import com.crm.demo.model.Attendance;
import com.crm.demo.model.AttendanceDay;
import com.crm.demo.model.Holiday;
import com.crm.demo.model.LeaveRequest;
import com.crm.demo.model.Meeting;
import com.crm.demo.model.PerformanceReview;
import com.crm.demo.model.Project;
import com.crm.demo.model.Report;
import com.crm.demo.model.ReportAttachment;
import com.crm.demo.model.Task;
import com.crm.demo.model.TaskAttachment;
import com.crm.demo.model.Team;
import com.crm.demo.model.User;
import com.crm.demo.repository.AttendanceRepository;
import com.crm.demo.repository.HolidayRepository;
import com.crm.demo.repository.LeaveRequestRepository;
import com.crm.demo.repository.MeetingRepository;
import com.crm.demo.repository.PerformanceReviewRepository;
import com.crm.demo.repository.ProjectRepository;
import com.crm.demo.repository.ReportAttachmentRepository;
import com.crm.demo.repository.ReportRepository;
import com.crm.demo.repository.TaskRepository;
import com.crm.demo.repository.TaskAttachmentRepository;
import com.crm.demo.repository.TeamRepository;
import com.crm.demo.repository.UserRepository;
import com.crm.demo.service.NotificationService;
import com.crm.demo.service.ProfileUpdateService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/manager")
public class ManagerController {

	@Value("${app.upload.dir:uploads/tasks}")
	private String uploadDir;
	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private TaskRepository taskRepository;

	@Autowired
	private AttendanceRepository attendanceRepository;

	@Autowired
	private HolidayRepository holidayRepository;

	@Autowired
	private TeamRepository teamRepository;

	@Autowired
	private MeetingRepository meetingRepository;

	@Autowired
	private LeaveRequestRepository leaveRequestRepository;

	@Autowired
	private TaskAttachmentRepository taskAttachmentRepository;

	@Autowired
	private ReportRepository reportRepository;

	@Autowired
	private ReportAttachmentRepository reportAttachmentRepository;

	@Autowired
	private PerformanceReviewRepository performanceReviewRepository;

	@Autowired
	private BCryptPasswordEncoder passwordEncoder;

	@Autowired
	private ProfileUpdateService profileUpdateService;

	@Autowired
	private NotificationService notificationService;

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

		// ── Load team(s) assigned to this manager ────────────────────────────
		Team myTeam = getPrimaryTeam(manager);
		List<User> teamMembers = getManagedTeamMembers(manager);

		long active   = teamMembers.stream().filter(User::isActive).count();
		long inactive = teamMembers.size() - active;

		model.addAttribute("myTeam",      myTeam);
		model.addAttribute("myTeamName",  getManagedTeamName(manager));
		model.addAttribute("teamMembers", teamMembers);
		model.addAttribute("teamCount",   teamMembers.size());
		model.addAttribute("activeTeam",  active);
		model.addAttribute("inactiveTeam",inactive);

		// ── Projects & Tasks ──────────────────────────────────────────────
		List<Project> projects = projectRepository.findAll();
		// For stats only — scoped tasks loaded per-page where needed
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

	private List<Team> getManagedTeams(User manager) {
		if (manager == null) {
			return Collections.emptyList();
		}
		return teamRepository.findByManagerWithMembers(manager);
	}

	private Team getPrimaryTeam(User manager) {
		List<Team> teams = getManagedTeams(manager);
		return teams.isEmpty() ? null : teams.get(0);
	}

	private List<User> getManagedTeamMembers(User manager) {
		Map<Long, User> uniqueMembers = new LinkedHashMap<>();
		for (Team team : getManagedTeams(manager)) {
			for (User member : team.getMembers()) {
				if (member != null && member.getId() != null) {
					uniqueMembers.putIfAbsent(member.getId(), member);
				}
			}
		}
		return new ArrayList<>(uniqueMembers.values());
	}

	private String getManagedTeamName(User manager) {
		List<Team> teams = getManagedTeams(manager);
		if (teams.isEmpty()) {
			return "No Team Assigned";
		}
		if (teams.size() == 1) {
			return teams.get(0).getName();
		}
		return String.join(", ", teams.stream().map(Team::getName).toList());
	}

	// =========================
	// DASHBOARD PAGE
	// =========================
	@GetMapping("/dashboard")
	public String dashboard(Model model) {

		injectStats(model);

		// ── Additional analytics data for charts ──────────────────────────
		String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
		User manager = userRepository.findByUsername(currentUsername);

		if (manager != null) {
			String tenant = getTenantSegmentFromEmail(manager.getEmail());
			List<User> teamMembers = getManagedTeamMembers(manager);

			// Scope tasks to this manager's created tasks within their tenant
			List<Task> myTasks = taskRepository.findByCreatedByAndTenantSegment(
					currentUsername, tenant);

			// Task status breakdown
			long statusDone       = myTasks.stream().filter(t -> "done".equalsIgnoreCase(t.getStatus())).count();
			long statusInProgress = myTasks.stream().filter(t -> "in-progress".equalsIgnoreCase(t.getStatus())).count();
			long statusPending    = myTasks.stream().filter(t -> "pending".equalsIgnoreCase(t.getStatus())).count();
			long statusReview     = myTasks.stream().filter(t -> "waiting-for-review".equalsIgnoreCase(t.getStatus())).count();

			model.addAttribute("chartStatusDone",       statusDone);
			model.addAttribute("chartStatusInProgress", statusInProgress);
			model.addAttribute("chartStatusPending",    statusPending);
			model.addAttribute("chartStatusReview",     statusReview);

			// Task priority breakdown
			long priorityHigh   = myTasks.stream().filter(t -> "High".equalsIgnoreCase(t.getPriority())).count();
			long priorityMedium = myTasks.stream().filter(t -> "Medium".equalsIgnoreCase(t.getPriority())).count();
			long priorityLow    = myTasks.stream().filter(t -> "Low".equalsIgnoreCase(t.getPriority())).count();

			model.addAttribute("chartPriorityHigh",   priorityHigh);
			model.addAttribute("chartPriorityMedium", priorityMedium);
			model.addAttribute("chartPriorityLow",    priorityLow);

			// Per-member task count (bar chart)
			List<String> memberLabels = new ArrayList<>();
			List<Long>   memberTaskCounts = new ArrayList<>();
			for (User member : teamMembers) {
				long count = myTasks.stream()
						.filter(t -> member.getUsername().equalsIgnoreCase(t.getAssignedTo()))
						.count();
				memberLabels.add(member.getUsername());
				memberTaskCounts.add(count);
			}
			model.addAttribute("chartMemberLabels",     memberLabels);
			model.addAttribute("chartMemberTaskCounts", memberTaskCounts);

			// Team active vs inactive
			long activeCount   = teamMembers.stream().filter(User::isActive).count();
			long inactiveCount = teamMembers.size() - activeCount;
			model.addAttribute("chartActiveTeam",   activeCount);
			model.addAttribute("chartInactiveTeam", inactiveCount);

			// Verification status breakdown
			long verified = myTasks.stream().filter(t -> "approved".equalsIgnoreCase(t.getVerificationStatus())).count();
			long rejected = myTasks.stream().filter(t -> "rejected".equalsIgnoreCase(t.getVerificationStatus())).count();
			long waiting  = myTasks.stream().filter(t -> "waiting-for-review".equalsIgnoreCase(t.getVerificationStatus())).count();
			long unverified = myTasks.size() - verified - rejected - waiting;

			model.addAttribute("chartVerified",   verified);
			model.addAttribute("chartRejected",   rejected);
			model.addAttribute("chartWaiting",    waiting);
			model.addAttribute("chartUnverified", unverified);

			model.addAttribute("chartTotalMyTasks", myTasks.size());
		} else {
			// zero fallbacks
			model.addAttribute("chartStatusDone", 0); model.addAttribute("chartStatusInProgress", 0);
			model.addAttribute("chartStatusPending", 0); model.addAttribute("chartStatusReview", 0);
			model.addAttribute("chartPriorityHigh", 0); model.addAttribute("chartPriorityMedium", 0);
			model.addAttribute("chartPriorityLow", 0);
			model.addAttribute("chartMemberLabels", new ArrayList<>());
			model.addAttribute("chartMemberTaskCounts", new ArrayList<>());
			model.addAttribute("chartActiveTeam", 0); model.addAttribute("chartInactiveTeam", 0);
			model.addAttribute("chartVerified", 0); model.addAttribute("chartRejected", 0);
			model.addAttribute("chartWaiting", 0); model.addAttribute("chartUnverified", 0);
			model.addAttribute("chartTotalMyTasks", 0);
		}

		return "manager-dashboard";
	}

	/** Extract tenant segment from email: "mgr.tcs@crm.com" → "tcs" */
	@GetMapping("/dashboard/analytics")
	@ResponseBody
	public Map<String, Object> dashboardAnalytics() {
		String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
		User manager = userRepository.findByUsername(currentUsername);
		if (manager == null) {
			return buildDashboardAnalytics(Collections.emptyList(), Collections.emptyList());
		}

		String tenant = getTenantSegmentFromEmail(manager.getEmail());
		List<User> teamMembers = getManagedTeamMembers(manager);
		List<Task> myTasks = taskRepository.findByCreatedByAndTenantSegment(currentUsername, tenant);
		return buildDashboardAnalytics(myTasks, teamMembers);
	}

	private Map<String, Object> buildDashboardAnalytics(List<Task> tasks, List<User> people) {
		Map<String, Object> data = new LinkedHashMap<>();
		List<Task> scopedTasks = tasks != null ? tasks : Collections.emptyList();
		List<User> scopedPeople = people != null ? people : Collections.emptyList();

		long statusDone = scopedTasks.stream().filter(t -> "done".equalsIgnoreCase(t.getStatus())).count();
		long statusInProgress = scopedTasks.stream().filter(t -> "in-progress".equalsIgnoreCase(t.getStatus())).count();
		long statusPending = scopedTasks.stream().filter(t -> "pending".equalsIgnoreCase(t.getStatus())).count();
		long statusReview = scopedTasks.stream().filter(t -> "waiting-for-review".equalsIgnoreCase(t.getStatus())).count();
		long priorityHigh = scopedTasks.stream().filter(t -> "High".equalsIgnoreCase(t.getPriority())).count();
		long priorityMedium = scopedTasks.stream().filter(t -> "Medium".equalsIgnoreCase(t.getPriority())).count();
		long priorityLow = scopedTasks.stream().filter(t -> "Low".equalsIgnoreCase(t.getPriority())).count();

		List<String> memberLabels = new ArrayList<>();
		List<Long> memberTaskCounts = new ArrayList<>();
		for (User member : scopedPeople) {
			long count = scopedTasks.stream()
					.filter(t -> member.getUsername() != null && member.getUsername().equalsIgnoreCase(t.getAssignedTo()))
					.count();
			memberLabels.add(member.getUsername());
			memberTaskCounts.add(count);
		}

		long activeCount = scopedPeople.stream().filter(User::isActive).count();
		long inactiveCount = scopedPeople.size() - activeCount;
		long verified = scopedTasks.stream().filter(t -> "approved".equalsIgnoreCase(t.getVerificationStatus())).count();
		long rejected = scopedTasks.stream().filter(t -> "rejected".equalsIgnoreCase(t.getVerificationStatus())).count();
		long waiting = scopedTasks.stream().filter(t -> "waiting-for-review".equalsIgnoreCase(t.getVerificationStatus())).count();
		long unverified = scopedTasks.size() - verified - rejected - waiting;

		data.put("statusDone", statusDone);
		data.put("statusInProgress", statusInProgress);
		data.put("statusPending", statusPending);
		data.put("statusReview", statusReview);
		data.put("priorityHigh", priorityHigh);
		data.put("priorityMedium", priorityMedium);
		data.put("priorityLow", priorityLow);
		data.put("memberLabels", memberLabels);
		data.put("memberTaskCounts", memberTaskCounts);
		data.put("activeTeam", activeCount);
		data.put("inactiveTeam", inactiveCount);
		data.put("verified", verified);
		data.put("rejected", rejected);
		data.put("waiting", waiting);
		data.put("unverified", Math.max(unverified, 0));
		data.put("totalMyTasks", scopedTasks.size());
		return data;
	}

	private String getTenantSegmentFromEmail(String email) {
		if (email == null || !email.contains("@")) return "";
		String local = email.substring(0, email.indexOf('@'));
		int dot = local.lastIndexOf('.');
		return dot >= 0 ? local.substring(dot + 1) : local;
	}

	// =========================
	// TEAM PAGE
	// =========================
	@GetMapping("/team")
	public String teamPage(Model model) {
		injectStats(model);
		return "manager-team";
	}

	/**
	 * REST: GET /manager/api/member/{id}
	 * Returns team member profile + last 30 days attendance as JSON for the modal.
	 */
	@GetMapping("/api/member/{id}")
	@ResponseBody
	public Map<String, Object> memberDetail(@PathVariable Long id) {
		Map<String, Object> resp = new LinkedHashMap<>();

		User manager = getCurrentManager();
		if (manager == null) { resp.put("error", "Not authenticated."); return resp; }

		// Verify the requested user is actually in this manager's team(s)
		List<Team> myTeams = getManagedTeams(manager);
		boolean inTeam = myTeams.stream().flatMap(t -> t.getMembers().stream()).anyMatch(m -> m.getId().equals(id));
		if (!inTeam) { resp.put("error", "Member not found in your team."); return resp; }

		User user = userRepository.findById(id).orElse(null);
		if (user == null) { resp.put("error", "User not found."); return resp; }

		// Profile
		resp.put("id",       user.getId());
		resp.put("username", user.getUsername());
		resp.put("email",    user.getEmail());
		resp.put("role",     user.getRole());
		resp.put("status",   user.getStatus());

		// Last 30 days attendance
		LocalDate today  = LocalDate.now();
		LocalDate from   = today.minusDays(29);
		String    tenant = getTenantSegment(manager);
		Map<LocalDate, String> holidays = fetchHolidays(tenant, from, today);
		List<Attendance> records = attendanceRepository.findByUserAndDateBetweenOrderByDateDesc(user, from, today);
		List<AttendanceDay> days = buildDayList(records, from, today, holidays, user);

		long present = days.stream().filter(d -> "present".equals(d.getStatus()) || "late".equals(d.getStatus())).count();
		long absent  = days.stream().filter(d -> "absent".equals(d.getStatus())).count();
		long halfDay = days.stream().filter(d -> "half-day".equals(d.getStatus())).count();
		long holiday = days.stream().filter(d -> "holiday".equals(d.getStatus())).count();
		resp.put("presentDays", present);
		resp.put("absentDays",  absent);
		resp.put("halfDays",    halfDay);
		resp.put("holidays",    holiday);

		List<Map<String, String>> rows = new ArrayList<>();
		for (AttendanceDay d : days) {
			Map<String, String> row = new LinkedHashMap<>();
			row.put("date",      d.getDate().toString());
			row.put("checkIn",   d.getCheckInDisplay());
			row.put("checkOut",  d.getCheckOutDisplay());
			row.put("worked",    d.getWorkedHours());
			row.put("breakTime", d.getBreakDuration());
			row.put("dayType",   d.isReal() && d.getRecord().getCheckOut() != null ? d.getRecord().getDayType() : "—");
			row.put("status",    d.getStatus());
			rows.add(row);
		}
		resp.put("attendance", rows);
		return resp;
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

		User manager = getCurrentManager();
		if (manager != null) {
			String tenant = getTenantSegment(manager);
			// Only show tasks belonging to this manager's tenant
			List<Task> tasks = taskRepository.findByTenantSegment(tenant);
			long done    = tasks.stream().filter(t -> "done".equalsIgnoreCase(t.getStatus())).count();
			long pending = tasks.stream().filter(t -> "pending".equalsIgnoreCase(t.getStatus())).count();
			model.addAttribute("tasks",            tasks);
			model.addAttribute("totalTasks",       tasks.size());
			model.addAttribute("doneTasks",        done);
			model.addAttribute("pendingTaskCount", pending);

			// Pass team members as "employees" for the assign dropdown
			List<User> teamMembers = getManagedTeamMembers(manager);
			model.addAttribute("employees", teamMembers);
		}

		return "manager-tasks";
	}

	// =========================
	// ASSIGN TASK (POST)
	// =========================
	@PostMapping("/tasks/assign")
	public String assignTask(
			@RequestParam String title,
			@RequestParam(required = false) String description,
			@RequestParam String priority,
			@RequestParam String status,
			@RequestParam(required = false) String startDate,
			@RequestParam(required = false) String dueDate,
			@RequestParam Long assignedToId,
			@RequestParam(value = "attachments", required = false) MultipartFile[] attachments,
			RedirectAttributes ra) {

		User manager = getCurrentManager();
		if (manager == null) {
			ra.addFlashAttribute("errorMessage", "Session expired. Please log in again.");
			return "redirect:/manager/tasks";
		}

		String tenant = getTenantSegment(manager);

		// Verify the assigned employee is actually in this manager's team
		List<User> teamMembers = getManagedTeamMembers(manager);
		User assignedUser = teamMembers.stream()
				.filter(u -> u.getId().equals(assignedToId))
				.findFirst()
				.orElse(null);

		if (assignedUser == null) {
			ra.addFlashAttribute("errorMessage", "Selected employee is not in your team.");
			return "redirect:/manager/tasks";
		}

		Task task = new Task();
		task.setTitle(title.trim());
		task.setDescription(description);
		task.setPriority(priority);
		task.setStatus(status);
		task.setStartDate(startDate);
		task.setDueDate(dueDate);
		task.setAssignedTo(assignedUser.getUsername());
		task.setAssignedToId(assignedUser.getId());
		task.setTenantSegment(tenant);
		task.setCreatedBy(manager.getUsername());
		taskRepository.save(task);

// Save uploaded files to database and update task metadata
        if (attachments != null) {
            List<String> uploadedNames = new ArrayList<>();
            for (MultipartFile file : attachments) {
                if (file == null || file.isEmpty()) continue;
                try {
                    byte[] fileData = file.getBytes();
                    String contentType = file.getContentType();
                    if (contentType == null) contentType = "application/octet-stream";
                    
                    // Create and save attachment in database
                    TaskAttachment taskAttachment = new TaskAttachment(
                        task,
                        file.getOriginalFilename(),
                        fileData,
                        contentType,
                        manager.getUsername()
                    );
                    taskAttachmentRepository.save(taskAttachment);
                    uploadedNames.add(file.getOriginalFilename());
                } catch (IOException e) {
                    ra.addFlashAttribute("errorMessage", "File upload failed: " + e.getMessage());
                    return "redirect:/manager/tasks";
                }
            }
            if (!uploadedNames.isEmpty()) {
                List<String> existingNames = new ArrayList<>();
                if (task.getAttachmentPaths() != null && !task.getAttachmentPaths().isBlank()) {
                    existingNames.addAll(java.util.Arrays.asList(task.getAttachmentPaths().split(",")));
                }
                existingNames.addAll(uploadedNames);
                task.setAttachmentPaths(String.join(",", existingNames));
                taskRepository.save(task);
			}
		}

		notificationService.notifyTaskAssigned(assignedUser, manager.getUsername(), task.getTitle());

		ra.addFlashAttribute("successMessage", "Task assigned to " + assignedUser.getUsername() + " successfully.");
		return "redirect:/manager/tasks";
	}

	// =========================
	// LIST TASK ATTACHMENTS (REST — for modal)
	// =========================
	@GetMapping("/api/task/{taskId}/attachments")
	@ResponseBody
	public List<Map<String, Object>> listTaskAttachments(@PathVariable Long taskId) {
		Task task = taskRepository.findById(taskId).orElse(null);
		if (task == null) return Collections.emptyList();

		List<Map<String, Object>> result = new ArrayList<>();
		if (task.getAttachments() != null) {
			for (TaskAttachment att : task.getAttachments()) {
				Map<String, Object> item = new LinkedHashMap<>();
				item.put("id",           att.getId());
				item.put("filename",     att.getOriginalFilename());
				item.put("contentType",  att.getContentType() != null ? att.getContentType() : "application/octet-stream");
				item.put("uploadedBy",   att.getUploadedBy() != null ? att.getUploadedBy() : "");
				item.put("viewUrl",      "/manager/tasks/view/"      + att.getId());
				item.put("downloadUrl",  "/manager/tasks/download/"  + att.getId());
				result.add(item);
			}
		}
		return result;
	}

	// =========================
	// DOWNLOAD TASK ATTACHMENT
	// =========================
	@GetMapping("/tasks/download/{attachmentId}")
	public ResponseEntity<?> downloadAttachment(@PathVariable Long attachmentId) {
		TaskAttachment attachment = taskAttachmentRepository.findById(attachmentId).orElse(null);
		if (attachment == null) {
			return ResponseEntity.notFound().build();
		}

		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + attachment.getOriginalFilename() + "\"")
				.header(HttpHeaders.CONTENT_TYPE, attachment.getContentType() != null ? attachment.getContentType() : "application/octet-stream")
				.body(attachment.getFileData());
	}

	// =========================
	// VIEW TASK ATTACHMENT INLINE
	// =========================
	@GetMapping("/tasks/view/{attachmentId}")
	public ResponseEntity<?> viewAttachment(@PathVariable Long attachmentId) {
		TaskAttachment attachment = taskAttachmentRepository.findById(attachmentId).orElse(null);
		if (attachment == null) {
			return ResponseEntity.notFound().build();
		}

		String contentType = attachment.getContentType() != null ? attachment.getContentType() : "application/octet-stream";
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + attachment.getOriginalFilename() + "\"")
				.header(HttpHeaders.CONTENT_TYPE, contentType)
				.body(attachment.getFileData());
	}

	// =========================
	// VERIFY TASK (POST)
	// action=approve  → manager verifies task as done
	// action=reject   → manager returns task to employee with feedback
	// action=reopen   → manager marks a verified task as incomplete again
	// =========================
	@PostMapping("/tasks/verify/{id}")
	public String verifyTask(@PathVariable Long id,
                            @RequestParam String action,
                            @RequestParam(required = false) String reason,
                            RedirectAttributes ra) {
		User manager = getCurrentManager();
		if (manager == null) {
			ra.addFlashAttribute("errorMessage", "Session expired. Please log in again.");
			return "redirect:/manager/tasks";
		}

		String tenant = getTenantSegment(manager);
		Task task = taskRepository.findById(id).orElse(null);
		if (task == null || !tenant.equals(task.getTenantSegment())) {
			ra.addFlashAttribute("errorMessage", "Task not found.");
			return "redirect:/manager/tasks";
		}

		java.time.LocalDateTime now = java.time.LocalDateTime.now();
		String timestamp = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

		if ("approve".equalsIgnoreCase(action)) {
			// Manager confirms the work is complete
			task.setStatus("done");
			task.setVerificationStatus("approved");
			task.setLastVerifiedBy(manager.getUsername());
			task.setLastVerifiedAt(timestamp);
			task.setVerificationReason(null);
			ra.addFlashAttribute("successMessage", "Task verified and marked as done.");

		} else if ("reject".equalsIgnoreCase(action)) {
			// Manager returns the task — employee must redo and resubmit
			task.setStatus("in-progress");
			task.setVerificationStatus("rejected");
			task.setLastVerifiedBy(manager.getUsername());
			task.setLastVerifiedAt(timestamp);
			task.setVerificationReason(reason != null ? reason.trim() : null);
			ra.addFlashAttribute("successMessage", "Task returned to employee for rework.");

		} else if ("reopen".equalsIgnoreCase(action)) {
			// Manager re-opens a previously verified task
			task.setStatus("in-progress");
			task.setVerificationStatus("pending");
			task.setLastVerifiedBy(manager.getUsername());
			task.setLastVerifiedAt(timestamp);
			task.setVerificationReason(null);
			ra.addFlashAttribute("successMessage", "Task marked as incomplete and returned to employee.");
		}

		taskRepository.save(task);

		User assignee = resolveTaskAssignee(task);
		if (assignee != null) {
			notificationService.notifyTaskVerified(
					assignee, manager.getUsername(), task.getTitle(), action, reason);
		}

		return "redirect:/manager/tasks";
	}

	private User resolveTaskAssignee(Task task) {
		if (task == null) return null;
		if (task.getAssignedToId() != null) {
			User byId = userRepository.findById(task.getAssignedToId()).orElse(null);
			if (byId != null) return byId;
		}
		if (task.getAssignedTo() != null && !task.getAssignedTo().isBlank()) {
			return userRepository.findByUsername(task.getAssignedTo());
		}
		return null;
	}

	// =========================
	// DELETE TASK (POST)
	// =========================
	@PostMapping("/tasks/delete/{id}")
	public String deleteTask(@PathVariable Long id, RedirectAttributes ra) {
		User manager = getCurrentManager();
		String tenant = manager != null ? getTenantSegment(manager) : "";

		Task task = taskRepository.findById(id).orElse(null);
		if (task == null || !tenant.equals(task.getTenantSegment())) {
			ra.addFlashAttribute("errorMessage", "Task not found.");
		} else {
			taskRepository.deleteById(id);
			ra.addFlashAttribute("successMessage", "Task deleted successfully.");
		}
		return "redirect:/manager/tasks";
	}

	// =========================
	// LEAVE REQUESTS PAGE
	// =========================
	@GetMapping("/leave")
	public String legacyLeaveRedirect() {
		return "redirect:/manager/leaves";
	}

	@GetMapping("/leaves")
	public String leavesPage(Model model) {
		injectStats(model);

		User manager = getCurrentManager();
		if (manager != null) {
			String tenant = getTenantSegment(manager);
			List<LeaveRequest> requests = leaveRequestRepository.findByEmployeeOrderByCreatedAtDesc(manager);
			long pending = leaveRequestRepository.countByEmployeeAndStatus(manager, "Pending");
			long approved = leaveRequestRepository.countByEmployeeAndStatus(manager, "Approved");
			long rejected = leaveRequestRepository.countByEmployeeAndStatus(manager, "Rejected");

			model.addAttribute("leaveRequests", requests);
			model.addAttribute("pendingLeaves", pending);
			model.addAttribute("approvedLeaves", approved);
			model.addAttribute("rejectedLeaves", rejected);
			model.addAttribute("casualBalance", 12);
			model.addAttribute("sickBalance", 6);
			model.addAttribute("annualBalance", 18);
			model.addAttribute("compBalance", 3);
			model.addAttribute("tenantSegment", tenant);
		} else {
			model.addAttribute("leaveRequests", Collections.emptyList());
			model.addAttribute("pendingLeaves", 0);
			model.addAttribute("approvedLeaves", 0);
			model.addAttribute("rejectedLeaves", 0);
			model.addAttribute("casualBalance", 12);
			model.addAttribute("sickBalance", 6);
			model.addAttribute("annualBalance", 18);
			model.addAttribute("compBalance", 3);
		}

		return "manager-leave";
	}

	@PostMapping("/leaves")
	public String submitLeave(@RequestParam String type,
	                          @RequestParam String fromDate,
	                          @RequestParam String toDate,
	                          @RequestParam String reason,
	                          @RequestParam(value = "attachment", required = false) MultipartFile attachment,
	                          RedirectAttributes ra) {
		User manager = getCurrentManager();
		if (manager == null) {
			ra.addFlashAttribute("errorMessage", "Session expired. Please log in again.");
			return "redirect:/manager/leaves";
		}

		LocalDate from = LocalDate.parse(fromDate);
		LocalDate to = LocalDate.parse(toDate);
		if (to.isBefore(from)) {
			ra.addFlashAttribute("errorMessage", "To date cannot be before from date.");
			return "redirect:/manager/leaves";
		}
		if (type == null || type.isBlank() || reason == null || reason.isBlank()) {
			ra.addFlashAttribute("errorMessage", "Please fill all required leave details.");
			return "redirect:/manager/leaves";
		}

		LeaveRequest leave = new LeaveRequest();
		leave.setEmployee(manager);
		leave.setEmployeeName(manager.getUsername());
		leave.setTenantSegment(getTenantSegment(manager));
		leave.setType(type.trim());
		leave.setFromDate(from);
		leave.setToDate(to);
		leave.setReason(reason.trim());
		leave.setStatus("Pending");

		if (attachment != null && !attachment.isEmpty()) {
			try {
				leave.setAttachmentName(attachment.getOriginalFilename());
				leave.setAttachmentContentType(attachment.getContentType() != null ? attachment.getContentType() : "application/octet-stream");
				leave.setAttachmentData(attachment.getBytes());
			} catch (IOException e) {
				ra.addFlashAttribute("errorMessage", "Attachment upload failed: " + e.getMessage());
				return "redirect:/manager/leaves";
			}
		}

		leaveRequestRepository.save(leave);
		ra.addFlashAttribute("successMessage", "Leave request submitted successfully.");
		return "redirect:/manager/leaves";
	}

	// =========================
	// REPORTS PAGE
	// =========================
	@GetMapping("/reports")
	public String reportsPage(Model model) {

		injectStats(model);

		User manager = getCurrentManager();
		if (manager != null) {
			String tenant = getTenantSegment(manager);

			// Team members with full performance stats
			List<User> teamMembers = getManagedTeamMembers(manager);
			model.addAttribute("teamMembers", teamMembers);

			// All possible recipients (Team members + HR + Admin)
			List<User> allTenantUsers = userRepository.findByTenantSegment(tenant);
			List<User> allRecipients = allTenantUsers.stream()
					.filter(u -> "HR".equalsIgnoreCase(u.getRole()) || "ADMIN".equalsIgnoreCase(u.getRole()) || teamMembers.contains(u))
					.distinct()
					.toList();
			model.addAttribute("allRecipients", allRecipients);

			// Sent reports history (for "Send Report" tracking)
			List<Report> sentReports = reportRepository
					.findBySentByAndTenantSegmentOrderBySentAtDesc(manager.getUsername(), tenant);
			model.addAttribute("sentReports", sentReports);
			model.addAttribute("sentReportCount", sentReports.size());

			// Build per-employee detail for click-to-view
			java.time.YearMonth ym = java.time.YearMonth.now();
			LocalDate from = ym.atDay(1);
			LocalDate to   = LocalDate.now();
			int workingDays = 0;
			LocalDate wd = from;
			while (!wd.isAfter(to)) {
				java.time.DayOfWeek dow = wd.getDayOfWeek();
				if (dow != java.time.DayOfWeek.SATURDAY && dow != java.time.DayOfWeek.SUNDAY) workingDays++;
				wd = wd.plusDays(1);
			}
			if (workingDays < 1) workingDays = 1;

			List<EmployeePerf> perfList = new ArrayList<>();
			for (User emp : teamMembers) {
				EmployeePerf p = new EmployeePerf();
				p.employee = emp;
				List<Task> tasks = taskRepository.findByAssignedToAndTenantSegment(emp.getUsername(), tenant);
				p.totalTasks   = tasks.size();
				p.doneTasks    = (int) tasks.stream().filter(t -> "done".equalsIgnoreCase(t.getStatus())).count();
				p.pendingTasks = (int) tasks.stream().filter(t -> "pending".equalsIgnoreCase(t.getStatus())).count();
				p.overdueTasks = (int) tasks.stream()
						.filter(t -> !"done".equalsIgnoreCase(t.getStatus())
								&& t.getDueDate() != null && !t.getDueDate().isBlank()
								&& LocalDate.parse(t.getDueDate()).isBefore(LocalDate.now()))
						.count();
				if (p.totalTasks > 0) {
					double raw = ((double) p.doneTasks / p.totalTasks) * 100.0 - (p.overdueTasks * 5.0);
					p.taskScore = (int) Math.max(0, Math.min(100, raw));
				} else { p.taskScore = 100; }

				List<Attendance> attRecords = attendanceRepository.findByUserAndDateBetweenOrderByDateDesc(emp, from, to);
				p.presentDays = (int) attRecords.stream().filter(a -> "present".equalsIgnoreCase(a.getStatus())).count();
				p.lateDays    = (int) attRecords.stream().filter(a -> "late".equalsIgnoreCase(a.getStatus())).count();
				List<LeaveRequest> leaves = leaveRequestRepository.findByEmployeeOrderByCreatedAtDesc(emp);
				int leaveDaysCount = 0;
				for (LeaveRequest lr : leaves) {
					if (!"Approved".equalsIgnoreCase(lr.getStatus()) || lr.getFromDate() == null) continue;
					LocalDate lStart = lr.getFromDate().isBefore(from) ? from : lr.getFromDate();
					LocalDate lEnd   = lr.getToDate() == null ? lStart : (lr.getToDate().isAfter(to) ? to : lr.getToDate());
					LocalDate c = lStart;
					while (!c.isAfter(lEnd)) {
						java.time.DayOfWeek d2 = c.getDayOfWeek();
						if (d2 != java.time.DayOfWeek.SATURDAY && d2 != java.time.DayOfWeek.SUNDAY) leaveDaysCount++;
						c = c.plusDays(1);
					}
				}
				p.leaveDays  = leaveDaysCount;
				int effectiveWorking = Math.max(1, workingDays - p.leaveDays);
				p.absentDays = Math.max(0, effectiveWorking - p.presentDays - p.lateDays);
				double attRaw = ((p.presentDays + p.lateDays * 0.5) / effectiveWorking) * 100.0;
				p.attendanceScore = (int) Math.max(0, Math.min(100, attRaw));
				p.overallScore = (int) (p.taskScore * 0.6 + p.attendanceScore * 0.4);
				p.grade = p.overallScore >= 90 ? "A+" : p.overallScore >= 75 ? "A" :
				          p.overallScore >= 60 ? "B"  : p.overallScore >= 45 ? "C" : "D";
				p.existingReview = performanceReviewRepository
						.findByEmployeeAndReviewMonthAndTenantSegment(emp, ym.toString(), tenant)
						.orElse(null);
				perfList.add(p);
			}
			model.addAttribute("perfList",      perfList);
			model.addAttribute("selectedMonth", ym.toString());
		} else {
			model.addAttribute("teamMembers", java.util.Collections.emptyList());
			model.addAttribute("sentReports", java.util.Collections.emptyList());
			model.addAttribute("sentReportCount", 0);
			model.addAttribute("perfList", java.util.Collections.emptyList());
			model.addAttribute("selectedMonth", java.time.YearMonth.now().toString());
		}

		return "manager-reports";
	}

	// =========================
	// SEND REPORT (POST)
	// =========================
	@PostMapping("/reports/send")
	public String sendReport(
			@RequestParam String title,
			@RequestParam(required = false) String message,
			@RequestParam(value = "recipientIds", required = false) List<Long> recipientIds,
			@RequestParam(value = "attachments", required = false) MultipartFile[] attachments,
			@RequestParam(required = false) Integer taskScore,
			@RequestParam(required = false) Integer attendanceScore,
			@RequestParam(required = false) Integer overallScore,
			@RequestParam(required = false) String grade,
			@RequestParam(required = false) Integer totalTasks,
			@RequestParam(required = false) Integer doneTasks,
			@RequestParam(required = false) Integer pendingTasks,
			@RequestParam(required = false) Integer overdueTasks,
			@RequestParam(required = false) Integer presentDays,
			@RequestParam(required = false) Integer absentDays,
			@RequestParam(required = false) Integer lateDays,
			@RequestParam(required = false) Integer leaveDays,
			RedirectAttributes ra) {

		User manager = getCurrentManager();
		if (manager == null) {
			ra.addFlashAttribute("errorMessage", "Session expired. Please log in again.");
			return "redirect:/manager/reports";
		}
		if (title == null || title.isBlank()) {
			ra.addFlashAttribute("errorMessage", "Report title is required.");
			return "redirect:/manager/reports";
		}
		if (recipientIds == null || recipientIds.isEmpty()) {
			ra.addFlashAttribute("errorMessage", "Please select at least one recipient.");
			return "redirect:/manager/reports";
		}

		String tenant = getTenantSegment(manager);

		// Verify recipients are in this manager's tenant and have valid roles (Employee in team, HR, or Admin)
		List<User> allTenantUsers = userRepository.findByTenantSegment(tenant);
		List<User> teamMembers = getManagedTeamMembers(manager);
		List<User> validRecipients = allTenantUsers.stream()
				.filter(u -> recipientIds.contains(u.getId()))
				.filter(u -> "HR".equalsIgnoreCase(u.getRole()) || "ADMIN".equalsIgnoreCase(u.getRole()) || teamMembers.contains(u))
				.toList();

		if (validRecipients.isEmpty()) {
			ra.addFlashAttribute("errorMessage", "None of the selected recipients are valid or in your tenant.");
			return "redirect:/manager/reports";
		}

		// Build CSV strings for IDs and names
		String idsCsv   = validRecipients.stream().map(u -> String.valueOf(u.getId())).collect(java.util.stream.Collectors.joining(","));
		String namesCsv = validRecipients.stream().map(User::getUsername).collect(java.util.stream.Collectors.joining(", "));

		Report report = new Report();
		report.setTitle(title.trim());
		report.setMessage(message != null ? message.trim() : "");
		report.setSentBy(manager.getUsername());
		report.setTenantSegment(tenant);
		report.setRecipientIds(idsCsv);
		report.setRecipientNames(namesCsv);

		// Performance Snapshots
		report.setTaskScore(taskScore);
		report.setAttendanceScore(attendanceScore);
		report.setOverallScore(overallScore);
		report.setGrade(grade);
		report.setTotalTasks(totalTasks);
		report.setDoneTasks(doneTasks);
		report.setPendingTasks(pendingTasks);
		report.setOverdueTasks(overdueTasks);
		report.setPresentDays(presentDays);
		report.setAbsentDays(absentDays);
		report.setLateDays(lateDays);
		report.setLeaveDays(leaveDays);

		reportRepository.save(report);

		// Save attachments
		if (attachments != null) {
			for (MultipartFile file : attachments) {
				if (file == null || file.isEmpty()) continue;
				try {
					String ct = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
					ReportAttachment ra2 = new ReportAttachment(report, file.getOriginalFilename(), file.getBytes(), ct);
					reportAttachmentRepository.save(ra2);
				} catch (IOException e) {
					ra.addFlashAttribute("errorMessage", "File upload failed: " + e.getMessage());
					return "redirect:/manager/reports";
				}
			}
		}

		for (User recipient : validRecipients) {
			notificationService.notifyReportReceived(recipient, manager.getUsername(), title.trim());
		}

		ra.addFlashAttribute("successMessage", "Report sent to " + validRecipients.size() + " recipient(s) successfully.");
		return "redirect:/manager/reports";
	}

	// =========================
	// VIEW REPORT ATTACHMENT INLINE
	// =========================
	@GetMapping("/reports/view/{attachmentId}")
	public ResponseEntity<?> viewReportAttachment(@PathVariable Long attachmentId) {
		ReportAttachment att = reportAttachmentRepository.findById(attachmentId).orElse(null);
		if (att == null) return ResponseEntity.notFound().build();
		String ct = att.getContentType() != null ? att.getContentType() : "application/octet-stream";
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + att.getOriginalFilename() + "\"")
				.header(HttpHeaders.CONTENT_TYPE, ct)
				.body(att.getFileData());
	}

	// =========================
	// DOWNLOAD REPORT ATTACHMENT
	// =========================
	@GetMapping("/reports/download/{attachmentId}")
	public ResponseEntity<?> downloadReportAttachment(@PathVariable Long attachmentId) {
		ReportAttachment att = reportAttachmentRepository.findById(attachmentId).orElse(null);
		if (att == null) return ResponseEntity.notFound().build();
		String ct = att.getContentType() != null ? att.getContentType() : "application/octet-stream";
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + att.getOriginalFilename() + "\"")
				.header(HttpHeaders.CONTENT_TYPE, ct)
				.body(att.getFileData());
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
	// MEETINGS PAGE
	// =========================

	/**
	 * Filter a list of today's meetings to only those that have not yet ended.
	 * A meeting ends at meetingTime + duration minutes. Meetings with no time are always shown.
	 */
	private List<Meeting> filterActiveMeetings(List<Meeting> meetings) {
		LocalTime now = LocalTime.now();
		return meetings.stream().filter(m -> {
			if (m.getMeetingTime() == null) return true;
			int durationMins = (m.getDuration() != null) ? m.getDuration() : 0;
			LocalTime endTime = m.getMeetingTime().plusMinutes(durationMins);
			return !endTime.isBefore(now);
		}).toList();
	}

	private List<Meeting> getPastMeetings(String tenant, String username) {
		List<Meeting> all = meetingRepository.findAllMeetingsForUserOrHost(tenant, username);
		LocalDate today = LocalDate.now();
		LocalTime now   = LocalTime.now();
		return all.stream().filter(m -> {
			if (m.getMeetingDate().isBefore(today)) return true;
			if (!m.getMeetingDate().equals(today)) return false;
			if (m.getMeetingTime() == null) return false;
			int dur = (m.getDuration() != null) ? m.getDuration() : 0;
			return m.getMeetingTime().plusMinutes(dur).isBefore(now);
		}).toList();
	}

	/** Returns upcoming meetings (today + future) where the user is a participant OR the host,
	 *  excluding today's meetings that have already ended. */
	private List<Meeting> getUpcomingMeetings(String tenant, String username) {
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

	/** GET /manager/meetings — show schedule form + list of meetings where manager is a participant */
	@GetMapping("/meetings")
	public String meetingsPage(Model model) {
		injectStats(model);
		User manager = getCurrentManager();
		if (manager != null) {
			String tenant   = getTenantSegment(manager);
			String username = manager.getUsername();
			model.addAttribute("meetings", getUpcomingMeetings(tenant, username));
			model.addAttribute("pastMeetings", getPastMeetings(tenant, username));
			// Team members available as participants
			model.addAttribute("teamMembers", getManagedTeamMembers(manager));
		} else {
			model.addAttribute("meetings", Collections.emptyList());
			model.addAttribute("teamMembers", Collections.emptyList());
		}
		if (!model.containsAttribute("meetingForm")) {
			model.addAttribute("meetingForm", new Meeting());
		}
		return "manager-meetings";
	}

	/** POST /manager/meetings — create a new meeting */
	@PostMapping("/meetings")
	public String scheduleMeeting(@Valid @ModelAttribute("meetingForm") Meeting meetingForm,
	                              BindingResult result,
	                              Model model,
	                              RedirectAttributes ra) {
		User manager = getCurrentManager();
		String tenant   = manager != null ? getTenantSegment(manager) : "";
		String username = manager != null ? manager.getUsername() : "";

		if (result.hasErrors()) {
			injectStats(model);
			if (manager != null) {
				model.addAttribute("meetings", getUpcomingMeetings(tenant, username));
				model.addAttribute("pastMeetings", getPastMeetings(tenant, username));
				model.addAttribute("teamMembers", getManagedTeamMembers(manager));
			} else {
				model.addAttribute("meetings", Collections.emptyList());
				model.addAttribute("teamMembers", Collections.emptyList());
			}
			model.addAttribute("errorMessage", "Please fix the errors below.");
			return "manager-meetings";
		}

		meetingForm.setTenantSegment(tenant);
		meetingForm.setScheduledBy(username);
		meetingRepository.save(meetingForm);
		notificationService.notifyMeetingParticipants(meetingForm);
		ra.addFlashAttribute("successMessage", "Meeting scheduled successfully.");
		return "redirect:/manager/meetings";
	}

	/** GET /manager/meetings/edit/{id} — load meeting into form */
	@GetMapping("/meetings/edit/{id}")
	public String editMeetingPage(@PathVariable Long id, Model model, RedirectAttributes ra) {
		User manager = getCurrentManager();
		String tenant   = manager != null ? getTenantSegment(manager) : "";
		String username = manager != null ? manager.getUsername() : "";

		Meeting meeting = meetingRepository.findById(id).orElse(null);
		if (meeting == null || !tenant.equals(meeting.getTenantSegment())) {
			ra.addFlashAttribute("errorMessage", "Meeting not found.");
			return "redirect:/manager/meetings";
		}

		injectStats(model);
		model.addAttribute("meetingForm", meeting);
		model.addAttribute("meetings", getUpcomingMeetings(tenant, username));
		model.addAttribute("pastMeetings", getPastMeetings(tenant, username));
		model.addAttribute("teamMembers", getManagedTeamMembers(manager));
		return "manager-meetings";
	}

	/** POST /manager/meetings/edit/{id} — update existing meeting */
	@PostMapping("/meetings/edit/{id}")
	public String updateMeeting(@PathVariable Long id,
	                            @Valid @ModelAttribute("meetingForm") Meeting meetingForm,
	                            BindingResult result,
	                            Model model,
	                            RedirectAttributes ra) {
		User manager = getCurrentManager();
		String tenant   = manager != null ? getTenantSegment(manager) : "";
		String username = manager != null ? manager.getUsername() : "";

		if (result.hasErrors()) {
			injectStats(model);
			model.addAttribute("meetings", getUpcomingMeetings(tenant, username));
			model.addAttribute("pastMeetings", getPastMeetings(tenant, username));
			model.addAttribute("teamMembers", getManagedTeamMembers(manager));
			model.addAttribute("errorMessage", "Please fix the errors below.");
			return "manager-meetings";
		}

		Meeting existing = meetingRepository.findById(id).orElse(null);
		if (existing == null || !tenant.equals(existing.getTenantSegment())) {
			ra.addFlashAttribute("errorMessage", "Meeting not found.");
			return "redirect:/manager/meetings";
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
		notificationService.notifyMeetingParticipants(existing);

		ra.addFlashAttribute("successMessage", "Meeting updated successfully.");
		return "redirect:/manager/meetings";
	}

	/** POST /manager/meetings/delete/{id} — delete a meeting */
	@PostMapping("/meetings/delete/{id}")
	public String deleteMeeting(@PathVariable Long id, RedirectAttributes ra) {
		User manager = getCurrentManager();
		String tenant = manager != null ? getTenantSegment(manager) : "";

		Meeting meeting = meetingRepository.findById(id).orElse(null);
		if (meeting == null || !tenant.equals(meeting.getTenantSegment())) {
			ra.addFlashAttribute("errorMessage", "Meeting not found.");
		} else {
			meetingRepository.delete(meeting);
			ra.addFlashAttribute("successMessage", "Meeting deleted successfully.");
		}
		return "redirect:/manager/meetings";
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
	public String updateProfile(@RequestParam(required = false) String username,
								@RequestParam(required = false) String email,
								@RequestParam(required = false) String password,
								@RequestParam(required = false) String confirmPassword,
								HttpServletResponse response,
								RedirectAttributes ra) {

		String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
		User manager = userRepository.findByUsername(currentUsername);

		if (manager == null) {
			return "redirect:/manager/settings";
		}

		profileUpdateService.updateProfile(manager, username, email, password, confirmPassword, ra, response);
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

	private boolean hasApprovedLeave(User user, LocalDate date) {
		if (user == null || date == null) return false;
		return leaveRequestRepository.findByEmployeeOrderByCreatedAtDesc(user).stream()
				.anyMatch(leave -> "Approved".equalsIgnoreCase(leave.getStatus())
						&& !date.isBefore(leave.getFromDate())
						&& !date.isAfter(leave.getToDate()));
	}

	/**
	 * Build a merged day list for the given date range.
	 * Priority: holiday > weekend > real record > absent.
	 * Result is sorted newest-first.
	 */
	private List<AttendanceDay> buildDayList(List<Attendance> records,
	                                          LocalDate from, LocalDate to,
	                                          Map<LocalDate, String> holidays,
	                                          User user) {
		Map<LocalDate, Attendance> byDate = new LinkedHashMap<>();
		for (Attendance a : records) byDate.put(a.getDate(), a);

		Set<LocalDate> approvedLeaveDates = new LinkedHashSet<>();
		if (user != null) {
			for (LeaveRequest leave : leaveRequestRepository.findByEmployeeOrderByCreatedAtDesc(user)) {
				if (!"Approved".equalsIgnoreCase(leave.getStatus()) || leave.getFromDate() == null || leave.getToDate() == null) {
					continue;
				}
				LocalDate cursor = leave.getFromDate();
				while (!cursor.isAfter(leave.getToDate())) {
					if (!cursor.isBefore(from) && !cursor.isAfter(to)) {
						approvedLeaveDates.add(cursor);
					}
					cursor = cursor.plusDays(1);
				}
			}
		}

		List<AttendanceDay> days = new ArrayList<>();
		LocalDate today  = LocalDate.now();
		LocalDate cursor = to;
		while (!cursor.isBefore(from)) {
			if (holidays.containsKey(cursor)) {
				days.add(new AttendanceDay(cursor, holidays.get(cursor), true));
			} else {
				DayOfWeek dow = cursor.getDayOfWeek();
				if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
					days.add(new AttendanceDay(cursor, "weekend"));
				} else if (byDate.containsKey(cursor)) {
					days.add(new AttendanceDay(byDate.get(cursor)));
				} else if (approvedLeaveDates.contains(cursor)) {
					days.add(new AttendanceDay(cursor, "leave"));
				} else if (!cursor.isAfter(today)) {
					days.add(new AttendanceDay(cursor, "absent"));
				}
			}
			cursor = cursor.minusDays(1);
		}
		return days;
	}

	/** Build holiday map (date → name) for a tenant within a date range. */
	private Map<LocalDate, String> fetchHolidays(String tenant, LocalDate from, LocalDate to) {
		Map<LocalDate, String> map = new LinkedHashMap<>();
		if (tenant == null || tenant.isBlank()) return map;
		List<Holiday> list = holidayRepository.findByTenantAndDateRange(
				tenant, from.toString(), to.toString());
		for (Holiday h : list) map.put(LocalDate.parse(h.getDate()), h.getName());
		return map;
	}

	@GetMapping("/attendance")
	public String attendancePage(
			@RequestParam(required = false) String from,
			@RequestParam(required = false) String to,
			@RequestParam(required = false) String status,
			Model model) {
		injectStats(model);

		User manager = getCurrentManager();
		if (manager == null) {
			return "redirect:/manager/dashboard";
		}

		LocalDate today = LocalDate.now();

		// Date range (default: last 30 days)
		LocalDate filterFrom = (from != null && !from.isBlank()) ? LocalDate.parse(from) : today.minusDays(29);
		LocalDate filterTo   = (to   != null && !to.isBlank())   ? LocalDate.parse(to)   : today;
		if (filterTo.isAfter(today))      filterTo   = today;
		if (filterFrom.isAfter(filterTo)) filterFrom = filterTo;

		// Fetch real records in range
		String tenant = getTenantSegment(manager);
		List<Attendance> records = attendanceRepository
				.findByUserAndDateBetweenOrderByDateDesc(manager, filterFrom, filterTo);

		// Holidays in range
		Map<LocalDate, String> holidays = fetchHolidays(tenant, filterFrom, filterTo);

		// Today's record — drives punch-in / punch-out button state
		Optional<Attendance> todayOpt = attendanceRepository.findByUserAndDate(manager, today);

		// Is today a holiday?
		String todayHolidayName = holidays.get(today);
		boolean isHolidayToday  = todayHolidayName != null;

		boolean punchedIn  = todayOpt.isPresent();
		boolean punchedOut = todayOpt.map(a -> a.getCheckOut() != null).orElse(false);
		boolean onBreak    = todayOpt.map(a ->
				(a.getBreakStart() != null && a.getBreakEnd() == null) ||
				(a.getBreak2Start() != null && a.getBreak2End() == null)).orElse(false);
		boolean breakDone  = todayOpt.map(a -> a.getBreak2End() != null).orElse(false);
		boolean canStartBreak = punchedIn && !punchedOut && !onBreak && !breakDone &&
				todayOpt.map(a -> a.getBreakStart() == null ||
						(a.getBreakEnd() != null && a.getBreak2Start() == null)).orElse(false);

		boolean todayOnLeave = hasApprovedLeave(manager, today);

		// Build merged day list (fills leave/absent/weekend/holiday gaps)
		List<AttendanceDay> allDays = buildDayList(records, filterFrom, filterTo, holidays, manager);

		// Apply status filter
		List<AttendanceDay> filteredDays = allDays;
		if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
			List<AttendanceDay> tmp = new ArrayList<>();
			for (AttendanceDay d : allDays) {
				if (d.getStatus().equalsIgnoreCase(status)) tmp.add(d);
			}
			filteredDays = tmp;
		}

		// Stats from all-time records
		List<Attendance> allRecords = attendanceRepository.findByUserOrderByDateDesc(manager);
		long presentCount = allRecords.stream()
				.filter(a -> "present".equalsIgnoreCase(a.getStatus()) || "late".equalsIgnoreCase(a.getStatus()))
				.count();
		long lateCount = allRecords.stream()
				.filter(a -> "late".equalsIgnoreCase(a.getStatus()))
				.count();

		model.addAttribute("attendanceDays",    filteredDays);
		model.addAttribute("totalRecords",      filteredDays.size());
		model.addAttribute("todayRecord",       todayOpt.orElse(null));
		model.addAttribute("todayOnLeave",      todayOnLeave);
		model.addAttribute("punchedIn",         punchedIn);
		model.addAttribute("punchedOut",        punchedOut);
		model.addAttribute("onBreak",           onBreak);
		model.addAttribute("breakDone",         breakDone);
		model.addAttribute("canStartBreak",     canStartBreak);
		model.addAttribute("isHolidayToday",    isHolidayToday);
		model.addAttribute("todayHolidayName",  todayHolidayName);
		model.addAttribute("presentCount",      presentCount);
		model.addAttribute("lateCount",         lateCount);
		model.addAttribute("filterFrom",        filterFrom.toString());
		model.addAttribute("filterTo",          filterTo.toString());
		model.addAttribute("filterStatus",      status != null ? status : "all");

		return "manager-attendance";
	}

	/** Punch In */
	@PostMapping("/attendance/punch-in")
	public String punchIn(RedirectAttributes ra) {
		User manager = getCurrentManager();
		if (manager == null) return "redirect:/manager/attendance";

		String    tenant = getTenantSegment(manager);
		LocalDate today  = LocalDate.now();

		// Block punch-in on holidays
		if (holidayRepository.findByDateAndTenantSegment(today.toString(), tenant).isPresent()) {
			ra.addFlashAttribute("errorMessage", "Today is a holiday. Punch-in is not allowed.");
			return "redirect:/manager/attendance";
		}

		if (hasApprovedLeave(manager, today)) {
			ra.addFlashAttribute("errorMessage", "You are on approved leave today. Punch-in is not allowed.");
			return "redirect:/manager/attendance";
		}

		// Prevent duplicate punch-in
		if (attendanceRepository.findByUserAndDate(manager, today).isPresent()) {
			ra.addFlashAttribute("errorMessage", "You have already punched in today.");
			return "redirect:/manager/attendance";
		}

		LocalTime now    = LocalTime.now();
		String    status = now.isAfter(LocalTime.of(9, 30)) ? "late" : "present";

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
		if (hasApprovedLeave(manager, today)) {
			ra.addFlashAttribute("errorMessage", "You are on approved leave today. Punch-out is not allowed.");
			return "redirect:/manager/attendance";
		}

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

	/** Break Start */
	@PostMapping("/attendance/break-start")
	public String breakStart(RedirectAttributes ra) {
		User manager = getCurrentManager();
		if (manager == null) return "redirect:/manager/attendance";

		LocalDate today = LocalDate.now();
		if (hasApprovedLeave(manager, today)) {
			ra.addFlashAttribute("errorMessage", "You are on approved leave today. Break actions are not allowed.");
			return "redirect:/manager/attendance";
		}

		Optional<Attendance> opt = attendanceRepository.findByUserAndDate(manager, today);

		if (opt.isEmpty()) {
			ra.addFlashAttribute("errorMessage", "You haven't punched in today.");
			return "redirect:/manager/attendance";
		}
		Attendance att = opt.get();
		if (att.getCheckOut() != null) {
			ra.addFlashAttribute("errorMessage", "You have already punched out.");
			return "redirect:/manager/attendance";
		}

		LocalTime now = LocalTime.now();

		if (att.getBreakStart() == null) {
			att.setBreakStart(now);
			attendanceRepository.save(att);
			ra.addFlashAttribute("successMessage",
					"Break 1 started at " + String.format("%02d:%02d", now.getHour(), now.getMinute()) + ".");
			return "redirect:/manager/attendance";
		}

		if (att.getBreakEnd() != null && att.getBreak2Start() == null) {
			att.setBreak2Start(now);
			attendanceRepository.save(att);
			ra.addFlashAttribute("successMessage",
					"Break 2 started at " + String.format("%02d:%02d", now.getHour(), now.getMinute()) + ".");
			return "redirect:/manager/attendance";
		}

		ra.addFlashAttribute("errorMessage", "No more breaks available today.");
		return "redirect:/manager/attendance";
	}

	/** Break End */
	@PostMapping("/attendance/break-end")
	public String breakEnd(RedirectAttributes ra) {
		User manager = getCurrentManager();
		if (manager == null) return "redirect:/manager/attendance";

		LocalDate today = LocalDate.now();
		if (hasApprovedLeave(manager, today)) {
			ra.addFlashAttribute("errorMessage", "You are on approved leave today. Break actions are not allowed.");
			return "redirect:/manager/attendance";
		}

		Optional<Attendance> opt = attendanceRepository.findByUserAndDate(manager, today);

		if (opt.isEmpty()) {
			ra.addFlashAttribute("errorMessage", "You haven't punched in today.");
			return "redirect:/manager/attendance";
		}
		Attendance att = opt.get();

		LocalTime now = LocalTime.now();

		if (att.getBreak2Start() != null && att.getBreak2End() == null) {
			att.setBreak2End(now);
			attendanceRepository.save(att);
			ra.addFlashAttribute("successMessage",
					"Break 2 ended at " + String.format("%02d:%02d", now.getHour(), now.getMinute()) + ".");
			return "redirect:/manager/attendance";
		}

		if (att.getBreakStart() != null && att.getBreakEnd() == null) {
			att.setBreakEnd(now);
			attendanceRepository.save(att);
			ra.addFlashAttribute("successMessage",
					"Break 1 ended at " + String.format("%02d:%02d", now.getHour(), now.getMinute()) + ".");
			return "redirect:/manager/attendance";
		}

		ra.addFlashAttribute("errorMessage", "No active break to end.");
		return "redirect:/manager/attendance";
	}

	// =========================
	// PERFORMANCE PAGE
	// =========================

	/** Inner DTO holding computed stats for one employee. */
	public static class EmployeePerf {
		public User   employee;
		public int    totalTasks;
		public int    doneTasks;
		public int    pendingTasks;
		public int    overdueTasks;
		public int    presentDays;
		public int    absentDays;
		public int    lateDays;
		public int    leaveDays;
		public int    attendanceScore; // 0-100
		public int    taskScore;       // 0-100
		public int    overallScore;    // 0-100
		public String grade;           // A+/A/B/C/D
		public PerformanceReview existingReview; // null if not yet reviewed this month
	}

	@GetMapping("/performance")
	public String performancePage(
			@RequestParam(required = false) String month,
			Model model) {

		injectStats(model);

		User manager = getCurrentManager();
		if (manager == null) return "manager-performance";

		String tenant = getTenantSegment(manager);

		// Default to current month
		java.time.YearMonth ym = (month != null && !month.isBlank())
				? java.time.YearMonth.parse(month)
				: java.time.YearMonth.now();

		LocalDate from = ym.atDay(1);
		LocalDate to   = ym.atEndOfMonth().isAfter(LocalDate.now()) ? LocalDate.now() : ym.atEndOfMonth();

		// Working days in the selected range (Mon–Fri, excluding today if future)
		int workingDays = 0;
		LocalDate d = from;
		while (!d.isAfter(to)) {
			java.time.DayOfWeek dow = d.getDayOfWeek();
			if (dow != java.time.DayOfWeek.SATURDAY && dow != java.time.DayOfWeek.SUNDAY) workingDays++;
			d = d.plusDays(1);
		}
		if (workingDays < 1) workingDays = 1;

		List<User> teamMembers = getManagedTeamMembers(manager);
		List<EmployeePerf> perfList = new ArrayList<>();

		for (User emp : teamMembers) {
			EmployeePerf p = new EmployeePerf();
			p.employee = emp;

			// ── Task stats ──────────────────────────────────────────────
			List<Task> tasks = taskRepository.findByAssignedToAndTenantSegment(emp.getUsername(), tenant);
			p.totalTasks   = tasks.size();
			p.doneTasks    = (int) tasks.stream().filter(t -> "done".equalsIgnoreCase(t.getStatus())).count();
			p.pendingTasks = (int) tasks.stream().filter(t -> "pending".equalsIgnoreCase(t.getStatus())).count();
			// Overdue = pending/in-progress tasks whose due date is before today
			p.overdueTasks = (int) tasks.stream()
					.filter(t -> !"done".equalsIgnoreCase(t.getStatus())
							&& t.getDueDate() != null
							&& !t.getDueDate().isBlank()
							&& LocalDate.parse(t.getDueDate()).isBefore(LocalDate.now()))
					.count();

			// Task score: done% out of total, penalise overdue
			if (p.totalTasks > 0) {
				double raw = ((double) p.doneTasks / p.totalTasks) * 100.0
						- (p.overdueTasks * 5.0);
				p.taskScore = (int) Math.max(0, Math.min(100, raw));
			} else {
				p.taskScore = 100; // no tasks = no penalty
			}

			// ── Attendance stats (selected month) ───────────────────────
			List<Attendance> attRecords = attendanceRepository
					.findByUserAndDateBetweenOrderByDateDesc(emp, from, to);

			p.presentDays = (int) attRecords.stream()
					.filter(a -> "present".equalsIgnoreCase(a.getStatus())).count();
			p.lateDays    = (int) attRecords.stream()
					.filter(a -> "late".equalsIgnoreCase(a.getStatus())).count();

			// Leave days in this month
			List<LeaveRequest> leaves = leaveRequestRepository.findByEmployeeOrderByCreatedAtDesc(emp);
			int leaveDaysCount = 0;
			for (LeaveRequest lr : leaves) {
				if (!"Approved".equalsIgnoreCase(lr.getStatus())) continue;
				if (lr.getFromDate() == null || lr.getToDate() == null) continue;
				LocalDate lStart = lr.getFromDate().isBefore(from) ? from : lr.getFromDate();
				LocalDate lEnd   = lr.getToDate().isAfter(to)      ? to   : lr.getToDate();
				if (lStart.isAfter(lEnd)) continue;
				LocalDate c = lStart;
				while (!c.isAfter(lEnd)) {
					java.time.DayOfWeek dow2 = c.getDayOfWeek();
					if (dow2 != java.time.DayOfWeek.SATURDAY && dow2 != java.time.DayOfWeek.SUNDAY) leaveDaysCount++;
					c = c.plusDays(1);
				}
			}
			p.leaveDays  = leaveDaysCount;
			int effectiveWorking = Math.max(1, workingDays - p.leaveDays);
			p.absentDays = Math.max(0, effectiveWorking - p.presentDays - p.lateDays);

			// Attendance score: present+late as % of effective working days, late counts half
			double attRaw = ((p.presentDays + p.lateDays * 0.5) / effectiveWorking) * 100.0;
			p.attendanceScore = (int) Math.max(0, Math.min(100, attRaw));

			// Overall: 60% task + 40% attendance
			p.overallScore = (int) (p.taskScore * 0.6 + p.attendanceScore * 0.4);

			// Grade
			p.grade = p.overallScore >= 90 ? "A+" :
			          p.overallScore >= 75 ? "A"  :
			          p.overallScore >= 60 ? "B"  :
			          p.overallScore >= 45 ? "C"  : "D";

			// Existing review this month
			p.existingReview = performanceReviewRepository
					.findByEmployeeAndReviewMonthAndTenantSegment(emp, ym.toString(), tenant)
					.orElse(null);

			perfList.add(p);
		}

		// Pre-compute summary stats (lambdas not supported in Thymeleaf SpEL)
		int avgScore = perfList.isEmpty() ? 0
				: (int) Math.round(perfList.stream().mapToInt(p -> p.overallScore).average().orElse(0));
		int totalDone = perfList.stream().mapToInt(p -> p.doneTasks).sum();
		long reviewedCount = perfList.stream().filter(p -> p.existingReview != null).count();

		model.addAttribute("perfList",      perfList);
		model.addAttribute("selectedMonth", ym.toString());
		model.addAttribute("workingDays",   workingDays);
		model.addAttribute("filterFrom",    from.toString());
		model.addAttribute("filterTo",      to.toString());
		model.addAttribute("avgScore",      avgScore);
		model.addAttribute("totalDone",     totalDone);
		model.addAttribute("reviewedCount", reviewedCount);

		return "manager-performance";
	}

	@PostMapping("/performance/review")
	public String saveReview(
			@RequestParam Long employeeId,
			@RequestParam int rating,
			@RequestParam(required = false) String remarks,
			@RequestParam String reviewMonth,
			RedirectAttributes ra) {

		User manager = getCurrentManager();
		if (manager == null) {
			ra.addFlashAttribute("errorMessage", "Session expired.");
			return "redirect:/manager/performance";
		}

		String tenant = getTenantSegment(manager);

		// Verify employee belongs to this manager's team
		List<User> teamMembers = getManagedTeamMembers(manager);
		User emp = teamMembers.stream()
				.filter(u -> u.getId().equals(employeeId))
				.findFirst().orElse(null);

		if (emp == null) {
			ra.addFlashAttribute("errorMessage", "Employee not found in your team.");
			return "redirect:/manager/performance?month=" + reviewMonth;
		}

		if (rating < 1 || rating > 5) {
			ra.addFlashAttribute("errorMessage", "Rating must be between 1 and 5.");
			return "redirect:/manager/performance?month=" + reviewMonth;
		}

		// Upsert — update existing review if one already exists for this month
		PerformanceReview review = performanceReviewRepository
				.findByEmployeeAndReviewMonthAndTenantSegment(emp, reviewMonth, tenant)
				.orElse(new PerformanceReview());

		review.setEmployee(emp);
		review.setReviewedBy(manager.getUsername());
		review.setTenantSegment(tenant);
		review.setReviewMonth(reviewMonth);
		review.setRating(rating);
		review.setRemarks(remarks != null ? remarks.trim() : "");
		review.setReviewedAt(System.currentTimeMillis());
		performanceReviewRepository.save(review);
		notificationService.notifyPerformanceReview(emp, manager.getUsername(), reviewMonth, rating);

		// Automatically send a report to the employee, HR, and Admin
		Report report = new Report();
		report.setTitle("Performance Review Update - " + reviewMonth);

		// Calculate stats for the report snapshot
		java.time.YearMonth ym = java.time.YearMonth.parse(reviewMonth);
		LocalDate from = ym.atDay(1);
		LocalDate to   = ym.atEndOfMonth().isAfter(LocalDate.now()) ? LocalDate.now() : ym.atEndOfMonth();

		int workingDays = 0;
		LocalDate d = from;
		while (!d.isAfter(to)) {
			java.time.DayOfWeek dow = d.getDayOfWeek();
			if (dow != java.time.DayOfWeek.SATURDAY && dow != java.time.DayOfWeek.SUNDAY) workingDays++;
			d = d.plusDays(1);
		}
		if (workingDays < 1) workingDays = 1;

		List<Task> tasks = taskRepository.findByAssignedToAndTenantSegment(emp.getUsername(), tenant);
		int totalT   = tasks.size();
		int doneT    = (int) tasks.stream().filter(t -> "done".equalsIgnoreCase(t.getStatus())).count();
		int pendingT = (int) tasks.stream().filter(t -> "pending".equalsIgnoreCase(t.getStatus())).count();
		int overdueT = (int) tasks.stream()
				.filter(t -> !"done".equalsIgnoreCase(t.getStatus())
						&& t.getDueDate() != null && !t.getDueDate().isBlank()
						&& LocalDate.parse(t.getDueDate()).isBefore(LocalDate.now()))
				.count();

		int taskScoreCalc = 100;
		if (totalT > 0) {
			double raw = ((double) doneT / totalT) * 100.0 - (overdueT * 5.0);
			taskScoreCalc = (int) Math.max(0, Math.min(100, raw));
		}

		List<Attendance> attRecords = attendanceRepository.findByUserAndDateBetweenOrderByDateDesc(emp, from, to);
		int presentD = (int) attRecords.stream().filter(a -> "present".equalsIgnoreCase(a.getStatus())).count();
		int lateD    = (int) attRecords.stream().filter(a -> "late".equalsIgnoreCase(a.getStatus())).count();

		List<LeaveRequest> leaves = leaveRequestRepository.findByEmployeeOrderByCreatedAtDesc(emp);
		int leaveD = 0;
		for (LeaveRequest lr : leaves) {
			if (!"Approved".equalsIgnoreCase(lr.getStatus()) || lr.getFromDate() == null) continue;
			LocalDate lStart = lr.getFromDate().isBefore(from) ? from : lr.getFromDate();
			LocalDate lEnd   = lr.getToDate() == null ? lStart : (lr.getToDate().isAfter(to) ? to : lr.getToDate());
			LocalDate c = lStart;
			while (!c.isAfter(lEnd)) {
				if (c.getDayOfWeek() != java.time.DayOfWeek.SATURDAY && c.getDayOfWeek() != java.time.DayOfWeek.SUNDAY) leaveD++;
				c = c.plusDays(1);
			}
		}
		int effWorking = Math.max(1, workingDays - leaveD);
		int absentD = Math.max(0, effWorking - presentD - lateD);
		double attRaw = ((presentD + lateD * 0.5) / effWorking) * 100.0;
		int attScore = (int) Math.max(0, Math.min(100, attRaw));
		int overall = (int) (taskScoreCalc * 0.6 + attScore * 0.4);
		String gradeStr = overall >= 90 ? "A+" : overall >= 75 ? "A" : overall >= 60 ? "B" : overall >= 45 ? "C" : "D";

		report.setMessage("A performance review has been updated for " + emp.getUsername() + ".\n" +
				"Month: " + reviewMonth + "\n" +
				"Rating: " + rating + "/5\n" +
				"Remarks: " + (remarks != null ? remarks.trim() : "None") + "\n\n" +
				"--- Performance Snapshot ---\n" +
				"Overall Score: " + overall + "% (" + gradeStr + ")\n" +
				"Task Score: " + taskScoreCalc + "% | Attendance Score: " + attScore + "%\n" +
				"Tasks: " + totalT + " total, " + doneT + " done, " + overdueT + " overdue\n" +
				"Attendance: " + presentD + " present, " + lateD + " late, " + absentD + " absent");

		report.setTaskScore(taskScoreCalc);
		report.setAttendanceScore(attScore);
		report.setOverallScore(overall);
		report.setGrade(gradeStr);
		report.setTotalTasks(totalT);
		report.setDoneTasks(doneT);
		report.setPendingTasks(pendingT);
		report.setOverdueTasks(overdueT);
		report.setPresentDays(presentD);
		report.setAbsentDays(absentD);
		report.setLateDays(lateD);
		report.setLeaveDays(leaveD);

		report.setSentBy(manager.getUsername());
		report.setTenantSegment(tenant);

		List<User> recipients = new ArrayList<>();
		recipients.add(emp);
		List<User> hrAndAdmins = userRepository.findByTenantSegment(tenant).stream()
				.filter(u -> "HR".equalsIgnoreCase(u.getRole()) || "ADMIN".equalsIgnoreCase(u.getRole()))
				.filter(u -> !u.getId().equals(emp.getId()))
				.toList();
		recipients.addAll(hrAndAdmins);

		String idsCsv   = recipients.stream().map(u -> String.valueOf(u.getId())).collect(java.util.stream.Collectors.joining(","));
		String namesCsv = recipients.stream().map(User::getUsername).collect(java.util.stream.Collectors.joining(", "));
		report.setRecipientIds(idsCsv);
		report.setRecipientNames(namesCsv);
		reportRepository.save(report);

		ra.addFlashAttribute("successMessage",
				"Performance review updated and report sent to employee, HR, and Admin.");
		return "redirect:/manager/performance?month=" + reviewMonth;
	}
}
