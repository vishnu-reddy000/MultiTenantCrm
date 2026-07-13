package com.crm.demo.controller;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.validation.BindingResult;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.crm.demo.model.Attendance;
import com.crm.demo.model.AttendanceDay;
import com.crm.demo.model.Meeting;
import com.crm.demo.model.Task;
import com.crm.demo.model.Team;
import com.crm.demo.model.User;
import com.crm.demo.repository.AttendanceRepository;

import com.crm.demo.repository.MeetingRepository;
import com.crm.demo.repository.PasswordResetTokenRepository;
import com.crm.demo.repository.PerformanceReviewRepository;
import com.crm.demo.repository.TaskRepository;
import com.crm.demo.repository.TeamRepository;

import com.crm.demo.model.PayrollTemplate;
import com.crm.demo.repository.PayrollTemplateRepository;
import com.crm.demo.repository.PayslipRepository;
import com.crm.demo.service.PayslipService;
import com.crm.demo.service.NotificationService;
import com.crm.demo.service.ProfileUpdateService;
import com.crm.demo.service.AttendanceService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/hr")
public class HrController extends BaseController {

    @Autowired private TeamRepository        teamRepository;
    @Autowired private AttendanceRepository  attendanceRepository;
    @Autowired private MeetingRepository     meetingRepository;
    @Autowired private PerformanceReviewRepository performanceReviewRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private TaskRepository         taskRepository;
    @Autowired private com.crm.demo.repository.ReportRepository reportRepository;
    @Autowired private com.crm.demo.repository.ReportAttachmentRepository reportAttachmentRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    @Autowired private AttendanceService     attendanceService;
    @Autowired private ProfileUpdateService  profileUpdateService;
    @Autowired private NotificationService   notificationService;

    @Autowired private PayrollTemplateRepository payrollTemplateRepository;
    @Autowired private PayslipRepository payslipRepository;
    @Autowired private PayslipService payslipService;
    @Autowired private com.crm.demo.repository.DomainCategoryRepository domainCategoryRepository;

    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_EMPLOYEE = "EMPLOYEE";
    private static final String ATTR_LOGGED_IN_USER = "loggedInUser";
    private static final String STATUS_ABSENT = "absent";
    private static final String STATUS_APPROVED = "Approved";
    private static final String STATUS_PENDING_ATTR = "statusPending";
    private static final String STATUS_REJECTED = "rejected";
    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_PRESENT = "present";
    private static final String ATTR_ERROR_MESSAGE = "errorMessage";
    private static final String ATTR_SUCCESS_MESSAGE = "successMessage";
    private static final String ATTR_ACTIVE_PAGE = "activePage";
    private static final String ATTR_EMPLOYEES = "employees";
    private static final String ATTR_TEAMS = "teams";
    private static final String ATTR_UPCOMING_MEETINGS = "upcomingMeetings";
    private static final String ATTR_PAST_MEETINGS = "pastMeetings";
    private static final String ATTR_TENANT_USERS = "tenantUsers";
    private static final String ATTR_MEETING_FORM = "meetingForm";

    private static final String REDIRECT_HR_ADD_USER = "redirect:/hr/add-user";
    private static final String REDIRECT_HR_EMPLOYEES = "redirect:/hr/employees";
    private static final String REDIRECT_HR_EDIT_EMPLOYEE = "redirect:/hr/edit-employee/";
    private static final String REDIRECT_HR_ATTENDANCE = "redirect:/hr/attendance";
    private static final String REDIRECT_HR_LEAVES = "redirect:/hr/leaves";
    private static final String REDIRECT_HR_TEAMS = "redirect:/hr/teams";
    private static final String REDIRECT_HR_MEETINGS = "redirect:/hr/meetings";
    private static final String REDIRECT_HR_PAYROLL = "redirect:/hr/payroll";
    private static final String REDIRECT_HR_SETTINGS = "redirect:/hr/settings";

    private static final String MSG_TEAM_NOT_FOUND = "Team not found.";
    private static final String MSG_MEETING_NOT_FOUND = "Meeting not found.";
    private static final String MSG_NOT_PUNCHED_IN = "You haven't punched in today.";

    private static final String DOMAIN_SUFFIX = "@crm.com).";
    private static final String OCTET_STREAM = "application/octet-stream";
    private static final String TIME_FORMAT = "%02d:%02d";
    private static final String PAGE_MEETINGS = "hr-meetings";
    private static final String ATTR_ERROR = "error";
    private static final String ERROR_AUTH = "?error=auth";
    private static final String ERROR_ALREADY = "?error=already";
    private static final String SUCCESS = "?success";
    private static final String ERROR_NOT_PUNCHED = "?error=notpunched";
    private static final String ERROR_NOT_FOUND = "?error=notfound";
    private static final String ERROR_EXISTS = "?error=exists";

    // ── Validation Helpers ──────────────────────────────────────────────────

    private String validateUserData(String username, String email, String password, String confirmPassword, String role, String segment) {
        var usernameError = validateUsername(username);
        if (usernameError != null) return usernameError;

        var emailError = validateEmail(email, segment);
        if (emailError != null) return emailError;

        var passwordError = validatePassword(password, confirmPassword);
        if (passwordError != null) return passwordError;

        // Block creating ADMIN or SUPER_ADMIN accounts
        if (ROLE_ADMIN.equalsIgnoreCase(role) || ROLE_SUPER_ADMIN.equalsIgnoreCase(role)) {
            return "You cannot create an account with that role.";
        }
        return null;
    }

    private String checkEmployeeLimit(String segment) {
        if (segment != null && !segment.isBlank()) {
            var tenantAdmin = userRepository.findByTenantSegment(segment).stream()
                    .filter(u -> ROLE_ADMIN.equalsIgnoreCase(u.getRole()))
                    .findFirst().orElse(null);
            if (tenantAdmin != null) {
                var employeeCount = userRepository.findByTenantSegment(segment).stream()
                        .filter(u -> !ROLE_ADMIN.equalsIgnoreCase(u.getRole()) && !ROLE_SUPER_ADMIN.equalsIgnoreCase(u.getRole()))
                        .count();
                var limit = tenantAdmin.getEmployeeLimit() != null ? tenantAdmin.getEmployeeLimit() : 10;
                if (employeeCount >= limit) {
                    return "Employee limit reached (" + limit + "). You cannot add more employees.";
                }
            }
        }
        return null;
    }

    private String validateUpdateEmployee(User emp, String username, String email, String role, String segment) {
        var usernameError = validateUsername(username);
        if (usernameError != null) return usernameError;

        var emailError = validateEmail(email, segment);
        if (emailError != null) return emailError;

        // Block promoting to ADMIN / SUPER_ADMIN
        if (ROLE_ADMIN.equalsIgnoreCase(role) || ROLE_SUPER_ADMIN.equalsIgnoreCase(role)) {
            return "You cannot assign that role.";
        }

        // Check uniqueness
        var existingUserByUname = userRepository.findByUsername(username.trim());
        if (existingUserByUname != null && !existingUserByUname.getId().equals(emp.getId())) {
            return "Username is already taken.";
        }
        var existingUserByEmail = userRepository.findByEmail(email.trim());
        if (existingUserByEmail != null && !existingUserByEmail.getId().equals(emp.getId())) {
            return "Email is already taken.";
        }
        return null;
    }

    private Attendance getAndValidateTodayAttendance(User user, LocalDate today, RedirectAttributes ra) {
        var opt = attendanceRepository.findByUserAndDate(user, today);
        if (opt.isEmpty()) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, MSG_NOT_PUNCHED_IN);
            return null;
        }
        return opt.get();
    }

    private Team getAndValidateTeam(Long id, String tenant, RedirectAttributes ra) {
        var team = teamRepository.findById(id).orElse(null);
        if (team == null || !tenant.equals(team.getTenantSegment())) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, MSG_TEAM_NOT_FOUND);
            return null;
        }
        return team;
    }

    private PayrollTemplate getAndValidatePayrollTemplate(Long id, String tenant, RedirectAttributes ra) {
        var opt = payrollTemplateRepository.findById(id);
        if (opt.isEmpty() || !tenant.equals(opt.get().getTenantSegment())) {
            if (ra != null) {
                ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Payroll template not found.");
            }
            return null;
        }
        return opt.get();
    }

    private String validateUsername(String username) {
        if (username == null || username.trim().isBlank()) {
            return "Username is required.";
        }
        if (!username.trim().matches("^[A-Za-z0-9._-]{3,50}$")) {
            return "Username must be 3-50 characters and contain only letters, numbers, dots, hyphens, or underscores.";
        }
        return null;
    }

    private String validateEmail(String email, String segment) {
        if (email == null || email.trim().isBlank()) {
            return "Email is required.";
        }
        if (!email.trim().matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$")) {
            return "Please provide a valid email address.";
        }
        if (segment != null && !segment.isBlank() && !email.trim().contains("." + segment + "@")) {
            return "Email must belong to your tenant domain (expected format: name." + segment + DOMAIN_SUFFIX;
        }
        return null;
    }

    private String validatePassword(String password, String confirmPassword) {
        if (password == null || password.length() < 4) {
            return "Password must be at least 4 characters long.";
        }
        if (!password.matches("^[A-Za-z0-9]+$")) {
            return "Password must contain only letters and numbers (no special characters).";
        }
        if (!password.equals(confirmPassword)) {
            return "Passwords do not match.";
        }
        return null;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void injectUser(HttpServletRequest request, Model model) {
        var username = (String) request.getAttribute(ATTR_LOGGED_IN_USER);
        model.addAttribute("adminName", username != null ? username : "HR User");
        model.addAttribute("adminRole", "HR");
    }

    /** Resolve the currently logged-in HR user via SecurityContextHolder. */
    private User getCurrentHr() {
        return getCurrentUser();
    }

    /** Extract tenant segment from the logged-in HR user's email. */
    private String getTenantSegment(HttpServletRequest request) {
        var username = (String) request.getAttribute(ATTR_LOGGED_IN_USER);
        if (username == null) return "";
        var hr = userRepository.findByUsername(username);
        return super.getTenantSegment(hr);
    }

    private String getTenantSegmentFromUser(User user) {
        return super.getTenantSegment(user);
    }

    /**
     * Build a merged day list for the given date range.
     * Priority: holiday > weekend > real record > absent.
     * Result is sorted newest-first.
     */
    private List<AttendanceDay> buildDayList(List<Attendance> records,
                                              LocalDate from, LocalDate to,
                                              Map<LocalDate, String> holidays) {
        var byDate = new LinkedHashMap<LocalDate, Attendance>();
        for (var a : records) byDate.put(a.getDate(), a);

        var days = new ArrayList<AttendanceDay>();
        var today  = LocalDate.now();
        var cursor = to;
        while (!cursor.isBefore(from)) {
            if (holidays.containsKey(cursor)) {
                days.add(new AttendanceDay(cursor, holidays.get(cursor), true));
            } else {
                var dow = cursor.getDayOfWeek();
                if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                    days.add(new AttendanceDay(cursor, "weekend"));
                } else if (byDate.containsKey(cursor)) {
                    days.add(new AttendanceDay(byDate.get(cursor)));
                } else if (!cursor.isAfter(today)) {
                    days.add(new AttendanceDay(cursor, STATUS_ABSENT));
                }
            }
            cursor = cursor.minusDays(1);
        }
        return days;
    }


    /** Returns true for roles that HR should manage (not ADMIN / SUPER_ADMIN). */
    private boolean isNonAdminRole(String role) {
        if (role == null) return false;
        return !role.equalsIgnoreCase(ROLE_ADMIN) && !role.equalsIgnoreCase(ROLE_SUPER_ADMIN);
    }

    private void injectStats(HttpServletRequest request, Model model) {
        var tenant = getTenantSegment(request);
        var currentUsername = (String) request.getAttribute(ATTR_LOGGED_IN_USER);

        // Include EMPLOYEE, MANAGER — exclude ADMIN, SUPER_ADMIN, and the logged-in HR themselves
        var employees = tenant.isEmpty()
                ? userRepository.findAll().stream()
                        .filter(u -> isNonAdminRole(u.getRole()))
                        .filter(u -> !u.getUsername().equals(currentUsername))
                        .sorted(java.util.Comparator.comparing(User::getId).reversed())
                        .collect(Collectors.toList())
                : userRepository.findByTenantSegment(tenant).stream()
                        .filter(u -> isNonAdminRole(u.getRole()))
                        .filter(u -> !u.getUsername().equals(currentUsername))
                        .collect(Collectors.toList());

        var active   = employees.stream().filter(User::isActive).count();
        var inactive = employees.size() - active;

        model.addAttribute(ATTR_EMPLOYEES,         employees);
        model.addAttribute("totalEmployees",    employees.size());
        model.addAttribute("activeEmployees",   active);
        model.addAttribute("inactiveEmployees", inactive);
        model.addAttribute("newHires",          0);
        var today = LocalDate.now();
        var monthStart = today.withDayOfMonth(1);
        var monthEnd = today.withDayOfMonth(today.lengthOfMonth());
        var onLeaveToday = tenant.isEmpty() ? 0
                : leaveRequestRepository.countByTenantSegmentAndStatusAndFromDateLessThanEqualAndToDateGreaterThanEqual(
                        tenant, STATUS_APPROVED, today, today);
        var pendingLeaves = tenant.isEmpty() ? 0 : leaveRequestRepository.countByTenantSegmentAndStatus(tenant, "Pending");
        var approvedThisMonth = tenant.isEmpty() ? 0
                : leaveRequestRepository.countByTenantSegmentAndStatusAndFromDateBetween(
                        tenant, STATUS_APPROVED, monthStart, monthEnd);

        model.addAttribute("onLeaveToday",      onLeaveToday);
        model.addAttribute("pendingLeaves",     pendingLeaves);
        model.addAttribute("approvedLeaves",    approvedThisMonth);
        model.addAttribute("openPositions",     0);
        model.addAttribute("leaveRequests",     tenant.isEmpty() ? Collections.emptyList() : leaveRequestRepository.findByTenantSegmentAndStatusOrderByCreatedAtDesc(tenant, "Pending"));
        model.addAttribute("attendanceMonth",   "May 2026");
        model.addAttribute("presentPercent",    "0%");
        model.addAttribute("absentPercent",     "0%");
        model.addAttribute("wfhPercent",        "0%");
    }

    // ── Pages ─────────────────────────────────────────────────────────────

    @GetMapping("/dashboard")
    public String dashboard(HttpServletRequest request, Model model) {
        injectUser(request, model);
        injectStats(request, model);
        addAnalyticsAttributes(model, dashboardAnalytics(request));
        return "hr-dashboard";
    }

    @GetMapping("/dashboard/analytics")
    @ResponseBody
    public Map<String, Object> dashboardAnalytics(HttpServletRequest request) {
        var tenant = getTenantSegment(request);
        var currentUsername = (String) request.getAttribute(ATTR_LOGGED_IN_USER);

        var employees = tenant.isBlank()
                ? userRepository.findAll().stream()
                        .filter(u -> isNonAdminRole(u.getRole()))
                        .filter(u -> currentUsername == null || !u.getUsername().equals(currentUsername))
                        .collect(Collectors.toList())
                : userRepository.findByTenantSegment(tenant).stream()
                        .filter(u -> isNonAdminRole(u.getRole()))
                        .filter(u -> currentUsername == null || !u.getUsername().equals(currentUsername))
                        .collect(Collectors.toList());

        var tasks = tenant.isBlank()
                ? taskRepository.findAll()
                : taskRepository.findByTenantSegment(tenant);

        var data = buildDashboardAnalytics(tasks, employees);
        data.put("totalEmployees", employees.size());
        data.put("pendingTaskTotal", data.get(STATUS_PENDING_ATTR));
        return data;
    }

    private Map<String, Object> buildDashboardAnalytics(List<Task> tasks, List<User> employees) {
        var data = new LinkedHashMap<String, Object>();
        var scopedTasks = tasks != null ? tasks : Collections.<Task>emptyList();
        var scopedEmployees = employees != null ? employees : Collections.<User>emptyList();

        var statusDone = scopedTasks.stream().filter(t -> "done".equalsIgnoreCase(t.getStatus())).count();
        var statusInProgress = scopedTasks.stream().filter(t -> "in-progress".equalsIgnoreCase(t.getStatus())).count();
        var statusPending = scopedTasks.stream().filter(t -> "pending".equalsIgnoreCase(t.getStatus())).count();
        var statusReview = scopedTasks.stream().filter(t -> "waiting-for-review".equalsIgnoreCase(t.getStatus())).count();
        var priorityHigh = scopedTasks.stream().filter(t -> "High".equalsIgnoreCase(t.getPriority())).count();
        var priorityMedium = scopedTasks.stream().filter(t -> "Medium".equalsIgnoreCase(t.getPriority())).count();
        var priorityLow = scopedTasks.stream().filter(t -> "Low".equalsIgnoreCase(t.getPriority())).count();

        var memberLabels = new ArrayList<String>();
        var memberTaskCounts = new ArrayList<Long>();
        for (var employee : scopedEmployees) {
            var count = scopedTasks.stream()
                    .filter(t -> employee.getUsername() != null && employee.getUsername().equalsIgnoreCase(t.getAssignedTo()))
                    .count();
            memberLabels.add(employee.getUsername());
            memberTaskCounts.add(count);
        }

        var activeCount = scopedEmployees.stream().filter(User::isActive).count();
        var inactiveCount = scopedEmployees.size() - activeCount;
        var verified = scopedTasks.stream().filter(t -> "approved".equalsIgnoreCase(t.getVerificationStatus())).count();
        var rejected = scopedTasks.stream().filter(t -> STATUS_REJECTED.equalsIgnoreCase(t.getVerificationStatus())).count();
        var waiting = scopedTasks.stream().filter(t -> "waiting-for-review".equalsIgnoreCase(t.getVerificationStatus())).count();
        var unverified = scopedTasks.size() - verified - rejected - waiting;

        data.put("statusDone", statusDone);
        data.put("statusInProgress", statusInProgress);
        data.put(STATUS_PENDING_ATTR, statusPending);
        data.put("statusReview", statusReview);
        data.put("priorityHigh", priorityHigh);
        data.put("priorityMedium", priorityMedium);
        data.put("priorityLow", priorityLow);
        data.put("memberLabels", memberLabels);
        data.put("memberTaskCounts", memberTaskCounts);
        data.put("activeTeam", activeCount);
        data.put("inactiveTeam", inactiveCount);
        data.put("verified", verified);
        data.put(STATUS_REJECTED, rejected);
        data.put("waiting", waiting);
        data.put("unverified", Math.max(unverified, 0));
        data.put("totalMyTasks", scopedTasks.size());
        return data;
    }

    private void addAnalyticsAttributes(Model model, Map<String, Object> data) {
        model.addAttribute("chartStatusDone", data.get("statusDone"));
        model.addAttribute("chartStatusInProgress", data.get("statusInProgress"));
        model.addAttribute("chartStatusPending", data.get(STATUS_PENDING_ATTR));
        model.addAttribute("chartStatusReview", data.get("statusReview"));
        model.addAttribute("chartPriorityHigh", data.get("priorityHigh"));
        model.addAttribute("chartPriorityMedium", data.get("priorityMedium"));
        model.addAttribute("chartPriorityLow", data.get("priorityLow"));
        model.addAttribute("chartMemberLabels", data.get("memberLabels"));
        model.addAttribute("chartMemberTaskCounts", data.get("memberTaskCounts"));
        model.addAttribute("chartActiveTeam", data.get("activeTeam"));
        model.addAttribute("chartInactiveTeam", data.get("inactiveTeam"));
        model.addAttribute("chartVerified", data.get("verified"));
        model.addAttribute("chartRejected", data.get(STATUS_REJECTED));
        model.addAttribute("chartWaiting", data.get("waiting"));
        model.addAttribute("chartUnverified", data.get("unverified"));
        model.addAttribute("chartTotalMyTasks", data.get("totalMyTasks"));
    }

    @GetMapping("/employees")
    public String employeesPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        injectStats(request, model);
        return "hr-employees";
    }

    @GetMapping("/tasks")
    public String tasksPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        injectStats(request, model);

        String tenant = getTenantSegment(request);
        List<Task> tasks = tenant.isBlank()
                ? taskRepository.findAll()
                : taskRepository.findByTenantSegment(tenant);
        tasks.sort(java.util.Comparator.comparing(Task::getId).reversed());

        long done = tasks.stream().filter(t -> "done".equalsIgnoreCase(t.getStatus())).count();
        long pending = tasks.stream().filter(t -> "pending".equalsIgnoreCase(t.getStatus())
                || "in-progress".equalsIgnoreCase(t.getStatus())).count();

        List<Team> teams = tenant.isBlank()
                ? teamRepository.findAll()
                : teamRepository.findByTenantSegmentOrderByIdDesc(tenant);

        model.addAttribute("tasks", tasks);
        model.addAttribute("totalTasks", tasks.size());
        model.addAttribute("doneTasks", done);
        model.addAttribute("pendingTaskCount", pending);
        model.addAttribute(ATTR_TEAMS, teams);

        return "hr-tasks";
    }


    // ═══════════════════════════════════════════════════════════════════════
    //  ADD / TOGGLE / DELETE USERS (HR can manage employees & managers)
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/add-user")
    public String addUserPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        String tenant = getTenantSegment(request);
        model.addAttribute("domainCategories", domainCategoryRepository.findByTenantSegment(tenant));
        return "hr-add-user";
    }

    @PostMapping("/add-user")
    public String addUser(@RequestParam String email,
                          @RequestParam String username,
                          @RequestParam String password,
                          @RequestParam String confirmPassword,
                          @RequestParam String role,
                          @RequestParam(required = false) String domain,
                          @RequestParam(required = false) String joiningDate,
                          HttpServletRequest request,
                          RedirectAttributes ra) {
        var segment = getTenantSegment(request);

        var validationError = validateUserData(username, email, password, confirmPassword, role, segment);
        if (validationError != null) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, validationError);
            return REDIRECT_HR_ADD_USER;
        }

        var limitError = checkEmployeeLimit(segment);
        if (limitError != null) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, limitError);
            return REDIRECT_HR_ADD_USER;
        }

        if (userRepository.existsByUsernameOrEmail(username.trim(), email.trim())) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Username or email already exists.");
            return REDIRECT_HR_ADD_USER;
        }

        var user = new User();
        user.setUsername(username.trim());
        user.setEmail(email.trim());
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role.toUpperCase());
        user.setStatus(STATUS_ACTIVE);
        
        if (domain != null && !domain.trim().isEmpty()) {
            user.setDomain(domain.trim());
        }
        if (joiningDate != null && !joiningDate.trim().isEmpty()) {
            try {
                user.setJoiningDate(java.time.LocalDate.parse(joiningDate.trim()));
            } catch (Exception e) {
                user.setJoiningDate(java.time.LocalDate.now());
            }
        } else {
            user.setJoiningDate(java.time.LocalDate.now());
        }

        userRepository.save(user);
        notificationService.notifyEmployeeManagementChanged(getTenantSegment(request), "added", username.trim());
        ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "User '" + username.trim() + "' added successfully.");
        return REDIRECT_HR_EMPLOYEES;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  BULK EMPLOYEE UPLOAD (Excel)
    //  Expected columns (row 0 = header, skipped):
    //    A: username  B: email  C: password  D: role
    //
    //  STRATEGY: validate ALL rows first — if ANY row has an error, reject
    //  the entire file and save nothing. Only save when everything is clean.
    // ═══════════════════════════════════════════════════════════════════════

    private void processUploadRow(Row row, int rowIndex, String segment, List<String> errors, List<User> toSave) {
        var username = getCellString(row, 0);
        var email    = getCellString(row, 1);
        var password = getCellString(row, 2);
        var role     = getCellString(row, 3);

        // Skip completely blank rows silently
        if (username.isBlank() && email.isBlank() && password.isBlank() && role.isBlank()) {
            return;
        }

        var rowLabel = "Row " + (rowIndex + 1);

        // Missing fields
        if (username.isBlank()) {
            errors.add(rowLabel + ": username is empty.");
            return;
        }
        if (email.isBlank()) {
            errors.add(rowLabel + " (" + username + "): email is empty.");
            return;
        }
        if (password.isBlank()) {
            errors.add(rowLabel + " (" + username + "): password is empty.");
            return;
        }
        if (role.isBlank()) {
            errors.add(rowLabel + " (" + username + "): role is empty.");
            return;
        }

        // Role check
        if (ROLE_ADMIN.equalsIgnoreCase(role) || ROLE_SUPER_ADMIN.equalsIgnoreCase(role)) {
            errors.add(rowLabel + " (" + username + "): role '" + role + "' is not allowed. Use EMPLOYEE or MANAGER.");
            return;
        }

        // Tenant domain check
        if (segment != null && !segment.isBlank() && !email.contains("." + segment + "@")) {
            errors.add(rowLabel + " (" + username + "): email '" + email
                    + "' does not belong to tenant domain (expected format: name." + segment + DOMAIN_SUFFIX);
            return;
        }

        // Duplicate check in DB
        if (userRepository.existsByUsernameOrEmail(username, email)) {
            errors.add(rowLabel + " (" + username + "): username or email already exists in the system.");
            return;
        }

        // Duplicate check within the same file (two rows with same username/email)
        var duplicateInFile = toSave.stream().anyMatch(u ->
                u.getUsername().equalsIgnoreCase(username) || u.getEmail().equalsIgnoreCase(email));
        if (duplicateInFile) {
            errors.add(rowLabel + " (" + username + "): username or email is duplicated within this file.");
            return;
        }

        // Valid — stage for saving
        var user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role.toUpperCase());
        user.setStatus(STATUS_ACTIVE);
        toSave.add(user);
    }

    @PostMapping("/bulk-upload")
    public String bulkUpload(@RequestParam("file") MultipartFile file,
                              HttpServletRequest request,
                              RedirectAttributes ra) {

        if (file == null || file.isEmpty()) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Please select an Excel file to upload.");
            return REDIRECT_HR_ADD_USER;
        }

        var originalFilename = file.getOriginalFilename();
        if (originalFilename == null ||
                (!originalFilename.endsWith(".xlsx") && !originalFilename.endsWith(".xls"))) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Only .xlsx or .xls files are supported.");
            return REDIRECT_HR_ADD_USER;
        }

        var segment = getTenantSegment(request);

        // ── Phase 1: parse and validate every row, collect all errors ────────
        var errors = new ArrayList<String>();
        var toSave = new ArrayList<User>();

        try (var is = file.getInputStream();
             var workbook = new XSSFWorkbook(is)) {

            var sheet = workbook.getSheetAt(0);

            if (sheet.getLastRowNum() < 1) {
                ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "The file has no data rows.");
                return REDIRECT_HR_ADD_USER;
            }

            for (var rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                var row = sheet.getRow(rowIndex);
                if (row == null) continue;
                processUploadRow(row, rowIndex, segment, errors, toSave);
            }

        } catch (Exception e) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Failed to parse file: " + e.getMessage());
            return REDIRECT_HR_ADD_USER;
        }

        // ── Phase 2: if any errors found, reject everything ──────────────────
        if (!errors.isEmpty()) {
            ra.addFlashAttribute("bulkErrors", errors);
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE,
                    "Upload rejected — " + errors.size() + " error(s) found. No employees were saved. Fix the issues and re-upload.");
            return REDIRECT_HR_ADD_USER;
        }

        // ── Phase 3: all rows valid — save them all ──────────────────────────
        if (toSave.isEmpty()) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "No valid data rows found in the file.");
            return REDIRECT_HR_ADD_USER;
        }

        userRepository.saveAll(toSave);
        ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE,
                toSave.size() + " employee(s) imported successfully.");
        return REDIRECT_HR_EMPLOYEES;
    }

    /** Safely read a cell value as a trimmed String regardless of cell type. */
    private String getCellString(Row row, int col) {
        var cell = row.getCell(col);
        if (cell == null) return "";
        if (cell.getCellType() == CellType.STRING) return cell.getStringCellValue().trim();
        if (cell.getCellType() == CellType.NUMERIC) return String.valueOf((long) cell.getNumericCellValue()).trim();
        if (cell.getCellType() == CellType.BOOLEAN) return String.valueOf(cell.getBooleanCellValue()).trim();
        return "";
    }

    @PostMapping("/toggle-user/{id}")
    public String toggleUser(@PathVariable Long id, RedirectAttributes ra) {
        var hr = getCurrentHr();
        var user = userRepository.findById(id).orElse(null);
        if (user != null && isNonAdminRole(user.getRole())) {
            var newStatus = STATUS_ACTIVE.equalsIgnoreCase(user.getStatus()) ? "inactive" : STATUS_ACTIVE;
            user.setStatus(newStatus);
            userRepository.save(user);
            notificationService.notifyEmployeeManagementChanged(getTenantSegmentFromUser(hr), "updated", user.getUsername());
            ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, user.getUsername() + " is now " + newStatus + ".");
        }
        return REDIRECT_HR_EMPLOYEES;
    }

    @PostMapping("/delete-user/{id}")
    @Transactional
    public String deleteUser(@PathVariable Long id, RedirectAttributes ra) {
        var hr = getCurrentHr();
        var user = userRepository.findById(id).orElse(null);
        if (user != null && isNonAdminRole(user.getRole())) {
            var name = user.getUsername();
            removeUserFromTeams(user);
            notificationService.deleteAllForUser(user.getId());
            passwordResetTokenRepository.deleteByUser(user);
            performanceReviewRepository.deleteByEmployee(user);
            leaveRequestRepository.deleteByEmployee(user);
            attendanceRepository.deleteByUser(user);
            userRepository.delete(user);
            notificationService.notifyEmployeeManagementChanged(getTenantSegmentFromUser(hr), "deleted", name);
            ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "User '" + name + "' deleted.");
        }
        return REDIRECT_HR_EMPLOYEES;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  EDIT EMPLOYEE
    // ═══════════════════════════════════════════════════════════════════════

    private void removeUserFromTeams(User user) {
        List<Team> changedTeams = new ArrayList<>();
        for (Team team : teamRepository.findAll()) {
            var changed = false;
            if (team.getManager() != null && user.getId().equals(team.getManager().getId())) {
                team.setManager(null);
                changed = true;
            }
            if (team.getMembers().removeIf(member -> user.getId().equals(member.getId()))) {
                changed = true;
            }
            if (changed) {
                changedTeams.add(team);
            }
        }
        if (!changedTeams.isEmpty()) {
            teamRepository.saveAll(changedTeams);
        }
    }

    @GetMapping("/edit-employee/{id}")
    public String editEmployeePage(@PathVariable Long id, HttpServletRequest request, Model model) {
        injectUser(request, model);
        var emp = userRepository.findById(id).orElse(null);
        if (emp == null || !isNonAdminRole(emp.getRole())) {
            return REDIRECT_HR_EMPLOYEES;
        }
        var tenant = getTenantSegment(request);
        model.addAttribute("domainCategories", domainCategoryRepository.findByTenantSegment(tenant));
        model.addAttribute("employee", emp);
        return "hr-edit-employee";
    }

    @PostMapping("/edit-employee/{id}")
    public String updateEmployee(@PathVariable Long id,
                                 @RequestParam String username,
                                 @RequestParam String email,
                                 @RequestParam String role,
                                 @RequestParam(required = false) String password,
                                 @RequestParam(required = false) String confirmPassword,
                                 @RequestParam(required = false) String domain,
                                 @RequestParam(required = false) String joiningDate,
                                 HttpServletRequest request,
                                 RedirectAttributes ra) {
        var emp = userRepository.findById(id).orElse(null);
        if (emp == null || !isNonAdminRole(emp.getRole())) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "User not found or cannot be edited.");
            return REDIRECT_HR_EMPLOYEES;
        }

        var segment = getTenantSegment(request);

        var validationError = validateUpdateEmployee(emp, username, email, role, segment);
        if (validationError != null) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, validationError);
            return REDIRECT_HR_EDIT_EMPLOYEE + id;
        }

        emp.setUsername(username.trim());
        emp.setEmail(email.trim());

        // Optional password change
        if (password != null && !password.isBlank()) {
            var passwordError = validatePassword(password, confirmPassword);
            if (passwordError != null) {
                ra.addFlashAttribute(ATTR_ERROR_MESSAGE, passwordError);
                return REDIRECT_HR_EDIT_EMPLOYEE + id;
            }
            emp.setPassword(passwordEncoder.encode(password));
        }

        emp.setRole(role.toUpperCase());
        if (domain != null && !domain.trim().isEmpty()) {
            emp.setDomain(domain.trim());
        }
        if (joiningDate != null && !joiningDate.trim().isEmpty()) {
            try {
                emp.setJoiningDate(java.time.LocalDate.parse(joiningDate.trim()));
            } catch (Exception e) {
                // Keep existing
            }
        }
        userRepository.save(emp);
        notificationService.notifyEmployeeManagementChanged(getTenantSegment(request), "updated", emp.getUsername());
        ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "'" + emp.getUsername() + "' updated successfully.");
        return REDIRECT_HR_EMPLOYEES;
    }

    /**
     * REST: GET /hr/api/employee/{id}
     * Returns employee profile + last 30 days attendance as JSON for the modal.
     */
    @GetMapping("/api/employee/{id}")
    @ResponseBody
    public Map<String, Object> employeeDetail(@PathVariable Long id, HttpServletRequest request) {
        attendanceService.processAutoPunchOuts();
        var resp = new LinkedHashMap<String, Object>();
        var tenant = getTenantSegment(request);

        var user = userRepository.findById(id).orElse(null);
        if (user == null || !isNonAdminRole(user.getRole())) {
            resp.put(ATTR_ERROR, "User not found.");
            return resp;
        }
        // Prevent HR from viewing their own record via the modal
        var currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        if (user.getUsername().equals(currentUsername)) {
            resp.put(ATTR_ERROR, "User not found.");
            return resp;
        }

        // Profile
        resp.put("id",       user.getId());
        resp.put("username", user.getUsername());
        resp.put("email",    user.getEmail());
        resp.put("role",     user.getRole());
        resp.put("status",   user.getStatus());

        // Last 30 days attendance
        var today = LocalDate.now();
        var from  = today.minusDays(29);
        var holidays = fetchHolidays(tenant, from, today);
        var records = attendanceRepository.findByUserAndDateBetweenOrderByDateDesc(user, from, today);
        var days = buildDayList(records, from, today, holidays);

        // Stats
        var present  = days.stream().filter(d -> STATUS_PRESENT.equals(d.getStatus()) || "late".equals(d.getStatus())).count();
        var absent   = days.stream().filter(d -> STATUS_ABSENT.equals(d.getStatus())).count();
        var halfDay  = days.stream().filter(d -> "half-day".equals(d.getStatus())).count();
        var holiday  = days.stream().filter(d -> "holiday".equals(d.getStatus())).count();
        resp.put("presentDays", present);
        resp.put("absentDays",  absent);
        resp.put("halfDays",    halfDay);
        resp.put("holidays",    holiday);

        // Attendance rows (last 30 days, newest first)
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

    @GetMapping("/recruitment")
    public String recruitmentPage(HttpServletRequest request, Model model) {
        return "redirect:/hr/dashboard";
    }

    private void populatePunchModel(Map<LocalDate, String> holidays, Optional<Attendance> todayOpt, Model model) {
        var today = LocalDate.now();
        var todayHolidayName = holidays.get(today);
        var isHolidayToday = todayHolidayName != null;

        var punchedIn = todayOpt.isPresent();
        var punchedOut = todayOpt.map(a -> a.getCheckOut() != null).orElse(false);
        var onBreak = todayOpt.map(a ->
                (a.getBreakStart() != null && a.getBreakEnd() == null) ||
                (a.getBreak2Start() != null && a.getBreak2End() == null)).orElse(false);
        var breakDone = todayOpt.map(a -> a.getBreak2End() != null).orElse(false);
        var canStartBreak = punchedIn && !punchedOut && !onBreak && !breakDone &&
                todayOpt.map(a -> a.getBreakStart() == null ||
                        (a.getBreakEnd() != null && a.getBreak2Start() == null)).orElse(false);

        model.addAttribute("punchedIn", punchedIn);
        model.addAttribute("punchedOut", punchedOut);
        model.addAttribute("onBreak", onBreak);
        model.addAttribute("breakDone", breakDone);
        model.addAttribute("canStartBreak", canStartBreak);
        model.addAttribute("isHolidayToday", isHolidayToday);
        model.addAttribute("todayHolidayName", todayHolidayName);
    }

    @GetMapping("/attendance")
    public String attendancePage(
            HttpServletRequest request,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String status,
            Model model) {
        attendanceService.processAutoPunchOuts();
        injectUser(request, model);
        injectStats(request, model);

        var hr = getCurrentHr();
        var today = LocalDate.now();

        // Date range (default: last 30 days)
        var range = resolveDateRange(from, to);
        var filterFrom = range[0];
        var filterTo = range[1];

        // Fetch real records in range
        var tenant = getTenantSegmentFromUser(hr);
        var records = hr != null
                ? attendanceRepository.findByUserAndDateBetweenOrderByDateDesc(hr, filterFrom, filterTo)
                : Collections.<Attendance>emptyList();

        // Holidays in range
        var holidays = fetchHolidays(tenant, filterFrom, filterTo);

        // Today's record — drives button state
        var todayOpt = hr != null
                ? attendanceRepository.findByUserAndDate(hr, today)
                : Optional.<Attendance>empty();

        populatePunchModel(holidays, todayOpt, model);

        // Build merged day list (fills absent/weekend/holiday gaps)
        var allDays = buildDayList(records, filterFrom, filterTo, holidays);

        // Apply status filter
        var filteredDays = allDays;
        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            filteredDays = allDays.stream()
                    .filter(d -> d.getStatus().equalsIgnoreCase(status))
                    .collect(Collectors.toList());
        }

        // Stats from all-time records
        var allRecords = hr != null
                ? attendanceRepository.findByUserOrderByDateDesc(hr)
                : Collections.<Attendance>emptyList();
        var presentCount = allRecords.stream()
                .filter(a -> STATUS_PRESENT.equalsIgnoreCase(a.getStatus()) || "late".equalsIgnoreCase(a.getStatus()))
                .count();
        var lateCount = allRecords.stream()
                .filter(a -> "late".equalsIgnoreCase(a.getStatus()))
                .count();

        model.addAttribute("attendanceDays",    filteredDays);
        model.addAttribute("totalRecords",      filteredDays.size());
        model.addAttribute("todayRecord",       todayOpt.orElse(null));
        model.addAttribute("presentCount",      presentCount);
        model.addAttribute("lateCount",         lateCount);
        model.addAttribute("filterFrom",        filterFrom.toString());
        model.addAttribute("filterTo",          filterTo.toString());
        model.addAttribute("filterStatus",      status != null ? status : "all");

        return "hr-attendance";
    }

    @PostMapping("/attendance/punch-in")
    public String punchIn(HttpServletRequest request, RedirectAttributes ra) {
        var hr = getCurrentHr();
        if (hr == null) return REDIRECT_HR_ATTENDANCE + ERROR_AUTH;

        var today  = LocalDate.now();
        var tenant = getTenantSegmentFromUser(hr);

        // Block punch-in on holidays
        if (holidayRepository.findByDateAndTenantSegment(today.toString(), tenant).isPresent()) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Today is a holiday. Punch-in is not allowed.");
            return REDIRECT_HR_ATTENDANCE + "?error=holiday";
        }

        if (attendanceRepository.findByUserAndDate(hr, today).isPresent()) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "You have already punched in today.");
            return REDIRECT_HR_ATTENDANCE + ERROR_ALREADY;
        }

        var now    = LocalTime.now();
        var status  = now.isAfter(LocalTime.of(9, 30)) ? "late" : STATUS_PRESENT;

        var att = new Attendance();
        att.setUser(hr);
        att.setDate(today);
        att.setCheckIn(now);
        att.setStatus(status);
        att.setTenantSegment(tenant);
        attendanceRepository.save(att);
        notificationService.notifyAttendanceUpdated(hr, "punch-in");

        ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE,
                "Punched in at " + String.format(TIME_FORMAT, now.getHour(), now.getMinute()) + ".");
        return REDIRECT_HR_ATTENDANCE + SUCCESS;
    }

    @PostMapping("/attendance/punch-out")
    public String punchOut(RedirectAttributes ra) {
        var hr = getCurrentHr();
        if (hr == null) return REDIRECT_HR_ATTENDANCE + ERROR_AUTH;

        var today = LocalDate.now();
        var att = getAndValidateTodayAttendance(hr, today, ra);
        if (att == null) {
            return REDIRECT_HR_ATTENDANCE + ERROR_NOT_PUNCHED;
        }
        if (att.getCheckOut() != null) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "You have already punched out today.");
            return REDIRECT_HR_ATTENDANCE + ERROR_ALREADY;
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
        notificationService.notifyAttendanceUpdated(hr, "punch-out");
        ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE,
                "Punched out at " + String.format(TIME_FORMAT, now.getHour(), now.getMinute()) + ".");
        return REDIRECT_HR_ATTENDANCE + SUCCESS;
    }

    @PostMapping("/attendance/break-start")
    public String breakStart(RedirectAttributes ra) {
        var hr = getCurrentHr();
        if (hr == null) return REDIRECT_HR_ATTENDANCE + ERROR_AUTH;

        var today = LocalDate.now();
        var att = getAndValidateTodayAttendance(hr, today, ra);
        if (att == null) {
            return REDIRECT_HR_ATTENDANCE + ERROR_NOT_PUNCHED;
        }
        if (att.getCheckOut() != null) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "You have already punched out.");
            return REDIRECT_HR_ATTENDANCE + ERROR_ALREADY;
        }
        var now = LocalTime.now();
        if (att.getBreakStart() == null) {
            att.setBreakStart(now);
            attendanceRepository.save(att);
            notificationService.notifyAttendanceUpdated(hr, "break-1-start");
            ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE,
                    "Break 1 started at " + String.format(TIME_FORMAT, now.getHour(), now.getMinute()) + ".");
            return REDIRECT_HR_ATTENDANCE + "?success=break1";
        }
        if (att.getBreakEnd() != null && att.getBreak2Start() == null) {
            att.setBreak2Start(now);
            attendanceRepository.save(att);
            notificationService.notifyAttendanceUpdated(hr, "break-2-start");
            ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE,
                    "Break 2 started at " + String.format(TIME_FORMAT, now.getHour(), now.getMinute()) + ".");
            return REDIRECT_HR_ATTENDANCE + "?success=break2";
        }
        ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "No more breaks available today.");
        return REDIRECT_HR_ATTENDANCE + "?error=nobreaks";
    }

    @PostMapping("/attendance/break-end")
    public String breakEnd(RedirectAttributes ra) {
        var hr = getCurrentHr();
        if (hr == null) return REDIRECT_HR_ATTENDANCE + ERROR_AUTH;

        var today = LocalDate.now();
        var att = getAndValidateTodayAttendance(hr, today, ra);
        if (att == null) {
            return REDIRECT_HR_ATTENDANCE + ERROR_NOT_PUNCHED;
        }
        var now = LocalTime.now();
        if (att.getBreak2Start() != null && att.getBreak2End() == null) {
            att.setBreak2End(now);
            attendanceRepository.save(att);
            notificationService.notifyAttendanceUpdated(hr, "break-2-end");
            ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE,
                    "Break 2 ended at " + String.format(TIME_FORMAT, now.getHour(), now.getMinute()) + ".");
            return REDIRECT_HR_ATTENDANCE + "?success=break2end";
        }
        if (att.getBreakStart() != null && att.getBreakEnd() == null) {
            att.setBreakEnd(now);
            attendanceRepository.save(att);
            notificationService.notifyAttendanceUpdated(hr, "break-1-end");
            ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE,
                    "Break 1 ended at " + String.format(TIME_FORMAT, now.getHour(), now.getMinute()) + ".");
            return REDIRECT_HR_ATTENDANCE + "?success=break1end";
        }
        ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "No active break to end.");
        return REDIRECT_HR_ATTENDANCE + "?error=noactivebreak";
    }

    @GetMapping("/leaves")
    public String leavesPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        injectStats(request, model);
        var tenant = getTenantSegment(request);
        model.addAttribute("leaveRequests", tenant.isEmpty() ? Collections.emptyList() : leaveRequestRepository.findByTenantSegmentOrderByCreatedAtDesc(tenant));
        return "hr-leaves";
    }

    @GetMapping("/leaves/view/{id}")
    public ResponseEntity<byte[]> viewLeaveAttachment(@PathVariable Long id, HttpServletRequest request) {
        var tenant = getTenantSegment(request);
        var leave = leaveRequestRepository.findById(id).orElse(null);
        if (leave == null || !tenant.equals(leave.getTenantSegment()) || leave.getAttachmentData() == null || leave.getAttachmentData().length == 0) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + (leave.getAttachmentName() != null ? leave.getAttachmentName() : "leave-attachment") + "\"")
                .header(HttpHeaders.CONTENT_TYPE, leave.getAttachmentContentType() != null ? leave.getAttachmentContentType() : OCTET_STREAM)
                .body(leave.getAttachmentData());
    }

    @PostMapping("/leaves/{id}/review")
    public String reviewLeave(@PathVariable Long id,
                              @RequestParam String action,
                              @RequestParam(required = false) String rejectionMessage,
                              HttpServletRequest request,
                              RedirectAttributes ra) {
        var tenant = getTenantSegment(request);
        var hr = getCurrentHr();
        var leave = leaveRequestRepository.findById(id).orElse(null);
        if (leave == null || !tenant.equals(leave.getTenantSegment())) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Leave request not found.");
            return REDIRECT_HR_LEAVES + ERROR_NOT_FOUND;
        }

        if ("approve".equalsIgnoreCase(action)) {
            leave.setStatus(STATUS_APPROVED);
            ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Leave request approved.");
        } else if ("reject".equalsIgnoreCase(action)) {
            if (rejectionMessage != null && rejectionMessage.trim().length() > 255) {
                ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Rejection message/reason cannot exceed 255 characters.");
                return REDIRECT_HR_LEAVES + "?error=message";
            }
            leave.setStatus(STATUS_REJECTED);
            leave.setRejectionMessage(rejectionMessage != null ? rejectionMessage.trim() : "");
            ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Leave request rejected.");
        } else {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Invalid leave action.");
            return REDIRECT_HR_LEAVES + "?error=invalid";
        }

        leave.setReviewedBy(hr != null ? hr.getUsername() : null);
        leave.setReviewedAt(LocalDateTime.now());
        leaveRequestRepository.save(leave);

        if (leave.getEmployee() != null) {
            notificationService.notifyLeaveReviewed(
                    leave.getEmployee(),
                    leave.getStatus(),
                    leave.getType(),
                    leave.getFromDate(),
                    leave.getToDate(),
                    hr != null ? hr.getUsername() : "HR");
        }
        return REDIRECT_HR_LEAVES + SUCCESS;
    }

    @GetMapping("/calendar")
    public String calendarPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        model.addAttribute("pageTitle",   "HR — Calendar");
        model.addAttribute("pageHeading", "Holiday Calendar");
        model.addAttribute(ATTR_ACTIVE_PAGE,  "calendar");
        return "hr-calendar";
    }

    @GetMapping("/reports")
    public String reportsPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        injectStats(request, model);
        var tenant = getTenantSegment(request);

        var hr = getCurrentHr();
        var allReports = hr != null
                ? reportRepository.findByRecipientId(String.valueOf(hr.getId()), tenant)
                : Collections.<com.crm.demo.model.Report>emptyList();

        model.addAttribute("allReports",  allReports);
        model.addAttribute("reportCount", allReports.size());
        return "hr-reports";
    }

    @GetMapping("/reports/view/{attachmentId}")
    public ResponseEntity<byte[]> viewReportAttachment(@PathVariable Long attachmentId) {
        var att = reportAttachmentRepository.findById(attachmentId).orElse(null);
        if (att == null) return ResponseEntity.notFound().build();
        var ct = att.getContentType() != null ? att.getContentType() : OCTET_STREAM;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + att.getOriginalFilename() + "\"")
                .header(HttpHeaders.CONTENT_TYPE, ct)
                .body(att.getFileData());
    }

    @GetMapping("/reports/download/{attachmentId}")
    public ResponseEntity<byte[]> downloadReportAttachment(@PathVariable Long attachmentId) {
        var att = reportAttachmentRepository.findById(attachmentId).orElse(null);
        if (att == null) return ResponseEntity.notFound().build();
        var ct = att.getContentType() != null ? att.getContentType() : OCTET_STREAM;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + att.getOriginalFilename() + "\"")
                .header(HttpHeaders.CONTENT_TYPE, ct)
                .body(att.getFileData());
    }

    @GetMapping("/profile")
    public String profilePage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        injectStats(request, model);
        var currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        var hr = userRepository.findByUsername(currentUsername);
        model.addAttribute("hrEmail", hr != null ? hr.getEmail() : "");
        return "hr-profile";
    }

    @PostMapping("/update-profile")
    public String updateProfile(@RequestParam(required = false) String username,
                                @RequestParam(required = false) String email,
                                @RequestParam(required = false) String password,
                                @RequestParam(required = false) String confirmPassword,
                                HttpServletResponse response,
                                RedirectAttributes ra) {
        var currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        var hr = userRepository.findByUsername(currentUsername);
        if (hr == null) return "redirect:/hr/profile" + ERROR_AUTH;

        var success = profileUpdateService.updateProfile(hr, username, email, password, confirmPassword, ra, response);
        return success ? "redirect:/hr/profile" + SUCCESS : "redirect:/hr/profile?error=validation";
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  TEAM MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════

    /** GET /hr/teams — list all teams for this tenant. */
    @GetMapping("/teams")
    public String teamsPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        var tenant = getTenantSegment(request);

        var teams = teamRepository.findByTenantSegmentOrderByIdDesc(tenant);

        // Managers and employees available in this tenant
        var tenantUsers = tenant.isEmpty()
                ? userRepository.findAll()
                : userRepository.findByTenantSegment(tenant);

        var managers  = tenantUsers.stream()
                .filter(u -> "MANAGER".equalsIgnoreCase(u.getRole()) && u.isActive()).collect(Collectors.toList());
        var employees = tenantUsers.stream()
                .filter(u -> ROLE_EMPLOYEE.equalsIgnoreCase(u.getRole()) && u.isActive()).collect(Collectors.toList());

        model.addAttribute(ATTR_TEAMS,     teams);
        model.addAttribute("managers",  managers);
        model.addAttribute(ATTR_EMPLOYEES, employees);
        model.addAttribute("teamCount", teams.size());
        model.addAttribute(ATTR_ACTIVE_PAGE, ATTR_TEAMS);
        return "hr-teams";
    }

    /** POST /hr/teams — create a new team. */
    @PostMapping("/teams")
    public String createTeam(@RequestParam String name,
                             @RequestParam(required = false) Long managerId,
                             HttpServletRequest request,
                             RedirectAttributes ra) {
        var tenant = getTenantSegment(request);

        if (name == null || name.isBlank()) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Team name is required.");
            return REDIRECT_HR_TEAMS + "?error=name";
        }
        if (name.trim().length() > 255) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Team name cannot exceed 255 characters.");
            return REDIRECT_HR_TEAMS + "?error=length";
        }
        if (teamRepository.existsByNameAndTenantSegment(name.trim(), tenant)) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "A team named '" + name.trim() + "' already exists.");
            return REDIRECT_HR_TEAMS + ERROR_EXISTS;
        }

        var team = new Team();
        team.setName(name.trim());
        team.setTenantSegment(tenant);

        if (managerId != null) {
            userRepository.findById(managerId).ifPresent(team::setManager);
        }

        teamRepository.save(team);
        ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Team '" + name.trim() + "' created.");
        return REDIRECT_HR_TEAMS + SUCCESS;
    }

    /** POST /hr/teams/{id}/assign-manager — assign or change the manager. */
    @PostMapping("/teams/{id}/assign-manager")
    public String assignManager(@PathVariable Long id,
                                @RequestParam Long managerId,
                                HttpServletRequest request,
                                RedirectAttributes ra) {
        var tenant = getTenantSegment(request);
        var team = getAndValidateTeam(id, tenant, ra);
        if (team == null) {
            return REDIRECT_HR_TEAMS + ERROR_NOT_FOUND;
        }
        userRepository.findById(managerId).ifPresent(manager -> {
            team.setManager(manager);
            notificationService.notifyManagerAssigned(manager, team.getName());
        });
        teamRepository.save(team);
        ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Manager assigned to team '" + team.getName() + "'.");
        return REDIRECT_HR_TEAMS + SUCCESS;
    }

    private void processMemberAddition(Long empId, Team team, List<String> skipped, int[] added) {
        var emp = userRepository.findById(empId).orElse(null);
        if (emp == null || !ROLE_EMPLOYEE.equalsIgnoreCase(emp.getRole())) {
            skipped.add("ID " + empId + " not found");
            return;
        }
        if (team.getMembers().contains(emp)) {
            skipped.add(emp.getUsername() + " already in team");
            return;
        }
        team.getMembers().add(emp);
        notificationService.notifyTeamAdded(emp, team.getName());
        added[0]++;
    }

    /** POST /hr/teams/{id}/add-member — add one or more employees to the team. */
    @PostMapping("/teams/{id}/add-member")
    public String addMember(@PathVariable Long id,
                            @RequestParam(value = "employeeIds", required = false) List<Long> employeeIds,
                            HttpServletRequest request,
                            RedirectAttributes ra) {
        var tenant = getTenantSegment(request);
        var team = getAndValidateTeam(id, tenant, ra);
        if (team == null) {
            return REDIRECT_HR_TEAMS + ERROR_NOT_FOUND;
        }
        if (employeeIds == null || employeeIds.isEmpty()) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Please select at least one employee.");
            return REDIRECT_HR_TEAMS + "?error=noemployees";
        }

        var added = new int[]{0};
        var skipped = new ArrayList<String>();

        for (var empId : employeeIds) {
            processMemberAddition(empId, team, skipped, added);
        }

        if (added[0] > 0) {
            teamRepository.save(team);
            var msg = added[0] + " member(s) added to team '" + team.getName() + "'.";
            if (!skipped.isEmpty()) msg += " Skipped: " + String.join(", ", skipped) + ".";
            ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, msg);
            return REDIRECT_HR_TEAMS + SUCCESS;
        } else {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE,
                    "No members added. " + (skipped.isEmpty() ? "" : "Skipped: " + String.join(", ", skipped)));
            return REDIRECT_HR_TEAMS + "?error=skipped";
        }
    }

    /** POST /hr/teams/{id}/remove-member — remove an employee from the team. */
    @PostMapping("/teams/{id}/remove-member")
    public String removeMember(@PathVariable Long id,
                               @RequestParam Long employeeId,
                               HttpServletRequest request,
                               RedirectAttributes ra) {
        var tenant = getTenantSegment(request);
        var team = getAndValidateTeam(id, tenant, ra);
        if (team == null) {
            return REDIRECT_HR_TEAMS + ERROR_NOT_FOUND;
        }
        team.getMembers().removeIf(m -> m.getId().equals(employeeId));
        teamRepository.save(team);
        ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Member removed from team '" + team.getName() + "'.");
        return REDIRECT_HR_TEAMS + SUCCESS;
    }

    /** POST /hr/teams/{id}/delete — delete a team. */
    @PostMapping("/teams/{id}/delete")
    public String deleteTeam(@PathVariable Long id,
                             HttpServletRequest request,
                             RedirectAttributes ra) {
        var tenant = getTenantSegment(request);
        var team = getAndValidateTeam(id, tenant, ra);
        if (team == null) {
            return REDIRECT_HR_TEAMS + ERROR_NOT_FOUND;
        }
        var name = team.getName();
        teamRepository.delete(team);
        ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Team '" + name + "' deleted.");
        return REDIRECT_HR_TEAMS + SUCCESS;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  MEETINGS
    // ═══════════════════════════════════════════════════════════════════════


    private List<Meeting> getPastMeetings(String tenant, String username) {
        var all = meetingRepository.findAllMeetingsForUserOrHost(tenant, username);
        var today = LocalDate.now();
        var now   = LocalTime.now();
        return all.stream().filter(m -> {
            if (m.getMeetingDate().isBefore(today)) return true;
            if (!m.getMeetingDate().equals(today)) return false;
            if (m.getMeetingTime() == null) return false;
            var dur = (m.getDuration() != null) ? m.getDuration() : 0;
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
            var dur = (m.getDuration() != null) ? m.getDuration() : 0;
            return !m.getMeetingTime().plusMinutes(dur).isBefore(now);
        }).collect(Collectors.toList());
    }

    /** GET /hr/meetings — side-by-side schedule form + meetings list */
    @GetMapping("/meetings")
    public String meetingsPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        var tenant   = getTenantSegment(request);
        var username = (String) request.getAttribute(ATTR_LOGGED_IN_USER);

        model.addAttribute(ATTR_UPCOMING_MEETINGS, getUpcomingMeetings(tenant, username != null ? username : ""));
        model.addAttribute(ATTR_PAST_MEETINGS, getPastMeetings(tenant, username != null ? username : ""));

        // All non-admin users in this tenant as potential participants (excluding the host)
        var tenantUsers = tenant.isEmpty()
                ? userRepository.findAll()
                : userRepository.findByTenantSegment(tenant);
        final var currentUsername = username != null ? username : "";
        model.addAttribute(ATTR_TENANT_USERS, tenantUsers.stream()
                .filter(u -> !ROLE_ADMIN.equalsIgnoreCase(u.getRole())
                          && !ROLE_SUPER_ADMIN.equalsIgnoreCase(u.getRole())
                          && !u.getUsername().equalsIgnoreCase(currentUsername))
                .collect(Collectors.toList()));

        if (!model.containsAttribute(ATTR_MEETING_FORM)) {
            model.addAttribute(ATTR_MEETING_FORM, new Meeting());
        }
        return PAGE_MEETINGS;
    }

    /** POST /hr/meetings — create a new meeting */
    @PostMapping("/meetings")
    public String scheduleMeeting(@Valid @ModelAttribute(ATTR_MEETING_FORM) Meeting meetingForm,
                                  BindingResult result,
                                  HttpServletRequest request,
                                  Model model,
                                  RedirectAttributes ra) {
        var tenant   = getTenantSegment(request);
        var username = (String) request.getAttribute(ATTR_LOGGED_IN_USER);

        if ("in-person".equalsIgnoreCase(meetingForm.getMeetingType()) && (meetingForm.getLocation() == null || meetingForm.getLocation().isBlank())) {
            result.rejectValue("location", "NotBlank", "Location is required for in-person meetings.");
        }

        if (result.hasErrors()) {
            injectUser(request, model);
            model.addAttribute(ATTR_UPCOMING_MEETINGS, getUpcomingMeetings(tenant, username != null ? username : ""));
            model.addAttribute(ATTR_PAST_MEETINGS, getPastMeetings(tenant, username != null ? username : ""));
            var tenantUsers = tenant.isEmpty()
                    ? userRepository.findAll()
                    : userRepository.findByTenantSegment(tenant);
            final var currentUsername = username != null ? username : "";
            model.addAttribute(ATTR_TENANT_USERS, tenantUsers.stream()
                    .filter(u -> !ROLE_ADMIN.equalsIgnoreCase(u.getRole())
                              && !ROLE_SUPER_ADMIN.equalsIgnoreCase(u.getRole())
                              && !u.getUsername().equalsIgnoreCase(currentUsername))
                    .collect(Collectors.toList()));
            model.addAttribute(ATTR_ERROR_MESSAGE, "Please fix the errors below.");
            return PAGE_MEETINGS;
        }

        meetingForm.setTenantSegment(tenant);
        meetingForm.setScheduledBy(username != null ? username : "");
        meetingRepository.save(meetingForm);
        notificationService.notifyMeetingParticipants(meetingForm);
        ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Meeting scheduled successfully.");
        return REDIRECT_HR_MEETINGS;
    }

    /** GET /hr/meetings/edit/{id} — load meeting into form */
    @GetMapping("/meetings/edit/{id}")
    public String editMeetingPage(@PathVariable Long id,
                                  HttpServletRequest request,
                                  Model model,
                                  RedirectAttributes ra) {
        injectUser(request, model);
        var tenant   = getTenantSegment(request);
        var username = (String) request.getAttribute(ATTR_LOGGED_IN_USER);

        var meeting = meetingRepository.findById(id).orElse(null);
        if (meeting == null || !tenant.equals(meeting.getTenantSegment())) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, MSG_MEETING_NOT_FOUND);
            return REDIRECT_HR_MEETINGS;
        }

        model.addAttribute(ATTR_MEETING_FORM, meeting);
        model.addAttribute(ATTR_UPCOMING_MEETINGS, getUpcomingMeetings(tenant, username != null ? username : ""));
        model.addAttribute(ATTR_PAST_MEETINGS, getPastMeetings(tenant, username != null ? username : ""));
        var tenantUsers = tenant.isEmpty()
                ? userRepository.findAll()
                : userRepository.findByTenantSegment(tenant);
        final var currentUsername = username != null ? username : "";
        model.addAttribute(ATTR_TENANT_USERS, tenantUsers.stream()
                .filter(u -> !ROLE_ADMIN.equalsIgnoreCase(u.getRole())
                          && !ROLE_SUPER_ADMIN.equalsIgnoreCase(u.getRole())
                          && !u.getUsername().equalsIgnoreCase(currentUsername))
                .collect(Collectors.toList()));
        return PAGE_MEETINGS;
    }

    /** POST /hr/meetings/edit/{id} — update existing meeting */
    @PostMapping("/meetings/edit/{id}")
    public String updateMeeting(@PathVariable Long id,
                                @Valid @ModelAttribute(ATTR_MEETING_FORM) Meeting meetingForm,
                                BindingResult result,
                                HttpServletRequest request,
                                Model model,
                                RedirectAttributes ra) {
        var tenant   = getTenantSegment(request);
        var username = (String) request.getAttribute(ATTR_LOGGED_IN_USER);

        if ("in-person".equalsIgnoreCase(meetingForm.getMeetingType()) && (meetingForm.getLocation() == null || meetingForm.getLocation().isBlank())) {
            result.rejectValue("location", "NotBlank", "Location is required for in-person meetings.");
        }

        if (result.hasErrors()) {
            injectUser(request, model);
            model.addAttribute(ATTR_UPCOMING_MEETINGS, getUpcomingMeetings(tenant, username != null ? username : ""));
            model.addAttribute(ATTR_PAST_MEETINGS, getPastMeetings(tenant, username != null ? username : ""));
            var tenantUsers = tenant.isEmpty()
                    ? userRepository.findAll()
                    : userRepository.findByTenantSegment(tenant);
            final var currentUsername = username != null ? username : "";
            model.addAttribute(ATTR_TENANT_USERS, tenantUsers.stream()
                    .filter(u -> !ROLE_ADMIN.equalsIgnoreCase(u.getRole())
                              && !ROLE_SUPER_ADMIN.equalsIgnoreCase(u.getRole())
                              && !u.getUsername().equalsIgnoreCase(currentUsername))
                    .collect(Collectors.toList()));
            model.addAttribute(ATTR_ERROR_MESSAGE, "Please fix the errors below.");
            return PAGE_MEETINGS;
        }

        var existing = meetingRepository.findById(id).orElse(null);
        if (existing == null || !tenant.equals(existing.getTenantSegment())) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, MSG_MEETING_NOT_FOUND);
            return REDIRECT_HR_MEETINGS;
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
        return REDIRECT_HR_MEETINGS;
    }

    /** POST /hr/meetings/delete/{id} — delete a meeting */
    @PostMapping("/meetings/delete/{id}")
    public String deleteMeeting(@PathVariable Long id,
                                HttpServletRequest request,
                                RedirectAttributes ra) {
        var tenant = getTenantSegment(request);
        var meeting = meetingRepository.findById(id).orElse(null);
        if (meeting == null || !tenant.equals(meeting.getTenantSegment())) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, MSG_MEETING_NOT_FOUND);
        } else {
            meetingRepository.delete(meeting);
            ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Meeting deleted successfully.");
        }
        return REDIRECT_HR_MEETINGS;
    }

    @SuppressWarnings("unchecked")
    @GetMapping("/performance")
    public String performancePage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        injectStats(request, model);
        
        var allEmployees = (List<User>) model.getAttribute(ATTR_EMPLOYEES);
        if (allEmployees != null) {
            var onlyEmployees = allEmployees.stream()
                    .filter(u -> ROLE_EMPLOYEE.equalsIgnoreCase(u.getRole()))
                    .collect(Collectors.toList());
            model.addAttribute(ATTR_EMPLOYEES, onlyEmployees);
        }
        
        return "hr-performance";
    }

    // ── PAYROLL ───────────────────────────────────────────────────────────

    @GetMapping("/payroll")
    public String payrollPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        injectStats(request, model);
        var tenant = getTenantSegment(request);
        var payrolls = payrollTemplateRepository.findByTenantSegmentOrderByCreatedAtDesc(tenant);
        model.addAttribute("payrolls", payrolls);
        model.addAttribute("totalPayroll", payrolls.size());
        var totalNet = payrolls.stream()
                .map(PayrollTemplate::getNetSalary)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        model.addAttribute("totalNetSalary", totalNet);
        // For the create/edit modal — list of employees without a template yet
        var tenantEmployees = userRepository.findEmployeesAndManagersByTenant(tenant);
        var existingIds = payrolls.stream()
                .map(p -> p.getEmployee().getId()).collect(Collectors.toList());
        var unassigned = tenantEmployees.stream()
                .filter(u -> !existingIds.contains(u.getId())).collect(Collectors.toList());
        model.addAttribute("unassignedEmployees", unassigned);

        var payslips = payslipRepository.findByTenantSegmentOrderByIdDesc(tenant);
        model.addAttribute("payslips", payslips);

        model.addAttribute(ATTR_ACTIVE_PAGE, "payroll");
        return "hr-payroll";
    }

    @PostMapping("/payroll/create")
    public String createPayroll(HttpServletRequest request,
                                 @RequestParam Long employeeId,
                                 @RequestParam(required = false) String designation,
                                 @RequestParam(required = false) String department,
                                 @RequestParam(defaultValue = "0") java.math.BigDecimal basicSalary,
                                 @RequestParam(defaultValue = "0") java.math.BigDecimal hra,
                                 @RequestParam(defaultValue = "0") java.math.BigDecimal transportAllowance,
                                 @RequestParam(defaultValue = "0") java.math.BigDecimal otherAllowance,
                                 @RequestParam(defaultValue = "0") java.math.BigDecimal taxDeduction,
                                 @RequestParam(defaultValue = "0") java.math.BigDecimal pfDeduction,
                                 @RequestParam(defaultValue = "0") java.math.BigDecimal otherDeduction,
                                 @RequestParam(required = false) String bankAccount,
                                 @RequestParam(required = false) Integer paymentMonth,
                                 @RequestParam(required = false) Integer paymentYear,
                                 RedirectAttributes ra) {
        var tenant = getTenantSegment(request);
        var empOpt = userRepository.findById(employeeId);
        if (empOpt.isEmpty()) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Employee not found.");
            return REDIRECT_HR_PAYROLL + ERROR_NOT_FOUND;
        }
        var emp = empOpt.get();
        // Check if template already exists
        var existing = payrollTemplateRepository.findByEmployeeAndTenantSegment(emp, tenant);
        if (existing.isPresent()) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Payroll template already exists for this employee. Use Edit.");
            return REDIRECT_HR_PAYROLL + ERROR_EXISTS;
        }
        var pt = new PayrollTemplate();
        pt.setEmployee(emp);
        pt.setTenantSegment(tenant);
        pt.setDesignation(designation);
        pt.setDepartment(department);
        pt.setBasicSalary(basicSalary);
        pt.setHra(hra);
        pt.setTransportAllowance(transportAllowance);
        pt.setOtherAllowance(otherAllowance);
        pt.setTaxDeduction(taxDeduction);
        pt.setPfDeduction(pfDeduction);
        pt.setOtherDeduction(otherDeduction);
        pt.setBankAccount(bankAccount);
        pt.setPaymentMonth(paymentMonth != null ? paymentMonth : LocalDate.now().getMonthValue());
        pt.setPaymentYear(paymentYear != null ? paymentYear : LocalDate.now().getYear());
        pt.setCreatedAt(LocalDateTime.now());
        pt.setUpdatedAt(LocalDateTime.now());
        payrollTemplateRepository.save(pt);
        ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Payroll template created for " + emp.getUsername() + ".");
        return REDIRECT_HR_PAYROLL + SUCCESS;
    }

    @PostMapping("/payroll/edit/{id}")
    public String editPayroll(HttpServletRequest request,
                               @PathVariable Long id,
                               @RequestParam(required = false) String designation,
                               @RequestParam(required = false) String department,
                               @RequestParam(defaultValue = "0") java.math.BigDecimal basicSalary,
                               @RequestParam(defaultValue = "0") java.math.BigDecimal hra,
                               @RequestParam(defaultValue = "0") java.math.BigDecimal transportAllowance,
                               @RequestParam(defaultValue = "0") java.math.BigDecimal otherAllowance,
                               @RequestParam(defaultValue = "0") java.math.BigDecimal taxDeduction,
                               @RequestParam(defaultValue = "0") java.math.BigDecimal pfDeduction,
                               @RequestParam(defaultValue = "0") java.math.BigDecimal otherDeduction,
                               @RequestParam(required = false) String bankAccount,
                               @RequestParam(required = false) Integer paymentMonth,
                               @RequestParam(required = false) Integer paymentYear,
                               RedirectAttributes ra) {
        var tenant = getTenantSegment(request);
        var pt = getAndValidatePayrollTemplate(id, tenant, ra);
        if (pt == null) {
            return REDIRECT_HR_PAYROLL + ERROR_NOT_FOUND;
        }
        pt.setDesignation(designation);
        pt.setDepartment(department);
        pt.setBasicSalary(basicSalary);
        pt.setHra(hra);
        pt.setTransportAllowance(transportAllowance);
        pt.setOtherAllowance(otherAllowance);
        pt.setTaxDeduction(taxDeduction);
        pt.setPfDeduction(pfDeduction);
        pt.setOtherDeduction(otherDeduction);
        pt.setBankAccount(bankAccount);
        if (paymentMonth != null) pt.setPaymentMonth(paymentMonth);
        if (paymentYear != null) pt.setPaymentYear(paymentYear);
        pt.setUpdatedAt(LocalDateTime.now());
        payrollTemplateRepository.save(pt);
        ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Payroll template updated.");
        return REDIRECT_HR_PAYROLL + SUCCESS;
    }

    @PostMapping("/payroll/delete/{id}")
    public String deletePayroll(HttpServletRequest request,
                                 @PathVariable Long id,
                                 RedirectAttributes ra) {
        var tenant = getTenantSegment(request);
        var pt = getAndValidatePayrollTemplate(id, tenant, ra);
        if (pt != null) {
            payrollTemplateRepository.deleteById(id);
            ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Payroll template deleted.");
            return REDIRECT_HR_PAYROLL + SUCCESS;
        }
        return REDIRECT_HR_PAYROLL + ERROR_NOT_FOUND;
    }

    /** Returns payroll data as JSON for the edit modal. */
    @GetMapping("/payroll/data/{id}")
    @ResponseBody
    public Map<String, Object> getPayrollData(HttpServletRequest request, @PathVariable Long id) {
        var tenant = getTenantSegment(request);
        var pt = getAndValidatePayrollTemplate(id, tenant, null);
        var result = new java.util.LinkedHashMap<String, Object>();
        if (pt == null) {
            result.put(ATTR_ERROR, "Not found");
            return result;
        }
        result.put("id", pt.getId());
        result.put("employeeName", pt.getEmployee().getUsername());
        result.put("designation", pt.getDesignation());
        result.put("department", pt.getDepartment());
        result.put("basicSalary", pt.getBasicSalary());
        result.put("hra", pt.getHra());
        result.put("transportAllowance", pt.getTransportAllowance());
        result.put("otherAllowance", pt.getOtherAllowance());
        result.put("taxDeduction", pt.getTaxDeduction());
        result.put("pfDeduction", pt.getPfDeduction());
        result.put("otherDeduction", pt.getOtherDeduction());
        result.put("bankAccount", pt.getBankAccount());
        result.put("paymentMonth", pt.getPaymentMonth());
        result.put("paymentYear", pt.getPaymentYear());
        return result;
    }

    @PostMapping("/payroll/generate-payslips")
    public String manualGeneratePayslips(HttpServletRequest request,
                                         @RequestParam Integer month,
                                         @RequestParam Integer year,
                                         RedirectAttributes ra) {
        var tenant = getTenantSegment(request);
        if (month == null || month < 1 || month > 12 || year == null) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Invalid month or year.");
            return REDIRECT_HR_PAYROLL + "?error=invalid";
        }

        // Payslips can only be generated on or after the 2nd day of the next month
        var today = java.time.LocalDate.now();
        var earliestAllowedDate = java.time.LocalDate.of(year, month, 1).plusMonths(1).withDayOfMonth(2);
        if (today.isBefore(earliestAllowedDate)) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Payslips for " + java.time.Month.of(month).name() + " " + year + 
                " can only be generated on or after the 2nd day of the next month (" + 
                earliestAllowedDate.format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy")) + ").");
            return REDIRECT_HR_PAYROLL + "?error=date";
        }

        var generated = payslipService.generatePayslipsForTenant(tenant, month, year);
        if (generated == 0) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "No payslips were generated. They may already exist, or there are no active templates.");
            return REDIRECT_HR_PAYROLL + "?error=none";
        } else {
            ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Successfully generated " + generated + " payslip(s) for " + java.time.Month.of(month).name() + " " + year + ".");
            return REDIRECT_HR_PAYROLL + SUCCESS;
        }
    }

    @GetMapping("/payroll/payslip/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getPayslipData(@PathVariable Long id, HttpServletRequest request) {
        var tenant = getTenantSegment(request);
        var opt = payslipRepository.findById(id);
        if (opt.isEmpty() || !tenant.equals(opt.get().getTenantSegment())) {
            return ResponseEntity.notFound().build();
        }
        var p = opt.get();
        var data = new java.util.LinkedHashMap<String, Object>();
        data.put("id", p.getId());
        data.put("employeeName", p.getEmployee().getUsername());
        data.put("employeeEmail", p.getEmployee().getEmail());
        data.put("designation", p.getDesignation() != null ? p.getDesignation() : "");
        data.put("department", p.getDepartment() != null ? p.getDepartment() : "");
        data.put("basicSalary", p.getBasicSalary());
        data.put("hra", p.getHra());
        data.put("transportAllowance", p.getTransportAllowance());
        data.put("otherAllowance", p.getOtherAllowance());
        data.put("taxDeduction", p.getTaxDeduction());
        data.put("pfDeduction", p.getPfDeduction());
        data.put("otherDeduction", p.getOtherDeduction());
        data.put("leaveDeduction", p.getLeaveDeduction());
        data.put("grossSalary", p.getGrossSalary());
        data.put("netSalary", p.getNetSalary());
        data.put("bankAccount", p.getBankAccount() != null ? p.getBankAccount() : "");
        data.put("period", java.time.Month.of(p.getPaymentMonth()).name() + " " + p.getPaymentYear());
        return ResponseEntity.ok(data);
    }

    @GetMapping("/settings")
    public String settingsPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        var tenant = getTenantSegment(request);
        model.addAttribute("categories", domainCategoryRepository.findByTenantSegment(tenant));
        model.addAttribute(ATTR_ACTIVE_PAGE, "settings");
        return "hr-settings";
    }

    @PostMapping("/settings/domain-categories")
    public String addDomainCategory(@RequestParam String name, HttpServletRequest request, RedirectAttributes ra) {
        var tenant = getTenantSegment(request);
        if (name == null || name.trim().isEmpty()) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Domain category name is required.");
            return REDIRECT_HR_SETTINGS + "?error=name";
        }
        var cleanName = name.trim();
        if (domainCategoryRepository.existsByNameAndTenantSegment(cleanName, tenant)) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Domain category already exists.");
            return REDIRECT_HR_SETTINGS + ERROR_EXISTS;
        }
        var cat = new com.crm.demo.model.DomainCategory();
        cat.setName(cleanName);
        cat.setTenantSegment(tenant);
        domainCategoryRepository.save(cat);
        ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Domain category '" + cleanName + "' added successfully.");
        return REDIRECT_HR_SETTINGS + SUCCESS;
    }

    @PostMapping("/settings/domain-categories/delete/{id}")
    public String deleteDomainCategory(@PathVariable Long id, HttpServletRequest request, RedirectAttributes ra) {
        var tenant = getTenantSegment(request);
        var catOpt = domainCategoryRepository.findById(id);
        if (catOpt.isPresent() && tenant.equals(catOpt.get().getTenantSegment())) {
            domainCategoryRepository.delete(catOpt.get());
            ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Domain category deleted successfully.");
            return REDIRECT_HR_SETTINGS + SUCCESS;
        } else {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Domain category not found.");
            return REDIRECT_HR_SETTINGS + ERROR_NOT_FOUND;
        }
    }
}
