package com.crm.demo.controller;
import java.io.IOException;
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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
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
import java.util.stream.Collectors;

import com.crm.demo.model.Attendance;
import com.crm.demo.model.AttendanceDay;
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

import com.crm.demo.repository.MeetingRepository;
import com.crm.demo.repository.PerformanceReviewRepository;
import com.crm.demo.repository.ProjectRepository;
import com.crm.demo.repository.ReportAttachmentRepository;
import com.crm.demo.repository.ReportRepository;
import com.crm.demo.repository.TaskRepository;
import com.crm.demo.repository.TaskAttachmentRepository;
import com.crm.demo.repository.TeamRepository;

import com.crm.demo.model.PayrollTemplate;
import com.crm.demo.repository.PayrollTemplateRepository;
import com.crm.demo.model.Payslip;
import com.crm.demo.repository.PayslipRepository;
import com.crm.demo.service.PayslipService;
import com.crm.demo.service.NotificationService;
import com.crm.demo.service.ProfileUpdateService;
import com.crm.demo.service.AttendanceService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/manager")
public class ManagerController extends BaseController {

	private static final String ROLE_ADMIN = "ADMIN";
	private static final String ROLE_EMPLOYEE = "EMPLOYEE";
	private static final String ROLE_HR = "HR";
	private static final String ATTR_ERROR = "error";
	private static final String ATTR_ERROR_MESSAGE = "errorMessage";
	private static final String ATTR_SUCCESS_MESSAGE = "successMessage";
	private static final String REDIRECT_MANAGER_TASKS = "redirect:/manager/tasks";
	private static final String REDIRECT_MANAGER_LEAVES = "redirect:/manager/leaves";
	private static final String REDIRECT_MANAGER_MEETINGS = "redirect:/manager/meetings";
	private static final String REDIRECT_MANAGER_ATTENDANCE = "redirect:/manager/attendance";
	private static final String REDIRECT_LOGIN = "redirect:/login";
	private static final String PARAM_ERROR = "?error";
	private static final String STATUS_PENDING = "pending";
	private static final String STATUS_APPROVED = "Approved";
	private static final String STATUS_APPROVED_LOWER = "approved";
	private static final String STATUS_REJECTED = "rejected";
	private static final String STATUS_IN_PROGRESS = "in-progress";
	private static final String STATUS_WAITING_FOR_REVIEW = "waiting-for-review";
	private static final String PRIORITY_MEDIUM = "Medium";
	private static final String STATUS_PRESENT = "present";
	private static final String STATUS_ABSENT = "absent";
	private static final String OCTET_STREAM = "application/octet-stream";
	private static final String TIME_FORMAT = "%02d:%02d";
	private static final String MSG_SESSION_EXPIRED = "Session expired. Please log in again.";
	private static final String MSG_MEETING_NOT_FOUND = "Meeting not found.";
	private static final String MSG_NOT_PUNCHED_IN = "You haven't punched in today.";
	private static final String DATE_REGEX = "^\\d{4}-\\d{2}-\\d{2}$";
	private static final String ATTR_TEAM_MEMBERS = "teamMembers";
	private static final String ATTR_ACTIVE_TEAM = "activeTeam";
	private static final String ATTR_INACTIVE_TEAM = "inactiveTeam";
	private static final String ATTR_TASKS = "tasks";
	private static final String ATTR_TOTAL_TASKS = "totalTasks";
	private static final String ATTR_DONE_TASKS = "doneTasks";
	private static final String ATTR_PENDING_TASK_COUNT = "pendingTaskCount";
	private static final String ATTR_MEETINGS = "meetings";
	private static final String ATTR_PAST_MEETINGS = "pastMeetings";
	private static final String ATTR_MEETING_FORM = "meetingForm";
	private static final String PAGE_MEETINGS = "manager-meetings";
	private static final String ATTR_PERF_LIST = "perfList";
	private static final String ATTR_SELECTED_MONTH = "selectedMonth";
	private static final String REDIRECT_MANAGER_REPORTS = "redirect:/manager/reports";
	private static final String REDIRECT_MANAGER_PERFORMANCE = "redirect:/manager/performance?month=";

	@Value("${app.upload.dir:uploads/tasks}")
	private String uploadDir;

	@Autowired
	private AttendanceService attendanceService;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private TaskRepository taskRepository;

	@Autowired
	private AttendanceRepository attendanceRepository;

	@Autowired
	private TeamRepository teamRepository;

	@Autowired
	private MeetingRepository meetingRepository;

	@Autowired
	private TaskAttachmentRepository taskAttachmentRepository;

	@Autowired
	private ReportRepository reportRepository;

	@Autowired
	private ReportAttachmentRepository reportAttachmentRepository;

	@Autowired
	private PerformanceReviewRepository performanceReviewRepository;

	@Autowired
	private ProfileUpdateService profileUpdateService;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private PayrollTemplateRepository payrollTemplateRepository;

	@Autowired
	private PayslipRepository payslipRepository;

	@Autowired
	private PayslipService payslipService;
	// =========================
	private void injectStats(Model model) {

		// Logged-in manager username
		var currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();

		var manager = userRepository.findByUsername(currentUsername);

		// Safety check
		if (manager == null) {
			model.addAttribute("managerName", "Manager");
			model.addAttribute("managerEmail", "");
			model.addAttribute(ATTR_TEAM_MEMBERS, Collections.emptyList());
			model.addAttribute("teamCount", 0);
			model.addAttribute(ATTR_ACTIVE_TEAM, 0);
			model.addAttribute(ATTR_INACTIVE_TEAM, 0);
			model.addAttribute("myTeam", null);
			model.addAttribute("myTeamName", "—");
			model.addAttribute("managedTeams", Collections.emptyList());
			model.addAttribute("projects", Collections.emptyList());
			model.addAttribute("totalProjects", 0);
			model.addAttribute("activeProjects", 0);
			model.addAttribute("completedProjects", 0);
			model.addAttribute("projectCount", 0);
			model.addAttribute(ATTR_TASKS, Collections.emptyList());
			model.addAttribute(ATTR_TOTAL_TASKS, 0);
			model.addAttribute(ATTR_DONE_TASKS, 0);
			model.addAttribute(ATTR_PENDING_TASK_COUNT, 0);
			model.addAttribute("taskCount", 0);
			model.addAttribute("overdueTasks", 0);
			model.addAttribute("notificationCount", 0);
			model.addAttribute("pendingTaskList", Collections.emptyList());
			return;
		}

		model.addAttribute("managerName", manager.getUsername());
		model.addAttribute("managerEmail", manager.getEmail());

		// ── Load team(s) assigned to this manager ────────────────────────────
		var myTeam = getPrimaryTeam(manager);
		var managedTeams = getManagedTeams(manager);
		var teamMembers = getManagedTeamMembers(manager);

		var active   = teamMembers.stream().filter(User::isActive).count();
		var inactive = teamMembers.size() - active;

		model.addAttribute("myTeam",      myTeam);
		model.addAttribute("myTeamName",  getManagedTeamName(manager));
		model.addAttribute("managedTeams",managedTeams);
		model.addAttribute(ATTR_TEAM_MEMBERS, teamMembers);
		model.addAttribute("teamCount",   teamMembers.size());
		model.addAttribute(ATTR_ACTIVE_TEAM,  active);
		model.addAttribute(ATTR_INACTIVE_TEAM,inactive);

		// ── Projects & Tasks ──────────────────────────────────────────────
		var projects = projectRepository.findAll();
		projects.sort(java.util.Comparator.comparing(Project::getId).reversed());
		// For stats only — scoped tasks loaded per-page where needed
		var tasks    = taskRepository.findAll();

		var doneCount = tasks.stream().filter(t -> "done".equalsIgnoreCase(t.getStatus())).count();
		var pending = tasks.stream().filter(t -> STATUS_PENDING.equalsIgnoreCase(t.getStatus())).count();
		var activeP = projects.stream().filter(p -> "active".equalsIgnoreCase(p.getStatus())).count();
		var completedP = projects.stream().filter(p -> "completed".equalsIgnoreCase(p.getStatus())).count();

		model.addAttribute("projects",          projects);
		model.addAttribute("totalProjects",     projects.size());
		model.addAttribute("activeProjects",    activeP);
		model.addAttribute("completedProjects", completedP);
		model.addAttribute("projectCount",      projects.size());

		model.addAttribute(ATTR_TASKS,             tasks);
		model.addAttribute(ATTR_TOTAL_TASKS,        tasks.size());
		model.addAttribute(ATTR_DONE_TASKS,         doneCount);
		model.addAttribute(ATTR_PENDING_TASK_COUNT,  pending);
		model.addAttribute("taskCount",         tasks.size());
		model.addAttribute("overdueTasks",      pending);
		model.addAttribute("notificationCount", 0);
		model.addAttribute("pendingTaskList",   Collections.emptyList());
	}

	private String validateTaskParams(String title, String description, String priority, String status, String dueDate) {
		if (title == null || title.trim().isBlank()) {
			return "Task title is required.";
		}
		if (title.trim().length() > 255) {
			return "Task title cannot exceed 255 characters.";
		}
		if (description != null && description.length() > 255) {
			return "Description cannot exceed 255 characters.";
		}
		if (priority == null || (!"High".equalsIgnoreCase(priority) && !PRIORITY_MEDIUM.equalsIgnoreCase(priority) && !"Low".equalsIgnoreCase(priority))) {
			return "Invalid priority selected.";
		}
		if (status == null || (!STATUS_PENDING.equalsIgnoreCase(status) && !STATUS_IN_PROGRESS.equalsIgnoreCase(status) && !"done".equalsIgnoreCase(status))) {
			return "Invalid status selected.";
		}
		if (dueDate == null || dueDate.trim().isBlank() || !dueDate.trim().matches(DATE_REGEX)) {
			return "Please select a valid due date.";
		}
		return null;
	}

	private String validateLeaveParams(String type, String reason, String fromDate, String toDate) {
		if (type == null || type.isBlank()) {
			return "Leave type is required.";
		}
		if (reason == null || reason.isBlank()) {
			return "Leave reason is required.";
		}
		if (reason.trim().length() > 255) {
			return "Reason cannot exceed 255 characters.";
		}
		if (fromDate == null || fromDate.isBlank() || !fromDate.matches(DATE_REGEX)) {
			return "Invalid start date format.";
		}
		if (toDate == null || toDate.isBlank() || !toDate.matches(DATE_REGEX)) {
			return "Invalid end date format.";
		}
		return null;
	}

	private List<EmployeePerf> computeEmployeePerformance(List<User> teamMembers, java.time.YearMonth ym, String tenant) {
		var from = ym.atDay(1);
		var to   = ym.atEndOfMonth().isAfter(LocalDate.now()) ? LocalDate.now() : ym.atEndOfMonth();

		var workingDays = calculateWorkingDays(from, to);
		var perfList = new ArrayList<EmployeePerf>();

		for (var emp : teamMembers) {
			perfList.add(getSingleEmployeePerformance(emp, from, to, workingDays, tenant, ym.toString()));
		}
		return perfList;
	}

	private void populatePunchModel(Optional<Attendance> todayOpt, boolean todayOnLeave, Model model) {
		var punchedIn  = todayOpt.isPresent();
		var punchedOut = todayOpt.map(a -> a.getCheckOut() != null).orElse(false);
		var onBreak    = todayOpt.map(a ->
				(a.getBreakStart() != null && a.getBreakEnd() == null) ||
				(a.getBreak2Start() != null && a.getBreak2End() == null)).orElse(false);
		var breakDone  = todayOpt.map(a -> a.getBreak2End() != null).orElse(false);
		var canStartBreak = punchedIn && !punchedOut && !onBreak && !breakDone &&
				todayOpt.map(a -> a.getBreakStart() == null ||
						(a.getBreakEnd() != null && a.getBreak2Start() == null)).orElse(false);

		model.addAttribute("punchedIn",         punchedIn);
		model.addAttribute("punchedOut",        punchedOut);
		model.addAttribute("onBreak",           onBreak);
		model.addAttribute("breakDone",         breakDone);
		model.addAttribute("canStartBreak",     canStartBreak);
		model.addAttribute("todayOnLeave",      todayOnLeave);
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
		if (manager == null) {
			return Collections.emptyList();
		}
		return getManagedTeams(manager).stream()
				.filter(team -> team != null && team.getMembers() != null)
				.flatMap(team -> team.getMembers().stream())
				.filter(member -> member != null && member.getId() != null)
				.collect(Collectors.toMap(User::getId, member -> member, (m1, m2) -> m1, LinkedHashMap::new))
				.values().stream()
				.collect(Collectors.toList());
	}

	private String getManagedTeamName(User manager) {
		List<Team> teams = getManagedTeams(manager);
		if (teams.isEmpty()) {
			return "No Team Assigned";
		}
		if (teams.size() == 1) {
			return teams.get(0).getName();
		}
		return String.join(", ", teams.stream().map(Team::getName).collect(Collectors.toList()));
	}

	// =========================
	// DASHBOARD PAGE
	// =========================
	@GetMapping("/dashboard")
	public String dashboard(Model model) {

		injectStats(model);

		// ── Additional analytics data for charts ──────────────────────────
		var currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
		var manager = userRepository.findByUsername(currentUsername);

		if (manager != null) {
			var tenant = getTenantSegmentFromEmail(manager.getEmail());
			var teamMembers = getManagedTeamMembers(manager);

			// Scope tasks to this manager's created tasks within their tenant
			var myTasks = taskRepository.findByCreatedByAndTenantSegment(
					currentUsername, tenant);

			// Task status breakdown
			var statusDone       = myTasks.stream().filter(t -> "done".equalsIgnoreCase(t.getStatus())).count();
			var statusInProgress = myTasks.stream().filter(t -> STATUS_IN_PROGRESS.equalsIgnoreCase(t.getStatus())).count();
			var statusPending    = myTasks.stream().filter(t -> STATUS_PENDING.equalsIgnoreCase(t.getStatus())).count();
			var statusReview     = myTasks.stream().filter(t -> STATUS_WAITING_FOR_REVIEW.equalsIgnoreCase(t.getStatus())).count();

			model.addAttribute("chartStatusDone",       statusDone);
			model.addAttribute("chartStatusInProgress", statusInProgress);
			model.addAttribute("chartStatusPending",    statusPending);
			model.addAttribute("chartStatusReview",     statusReview);

			// Task priority breakdown
			var priorityHigh   = myTasks.stream().filter(t -> "High".equalsIgnoreCase(t.getPriority())).count();
			var priorityMedium = myTasks.stream().filter(t -> PRIORITY_MEDIUM.equalsIgnoreCase(t.getPriority())).count();
			var priorityLow    = myTasks.stream().filter(t -> "Low".equalsIgnoreCase(t.getPriority())).count();

			model.addAttribute("chartPriorityHigh",   priorityHigh);
			model.addAttribute("chartPriorityMedium", priorityMedium);
			model.addAttribute("chartPriorityLow",    priorityLow);

			// Per-member task count (bar chart)
			var memberLabels = new ArrayList<String>();
			var memberTaskCounts = new ArrayList<Long>();
			for (var member : teamMembers) {
				var count = myTasks.stream()
						.filter(t -> member.getUsername().equalsIgnoreCase(t.getAssignedTo()))
						.count();
				memberLabels.add(member.getUsername());
				memberTaskCounts.add(count);
			}
			model.addAttribute("chartMemberLabels",     memberLabels);
			model.addAttribute("chartMemberTaskCounts", memberTaskCounts);

			// Team active vs inactive
			var activeCount   = teamMembers.stream().filter(User::isActive).count();
			var inactiveCount = teamMembers.size() - activeCount;
			model.addAttribute("chartActiveTeam",   activeCount);
			model.addAttribute("chartInactiveTeam", inactiveCount);

			// Verification status breakdown
			var verified = myTasks.stream().filter(t -> STATUS_APPROVED_LOWER.equalsIgnoreCase(t.getVerificationStatus())).count();
			var rejected = myTasks.stream().filter(t -> STATUS_REJECTED.equalsIgnoreCase(t.getVerificationStatus())).count();
			var waiting  = myTasks.stream().filter(t -> STATUS_WAITING_FOR_REVIEW.equalsIgnoreCase(t.getVerificationStatus())).count();
			var unverified = myTasks.size() - verified - rejected - waiting;

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
		var currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
		var manager = userRepository.findByUsername(currentUsername);
		if (manager == null) {
			return buildDashboardAnalytics(Collections.emptyList(), Collections.emptyList());
		}

		var tenant = getTenantSegmentFromEmail(manager.getEmail());
		var teamMembers = getManagedTeamMembers(manager);
		var myTasks = taskRepository.findByCreatedByAndTenantSegment(currentUsername, tenant);
		return buildDashboardAnalytics(myTasks, teamMembers);
	}

	private Map<String, Object> buildDashboardAnalytics(List<Task> tasks, List<User> people) {
		var data = new LinkedHashMap<String, Object>();
		var scopedTasks = tasks != null ? tasks : Collections.<Task>emptyList();
		var scopedPeople = people != null ? people : Collections.<User>emptyList();

		var statusDone = scopedTasks.stream().filter(t -> "done".equalsIgnoreCase(t.getStatus())).count();
		var statusInProgress = scopedTasks.stream().filter(t -> STATUS_IN_PROGRESS.equalsIgnoreCase(t.getStatus())).count();
		var statusPending = scopedTasks.stream().filter(t -> STATUS_PENDING.equalsIgnoreCase(t.getStatus())).count();
		var statusReview = scopedTasks.stream().filter(t -> STATUS_WAITING_FOR_REVIEW.equalsIgnoreCase(t.getStatus())).count();
		var priorityHigh = scopedTasks.stream().filter(t -> "High".equalsIgnoreCase(t.getPriority())).count();
		var priorityMedium = scopedTasks.stream().filter(t -> PRIORITY_MEDIUM.equalsIgnoreCase(t.getPriority())).count();
		var priorityLow = scopedTasks.stream().filter(t -> "Low".equalsIgnoreCase(t.getPriority())).count();

		var memberLabels = new ArrayList<String>();
		var memberTaskCounts = new ArrayList<Long>();
		for (var member : scopedPeople) {
			var count = scopedTasks.stream()
					.filter(t -> member.getUsername() != null && member.getUsername().equalsIgnoreCase(t.getAssignedTo()))
					.count();
			memberLabels.add(member.getUsername());
			memberTaskCounts.add(count);
		}

		var activeCount = scopedPeople.stream().filter(User::isActive).count();
		var inactiveCount = scopedPeople.size() - activeCount;
		var verified = scopedTasks.stream().filter(t -> STATUS_APPROVED_LOWER.equalsIgnoreCase(t.getVerificationStatus())).count();
		var rejected = scopedTasks.stream().filter(t -> STATUS_REJECTED.equalsIgnoreCase(t.getVerificationStatus())).count();
		var waiting = scopedTasks.stream().filter(t -> STATUS_WAITING_FOR_REVIEW.equalsIgnoreCase(t.getVerificationStatus())).count();
		var unverified = scopedTasks.size() - verified - rejected - waiting;

		data.put("statusDone", statusDone);
		data.put("statusInProgress", statusInProgress);
		data.put("statusPending", statusPending);
		data.put("statusReview", statusReview);
		data.put("priorityHigh", priorityHigh);
		data.put("priorityMedium", priorityMedium);
		data.put("priorityLow", priorityLow);
		data.put("memberLabels", memberLabels);
		data.put("memberTaskCounts", memberTaskCounts);
		data.put(ATTR_ACTIVE_TEAM, activeCount);
		data.put(ATTR_INACTIVE_TEAM, inactiveCount);
		data.put("verified", verified);
		data.put(STATUS_REJECTED, rejected);
		data.put("waiting", waiting);
		data.put("unverified", Math.max(unverified, 0));
		data.put("totalMyTasks", scopedTasks.size());
		return data;
	}

	private String getTenantSegmentFromEmail(String email) {
		if (email == null || !email.contains("@")) return "";
		var local = email.substring(0, email.indexOf('@'));
		var dot = local.lastIndexOf('.');
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
		attendanceService.processAutoPunchOuts();
		var resp = new LinkedHashMap<String, Object>();

		var manager = getCurrentManager();
		if (manager == null) { resp.put(ATTR_ERROR, "Not authenticated."); return resp; }

		// Verify the requested user is actually in this manager's team(s)
		var myTeams = getManagedTeams(manager);
		var inTeam = myTeams.stream().flatMap(t -> t.getMembers().stream()).anyMatch(m -> m.getId().equals(id));
		if (!inTeam) { resp.put(ATTR_ERROR, "Member not found in your team."); return resp; }

		var user = userRepository.findById(id).orElse(null);
		if (user == null) { resp.put(ATTR_ERROR, "User not found."); return resp; }

		// Profile
		resp.put("id",       user.getId());
		resp.put("username", user.getUsername());
		resp.put("email",    user.getEmail());
		resp.put("role",     user.getRole());
		resp.put("status",   user.getStatus());

		// Last 30 days attendance
		var today  = LocalDate.now();
		var from   = today.minusDays(29);
		var tenant = getTenantSegment(manager);
		var holidays = fetchHolidays(tenant, from, today);
		var records = attendanceRepository.findByUserAndDateBetweenOrderByDateDesc(user, from, today);
		var days = buildDayList(records, from, today, holidays, user);

		var present = days.stream().filter(d -> STATUS_PRESENT.equals(d.getStatus()) || "late".equals(d.getStatus())).count();
		var absent  = days.stream().filter(d -> STATUS_ABSENT.equals(d.getStatus())).count();
		var halfDay = days.stream().filter(d -> "half-day".equals(d.getStatus())).count();
		var holiday = days.stream().filter(d -> "holiday".equals(d.getStatus())).count();
		resp.put("presentDays", present);
		resp.put("absentDays",  absent);
		resp.put("halfDays",    halfDay);
		resp.put("holidays",    holiday);

		var rows = new ArrayList<Map<String, String>>();
		for (var d : days) {
			var row = new LinkedHashMap<String, String>();
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
			tasks.sort(java.util.Comparator.comparing(Task::getId).reversed());
			long done    = tasks.stream().filter(t -> "done".equalsIgnoreCase(t.getStatus())).count();
			long pending = tasks.stream().filter(t -> STATUS_PENDING.equalsIgnoreCase(t.getStatus())).count();
			model.addAttribute(ATTR_TASKS,            tasks);
			model.addAttribute(ATTR_TOTAL_TASKS,       tasks.size());
			model.addAttribute(ATTR_DONE_TASKS,        done);
			model.addAttribute(ATTR_PENDING_TASK_COUNT, pending);

			// Pass team members as "employees" for the assign dropdown
			List<User> teamMembers = getManagedTeamMembers(manager);
			model.addAttribute("employees", teamMembers);

			List<Team> teams = getManagedTeams(manager);
			model.addAttribute("teams", teams);
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
			@RequestParam(value = "assignedToIds", required = false) List<Long> assignedToIds,
			@RequestParam(value = "assignToTeam", required = false, defaultValue = "false") boolean assignToTeam,
			@RequestParam(value = "assignedToTeamId", required = false) String assignedToTeamId,
			@RequestParam(value = "attachments", required = false) MultipartFile[] attachments,
			RedirectAttributes ra) {

		var manager = getCurrentManager();
		if (manager == null) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, MSG_SESSION_EXPIRED);
			return REDIRECT_LOGIN;
		}

		var validationError = validateTaskParams(title, description, priority, status, dueDate);
		if (validationError != null) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, validationError);
			return REDIRECT_MANAGER_TASKS;
		}

		var parsedDates = new LocalDate[2]; // [0] = start, [1] = due
		var dateError = validateTaskDates(startDate, dueDate, parsedDates);
		if (dateError != null) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, dateError);
			return REDIRECT_MANAGER_TASKS;
		}

		var tenant = getTenantSegment(manager);
		var teamMembers = getManagedTeamMembers(manager);
		var targetUsers = new ArrayList<User>();
		var groupNameHolder = new String[]{""};

		var resolveError = resolveTargetUsers(assignToTeam, assignedToTeamId, assignedToIds, manager, teamMembers, targetUsers, groupNameHolder);
		if (resolveError != null) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, resolveError);
			return REDIRECT_MANAGER_TASKS;
		}

		var attachmentInfos = new ArrayList<TaskAttachmentInfo>();
		var uploadError = processAttachments(attachments, attachmentInfos);
		if (uploadError != null) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, uploadError);
			return REDIRECT_MANAGER_TASKS;
		}

		for (var targetUser : targetUsers) {
			var task = new Task();
			task.setTitle(title.trim());
			task.setDescription(description);
			task.setPriority(priority);
			task.setStatus(status);
			task.setStartDate(startDate);
			task.setDueDate(dueDate);
			task.setAssignedTo(targetUser.getUsername());
			task.setAssignedToId(targetUser.getId());
			task.setTenantSegment(tenant);
			task.setCreatedBy(manager.getUsername());
			taskRepository.save(task);

			var uploadedNames = new ArrayList<String>();
			for (var info : attachmentInfos) {
				var taskAttachment = new TaskAttachment(
					task,
					info.filename(),
					info.fileData(),
					info.contentType(),
					manager.getUsername()
				);
				taskAttachmentRepository.save(taskAttachment);
				uploadedNames.add(info.filename());
			}

			if (!uploadedNames.isEmpty()) {
				task.setAttachmentPaths(String.join(",", uploadedNames));
				taskRepository.save(task);
			}

			notificationService.notifyTaskAssigned(targetUser, manager.getUsername(), task.getTitle());
		}

		if (assignToTeam) {
			ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Task assigned to the " + groupNameHolder[0] + " successfully.");
		} else {
			if (targetUsers.size() == 1) {
				ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Task assigned to " + targetUsers.get(0).getUsername() + " successfully.");
			} else {
				ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Task assigned to " + targetUsers.size() + " employees successfully.");
			}
		}
		return REDIRECT_MANAGER_TASKS;
	}

	// =========================
	// LIST TASK ATTACHMENTS (REST — for modal)
	// =========================
	@GetMapping("/api/task/{taskId}/attachments")
	@ResponseBody
	public List<Map<String, Object>> listTaskAttachments(@PathVariable Long taskId) {
		var task = taskRepository.findById(taskId).orElse(null);
		if (task == null) return Collections.emptyList();

		var result = new ArrayList<Map<String, Object>>();
		if (task.getAttachments() != null) {
			for (var att : task.getAttachments()) {
				var item = new LinkedHashMap<String, Object>();
				item.put("id",           att.getId());
				item.put("filename",     att.getOriginalFilename());
				item.put("contentType",  att.getContentType() != null ? att.getContentType() : OCTET_STREAM);
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
	public ResponseEntity<byte[]> downloadAttachment(@PathVariable Long attachmentId) {
		TaskAttachment attachment = taskAttachmentRepository.findById(attachmentId).orElse(null);
		if (attachment == null) {
			return ResponseEntity.notFound().build();
		}

		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + attachment.getOriginalFilename() + "\"")
				.header(HttpHeaders.CONTENT_TYPE, attachment.getContentType() != null ? attachment.getContentType() : OCTET_STREAM)
				.body(attachment.getFileData());
	}

	// =========================
	// VIEW TASK ATTACHMENT INLINE
	// =========================
	@GetMapping("/tasks/view/{attachmentId}")
	public ResponseEntity<byte[]> viewAttachment(@PathVariable Long attachmentId) {
		TaskAttachment attachment = taskAttachmentRepository.findById(attachmentId).orElse(null);
		if (attachment == null) {
			return ResponseEntity.notFound().build();
		}

		String contentType = attachment.getContentType() != null ? attachment.getContentType() : OCTET_STREAM;
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
		var manager = getCurrentManager();
		if (manager == null) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, MSG_SESSION_EXPIRED);
			return REDIRECT_LOGIN;
		}

		var tenant = getTenantSegment(manager);
		var task = getAndValidateTask(id, tenant, ra);
		if (task == null) {
			return REDIRECT_MANAGER_TASKS;
		}

		java.time.LocalDateTime now = java.time.LocalDateTime.now();
		var timestamp = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

		if ("approve".equalsIgnoreCase(action)) {
			// Manager confirms the work is complete
			task.setStatus("done");
			task.setVerificationStatus(STATUS_APPROVED_LOWER);
			task.setLastVerifiedBy(manager.getUsername());
			task.setLastVerifiedAt(timestamp);
			task.setVerificationReason(null);
			ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Task verified and marked as done.");

		} else if ("reject".equalsIgnoreCase(action)) {
			if (reason == null || reason.trim().isBlank()) {
				ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Reason for return is required.");
				return REDIRECT_MANAGER_TASKS;
			}
			if (reason.trim().length() > 255) {
				ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Reason cannot exceed 255 characters.");
				return REDIRECT_MANAGER_TASKS;
			}
			// Manager returns the task — employee must redo and resubmit
			task.setStatus(STATUS_IN_PROGRESS);
			task.setVerificationStatus(STATUS_REJECTED);
			task.setLastVerifiedBy(manager.getUsername());
			task.setLastVerifiedAt(timestamp);
			task.setVerificationReason(reason.trim());
			ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Task returned to employee for rework.");

		} else if ("reopen".equalsIgnoreCase(action)) {
			// Manager re-opens a previously verified task
			task.setStatus(STATUS_IN_PROGRESS);
			task.setVerificationStatus(STATUS_PENDING);
			task.setLastVerifiedBy(manager.getUsername());
			task.setLastVerifiedAt(timestamp);
			task.setVerificationReason(null);
			ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Task marked as incomplete and returned to employee.");
		}

		taskRepository.save(task);

		var assignee = resolveTaskAssignee(task);
		if (assignee != null) {
			notificationService.notifyTaskVerified(
					assignee, manager.getUsername(), task.getTitle(), action, reason);
		}

		return REDIRECT_MANAGER_TASKS;
	}

	private User resolveTaskAssignee(Task task) {
		if (task == null) return null;
		if (task.getAssignedToId() != null) {
			var byId = userRepository.findById(task.getAssignedToId()).orElse(null);
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
		var manager = getCurrentManager();
		var tenant = manager != null ? getTenantSegment(manager) : "";

		var task = getAndValidateTask(id, tenant, ra);
		if (task != null) {
			taskRepository.deleteById(id);
			ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Task deleted successfully.");
		}
		return REDIRECT_MANAGER_TASKS;
	}

	// =========================
	// LEAVE REQUESTS PAGE
	// =========================
	@GetMapping("/leave")
	public String legacyLeaveRedirect() {
		return REDIRECT_MANAGER_LEAVES;
	}

	@GetMapping("/leaves")
	public String leavesPage(Model model) {
		injectStats(model);

		User manager = getCurrentManager();
		if (manager != null) {
			String tenant = getTenantSegment(manager);
			List<LeaveRequest> requests = leaveRequestRepository.findByEmployeeOrderByCreatedAtDesc(manager);
			long pending = leaveRequestRepository.countByEmployeeAndStatus(manager, "Pending");
			long approved = leaveRequestRepository.countByEmployeeAndStatus(manager, STATUS_APPROVED);
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
	@SuppressWarnings("java:S3516")
	public String submitLeave(@RequestParam String type,
	                          @RequestParam String fromDate,
	                          @RequestParam String toDate,
	                          @RequestParam String reason,
	                          @RequestParam(value = "attachment", required = false) MultipartFile attachment,
	                          RedirectAttributes ra) {
		var manager = getCurrentManager();
		if (manager == null) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, MSG_SESSION_EXPIRED);
			return REDIRECT_MANAGER_LEAVES + PARAM_ERROR;
		}

		var validationError = validateLeaveParams(type, reason, fromDate, toDate);
		if (validationError != null) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, validationError);
			return REDIRECT_MANAGER_LEAVES + PARAM_ERROR;
		}

		LocalDate from;
		LocalDate to;
		try {
			from = LocalDate.parse(fromDate);
			to = LocalDate.parse(toDate);
		} catch (java.time.format.DateTimeParseException e) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Invalid date value.");
			return REDIRECT_MANAGER_LEAVES + PARAM_ERROR;
		}

		if (to.isBefore(from)) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "To date cannot be before from date.");
			return REDIRECT_MANAGER_LEAVES + PARAM_ERROR;
		}

		var leave = new LeaveRequest();
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
				leave.setAttachmentContentType(attachment.getContentType() != null ? attachment.getContentType() : OCTET_STREAM);
				leave.setAttachmentData(attachment.getBytes());
			} catch (IOException e) {
				ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Attachment upload failed: " + e.getMessage());
				return REDIRECT_MANAGER_LEAVES + PARAM_ERROR;
			}
		}

		leaveRequestRepository.save(leave);
		notificationService.notifyLeaveSubmitted(leave);
		ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Leave request submitted successfully.");
		return REDIRECT_MANAGER_LEAVES + "?success";
	}

	// =========================
	// REPORTS PAGE
	// =========================
	@GetMapping("/reports")
	public String reportsPage(Model model) {

		injectStats(model);

		var manager = getCurrentManager();
		if (manager != null) {
			var tenant = getTenantSegment(manager);

			// Team members with full performance stats
			var teamMembers = getManagedTeamMembers(manager);
			model.addAttribute(ATTR_TEAM_MEMBERS, teamMembers);

			// All possible recipients (Team members + HR + Admin)
			var allTenantUsers = userRepository.findByTenantSegment(tenant);
			var allRecipients = allTenantUsers.stream()
					.filter(u -> ROLE_HR.equalsIgnoreCase(u.getRole()) || ROLE_ADMIN.equalsIgnoreCase(u.getRole()) || teamMembers.contains(u))
					.distinct()
					.collect(Collectors.toList());
			model.addAttribute("allRecipients", allRecipients);

			// Sent reports history (for "Send Report" tracking)
			var sentReports = reportRepository
					.findBySentByAndTenantSegmentOrderBySentAtDesc(manager.getUsername(), tenant);
			model.addAttribute("sentReports", sentReports);
			model.addAttribute("sentReportCount", sentReports.size());

			// Build per-employee detail for click-to-view
			var ym = java.time.YearMonth.now();
			var perfList = computeEmployeePerformance(teamMembers, ym, tenant);
			model.addAttribute(ATTR_PERF_LIST,      perfList);
			model.addAttribute(ATTR_SELECTED_MONTH, ym.toString());
		} else {
			model.addAttribute(ATTR_TEAM_MEMBERS, java.util.Collections.emptyList());
			model.addAttribute("sentReports", java.util.Collections.emptyList());
			model.addAttribute("sentReportCount", 0);
			model.addAttribute(ATTR_PERF_LIST, java.util.Collections.emptyList());
			model.addAttribute(ATTR_SELECTED_MONTH, java.time.YearMonth.now().toString());
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

		var manager = getCurrentManager();
		if (manager == null) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, MSG_SESSION_EXPIRED);
			return REDIRECT_LOGIN;
		}

		var paramError = validateReportParams(title, message, recipientIds);
		if (paramError != null) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, paramError);
			return REDIRECT_MANAGER_REPORTS;
		}

		var tenant = getTenantSegment(manager);

		// Verify recipients are in this manager's tenant and have valid roles (Employee in team, HR, or Admin)
		var allTenantUsers = userRepository.findByTenantSegment(tenant);
		var teamMembers = getManagedTeamMembers(manager);
		var validRecipients = allTenantUsers.stream()
				.filter(u -> recipientIds.contains(u.getId()))
				.filter(u -> ROLE_HR.equalsIgnoreCase(u.getRole()) || ROLE_ADMIN.equalsIgnoreCase(u.getRole()) || teamMembers.contains(u))
				.collect(Collectors.toList());

		if (validRecipients.size() != recipientIds.size()) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "One or more selected recipients are invalid or outside your tenant.");
			return REDIRECT_MANAGER_REPORTS;
		}

		// Build CSV strings for IDs and names
		var idsCsv   = validRecipients.stream().map(u -> String.valueOf(u.getId())).collect(java.util.stream.Collectors.joining(","));
		var namesCsv = validRecipients.stream().map(User::getUsername).collect(java.util.stream.Collectors.joining(", "));

		var report = new Report();
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
		var attachError = processReportAttachments(report, attachments);
		if (attachError != null) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, attachError);
			return REDIRECT_MANAGER_REPORTS;
		}

		for (var recipient : validRecipients) {
			notificationService.notifyReportReceived(recipient, manager.getUsername(), title.trim());
		}

		ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Report sent to " + validRecipients.size() + " recipient(s) successfully.");
		return REDIRECT_MANAGER_REPORTS;
	}

	// =========================
	// VIEW REPORT ATTACHMENT INLINE
	// =========================
	@GetMapping("/reports/view/{attachmentId}")
	public ResponseEntity<byte[]> viewReportAttachment(@PathVariable Long attachmentId) {
		ReportAttachment att = reportAttachmentRepository.findById(attachmentId).orElse(null);
		if (att == null) return ResponseEntity.notFound().build();
		String ct = att.getContentType() != null ? att.getContentType() : OCTET_STREAM;
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + att.getOriginalFilename() + "\"")
				.header(HttpHeaders.CONTENT_TYPE, ct)
				.body(att.getFileData());
	}

	// =========================
	// DOWNLOAD REPORT ATTACHMENT
	// =========================
	@GetMapping("/reports/download/{attachmentId}")
	public ResponseEntity<byte[]> downloadReportAttachment(@PathVariable Long attachmentId) {
		ReportAttachment att = reportAttachmentRepository.findById(attachmentId).orElse(null);
		if (att == null) return ResponseEntity.notFound().build();
		String ct = att.getContentType() != null ? att.getContentType() : OCTET_STREAM;
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
	private List<Meeting> getPastMeetings(String tenant, String username) {
		var all = meetingRepository.findAllMeetingsForUserOrHost(tenant, username);
		var today = LocalDate.now();
		var now   = LocalTime.now();
		return all.stream().filter(m -> {
			if (m.getMeetingDate().isBefore(today)) return true;
			if (!m.getMeetingDate().equals(today)) return false;
			if (m.getMeetingTime() == null) return false;
			int dur = (m.getDuration() != null) ? m.getDuration() : 0;
			return m.getMeetingTime().plusMinutes(dur).isBefore(now);
		}).collect(Collectors.toList());
	}

	/** Returns upcoming meetings (today + future) where the user is a participant OR the host,
	 *  excluding today's meetings that have already ended. */
	private List<Meeting> getUpcomingMeetings(String tenant, String username) {
		var all = meetingRepository
				.findUpcomingMeetingsForUserOrHost(tenant, username, LocalDate.now());
		var today = LocalDate.now();
		var now   = LocalTime.now();
		return all.stream().filter(m -> {
			if (!m.getMeetingDate().equals(today)) return true;
			if (m.getMeetingTime() == null) return true;
			int dur = (m.getDuration() != null) ? m.getDuration() : 0;
			return !m.getMeetingTime().plusMinutes(dur).isBefore(now);
		}).collect(Collectors.toList());
	}

	/** GET /manager/meetings — show schedule form + list of meetings where manager is a participant */
	@GetMapping("/meetings")
	public String meetingsPage(Model model) {
		injectStats(model);
		User manager = getCurrentManager();
		if (manager != null) {
			String tenant   = getTenantSegment(manager);
			String username = manager.getUsername();
			model.addAttribute(ATTR_MEETINGS, getUpcomingMeetings(tenant, username));
			model.addAttribute(ATTR_PAST_MEETINGS, getPastMeetings(tenant, username));
			// Team members available as participants
			model.addAttribute(ATTR_TEAM_MEMBERS, getManagedTeamMembers(manager));
		} else {
			model.addAttribute(ATTR_MEETINGS, Collections.emptyList());
			model.addAttribute(ATTR_TEAM_MEMBERS, Collections.emptyList());
		}
		if (!model.containsAttribute(ATTR_MEETING_FORM)) {
			model.addAttribute(ATTR_MEETING_FORM, new Meeting());
		}
		return PAGE_MEETINGS;
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

		if ("in-person".equalsIgnoreCase(meetingForm.getMeetingType()) && (meetingForm.getLocation() == null || meetingForm.getLocation().isBlank())) {
			result.rejectValue("location", "NotBlank", "Location is required for in-person meetings.");
		}

		if (result.hasErrors()) {
			injectStats(model);
			if (manager != null) {
				model.addAttribute(ATTR_MEETINGS, getUpcomingMeetings(tenant, username));
				model.addAttribute(ATTR_PAST_MEETINGS, getPastMeetings(tenant, username));
				model.addAttribute(ATTR_TEAM_MEMBERS, getManagedTeamMembers(manager));
			} else {
				model.addAttribute(ATTR_MEETINGS, Collections.emptyList());
				model.addAttribute(ATTR_TEAM_MEMBERS, Collections.emptyList());
			}
			model.addAttribute(ATTR_ERROR_MESSAGE, "Please fix the errors below.");
			return PAGE_MEETINGS;
		}

		meetingForm.setTenantSegment(tenant);
		meetingForm.setScheduledBy(username);
		meetingRepository.save(meetingForm);
		notificationService.notifyMeetingParticipants(meetingForm);
		ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Meeting scheduled successfully.");
		return REDIRECT_MANAGER_MEETINGS;
	}

	/** GET /manager/meetings/edit/{id} — load meeting into form */
	@GetMapping("/meetings/edit/{id}")
	public String editMeetingPage(@PathVariable Long id, Model model, RedirectAttributes ra) {
		var manager = getCurrentManager();
		var tenant   = manager != null ? getTenantSegment(manager) : "";
		var username = manager != null ? manager.getUsername() : "";

		var meeting = getAndValidateMeeting(id, tenant, ra);
		if (meeting == null) {
			return REDIRECT_MANAGER_MEETINGS;
		}

		injectStats(model);
		model.addAttribute(ATTR_MEETING_FORM, meeting);
		model.addAttribute(ATTR_MEETINGS, getUpcomingMeetings(tenant, username));
		model.addAttribute(ATTR_PAST_MEETINGS, getPastMeetings(tenant, username));
		model.addAttribute(ATTR_TEAM_MEMBERS, getManagedTeamMembers(manager));
		return PAGE_MEETINGS;
	}

	/** POST /manager/meetings/edit/{id} — update existing meeting */
	@PostMapping("/meetings/edit/{id}")
	public String updateMeeting(@PathVariable Long id,
	                            @Valid @ModelAttribute("meetingForm") Meeting meetingForm,
	                            BindingResult result,
	                            Model model,
	                            RedirectAttributes ra) {
		var manager = getCurrentManager();
		var tenant   = manager != null ? getTenantSegment(manager) : "";
		var username = manager != null ? manager.getUsername() : "";

		if ("in-person".equalsIgnoreCase(meetingForm.getMeetingType()) && (meetingForm.getLocation() == null || meetingForm.getLocation().isBlank())) {
			result.rejectValue("location", "NotBlank", "Location is required for in-person meetings.");
		}

		if (result.hasErrors()) {
			injectStats(model);
			model.addAttribute(ATTR_MEETINGS, getUpcomingMeetings(tenant, username));
			model.addAttribute(ATTR_PAST_MEETINGS, getPastMeetings(tenant, username));
			model.addAttribute(ATTR_TEAM_MEMBERS, getManagedTeamMembers(manager));
			model.addAttribute(ATTR_ERROR_MESSAGE, "Please fix the errors below.");
			return PAGE_MEETINGS;
		}

		var existing = getAndValidateMeeting(id, tenant, ra);
		if (existing == null) {
			return REDIRECT_MANAGER_MEETINGS;
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

		ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Meeting updated successfully.");
		return REDIRECT_MANAGER_MEETINGS;
	}

	/** POST /manager/meetings/delete/{id} — delete a meeting */
	@PostMapping("/meetings/delete/{id}")
	public String deleteMeeting(@PathVariable Long id, RedirectAttributes ra) {
		var manager = getCurrentManager();
		var tenant = manager != null ? getTenantSegment(manager) : "";

		var meeting = getAndValidateMeeting(id, tenant, ra);
		if (meeting != null) {
			meetingRepository.delete(meeting);
			ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Meeting deleted successfully.");
		}
		return REDIRECT_MANAGER_MEETINGS;
	}

	// =========================
	// PROFILE PAGE
	// =========================
	@GetMapping("/profile")
	public String profilePage(Model model) {

		injectStats(model);

		return "manager-profile";
	}

	// =========================
	// UPDATE PROFILE
	// =========================
	@PostMapping("/update-profile")
	public String updateProfile(@RequestParam(required = false) String username,
								@RequestParam(required = false) String email,
								@RequestParam(required = false) String password,
								@RequestParam(required = false) String confirmPassword,
								HttpServletResponse response,
								RedirectAttributes ra) {

		var currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
		var manager = userRepository.findByUsername(currentUsername);

		if (manager == null) {
			return "redirect:/manager/profile?error=auth";
		}

		var success = profileUpdateService.updateProfile(manager, username, email, password, confirmPassword, ra, response);
		return success ? "redirect:/manager/profile?success" : "redirect:/manager/profile?error=validation";
	}

	// ═══════════════════════════════════════════════════════════════════════
	//  ATTENDANCE PAGE
	// ═══════════════════════════════════════════════════════════════════════

	/** Helper: resolve current manager + tenant segment */
	private User getCurrentManager() {
		return getCurrentUser();
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
		var byDate = new LinkedHashMap<LocalDate, Attendance>();
		for (var a : records) byDate.put(a.getDate(), a);

		var approvedLeaveDates = getApprovedLeaveDates(user, from, to);

		var days = new ArrayList<AttendanceDay>();
		var today  = LocalDate.now();
		var cursor = to;
		while (!cursor.isBefore(from)) {
			var day = buildAttendanceDayForDate(cursor, holidays, byDate, approvedLeaveDates, today);
			if (day != null) {
				days.add(day);
			}
			cursor = cursor.minusDays(1);
		}
		return days;
	}


	@GetMapping("/attendance")
	public String attendancePage(
			@RequestParam(required = false) String from,
			@RequestParam(required = false) String to,
			@RequestParam(required = false) String status,
			Model model) {
		attendanceService.processAutoPunchOuts();
		injectStats(model);

		var manager = getCurrentManager();
		if (manager == null) {
			return "redirect:/manager/dashboard";
		}

		var today = LocalDate.now();

		// Date range (default: last 30 days)
		LocalDate filterFrom = (from != null && !from.isBlank()) ? LocalDate.parse(from) : today.minusDays(29);
		LocalDate filterTo   = (to   != null && !to.isBlank())   ? LocalDate.parse(to)   : today;
		if (filterFrom == null) filterFrom = today.minusDays(29);
		if (filterTo == null) filterTo = today;
		if (filterTo != null && filterTo.isAfter(today))      filterTo   = today;
		if (filterFrom != null && filterTo != null && filterFrom.isAfter(filterTo)) filterFrom = filterTo;

		// Fetch real records in range
		var tenant = getTenantSegment(manager);
		var records = attendanceRepository
				.findByUserAndDateBetweenOrderByDateDesc(manager, filterFrom, filterTo);

		// Holidays in range
		var holidays = fetchHolidays(tenant, filterFrom, filterTo);

		// Today's record — drives punch-in / punch-out button state
		var todayOpt = attendanceRepository.findByUserAndDate(manager, today);

		// Is today a holiday?
		var todayHolidayName = holidays.get(today);
		var isHolidayToday  = todayHolidayName != null;

		var todayOnLeave = hasApprovedLeave(manager, today);

		// Build merged day list (fills leave/absent/weekend/holiday gaps)
		var allDays = buildDayList(records, filterFrom, filterTo, holidays, manager);

		// Apply status filter
		var filteredDays = allDays;
		if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
			filteredDays = allDays.stream()
					.filter(d -> d.getStatus().equalsIgnoreCase(status))
					.collect(Collectors.toList());
		}

		// Stats from all-time records
		var allRecords = attendanceRepository.findByUserOrderByDateDesc(manager);
		var presentCount = allRecords.stream()
				.filter(a -> STATUS_PRESENT.equalsIgnoreCase(a.getStatus()) || "late".equalsIgnoreCase(a.getStatus()))
				.count();
		var lateCount = allRecords.stream()
				.filter(a -> "late".equalsIgnoreCase(a.getStatus()))
				.count();

		model.addAttribute("attendanceDays",    filteredDays);
		model.addAttribute("totalRecords",      filteredDays.size());
		model.addAttribute("todayRecord",       todayOpt.orElse(null));
		populatePunchModel(todayOpt, todayOnLeave, model);
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
		var manager = getCurrentManager();
		if (manager == null) return REDIRECT_LOGIN;

		var tenant = getTenantSegment(manager);
		var today  = LocalDate.now();

		// Block punch-in on holidays
		if (holidayRepository.findByDateAndTenantSegment(today.toString(), tenant).isPresent()) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Today is a holiday. Punch-in is not allowed.");
			return REDIRECT_MANAGER_ATTENDANCE;
		}

		if (hasApprovedLeave(manager, today)) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "You are on approved leave today. Punch-in is not allowed.");
			return REDIRECT_MANAGER_ATTENDANCE;
		}

		// Prevent duplicate punch-in
		if (attendanceRepository.findByUserAndDate(manager, today).isPresent()) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "You have already punched in today.");
			return REDIRECT_MANAGER_ATTENDANCE;
		}

		var now    = LocalTime.now();
		var status = now.isAfter(LocalTime.of(9, 30)) ? "late" : STATUS_PRESENT;

		var att = new Attendance();
		att.setUser(manager);
		att.setDate(today);
		att.setCheckIn(now);
		att.setStatus(status);
		att.setTenantSegment(tenant);
		attendanceRepository.save(att);
		notificationService.notifyAttendanceUpdated(manager, "punch-in");

		ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE,
				"Punched in at " + String.format(TIME_FORMAT, now.getHour(), now.getMinute()) + ".");
		return REDIRECT_MANAGER_ATTENDANCE;
	}

	/** Punch Out */
	@PostMapping("/attendance/punch-out")
	public String punchOut(RedirectAttributes ra) {
		var manager = getCurrentManager();
		if (manager == null) return REDIRECT_LOGIN;

		var att = getAndValidateTodayAttendance(manager, "You are on approved leave today. Punch-out is not allowed.", ra);
		if (att == null) {
			return REDIRECT_MANAGER_ATTENDANCE;
		}

		if (att.getCheckOut() != null) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "You have already punched out today.");
			return REDIRECT_MANAGER_ATTENDANCE;
		}

		var now = LocalTime.now();
		att.setCheckOut(now);
		
		// Recalculate status based on worked hours:
		var mins = att.getWorkedMinutes();
		if (mins >= 0 && mins < 240) {
			att.setStatus(STATUS_ABSENT);
		} else if (mins >= 240 && mins < 360) {
			att.setStatus("half-day");
		}
		
		attendanceRepository.save(att);
		notificationService.notifyAttendanceUpdated(manager, "punch-out");

		ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE,
				"Punched out at " + String.format(TIME_FORMAT, now.getHour(), now.getMinute()) + ".");
		return REDIRECT_MANAGER_ATTENDANCE;
	}

	/** Break Start */
	@PostMapping("/attendance/break-start")
	public String breakStart(RedirectAttributes ra) {
		var manager = getCurrentManager();
		if (manager == null) return REDIRECT_LOGIN;

		var att = getAndValidateTodayAttendance(manager, "You are on approved leave today. Break actions are not allowed.", ra);
		if (att == null) {
			return REDIRECT_MANAGER_ATTENDANCE;
		}
		if (att.getCheckOut() != null) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "You have already punched out.");
			return REDIRECT_MANAGER_ATTENDANCE;
		}

		var now = LocalTime.now();

		if (att.getBreakStart() == null) {
			att.setBreakStart(now);
			attendanceRepository.save(att);
			notificationService.notifyAttendanceUpdated(manager, "break-1-start");
			ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE,
					"Break 1 started at " + String.format(TIME_FORMAT, now.getHour(), now.getMinute()) + ".");
			return REDIRECT_MANAGER_ATTENDANCE;
		}

		if (att.getBreakEnd() != null && att.getBreak2Start() == null) {
			att.setBreak2Start(now);
			attendanceRepository.save(att);
			notificationService.notifyAttendanceUpdated(manager, "break-2-start");
			ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE,
					"Break 2 started at " + String.format(TIME_FORMAT, now.getHour(), now.getMinute()) + ".");
			return REDIRECT_MANAGER_ATTENDANCE;
		}

		ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "No more breaks available today.");
		return REDIRECT_MANAGER_ATTENDANCE;
	}

	/** Break End */
	@PostMapping("/attendance/break-end")
	public String breakEnd(RedirectAttributes ra) {
		var manager = getCurrentManager();
		if (manager == null) return REDIRECT_LOGIN;

		var att = getAndValidateTodayAttendance(manager, "You are on approved leave today. Break actions are not allowed.", ra);
		if (att == null) {
			return REDIRECT_MANAGER_ATTENDANCE;
		}

		var now = LocalTime.now();

		if (att.getBreak2Start() != null && att.getBreak2End() == null) {
			att.setBreak2End(now);
			attendanceRepository.save(att);
			notificationService.notifyAttendanceUpdated(manager, "break-2-end");
			ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE,
					"Break 2 ended at " + String.format(TIME_FORMAT, now.getHour(), now.getMinute()) + ".");
			return REDIRECT_MANAGER_ATTENDANCE;
		}

		if (att.getBreakStart() != null && att.getBreakEnd() == null) {
			att.setBreakEnd(now);
			attendanceRepository.save(att);
			notificationService.notifyAttendanceUpdated(manager, "break-1-end");
			ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE,
					"Break 1 ended at " + String.format(TIME_FORMAT, now.getHour(), now.getMinute()) + ".");
			return REDIRECT_MANAGER_ATTENDANCE;
		}

		ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "No active break to end.");
		return REDIRECT_MANAGER_ATTENDANCE;
	}

	// =========================
	// PERFORMANCE PAGE
	// =========================

	/** Inner DTO holding computed stats for one employee. */
	public static class EmployeePerf {
		private User   employee;
		private int    totalTasks;
		private int    doneTasks;
		private int    pendingTasks;
		private int    overdueTasks;
		private int    presentDays;
		private int    absentDays;
		private int    lateDays;
		private int    leaveDays;
		private int    attendanceScore; // 0-100
		private int    taskScore;       // 0-100
		private int    overallScore;    // 0-100
		private String grade;           // A+/A/B/C/D
		private PerformanceReview existingReview; // null if not yet reviewed this month
		private boolean weeklyLocked;

		public User getEmployee() { return employee; }
		public void setEmployee(User employee) { this.employee = employee; }

		public int getTotalTasks() { return totalTasks; }
		public void setTotalTasks(int totalTasks) { this.totalTasks = totalTasks; }

		public int getDoneTasks() { return doneTasks; }
		public void setDoneTasks(int doneTasks) { this.doneTasks = doneTasks; }

		public int getPendingTasks() { return pendingTasks; }
		public void setPendingTasks(int pendingTasks) { this.pendingTasks = pendingTasks; }

		public int getOverdueTasks() { return overdueTasks; }
		public void setOverdueTasks(int overdueTasks) { this.overdueTasks = overdueTasks; }

		public int getPresentDays() { return presentDays; }
		public void setPresentDays(int presentDays) { this.presentDays = presentDays; }

		public int getAbsentDays() { return absentDays; }
		public void setAbsentDays(int absentDays) { this.absentDays = absentDays; }

		public int getLateDays() { return lateDays; }
		public void setLateDays(int lateDays) { this.lateDays = lateDays; }

		public int getLeaveDays() { return leaveDays; }
		public void setLeaveDays(int leaveDays) { this.leaveDays = leaveDays; }

		public int getAttendanceScore() { return attendanceScore; }
		public void setAttendanceScore(int attendanceScore) { this.attendanceScore = attendanceScore; }

		public int getTaskScore() { return taskScore; }
		public void setTaskScore(int taskScore) { this.taskScore = taskScore; }

		public int getOverallScore() { return overallScore; }
		public void setOverallScore(int overallScore) { this.overallScore = overallScore; }

		public String getGrade() { return grade; }
		public void setGrade(String grade) { this.grade = grade; }

		public PerformanceReview getExistingReview() { return existingReview; }
		public void setExistingReview(PerformanceReview existingReview) { this.existingReview = existingReview; }

		public boolean isWeeklyLocked() { return weeklyLocked; }
		public void setWeeklyLocked(boolean weeklyLocked) { this.weeklyLocked = weeklyLocked; }
	}

	private boolean isWeeklyLocked(User employee) {
		return performanceReviewRepository.findByEmployeeOrderByReviewMonthDesc(employee).stream()
				.max(java.util.Comparator.comparing(PerformanceReview::getReviewedAt))
				.map(PerformanceReview::getReviewedAt)
				.map(reviewedAt -> System.currentTimeMillis() - reviewedAt < 7L * 24 * 60 * 60 * 1000)
				.orElse(false);
	}

	@GetMapping("/performance")
	public String performancePage(
			@RequestParam(required = false) String month,
			Model model) {

		attendanceService.processAutoPunchOuts();
		injectStats(model);

		var manager = getCurrentManager();
		if (manager == null) return "manager-performance";

		var tenant = getTenantSegment(manager);

		// Default to current month
		var ym = (month != null && !month.isBlank())
				? java.time.YearMonth.parse(month)
				: java.time.YearMonth.now();

		var from = ym.atDay(1);
		var to   = ym.atEndOfMonth().isAfter(LocalDate.now()) ? LocalDate.now() : ym.atEndOfMonth();

		// Working days in the selected range (Mon–Fri, excluding today if future)
		var workingDays = 0;
		var d = from;
		while (!d.isAfter(to)) {
			var dow = d.getDayOfWeek();
			if (dow != java.time.DayOfWeek.SATURDAY && dow != java.time.DayOfWeek.SUNDAY) workingDays++;
			d = d.plusDays(1);
		}
		if (workingDays < 1) workingDays = 1;

		var teamMembers = getManagedTeamMembers(manager).stream()
				.filter(u -> ROLE_EMPLOYEE.equalsIgnoreCase(u.getRole()))
				.collect(Collectors.toList());
		var perfList = computeEmployeePerformance(teamMembers, ym, tenant);

		// Pre-compute summary stats (lambdas not supported in Thymeleaf SpEL)
		var avgScore = perfList.isEmpty() ? 0
				: (int) Math.round(perfList.stream().mapToInt(EmployeePerf::getOverallScore).average().orElse(0));
		var totalDone = perfList.stream().mapToInt(EmployeePerf::getDoneTasks).sum();
		var reviewedCount = perfList.stream().filter(p -> p.getExistingReview() != null).count();

		model.addAttribute(ATTR_PERF_LIST,      perfList);
		model.addAttribute(ATTR_SELECTED_MONTH, ym.toString());
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
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, MSG_SESSION_EXPIRED);
			return "redirect:/manager/performance";
		}

		if (reviewMonth == null || reviewMonth.trim().isBlank() || !reviewMonth.trim().matches("^\\d{4}-\\d{2}$")) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Invalid review month format.");
			return "redirect:/manager/performance";
		}

		if (remarks != null && remarks.length() > 255) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Remarks cannot exceed 255 characters.");
			return REDIRECT_MANAGER_PERFORMANCE + reviewMonth;
		}

		String tenant = getTenantSegment(manager);

		// Verify employee belongs to this manager's team
		List<User> teamMembers = getManagedTeamMembers(manager);
		User emp = teamMembers.stream()
				.filter(u -> u.getId().equals(employeeId))
				.findFirst().orElse(null);

		if (emp == null) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Employee not found in your team.");
			return REDIRECT_MANAGER_PERFORMANCE + reviewMonth;
		}

		if (rating < 1 || rating > 5) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Rating must be between 1 and 5.");
			return REDIRECT_MANAGER_PERFORMANCE + reviewMonth;
		}

		Optional<PerformanceReview> existingOpt = performanceReviewRepository
				.findByEmployeeAndReviewMonthAndTenantSegment(emp, reviewMonth, tenant);
		if (existingOpt.isPresent()) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Performance reviews cannot be updated once submitted.");
			return REDIRECT_MANAGER_PERFORMANCE + reviewMonth;
		}

		if (isWeeklyLocked(emp)) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "You can only submit a performance review once a week for this employee.");
			return REDIRECT_MANAGER_PERFORMANCE + reviewMonth;
		}

		var review = new PerformanceReview();
		review.setEmployee(emp);
		review.setReviewedBy(manager.getUsername());
		review.setTenantSegment(tenant);
		review.setReviewMonth(reviewMonth);
		review.setRating(rating);
		review.setRemarks(remarks != null ? remarks.trim() : "");
		review.setReviewedAt(System.currentTimeMillis());
		performanceReviewRepository.save(review);
		notificationService.notifyPerformanceReview(emp, manager.getUsername(), reviewMonth, rating);

		ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE,
				"Performance review submitted successfully.");
		return REDIRECT_MANAGER_PERFORMANCE + reviewMonth;
	}

	private static class TaskAttachmentInfo {
		private final String filename;
		private final byte[] fileData;
		private final String contentType;

		public TaskAttachmentInfo(String filename, byte[] fileData, String contentType) {
			this.filename = filename;
			this.fileData = fileData;
			this.contentType = contentType;
		}

		public String filename() {
			return filename;
		}

		public byte[] fileData() {
			return fileData;
		}

		public String contentType() {
			return contentType;
		}
	}

	private Attendance getAndValidateTodayAttendance(User user, String actionMsg, RedirectAttributes ra) {
		var today = LocalDate.now();
		if (hasApprovedLeave(user, today)) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, actionMsg);
			return null;
		}
		var opt = attendanceRepository.findByUserAndDate(user, today).orElse(null);
		if (opt == null) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, MSG_NOT_PUNCHED_IN);
			return null;
		}
		return opt;
	}

	private Meeting getAndValidateMeeting(Long id, String tenant, RedirectAttributes ra) {
		var meeting = meetingRepository.findById(id).orElse(null);
		if (meeting == null || !tenant.equals(meeting.getTenantSegment())) {
			if (ra != null) {
				ra.addFlashAttribute(ATTR_ERROR_MESSAGE, MSG_MEETING_NOT_FOUND);
			}
			return null;
		}
		return meeting;
	}

	private Task getAndValidateTask(Long id, String tenant, RedirectAttributes ra) {
		var task = taskRepository.findById(id).orElse(null);
		if (task == null || !tenant.equals(task.getTenantSegment())) {
			if (ra != null) {
				ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Task not found.");
			}
			return null;
		}
		return task;
	}

	private String determineGrade(int score) {
		if (score >= 90) return "A+";
		if (score >= 75) return "A";
		if (score >= 60) return "B";
		if (score >= 45) return "C";
		return "D";
	}

	private int calculateWorkingDays(LocalDate from, LocalDate to) {
		var workingDays = 0;
		var d = from;
		while (!d.isAfter(to)) {
			var dow = d.getDayOfWeek();
			if (dow != java.time.DayOfWeek.SATURDAY && dow != java.time.DayOfWeek.SUNDAY) {
				workingDays++;
			}
			d = d.plusDays(1);
		}
		return Math.max(1, workingDays);
	}

	private int calculateLeaveDays(User emp, LocalDate from, LocalDate to) {
		var leaves = leaveRequestRepository.findByEmployeeOrderByCreatedAtDesc(emp);
		var leaveDaysCount = 0;
		for (var lr : leaves) {
			leaveDaysCount += calculateOverlapLeaveDays(lr, from, to);
		}
		return leaveDaysCount;
	}

	private int calculateOverlapLeaveDays(LeaveRequest lr, LocalDate from, LocalDate to) {
		if (lr == null || !STATUS_APPROVED.equalsIgnoreCase(lr.getStatus()) || lr.getFromDate() == null || lr.getToDate() == null) {
			return 0;
		}
		var lStart = lr.getFromDate().isBefore(from) ? from : lr.getFromDate();
		var lEnd   = lr.getToDate().isAfter(to)      ? to   : lr.getToDate();
		if (lStart.isAfter(lEnd)) {
			return 0;
		}
		var count = 0;
		var c = lStart;
		while (!c.isAfter(lEnd)) {
			var dow2 = c.getDayOfWeek();
			if (dow2 != java.time.DayOfWeek.SATURDAY && dow2 != java.time.DayOfWeek.SUNDAY) {
				count++;
			}
			c = c.plusDays(1);
		}
		return count;
	}

	private EmployeePerf getSingleEmployeePerformance(User emp, LocalDate from, LocalDate to, int workingDays, String tenant, String ymString) {
		var p = new EmployeePerf();
		p.setEmployee(emp);

		// Task stats
		var tasks = taskRepository.findByAssignedToAndTenantSegment(emp.getUsername(), tenant);
		p.setTotalTasks(tasks.size());
		p.setDoneTasks((int) tasks.stream().filter(t -> "done".equalsIgnoreCase(t.getStatus())).count());
		p.setPendingTasks((int) tasks.stream().filter(t -> STATUS_PENDING.equalsIgnoreCase(t.getStatus())).count());
		p.setOverdueTasks((int) tasks.stream()
				.filter(t -> !"done".equalsIgnoreCase(t.getStatus())
						&& t.getDueDate() != null
						&& !t.getDueDate().isBlank()
						&& LocalDate.parse(t.getDueDate()).isBefore(LocalDate.now()))
				.count());

		if (p.getTotalTasks() > 0) {
			double raw = ((double) p.getDoneTasks() / p.getTotalTasks()) * 100.0
					- (p.getOverdueTasks() * 5.0);
			p.setTaskScore((int) Math.max(0, Math.min(100, raw)));
		} else {
			p.setTaskScore(100);
		}

		// Attendance stats
		var attRecords = attendanceRepository.findByUserAndDateBetweenOrderByDateDesc(emp, from, to);
		p.setPresentDays((int) attRecords.stream().filter(a -> STATUS_PRESENT.equalsIgnoreCase(a.getStatus())).count());
		p.setLateDays((int) attRecords.stream().filter(a -> "late".equalsIgnoreCase(a.getStatus())).count());

		p.setLeaveDays(calculateLeaveDays(emp, from, to));
		var effectiveWorking = Math.max(1, workingDays - p.getLeaveDays());
		p.setAbsentDays(Math.max(0, effectiveWorking - p.getPresentDays() - p.getLateDays()));

		double attRaw = ((p.getPresentDays() + p.getLateDays() * 0.5) / effectiveWorking) * 100.0;
		p.setAttendanceScore((int) Math.max(0, Math.min(100, attRaw)));

		p.setOverallScore((int) (p.getTaskScore() * 0.6 + p.getAttendanceScore() * 0.4));
		p.setGrade(determineGrade(p.getOverallScore()));

		p.setExistingReview(performanceReviewRepository.findByEmployeeAndReviewMonthAndTenantSegment(emp, ymString, tenant).orElse(null));
		p.setWeeklyLocked(isWeeklyLocked(emp));

		return p;
	}

	private String validateTaskDates(String startDate, String dueDate, LocalDate[] parsedDates) {
		try {
			parsedDates[1] = LocalDate.parse(dueDate.trim());
		} catch (java.time.format.DateTimeParseException e) {
			return "Invalid due date value.";
		}
		if (parsedDates[1].isBefore(LocalDate.now())) {
			return "Due date cannot be in the past.";
		}
		if (startDate != null && !startDate.trim().isEmpty()) {
			if (!startDate.trim().matches(DATE_REGEX)) {
				return "Invalid start date format.";
			}
			try {
				parsedDates[0] = LocalDate.parse(startDate.trim());
			} catch (java.time.format.DateTimeParseException e) {
				return "Invalid start date value.";
			}
			if (parsedDates[0].isAfter(parsedDates[1])) {
				return "Start date cannot be after due date.";
			}
		}
		return null;
	}

	private String resolveTargetUsers(
			boolean assignToTeam,
			String assignedToTeamId,
			List<Long> assignedToIds,
			User manager,
			List<User> teamMembers,
			List<User> targetUsers,
			String[] groupNameHolder) {
		if (assignToTeam) {
			return resolveTargetUsersForTeam(assignedToTeamId, manager, teamMembers, targetUsers, groupNameHolder);
		} else {
			return resolveTargetUsersForIndividual(assignedToIds, teamMembers, targetUsers);
		}
	}

	private String resolveTargetUsersForTeam(
			String assignedToTeamId,
			User manager,
			List<User> teamMembers,
			List<User> targetUsers,
			String[] groupNameHolder) {
		if (assignedToTeamId == null || "all".equalsIgnoreCase(assignedToTeamId)) {
			if (teamMembers == null || teamMembers.isEmpty()) {
				return "You do not have any employees in your team to assign tasks to.";
			}
			targetUsers.addAll(teamMembers);
			groupNameHolder[0] = "entire team";
			return null;
		}
		try {
			var teamId = Long.parseLong(assignedToTeamId);
			var managedTeams = getManagedTeams(manager);
			var targetTeam = managedTeams.stream()
					.filter(t -> t.getId().equals(teamId))
					.findFirst()
					.orElse(null);

			if (targetTeam == null) {
				return "Selected team is not managed by you.";
			}

			var members = targetTeam.getMembers();
			if (members == null || members.isEmpty()) {
				return "Selected team '" + targetTeam.getName() + "' does not have any members.";
			}
			targetUsers.addAll(members);
			groupNameHolder[0] = "team " + targetTeam.getName();
			return null;
		} catch (NumberFormatException e) {
			return "Invalid team ID selected.";
		}
	}

	private String resolveTargetUsersForIndividual(
			List<Long> assignedToIds,
			List<User> teamMembers,
			List<User> targetUsers) {
		if (assignedToIds == null || assignedToIds.isEmpty()) {
			return "Please select at least one employee to assign the task.";
		}
		for (var empId : assignedToIds) {
			var assignedUser = teamMembers.stream()
					.filter(u -> u.getId().equals(empId))
					.findFirst()
					.orElse(null);

			if (assignedUser == null) {
				return "One or more selected employees are not in your team.";
			}
			targetUsers.add(assignedUser);
		}
		return null;
	}

	private String processAttachments(MultipartFile[] attachments, List<TaskAttachmentInfo> attachmentInfos) {
		if (attachments != null) {
			for (var file : attachments) {
				if (file == null || file.isEmpty()) continue;
				try {
					var fileData = file.getBytes();
					var contentType = file.getContentType();
					if (contentType == null) contentType = OCTET_STREAM;
					attachmentInfos.add(new TaskAttachmentInfo(file.getOriginalFilename(), fileData, contentType));
				} catch (IOException e) {
					return "File upload failed: " + e.getMessage();
				}
			}
		}
		return null;
	}

	private String validateReportParams(String title, String message, List<Long> recipientIds) {
		if (title == null || title.isBlank()) {
			return "Report title is required.";
		}
		if (title.trim().length() > 200) {
			return "Report title cannot exceed 200 characters.";
		}
		if (message != null && message.length() > 255) {
			return "Message cannot exceed 255 characters.";
		}
		if (recipientIds == null || recipientIds.isEmpty()) {
			return "Please select at least one recipient.";
		}
		return null;
	}

	private String processReportAttachments(Report report, MultipartFile[] attachments) {
		if (attachments != null) {
			for (var file : attachments) {
				if (file == null || file.isEmpty()) continue;
				try {
					var ct = file.getContentType() != null ? file.getContentType() : OCTET_STREAM;
					var ra2 = new ReportAttachment(report, file.getOriginalFilename(), file.getBytes(), ct);
					reportAttachmentRepository.save(ra2);
				} catch (IOException e) {
					return "File upload failed: " + e.getMessage();
				}
			}
		}
		return null;
	}

	private Set<LocalDate> getApprovedLeaveDates(User user, LocalDate from, LocalDate to) {
		Set<LocalDate> approvedLeaveDates = new LinkedHashSet<>();
		if (user == null) {
			return approvedLeaveDates;
		}
		for (LeaveRequest leave : leaveRequestRepository.findByEmployeeOrderByCreatedAtDesc(user)) {
			addApprovedLeaveDates(leave, from, to, approvedLeaveDates);
		}
		return approvedLeaveDates;
	}

	private void addApprovedLeaveDates(LeaveRequest leave, LocalDate from, LocalDate to, Set<LocalDate> approvedLeaveDates) {
		if (leave == null || !STATUS_APPROVED.equalsIgnoreCase(leave.getStatus()) || leave.getFromDate() == null || leave.getToDate() == null) {
			return;
		}
		LocalDate cursor = leave.getFromDate();
		while (!cursor.isAfter(leave.getToDate())) {
			if (!cursor.isBefore(from) && !cursor.isAfter(to)) {
				approvedLeaveDates.add(cursor);
			}
			cursor = cursor.plusDays(1);
		}
	}

	private AttendanceDay buildAttendanceDayForDate(LocalDate cursor, Map<LocalDate, String> holidays, Map<LocalDate, Attendance> byDate, Set<LocalDate> approvedLeaveDates, LocalDate today) {
		if (holidays.containsKey(cursor)) {
			return new AttendanceDay(cursor, holidays.get(cursor), true);
		}
		var dow = cursor.getDayOfWeek();
		if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
			return new AttendanceDay(cursor, "weekend");
		}
		if (byDate.containsKey(cursor)) {
			return new AttendanceDay(byDate.get(cursor));
		}
		if (approvedLeaveDates.contains(cursor)) {
			return new AttendanceDay(cursor, "leave");
		}
		if (!cursor.isAfter(today)) {
			return new AttendanceDay(cursor, STATUS_ABSENT);
		}
		return null;
	}

	// ── PAYROLL ───────────────────────────────────────────────────────────

	@GetMapping("/payroll")
	public String payrollPage(Model model) {
		User manager = getCurrentManager();
		if (manager == null) return REDIRECT_LOGIN;

		injectStats(model);
		String tenant = getTenantSegment(manager);
		List<User> teamMembers = getManagedTeamMembers(manager);
		List<PayrollTemplate> payrolls = payrollTemplateRepository.findByEmployeesAndTenant(teamMembers, tenant);
		model.addAttribute("payrolls", payrolls);
		model.addAttribute(ATTR_TEAM_MEMBERS, teamMembers);

		java.util.Optional<PayrollTemplate> myPayrollOpt = payrollTemplateRepository.findByEmployeeAndTenantSegment(manager, tenant);
		if (myPayrollOpt.isPresent()) {
			PayrollTemplate myPayroll = myPayrollOpt.get();
			model.addAttribute("myPayroll", myPayroll);
			
			// Calculate real-time estimated leave deductions for the current month
			int currentMonth = java.time.LocalDate.now().getMonthValue();
			int currentYear = java.time.LocalDate.now().getYear();
			java.math.BigDecimal leaveDeduction = payslipService.calculateLeaveDeduction(manager, myPayroll.getBasicSalary(), currentMonth, currentYear);
			java.math.BigDecimal estimatedNet = myPayroll.getNetSalary().subtract(leaveDeduction);
			if (estimatedNet.compareTo(java.math.BigDecimal.ZERO) < 0) {
				estimatedNet = java.math.BigDecimal.ZERO;
			}
			model.addAttribute("myPayrollLeaveDeduction", leaveDeduction);
			model.addAttribute("myPayrollEstimatedNet", estimatedNet);
		} else {
			model.addAttribute("myPayroll", null);
		}

		// Personal Generated Payslips
		List<Payslip> myPayslips = payslipRepository.findByEmployeeOrderByIdDesc(manager);
		model.addAttribute("myPayslips", myPayslips);

		// Team Generated Payslips
		List<Payslip> teamPayslips = new java.util.ArrayList<>();
		for (User member : teamMembers) {
			teamPayslips.addAll(payslipRepository.findByEmployeeOrderByIdDesc(member));
		}
		teamPayslips.sort((p1, p2) -> p2.getId().compareTo(p1.getId()));
		model.addAttribute("teamPayslips", teamPayslips);

		model.addAttribute("activePage", "payroll");
		return "manager-payroll";
	}
}
