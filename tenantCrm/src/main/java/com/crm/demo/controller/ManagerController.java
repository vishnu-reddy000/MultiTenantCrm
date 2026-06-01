package com.crm.demo.controller;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.springframework.validation.BindingResult;

import com.crm.demo.model.Attendance;
import com.crm.demo.model.AttendanceDay;
import com.crm.demo.model.Holiday;
import com.crm.demo.model.Meeting;
import com.crm.demo.model.Project;
import com.crm.demo.model.Task;
import com.crm.demo.model.Team;
import com.crm.demo.model.User;
import com.crm.demo.repository.AttendanceRepository;
import com.crm.demo.repository.HolidayRepository;
import com.crm.demo.repository.MeetingRepository;
import com.crm.demo.repository.ProjectRepository;
import com.crm.demo.repository.TaskRepository;
import com.crm.demo.repository.TeamRepository;
import com.crm.demo.repository.UserRepository;

import jakarta.validation.Valid;

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
	private HolidayRepository holidayRepository;

	@Autowired
	private TeamRepository teamRepository;

	@Autowired
	private MeetingRepository meetingRepository;

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
        Team myTeam = getManagerTeamWithMembers(manager);
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

	private Team getManagerTeamWithMembers(User manager) {
		List<Team> teams = teamRepository.findByManagerWithMembers(manager);
		return teams.isEmpty() ? null : teams.get(0);
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

		// Verify the requested user is actually in this manager's team
		Team myTeam = getManagerTeamWithMembers(manager);
		boolean inTeam = myTeam != null && myTeam.getMembers().stream().anyMatch(m -> m.getId().equals(id));
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
		List<AttendanceDay> days = buildDayList(records, from, today, holidays);

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
			// Team members available as participants
			Team myTeam = getManagerTeamWithMembers(manager);
			List<User> teamMembers = myTeam != null ? myTeam.getMembers() : Collections.emptyList();
			model.addAttribute("teamMembers", teamMembers);
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
				Team myTeam = getManagerTeamWithMembers(manager);
				model.addAttribute("teamMembers",
						myTeam != null ? myTeam.getMembers() : Collections.emptyList());
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
		Team myTeam = manager != null ? getManagerTeamWithMembers(manager) : null;
		model.addAttribute("teamMembers", myTeam != null ? myTeam.getMembers() : Collections.emptyList());
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
			Team myTeam = manager != null ? getManagerTeamWithMembers(manager) : null;
			model.addAttribute("teamMembers", myTeam != null ? myTeam.getMembers() : Collections.emptyList());
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

	/**
	 * Build a merged day list for the given date range.
	 * Priority: holiday > weekend > real record > absent.
	 * Result is sorted newest-first.
	 */
	private List<AttendanceDay> buildDayList(List<Attendance> records,
	                                          LocalDate from, LocalDate to,
	                                          Map<LocalDate, String> holidays) {
		Map<LocalDate, Attendance> byDate = new LinkedHashMap<>();
		for (Attendance a : records) byDate.put(a.getDate(), a);

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

		// Build merged day list (fills absent/weekend/holiday gaps)
		List<AttendanceDay> allDays = buildDayList(records, filterFrom, filterTo, holidays);

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
}