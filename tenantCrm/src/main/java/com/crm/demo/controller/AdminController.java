package com.crm.demo.controller;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.crm.demo.model.Meeting;
import com.crm.demo.model.Project;
import com.crm.demo.model.Report;
import com.crm.demo.model.ReportAttachment;
import com.crm.demo.model.Task;
import com.crm.demo.model.User;
import com.crm.demo.model.Attendance;
import com.crm.demo.model.AttendanceDay;
import com.crm.demo.model.Holiday;
import com.crm.demo.repository.MeetingRepository;
import com.crm.demo.repository.ProjectRepository;
import com.crm.demo.repository.ReportAttachmentRepository;
import com.crm.demo.repository.ReportRepository;
import com.crm.demo.model.Team;
import com.crm.demo.repository.TaskRepository;
import com.crm.demo.repository.UserRepository;
import com.crm.demo.repository.HolidayRepository;
import com.crm.demo.repository.AttendanceRepository;
import com.crm.demo.repository.TeamRepository;
import com.crm.demo.service.NotificationService;
import com.crm.demo.service.ProfileUpdateService;
import com.crm.demo.service.AttendanceService;
import java.time.DayOfWeek;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/admin")
public class AdminController {

	@Autowired
	private AttendanceService attendanceService;

	@Autowired
	private MeetingRepository meetingRepository;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private TaskRepository taskRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ReportRepository reportRepository;

	@Autowired
	private ReportAttachmentRepository reportAttachmentRepository;

	@Autowired
	private BCryptPasswordEncoder passwordEncoder;

	@Autowired
	private ProfileUpdateService profileUpdateService;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private HolidayRepository holidayRepository;

	@Autowired
	private AttendanceRepository attendanceRepository;

	@Autowired
	private TeamRepository teamRepository;

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
		List<Task>    tasks     = tenant.isBlank() ? taskRepository.findAll() : taskRepository.findByTenantSegment(tenant);

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
		addAnalyticsAttributes(model, dashboardAnalytics(request));

		return "admin-dashboard";
	}

	@GetMapping("/dashboard/analytics")
	@ResponseBody
	public Map<String, Object> dashboardAnalytics(HttpServletRequest request) {
		String username = (String) request.getAttribute("loggedInUser");
		String tenant = getTenantSegment(username);

		List<User> users = tenant.isBlank()
				? userRepository.findAll()
				: userRepository.findByTenantSegment(tenant);
		List<User> employees = users.stream()
				.filter(u -> !"ADMIN".equalsIgnoreCase(u.getRole()))
				.filter(u -> !"SUPER_ADMIN".equalsIgnoreCase(u.getRole()))
				.toList();
		List<Task> tasks = tenant.isBlank()
				? taskRepository.findAll()
				: taskRepository.findByTenantSegment(tenant);

		Map<String, Object> data = buildDashboardAnalytics(tasks, employees);
		data.put("totalEmployees", employees.size());
		data.put("activeProjects", projectRepository.findAll().stream()
				.filter(p -> "active".equalsIgnoreCase(p.getStatus()))
				.count());
		data.put("tasksDone", data.get("statusDone"));
		data.put("pendingTaskTotal", data.get("statusPending"));
		return data;
	}

	private Map<String, Object> buildDashboardAnalytics(List<Task> tasks, List<User> employees) {
		Map<String, Object> data = new LinkedHashMap<>();
		List<Task> scopedTasks = tasks != null ? tasks : Collections.emptyList();
		List<User> scopedEmployees = employees != null ? employees : Collections.emptyList();

		long statusDone = scopedTasks.stream().filter(t -> "done".equalsIgnoreCase(t.getStatus())).count();
		long statusInProgress = scopedTasks.stream().filter(t -> "in-progress".equalsIgnoreCase(t.getStatus())).count();
		long statusPending = scopedTasks.stream().filter(t -> "pending".equalsIgnoreCase(t.getStatus())).count();
		long statusReview = scopedTasks.stream().filter(t -> "waiting-for-review".equalsIgnoreCase(t.getStatus())).count();
		long priorityHigh = scopedTasks.stream().filter(t -> "High".equalsIgnoreCase(t.getPriority())).count();
		long priorityMedium = scopedTasks.stream().filter(t -> "Medium".equalsIgnoreCase(t.getPriority())).count();
		long priorityLow = scopedTasks.stream().filter(t -> "Low".equalsIgnoreCase(t.getPriority())).count();

		List<String> memberLabels = new ArrayList<>();
		List<Long> memberTaskCounts = new ArrayList<>();
		for (User employee : scopedEmployees) {
			if ("HR".equalsIgnoreCase(employee.getRole()) || "MANAGER".equalsIgnoreCase(employee.getRole())) {
				continue;
			}
			long count = scopedTasks.stream()
					.filter(t -> employee.getUsername() != null && employee.getUsername().equalsIgnoreCase(t.getAssignedTo()))
					.count();
			memberLabels.add(employee.getUsername());
			memberTaskCounts.add(count);
		}

		long activeCount = scopedEmployees.stream().filter(User::isActive).count();
		long inactiveCount = scopedEmployees.size() - activeCount;
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

	private void addAnalyticsAttributes(Model model, Map<String, Object> data) {
		model.addAttribute("chartStatusDone", data.get("statusDone"));
		model.addAttribute("chartStatusInProgress", data.get("statusInProgress"));
		model.addAttribute("chartStatusPending", data.get("statusPending"));
		model.addAttribute("chartStatusReview", data.get("statusReview"));
		model.addAttribute("chartPriorityHigh", data.get("priorityHigh"));
		model.addAttribute("chartPriorityMedium", data.get("priorityMedium"));
		model.addAttribute("chartPriorityLow", data.get("priorityLow"));
		model.addAttribute("chartMemberLabels", data.get("memberLabels"));
		model.addAttribute("chartMemberTaskCounts", data.get("memberTaskCounts"));
		model.addAttribute("chartActiveTeam", data.get("activeTeam"));
		model.addAttribute("chartInactiveTeam", data.get("inactiveTeam"));
		model.addAttribute("chartVerified", data.get("verified"));
		model.addAttribute("chartRejected", data.get("rejected"));
		model.addAttribute("chartWaiting", data.get("waiting"));
		model.addAttribute("chartUnverified", data.get("unverified"));
		model.addAttribute("chartTotalMyTasks", data.get("totalMyTasks"));
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

	/**
	 * REST: GET /admin/api/employee/{id}
	 * Returns employee profile + last 30 days attendance as JSON for the modal.
	 */
	@GetMapping("/api/employee/{id}")
	@ResponseBody
	public Map<String, Object> employeeDetail(@PathVariable Long id, HttpServletRequest request) {
		attendanceService.processAutoPunchOuts();
		Map<String, Object> resp = new LinkedHashMap<>();
		String username = (String) request.getAttribute("loggedInUser");
		String tenant   = getTenantSegment(username);

		User user = userRepository.findById(id).orElse(null);
		if (user == null || (!"EMPLOYEE".equalsIgnoreCase(user.getRole())
				&& !"MANAGER".equalsIgnoreCase(user.getRole())
				&& !"HR".equalsIgnoreCase(user.getRole()))) {
			resp.put("error", "User not found.");
			return resp;
		}

		// Profile
		resp.put("id",       user.getId());
		resp.put("username", user.getUsername());
		resp.put("email",    user.getEmail());
		resp.put("role",     user.getRole());
		resp.put("status",   user.getStatus());

		// Last 30 days attendance
		LocalDate today = LocalDate.now();
		LocalDate from  = today.minusDays(29);
		Map<LocalDate, String> holidays = fetchHolidays(tenant, from, today);
		List<Attendance> records = attendanceRepository.findByUserAndDateBetweenOrderByDateDesc(user, from, today);
		List<AttendanceDay> days = buildDayList(records, from, today, holidays);

		// Stats
		long present  = days.stream().filter(d -> "present".equals(d.getStatus()) || "late".equals(d.getStatus())).count();
		long absent   = days.stream().filter(d -> "absent".equals(d.getStatus())).count();
		long halfDay  = days.stream().filter(d -> "half-day".equals(d.getStatus())).count();
		long holiday  = days.stream().filter(d -> "holiday".equals(d.getStatus())).count();
		resp.put("presentDays", present);
		resp.put("absentDays",  absent);
		resp.put("halfDays",    halfDay);
		resp.put("holidays",    holiday);

		// Attendance rows (last 30 days, newest first)
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

	private Map<LocalDate, String> fetchHolidays(String tenant, LocalDate from, LocalDate to) {
		Map<LocalDate, String> map = new LinkedHashMap<>();
		if (tenant == null || tenant.isBlank()) return map;
		List<Holiday> list = holidayRepository.findByTenantAndDateRange(
				tenant, from.toString(), to.toString());
		for (Holiday h : list) map.put(LocalDate.parse(h.getDate()), h.getName());
		return map;
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

		if (username == null || username.trim().isBlank()) {
			ra.addFlashAttribute("errorMessage", "Username is required.");
			return "redirect:/admin/add-employee";
		}
		if (!username.trim().matches("^[A-Za-z0-9._-]{3,50}$")) {
			ra.addFlashAttribute("errorMessage", "Username must be 3-50 characters and contain only letters, numbers, dots, hyphens, or underscores.");
			return "redirect:/admin/add-employee";
		}
		if (email == null || email.trim().isBlank()) {
			ra.addFlashAttribute("errorMessage", "Email is required.");
			return "redirect:/admin/add-employee";
		}
		if (!email.trim().matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$")) {
			ra.addFlashAttribute("errorMessage", "Please provide a valid email address.");
			return "redirect:/admin/add-employee";
		}
		if (password == null || password.length() < 4) {
			ra.addFlashAttribute("errorMessage", "Password must be at least 4 characters long.");
			return "redirect:/admin/add-employee";
		}
		if (!password.matches("^[A-Za-z0-9]+$")) {
			ra.addFlashAttribute("errorMessage", "Password must contain only letters and numbers (no special characters).");
			return "redirect:/admin/add-employee";
		}
		if (!password.equals(confirmPassword)) {
			ra.addFlashAttribute("errorMessage", "Passwords do not match.");
			return "redirect:/admin/add-employee";
		}
		if (role == null || role.trim().isBlank() || (!"HR".equalsIgnoreCase(role) && !"MANAGER".equalsIgnoreCase(role) && !"EMPLOYEE".equalsIgnoreCase(role))) {
			ra.addFlashAttribute("errorMessage", "Please select a valid role (HR, Manager, or Employee).");
			return "redirect:/admin/add-employee";
		}

		String adminUser = (String) request.getAttribute("loggedInUser");
		if (adminUser == null) {
			adminUser = SecurityContextHolder.getContext().getAuthentication().getName();
		}
		User currentAdmin = userRepository.findByUsername(adminUser);
		String tenant = getTenantSegment(adminUser);
		if (tenant != null && !tenant.isBlank() && !email.trim().contains("." + tenant + "@")) {
			ra.addFlashAttribute("errorMessage", "Email must belong to your tenant domain (expected format: name." + tenant + "@crm.com).");
			return "redirect:/admin/add-employee";
		}

		if (currentAdmin != null) {
			long employeeCount = userRepository.findByTenantSegment(tenant).stream()
					.filter(u -> !"ADMIN".equalsIgnoreCase(u.getRole()) && !"SUPER_ADMIN".equalsIgnoreCase(u.getRole()))
					.count();
			int limit = currentAdmin.getEmployeeLimit() != null ? currentAdmin.getEmployeeLimit() : 10;
			if (employeeCount >= limit) {
				ra.addFlashAttribute("errorMessage", "Employee limit reached (" + limit + "). You cannot add more employees.");
				return "redirect:/admin/add-employee";
			}
		}

		if (userRepository.findByEmail(email.trim()) != null) {
			ra.addFlashAttribute("errorMessage", "Email already in use.");
			return "redirect:/admin/add-employee";
		}

		if (userRepository.findByUsername(username.trim()) != null) {
			ra.addFlashAttribute("errorMessage", "Username already in use.");
			return "redirect:/admin/add-employee";
		}

		User user = new User();
		user.setEmail(email.trim());
		user.setUsername(username.trim());
		user.setPassword(passwordEncoder.encode(password));
		user.setRole(role.toUpperCase());
		user.setStatus("active");
		userRepository.save(user);

		notificationService.notifyEmployeeManagementChanged(tenant, "added", username.trim());

		ra.addFlashAttribute("successMessage", "Employee added successfully.");
		return "redirect:/admin/employees";
	}

	// =========================================================
	// BULK EMPLOYEE UPLOAD (Excel)
	//  Expected columns (row 0 = header, skipped):
	//    A: username  B: email  C: password  D: role
	//
	//  Validate ALL rows first — any error rejects the whole file.
	// =========================================================

	@PostMapping("/bulk-upload")
	public String bulkUpload(@RequestParam("file") MultipartFile file,
	                         HttpServletRequest request,
	                         RedirectAttributes ra) {

		if (file == null || file.isEmpty()) {
			ra.addFlashAttribute("errorMessage", "Please select an Excel file to upload.");
			return "redirect:/admin/add-employee";
		}
		String originalFilename = file.getOriginalFilename();
		if (originalFilename == null ||
				(!originalFilename.endsWith(".xlsx") && !originalFilename.endsWith(".xls"))) {
			ra.addFlashAttribute("errorMessage", "Only .xlsx or .xls files are supported.");
			return "redirect:/admin/add-employee";
		}

		String username = (String) request.getAttribute("loggedInUser");
		String segment  = getTenantSegment(username);

		List<String> errors = new ArrayList<>();
		List<User>   toSave = new ArrayList<>();

		try (InputStream is = file.getInputStream();
		     Workbook workbook = new XSSFWorkbook(is)) {

			Sheet sheet = workbook.getSheetAt(0);
			if (sheet.getLastRowNum() < 1) {
				ra.addFlashAttribute("errorMessage", "The file has no data rows.");
				return "redirect:/admin/add-employee";
			}

			for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
				Row row = sheet.getRow(rowIndex);
				if (row == null) continue;

				String uname = getAdminCellString(row, 0);
				String email  = getAdminCellString(row, 1);
				String pwd    = getAdminCellString(row, 2);
				String role   = getAdminCellString(row, 3);

				// Skip fully blank rows
				if (uname.isBlank() && email.isBlank() && pwd.isBlank() && role.isBlank()) continue;

				String rowLabel = "Row " + (rowIndex + 1);

				if (uname.isBlank()) { errors.add(rowLabel + ": username is empty."); continue; }
				if (email.isBlank())  { errors.add(rowLabel + " (" + uname + "): email is empty."); continue; }
				if (pwd.isBlank())    { errors.add(rowLabel + " (" + uname + "): password is empty."); continue; }
				if (role.isBlank())   { errors.add(rowLabel + " (" + uname + "): role is empty."); continue; }

				if ("ADMIN".equalsIgnoreCase(role) || "SUPER_ADMIN".equalsIgnoreCase(role)) {
					errors.add(rowLabel + " (" + uname + "): role '" + role + "' is not allowed.");
					continue;
				}
				if (segment != null && !segment.isBlank() && !email.contains("." + segment + "@")) {
					errors.add(rowLabel + " (" + uname + "): email '" + email
							+ "' does not belong to tenant domain (expected: name." + segment + "@crm.com).");
					continue;
				}
				if (userRepository.existsByUsernameOrEmail(uname, email)) {
					errors.add(rowLabel + " (" + uname + "): username or email already exists in the system.");
					continue;
				}
				boolean dupInFile = toSave.stream().anyMatch(u ->
						u.getUsername().equalsIgnoreCase(uname) || u.getEmail().equalsIgnoreCase(email));
				if (dupInFile) {
					errors.add(rowLabel + " (" + uname + "): username or email is duplicated within this file.");
					continue;
				}

				User user = new User();
				user.setUsername(uname);
				user.setEmail(email);
				user.setPassword(passwordEncoder.encode(pwd));
				user.setRole(role.toUpperCase());
				user.setStatus("active");
				toSave.add(user);
			}

		} catch (Exception e) {
			ra.addFlashAttribute("errorMessage", "Failed to parse file: " + e.getMessage());
			return "redirect:/admin/add-employee";
		}

		// Any errors → reject all
		if (!errors.isEmpty()) {
			ra.addFlashAttribute("bulkErrors", errors);
			ra.addFlashAttribute("errorMessage",
					"Upload rejected — " + errors.size() + " error(s) found. No employees were saved.");
			return "redirect:/admin/add-employee";
		}

		if (toSave.isEmpty()) {
			ra.addFlashAttribute("errorMessage", "No valid data rows found in the file.");
			return "redirect:/admin/add-employee";
		}

		// Enforce employee limit for bulk upload
		String adminUser = (String) request.getAttribute("loggedInUser");
		if (adminUser == null) {
			adminUser = SecurityContextHolder.getContext().getAuthentication().getName();
		}
		User currentAdmin = userRepository.findByUsername(adminUser);
		if (currentAdmin != null) {
			String tenant = getTenantSegment(adminUser);
			long existingCount = userRepository.findByTenantSegment(tenant).stream()
					.filter(u -> !"ADMIN".equalsIgnoreCase(u.getRole()) && !"SUPER_ADMIN".equalsIgnoreCase(u.getRole()))
					.count();
			int limit = currentAdmin.getEmployeeLimit() != null ? currentAdmin.getEmployeeLimit() : 10;
			if (existingCount + toSave.size() > limit) {
				ra.addFlashAttribute("errorMessage", "Upload rejected. Adding " + toSave.size()
						+ " employee(s) would exceed your limit of " + limit + " (current count: " + existingCount + ").");
				return "redirect:/admin/add-employee";
			}
		}

		userRepository.saveAll(toSave);
		notificationService.notifyEmployeeManagementChanged(segment, "uploaded", "multiple");
		ra.addFlashAttribute("successMessage", toSave.size() + " employee(s) imported successfully.");
		return "redirect:/admin/employees";
	}

	/** Safely read a cell value as a trimmed String. */
	private String getAdminCellString(Row row, int col) {
		Cell cell = row.getCell(col);
		if (cell == null) return "";
		if (cell.getCellType() == CellType.STRING)  return cell.getStringCellValue().trim();
		if (cell.getCellType() == CellType.NUMERIC) return String.valueOf((long) cell.getNumericCellValue()).trim();
		if (cell.getCellType() == CellType.BOOLEAN) return String.valueOf(cell.getBooleanCellValue()).trim();
		return "";
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

			String adminName = SecurityContextHolder.getContext().getAuthentication().getName();
			String tenant = getTenantSegment(adminName);
			notificationService.notifyEmployeeManagementChanged(tenant, "updated", user.getUsername());

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
			
			String adminName = SecurityContextHolder.getContext().getAuthentication().getName();
			String tenant = getTenantSegment(adminName);
			notificationService.deleteAllForUser(user.getId());
			
			userRepository.delete(user);
			
			notificationService.notifyEmployeeManagementChanged(tenant, "deleted", name);

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

		if (username == null || username.trim().isBlank()) {
			ra.addFlashAttribute("errorMessage", "Username is required.");
			return "redirect:/admin/edit-employee/" + id;
		}
		if (!username.trim().matches("^[A-Za-z0-9._-]{3,50}$")) {
			ra.addFlashAttribute("errorMessage", "Username must be 3-50 characters and contain only letters, numbers, dots, hyphens, or underscores.");
			return "redirect:/admin/edit-employee/" + id;
		}
		if (email == null || email.trim().isBlank()) {
			ra.addFlashAttribute("errorMessage", "Email is required.");
			return "redirect:/admin/edit-employee/" + id;
		}
		if (!email.trim().matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$")) {
			ra.addFlashAttribute("errorMessage", "Please provide a valid email address.");
			return "redirect:/admin/edit-employee/" + id;
		}
		if (role == null || role.trim().isBlank() || ("ADMIN".equalsIgnoreCase(role) || "SUPER_ADMIN".equalsIgnoreCase(role))) {
			ra.addFlashAttribute("errorMessage", "You cannot assign that role.");
			return "redirect:/admin/edit-employee/" + id;
		}

		String adminName = SecurityContextHolder.getContext().getAuthentication().getName();
		String tenant = getTenantSegment(adminName);
		if (tenant != null && !tenant.isBlank() && !email.trim().contains("." + tenant + "@")) {
			ra.addFlashAttribute("errorMessage", "Email must belong to your tenant domain (expected format: name." + tenant + "@crm.com).");
			return "redirect:/admin/edit-employee/" + id;
		}

		// Check uniqueness
		User existingUserByUname = userRepository.findByUsername(username.trim());
		if (existingUserByUname != null && !existingUserByUname.getId().equals(emp.getId())) {
			ra.addFlashAttribute("errorMessage", "Username is already taken.");
			return "redirect:/admin/edit-employee/" + id;
		}
		User existingUserByEmail = userRepository.findByEmail(email.trim());
		if (existingUserByEmail != null && !existingUserByEmail.getId().equals(emp.getId())) {
			ra.addFlashAttribute("errorMessage", "Email is already taken.");
			return "redirect:/admin/edit-employee/" + id;
		}

		emp.setUsername(username.trim());
		emp.setEmail(email.trim());

		if (password != null && !password.isBlank()) {
			if (password.length() < 4) {
				ra.addFlashAttribute("errorMessage", "Password must be at least 4 characters long.");
				return "redirect:/admin/edit-employee/" + id;
			}
			if (!password.matches("^[A-Za-z0-9]+$")) {
				ra.addFlashAttribute("errorMessage", "Password must contain only letters and numbers (no special characters).");
				return "redirect:/admin/edit-employee/" + id;
			}
			if (!password.equals(confirmPassword)) {
				ra.addFlashAttribute("errorMessage", "Passwords do not match.");
				return "redirect:/admin/edit-employee/" + id;
			}
			emp.setPassword(passwordEncoder.encode(password));
		}
		emp.setRole(role.toUpperCase());
		userRepository.save(emp);

		notificationService.notifyEmployeeManagementChanged(tenant, "updated", emp.getUsername());

		ra.addFlashAttribute("successMessage", "'" + emp.getUsername() + "' updated successfully.");
		return "redirect:/admin/employees";
	}



	// =========================================================
	// TASKS
	// =========================================================

	@GetMapping("/tasks")
	public String tasksPage(HttpServletRequest request, Model model) {
		injectUser(request, model);

		String username = (String) request.getAttribute("loggedInUser");
		String tenant   = getTenantSegment(username);

		List<Task> tasks = tenant.isBlank()
				? taskRepository.findAll()
				: taskRepository.findByTenantSegment(tenant);
		tasks.sort(java.util.Comparator.comparing(Task::getId).reversed());

		long done    = tasks.stream().filter(t -> "done".equalsIgnoreCase(t.getStatus())).count();
		long pending = tasks.stream().filter(t -> "pending".equalsIgnoreCase(t.getStatus())).count();

		List<Team> teams = tenant.isBlank()
				? teamRepository.findAll()
				: teamRepository.findByTenantSegmentOrderByIdDesc(tenant);

		model.addAttribute("tasks",            tasks);
		model.addAttribute("totalTasks",       tasks.size());
		model.addAttribute("doneTasks",        done);
		model.addAttribute("pendingTaskCount", pending);
		model.addAttribute("teams",            teams);

		return "admin-tasks";
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

		// Manager-sent reports for this tenant
		User admin = userRepository.findByUsername(username);
		List<Report> allReports;
		if (admin != null) {
			allReports = reportRepository.findByRecipientId(String.valueOf(admin.getId()), tenant);
		} else {
			allReports = java.util.Collections.emptyList();
		}
		model.addAttribute("allReports",  allReports);
		model.addAttribute("reportCount", allReports.size());

		return "admin-reports";
	}

	@GetMapping("/reports/view/{attachmentId}")
	public org.springframework.http.ResponseEntity<?> viewReportAttachment(
			@PathVariable Long attachmentId) {
		ReportAttachment att = reportAttachmentRepository.findById(attachmentId).orElse(null);
		if (att == null) return org.springframework.http.ResponseEntity.notFound().build();
		String ct = att.getContentType() != null ? att.getContentType() : "application/octet-stream";
		return org.springframework.http.ResponseEntity.ok()
				.header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
						"inline; filename=\"" + att.getOriginalFilename() + "\"")
				.header(org.springframework.http.HttpHeaders.CONTENT_TYPE, ct)
				.body(att.getFileData());
	}

	@GetMapping("/reports/download/{attachmentId}")
	public org.springframework.http.ResponseEntity<?> downloadReportAttachment(
			@PathVariable Long attachmentId) {
		ReportAttachment att = reportAttachmentRepository.findById(attachmentId).orElse(null);
		if (att == null) return org.springframework.http.ResponseEntity.notFound().build();
		String ct = att.getContentType() != null ? att.getContentType() : "application/octet-stream";
		return org.springframework.http.ResponseEntity.ok()
				.header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
						"attachment; filename=\"" + att.getOriginalFilename() + "\"")
				.header(org.springframework.http.HttpHeaders.CONTENT_TYPE, ct)
				.body(att.getFileData());
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

	@GetMapping("/profile")
	public String profilePage(HttpServletRequest request, Model model) {
		injectUser(request, model);

		String username = (String) request.getAttribute("loggedInUser");
		User admin = userRepository.findByUsername(username);

		model.addAttribute("adminEmail",         admin != null ? admin.getEmail() : "");
		model.addAttribute("settingsTotalUsers", userRepository.count());

		return "admin-profile";
	}

	@PostMapping("/update-profile")
	public String updateProfile(@RequestParam(required = false) String username,
	                            @RequestParam(required = false) String email,
	                            @RequestParam(required = false) String password,
	                            @RequestParam(required = false) String confirmPassword,
	                            HttpServletResponse response,
	                            RedirectAttributes ra) {

		String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
		User admin = userRepository.findByUsername(currentUsername);

		if (admin == null) {
			return "redirect:/admin/profile";
		}

		profileUpdateService.updateProfile(admin, username, email, password, confirmPassword, ra, response);
		return "redirect:/admin/profile";
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

		if ("in-person".equalsIgnoreCase(meetingForm.getMeetingType()) && (meetingForm.getLocation() == null || meetingForm.getLocation().isBlank())) {
			result.rejectValue("location", "NotBlank", "Location is required for in-person meetings.");
		}

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

		meetingForm.setTenantSegment(tenant);
		meetingForm.setScheduledBy(username != null ? username : "");
		meetingRepository.save(meetingForm);
		notificationService.notifyMeetingParticipants(meetingForm);
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

		if ("in-person".equalsIgnoreCase(meetingForm.getMeetingType()) && (meetingForm.getLocation() == null || meetingForm.getLocation().isBlank())) {
			result.rejectValue("location", "NotBlank", "Location is required for in-person meetings.");
		}

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
		notificationService.notifyMeetingParticipants(existing);

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
