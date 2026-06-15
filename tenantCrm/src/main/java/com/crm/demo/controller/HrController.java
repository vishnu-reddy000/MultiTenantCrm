package com.crm.demo.controller;

import java.io.InputStream;
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

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
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
import com.crm.demo.model.Holiday;
import com.crm.demo.model.LeaveRequest;
import com.crm.demo.model.Meeting;
import com.crm.demo.model.Report;
import com.crm.demo.model.ReportAttachment;
import com.crm.demo.model.Task;
import com.crm.demo.model.Team;
import com.crm.demo.model.User;
import com.crm.demo.repository.AttendanceRepository;
import com.crm.demo.repository.HolidayRepository;
import com.crm.demo.repository.LeaveRequestRepository;
import com.crm.demo.repository.MeetingRepository;
import com.crm.demo.repository.PasswordResetTokenRepository;
import com.crm.demo.repository.PerformanceReviewRepository;
import com.crm.demo.repository.ReportAttachmentRepository;
import com.crm.demo.repository.ReportRepository;
import com.crm.demo.repository.TaskRepository;
import com.crm.demo.repository.TeamRepository;
import com.crm.demo.repository.UserRepository;
import com.crm.demo.service.NotificationService;
import com.crm.demo.service.ProfileUpdateService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/hr")
public class HrController {

    @Autowired private UserRepository        userRepository;
    @Autowired private TeamRepository        teamRepository;
    @Autowired private AttendanceRepository  attendanceRepository;
    @Autowired private HolidayRepository     holidayRepository;
    @Autowired private MeetingRepository     meetingRepository;
    @Autowired private LeaveRequestRepository leaveRequestRepository;
    @Autowired private PerformanceReviewRepository performanceReviewRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private TaskRepository         taskRepository;
    @Autowired private com.crm.demo.repository.ReportRepository reportRepository;
    @Autowired private com.crm.demo.repository.ReportAttachmentRepository reportAttachmentRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    @Autowired private ProfileUpdateService  profileUpdateService;
    @Autowired private NotificationService   notificationService;

    // ── Helpers ───────────────────────────────────────────────────────────

    private void injectUser(HttpServletRequest request, Model model) {
        String username = (String) request.getAttribute("loggedInUser");
        model.addAttribute("adminName", username != null ? username : "HR User");
        model.addAttribute("adminRole", "HR");
    }

    /** Resolve the currently logged-in HR user via SecurityContextHolder. */
    private User getCurrentHr() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username);
    }

    /** Extract tenant segment from the logged-in HR user's email. */
    private String getTenantSegment(HttpServletRequest request) {
        String username = (String) request.getAttribute("loggedInUser");
        if (username == null) return "";
        User hr = userRepository.findByUsername(username);
        if (hr == null || hr.getEmail() == null) return "";
        String email = hr.getEmail();
        String local = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        int dot = local.lastIndexOf('.');
        return dot >= 0 ? local.substring(dot + 1) : local;
    }

    private String getTenantSegmentFromUser(User user) {
        if (user == null || user.getEmail() == null) return "";
        String email = user.getEmail();
        String local = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        int dot = local.lastIndexOf('.');
        return dot >= 0 ? local.substring(dot + 1) : local;
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

    /** Returns true for roles that HR should manage (not ADMIN / SUPER_ADMIN). */
    private boolean isNonAdminRole(String role) {
        if (role == null) return false;
        return !role.equalsIgnoreCase("ADMIN") && !role.equalsIgnoreCase("SUPER_ADMIN");
    }

    private void injectStats(HttpServletRequest request, Model model) {
        String tenant = getTenantSegment(request);
        String currentUsername = (String) request.getAttribute("loggedInUser");

        // Include EMPLOYEE, MANAGER — exclude ADMIN, SUPER_ADMIN, and the logged-in HR themselves
        List<User> employees = tenant.isEmpty()
                ? userRepository.findAll().stream()
                        .filter(u -> isNonAdminRole(u.getRole()))
                        .filter(u -> !u.getUsername().equals(currentUsername))
                        .toList()
                : userRepository.findByTenantSegment(tenant).stream()
                        .filter(u -> isNonAdminRole(u.getRole()))
                        .filter(u -> !u.getUsername().equals(currentUsername))
                        .toList();

        long active   = employees.stream().filter(User::isActive).count();
        long inactive = employees.size() - active;

        model.addAttribute("employees",         employees);
        model.addAttribute("totalEmployees",    employees.size());
        model.addAttribute("activeEmployees",   active);
        model.addAttribute("inactiveEmployees", inactive);
        model.addAttribute("newHires",          0);
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
        long onLeaveToday = tenant.isEmpty() ? 0
                : leaveRequestRepository.countByTenantSegmentAndStatusAndFromDateLessThanEqualAndToDateGreaterThanEqual(
                        tenant, "Approved", today, today);
        long pendingLeaves = tenant.isEmpty() ? 0 : leaveRequestRepository.countByTenantSegmentAndStatus(tenant, "Pending");
        long approvedThisMonth = tenant.isEmpty() ? 0
                : leaveRequestRepository.countByTenantSegmentAndStatusAndFromDateBetween(
                        tenant, "Approved", monthStart, monthEnd);

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
        String tenant = getTenantSegment(request);
        String currentUsername = (String) request.getAttribute("loggedInUser");

        List<User> employees = tenant.isBlank()
                ? userRepository.findAll().stream()
                        .filter(u -> isNonAdminRole(u.getRole()))
                        .filter(u -> currentUsername == null || !u.getUsername().equals(currentUsername))
                        .toList()
                : userRepository.findByTenantSegment(tenant).stream()
                        .filter(u -> isNonAdminRole(u.getRole()))
                        .filter(u -> currentUsername == null || !u.getUsername().equals(currentUsername))
                        .toList();

        List<Task> tasks = tenant.isBlank()
                ? taskRepository.findAll()
                : taskRepository.findByTenantSegment(tenant);

        Map<String, Object> data = buildDashboardAnalytics(tasks, employees);
        data.put("totalEmployees", employees.size());
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

        long done = tasks.stream().filter(t -> "done".equalsIgnoreCase(t.getStatus())).count();
        long pending = tasks.stream().filter(t -> "pending".equalsIgnoreCase(t.getStatus())
                || "in-progress".equalsIgnoreCase(t.getStatus())).count();

        model.addAttribute("tasks", tasks);
        model.addAttribute("totalTasks", tasks.size());
        model.addAttribute("doneTasks", done);
        model.addAttribute("pendingTaskCount", pending);

        return "hr-tasks";
    }


    // ═══════════════════════════════════════════════════════════════════════
    //  ADD / TOGGLE / DELETE USERS (HR can manage employees & managers)
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/add-user")
    public String addUserPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        return "hr-add-user";
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
            return "redirect:/hr/add-user";
        }

        // Block creating ADMIN or SUPER_ADMIN accounts
        if ("ADMIN".equalsIgnoreCase(role) || "SUPER_ADMIN".equalsIgnoreCase(role)) {
            ra.addFlashAttribute("errorMessage", "You cannot create an account with that role.");
            return "redirect:/hr/add-user";
        }

        // Enforce tenant domain: new user's email must contain the HR's tenant segment
        String segment = getTenantSegment(request);
        if (segment != null && !segment.isBlank() && !email.contains("." + segment + "@")) {
            ra.addFlashAttribute("errorMessage",
                    "Email must belong to your tenant domain (e.g. user." + segment + "@crm.com).");
            return "redirect:/hr/add-user";
        }

        if (userRepository.existsByUsernameOrEmail(username, email)) {
            ra.addFlashAttribute("errorMessage", "Username or email already exists.");
            return "redirect:/hr/add-user";
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        user.setStatus("active");
        userRepository.save(user);
        ra.addFlashAttribute("successMessage", "User '" + username + "' added successfully.");
        return "redirect:/hr/employees";
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  BULK EMPLOYEE UPLOAD (Excel)
    //  Expected columns (row 0 = header, skipped):
    //    A: username  B: email  C: password  D: role
    //
    //  STRATEGY: validate ALL rows first — if ANY row has an error, reject
    //  the entire file and save nothing. Only save when everything is clean.
    // ═══════════════════════════════════════════════════════════════════════

    @PostMapping("/bulk-upload")
    public String bulkUpload(@RequestParam("file") MultipartFile file,
                              HttpServletRequest request,
                              RedirectAttributes ra) {

        if (file == null || file.isEmpty()) {
            ra.addFlashAttribute("errorMessage", "Please select an Excel file to upload.");
            return "redirect:/hr/add-user";
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null ||
                (!originalFilename.endsWith(".xlsx") && !originalFilename.endsWith(".xls"))) {
            ra.addFlashAttribute("errorMessage", "Only .xlsx or .xls files are supported.");
            return "redirect:/hr/add-user";
        }

        String segment = getTenantSegment(request);

        // ── Phase 1: parse and validate every row, collect all errors ────────
        List<String> errors = new ArrayList<>();
        List<User>   toSave = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);

            if (sheet.getLastRowNum() < 1) {
                ra.addFlashAttribute("errorMessage", "The file has no data rows.");
                return "redirect:/hr/add-user";
            }

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;

                String username = getCellString(row, 0);
                String email    = getCellString(row, 1);
                String password = getCellString(row, 2);
                String role     = getCellString(row, 3);

                // Skip completely blank rows silently
                if (username.isBlank() && email.isBlank() && password.isBlank() && role.isBlank()) continue;

                String rowLabel = "Row " + (rowIndex + 1);

                // Missing fields
                if (username.isBlank()) {
                    errors.add(rowLabel + ": username is empty.");
                    continue;
                }
                if (email.isBlank()) {
                    errors.add(rowLabel + " (" + username + "): email is empty.");
                    continue;
                }
                if (password.isBlank()) {
                    errors.add(rowLabel + " (" + username + "): password is empty.");
                    continue;
                }
                if (role.isBlank()) {
                    errors.add(rowLabel + " (" + username + "): role is empty.");
                    continue;
                }

                // Role check
                if ("ADMIN".equalsIgnoreCase(role) || "SUPER_ADMIN".equalsIgnoreCase(role)) {
                    errors.add(rowLabel + " (" + username + "): role '" + role + "' is not allowed. Use EMPLOYEE or MANAGER.");
                    continue;
                }

                // Tenant domain check
                if (segment != null && !segment.isBlank() && !email.contains("." + segment + "@")) {
                    errors.add(rowLabel + " (" + username + "): email '" + email
                            + "' does not belong to tenant domain (expected format: name." + segment + "@crm.com).");
                    continue;
                }

                // Duplicate check in DB
                if (userRepository.existsByUsernameOrEmail(username, email)) {
                    errors.add(rowLabel + " (" + username + "): username or email already exists in the system.");
                    continue;
                }

                // Duplicate check within the same file (two rows with same username/email)
                boolean duplicateInFile = toSave.stream().anyMatch(u ->
                        u.getUsername().equalsIgnoreCase(username) || u.getEmail().equalsIgnoreCase(email));
                if (duplicateInFile) {
                    errors.add(rowLabel + " (" + username + "): username or email is duplicated within this file.");
                    continue;
                }

                // Valid — stage for saving
                User user = new User();
                user.setUsername(username);
                user.setEmail(email);
                user.setPassword(passwordEncoder.encode(password));
                user.setRole(role.toUpperCase());
                user.setStatus("active");
                toSave.add(user);
            }

        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Failed to parse file: " + e.getMessage());
            return "redirect:/hr/add-user";
        }

        // ── Phase 2: if any errors found, reject everything ──────────────────
        if (!errors.isEmpty()) {
            ra.addFlashAttribute("bulkErrors", errors);
            ra.addFlashAttribute("errorMessage",
                    "Upload rejected — " + errors.size() + " error(s) found. No employees were saved. Fix the issues and re-upload.");
            return "redirect:/hr/add-user";
        }

        // ── Phase 3: all rows valid — save them all ──────────────────────────
        if (toSave.isEmpty()) {
            ra.addFlashAttribute("errorMessage", "No valid data rows found in the file.");
            return "redirect:/hr/add-user";
        }

        userRepository.saveAll(toSave);
        ra.addFlashAttribute("successMessage",
                toSave.size() + " employee(s) imported successfully.");
        return "redirect:/hr/employees";
    }

    /** Safely read a cell value as a trimmed String regardless of cell type. */
    private String getCellString(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        if (cell.getCellType() == CellType.STRING) return cell.getStringCellValue().trim();
        if (cell.getCellType() == CellType.NUMERIC) return String.valueOf((long) cell.getNumericCellValue()).trim();
        if (cell.getCellType() == CellType.BOOLEAN) return String.valueOf(cell.getBooleanCellValue()).trim();
        return "";
    }

    @PostMapping("/toggle-user/{id}")
    public String toggleUser(@PathVariable Long id, RedirectAttributes ra) {
        User user = userRepository.findById(id).orElse(null);
        if (user != null && isNonAdminRole(user.getRole())) {
            String newStatus = "active".equalsIgnoreCase(user.getStatus()) ? "inactive" : "active";
            user.setStatus(newStatus);
            userRepository.save(user);
            ra.addFlashAttribute("successMessage", user.getUsername() + " is now " + newStatus + ".");
        }
        return "redirect:/hr/employees";
    }

    @PostMapping("/delete-user/{id}")
    @Transactional
    public String deleteUser(@PathVariable Long id, RedirectAttributes ra) {
        User user = userRepository.findById(id).orElse(null);
        if (user != null && isNonAdminRole(user.getRole())) {
            String name = user.getUsername();
            removeUserFromTeams(user);
            notificationService.deleteAllForUser(user.getId());
            passwordResetTokenRepository.deleteByUser(user);
            performanceReviewRepository.deleteByEmployee(user);
            leaveRequestRepository.deleteByEmployee(user);
            attendanceRepository.deleteByUser(user);
            userRepository.delete(user);
            ra.addFlashAttribute("successMessage", "User '" + name + "' deleted.");
        }
        return "redirect:/hr/employees";
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  EDIT EMPLOYEE
    // ═══════════════════════════════════════════════════════════════════════

    private void removeUserFromTeams(User user) {
        List<Team> changedTeams = new ArrayList<>();
        for (Team team : teamRepository.findAll()) {
            boolean changed = false;
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
        User emp = userRepository.findById(id).orElse(null);
        if (emp == null || !isNonAdminRole(emp.getRole())) {
            return "redirect:/hr/employees";
        }
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
                                 HttpServletRequest request,
                                 RedirectAttributes ra) {
        User emp = userRepository.findById(id).orElse(null);
        if (emp == null || !isNonAdminRole(emp.getRole())) {
            ra.addFlashAttribute("errorMessage", "User not found or cannot be edited.");
            return "redirect:/hr/employees";
        }

        // Block promoting to ADMIN / SUPER_ADMIN
        if ("ADMIN".equalsIgnoreCase(role) || "SUPER_ADMIN".equalsIgnoreCase(role)) {
            ra.addFlashAttribute("errorMessage", "You cannot assign that role.");
            return "redirect:/hr/edit-employee/" + id;
        }

        // Check duplicate (excluding self)
        User existing = userRepository.findByUsernameOrEmail(username, email).orElse(null);
        if (existing != null && !existing.getId().equals(emp.getId())) {
            ra.addFlashAttribute("errorMessage", "Username or email already in use.");
            return "redirect:/hr/edit-employee/" + id;
        }

        // Optional password change
        if (password != null && !password.isBlank()) {
            if (!password.equals(confirmPassword)) {
                ra.addFlashAttribute("errorMessage", "Passwords do not match.");
                return "redirect:/hr/edit-employee/" + id;
            }
            emp.setPassword(passwordEncoder.encode(password));
        }

        emp.setUsername(username);
        emp.setEmail(email);
        emp.setRole(role);
        userRepository.save(emp);
        ra.addFlashAttribute("successMessage", "'" + username + "' updated successfully.");
        return "redirect:/hr/employees";
    }

    /**
     * REST: GET /hr/api/employee/{id}
     * Returns employee profile + last 30 days attendance as JSON for the modal.
     */
    @GetMapping("/api/employee/{id}")
    @ResponseBody
    public Map<String, Object> employeeDetail(@PathVariable Long id, HttpServletRequest request) {
        Map<String, Object> resp = new LinkedHashMap<>();
        String tenant = getTenantSegment(request);

        User user = userRepository.findById(id).orElse(null);
        if (user == null || !isNonAdminRole(user.getRole())) {
            resp.put("error", "User not found.");
            return resp;
        }
        // Prevent HR from viewing their own record via the modal
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        if (user.getUsername().equals(currentUsername)) {
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

    @GetMapping("/recruitment")
    public String recruitmentPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        injectStats(request, model);
        return "hr-recruitment";
    }

    @GetMapping("/attendance")
    public String attendancePage(
            HttpServletRequest request,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String status,
            Model model) {
        injectUser(request, model);
        injectStats(request, model);

        User hr = getCurrentHr();
        LocalDate today = LocalDate.now();

        // Date range (default: last 30 days)
        LocalDate filterFrom = (from != null && !from.isBlank()) ? LocalDate.parse(from) : today.minusDays(29);
        LocalDate filterTo   = (to   != null && !to.isBlank())   ? LocalDate.parse(to)   : today;
        if (filterTo.isAfter(today))      filterTo   = today;
        if (filterFrom.isAfter(filterTo)) filterFrom = filterTo;

        // Fetch real records in range
        String tenant = getTenantSegmentFromUser(hr);
        List<Attendance> records = hr != null
                ? attendanceRepository.findByUserAndDateBetweenOrderByDateDesc(hr, filterFrom, filterTo)
                : Collections.emptyList();

        // Holidays in range
        Map<LocalDate, String> holidays = fetchHolidays(tenant, filterFrom, filterTo);

        // Today's record — drives button state
        Optional<Attendance> todayOpt = hr != null
                ? attendanceRepository.findByUserAndDate(hr, today)
                : Optional.empty();

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
        List<Attendance> allRecords = hr != null
                ? attendanceRepository.findByUserOrderByDateDesc(hr)
                : Collections.emptyList();
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

        return "hr-attendance";
    }

    @PostMapping("/attendance/punch-in")
    public String punchIn(HttpServletRequest request, RedirectAttributes ra) {
        User hr = getCurrentHr();
        if (hr == null) return "redirect:/hr/attendance";

        LocalDate today  = LocalDate.now();
        String    tenant = getTenantSegmentFromUser(hr);

        // Block punch-in on holidays
        if (holidayRepository.findByDateAndTenantSegment(today.toString(), tenant).isPresent()) {
            ra.addFlashAttribute("errorMessage", "Today is a holiday. Punch-in is not allowed.");
            return "redirect:/hr/attendance";
        }

        if (attendanceRepository.findByUserAndDate(hr, today).isPresent()) {
            ra.addFlashAttribute("errorMessage", "You have already punched in today.");
            return "redirect:/hr/attendance";
        }

        LocalTime now    = LocalTime.now();
        String   status  = now.isAfter(LocalTime.of(9, 30)) ? "late" : "present";

        Attendance att = new Attendance();
        att.setUser(hr);
        att.setDate(today);
        att.setCheckIn(now);
        att.setStatus(status);
        att.setTenantSegment(tenant);
        attendanceRepository.save(att);

        ra.addFlashAttribute("successMessage",
                "Punched in at " + String.format("%02d:%02d", now.getHour(), now.getMinute()) + ".");
        return "redirect:/hr/attendance";
    }

    @PostMapping("/attendance/punch-out")
    public String punchOut(RedirectAttributes ra) {
        User hr = getCurrentHr();
        if (hr == null) return "redirect:/hr/attendance";

        LocalDate today = LocalDate.now();
        Optional<Attendance> opt = attendanceRepository.findByUserAndDate(hr, today);
        if (opt.isEmpty()) {
            ra.addFlashAttribute("errorMessage", "You haven't punched in today.");
            return "redirect:/hr/attendance";
        }
        Attendance att = opt.get();
        if (att.getCheckOut() != null) {
            ra.addFlashAttribute("errorMessage", "You have already punched out today.");
            return "redirect:/hr/attendance";
        }
        LocalTime now = LocalTime.now();
        att.setCheckOut(now);
        attendanceRepository.save(att);
        ra.addFlashAttribute("successMessage",
                "Punched out at " + String.format("%02d:%02d", now.getHour(), now.getMinute()) + ".");
        return "redirect:/hr/attendance";
    }

    @PostMapping("/attendance/break-start")
    public String breakStart(RedirectAttributes ra) {
        User hr = getCurrentHr();
        if (hr == null) return "redirect:/hr/attendance";

        LocalDate today = LocalDate.now();
        Optional<Attendance> opt = attendanceRepository.findByUserAndDate(hr, today);
        if (opt.isEmpty()) {
            ra.addFlashAttribute("errorMessage", "You haven't punched in today.");
            return "redirect:/hr/attendance";
        }
        Attendance att = opt.get();
        if (att.getCheckOut() != null) {
            ra.addFlashAttribute("errorMessage", "You have already punched out.");
            return "redirect:/hr/attendance";
        }
        LocalTime now = LocalTime.now();
        if (att.getBreakStart() == null) {
            att.setBreakStart(now);
            attendanceRepository.save(att);
            ra.addFlashAttribute("successMessage",
                    "Break 1 started at " + String.format("%02d:%02d", now.getHour(), now.getMinute()) + ".");
            return "redirect:/hr/attendance";
        }
        if (att.getBreakEnd() != null && att.getBreak2Start() == null) {
            att.setBreak2Start(now);
            attendanceRepository.save(att);
            ra.addFlashAttribute("successMessage",
                    "Break 2 started at " + String.format("%02d:%02d", now.getHour(), now.getMinute()) + ".");
            return "redirect:/hr/attendance";
        }
        ra.addFlashAttribute("errorMessage", "No more breaks available today.");
        return "redirect:/hr/attendance";
    }

    @PostMapping("/attendance/break-end")
    public String breakEnd(RedirectAttributes ra) {
        User hr = getCurrentHr();
        if (hr == null) return "redirect:/hr/attendance";

        LocalDate today = LocalDate.now();
        Optional<Attendance> opt = attendanceRepository.findByUserAndDate(hr, today);
        if (opt.isEmpty()) {
            ra.addFlashAttribute("errorMessage", "You haven't punched in today.");
            return "redirect:/hr/attendance";
        }
        Attendance att = opt.get();
        LocalTime now = LocalTime.now();
        if (att.getBreak2Start() != null && att.getBreak2End() == null) {
            att.setBreak2End(now);
            attendanceRepository.save(att);
            ra.addFlashAttribute("successMessage",
                    "Break 2 ended at " + String.format("%02d:%02d", now.getHour(), now.getMinute()) + ".");
            return "redirect:/hr/attendance";
        }
        if (att.getBreakStart() != null && att.getBreakEnd() == null) {
            att.setBreakEnd(now);
            attendanceRepository.save(att);
            ra.addFlashAttribute("successMessage",
                    "Break 1 ended at " + String.format("%02d:%02d", now.getHour(), now.getMinute()) + ".");
            return "redirect:/hr/attendance";
        }
        ra.addFlashAttribute("errorMessage", "No active break to end.");
        return "redirect:/hr/attendance";
    }

    @GetMapping("/leaves")
    public String leavesPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        injectStats(request, model);
        String tenant = getTenantSegment(request);
        model.addAttribute("leaveRequests", tenant.isEmpty() ? Collections.emptyList() : leaveRequestRepository.findByTenantSegmentOrderByCreatedAtDesc(tenant));
        return "hr-leaves";
    }

    @GetMapping("/leaves/view/{id}")
    public ResponseEntity<?> viewLeaveAttachment(@PathVariable Long id, HttpServletRequest request) {
        String tenant = getTenantSegment(request);
        LeaveRequest leave = leaveRequestRepository.findById(id).orElse(null);
        if (leave == null || !tenant.equals(leave.getTenantSegment()) || leave.getAttachmentData() == null || leave.getAttachmentData().length == 0) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + (leave.getAttachmentName() != null ? leave.getAttachmentName() : "leave-attachment") + "\"")
                .header(HttpHeaders.CONTENT_TYPE, leave.getAttachmentContentType() != null ? leave.getAttachmentContentType() : "application/octet-stream")
                .body(leave.getAttachmentData());
    }

    @PostMapping("/leaves/{id}/review")
    public String reviewLeave(@PathVariable Long id,
                              @RequestParam String action,
                              @RequestParam(required = false) String rejectionMessage,
                              HttpServletRequest request,
                              RedirectAttributes ra) {
        String tenant = getTenantSegment(request);
        User hr = getCurrentHr();
        LeaveRequest leave = leaveRequestRepository.findById(id).orElse(null);
        if (leave == null || !tenant.equals(leave.getTenantSegment())) {
            ra.addFlashAttribute("errorMessage", "Leave request not found.");
            return "redirect:/hr/leaves";
        }

        if ("approve".equalsIgnoreCase(action)) {
            leave.setStatus("Approved");
            ra.addFlashAttribute("successMessage", "Leave request approved.");
        } else if ("reject".equalsIgnoreCase(action)) {
            leave.setStatus("Rejected");
            leave.setRejectionMessage(rejectionMessage != null ? rejectionMessage.trim() : null);
            ra.addFlashAttribute("successMessage", "Leave request rejected.");
        } else {
            ra.addFlashAttribute("errorMessage", "Invalid leave action.");
            return "redirect:/hr/leaves";
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
        return "redirect:/hr/leaves";
    }

    @GetMapping("/calendar")
    public String calendarPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        model.addAttribute("pageTitle",   "HR — Calendar");
        model.addAttribute("pageHeading", "Holiday Calendar");
        model.addAttribute("activePage",  "calendar");
        return "hr-calendar";
    }

    @GetMapping("/reports")
    public String reportsPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        injectStats(request, model);
        String tenant = getTenantSegment(request);

        User hr = getCurrentHr();
        java.util.List<com.crm.demo.model.Report> allReports;
        if (hr != null) {
            allReports = reportRepository.findByRecipientId(String.valueOf(hr.getId()), tenant);
        } else {
            allReports = java.util.Collections.emptyList();
        }

        model.addAttribute("allReports",  allReports);
        model.addAttribute("reportCount", allReports.size());
        return "hr-reports";
    }

    @GetMapping("/reports/view/{attachmentId}")
    public org.springframework.http.ResponseEntity<?> viewReportAttachment(
            @PathVariable Long attachmentId) {
        com.crm.demo.model.ReportAttachment att = reportAttachmentRepository.findById(attachmentId).orElse(null);
        if (att == null) return org.springframework.http.ResponseEntity.notFound().build();
        String ct = att.getContentType() != null ? att.getContentType() : "application/octet-stream";
        return org.springframework.http.ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + att.getOriginalFilename() + "\"")
                .header(HttpHeaders.CONTENT_TYPE, ct)
                .body(att.getFileData());
    }

    @GetMapping("/reports/download/{attachmentId}")
    public org.springframework.http.ResponseEntity<?> downloadReportAttachment(
            @PathVariable Long attachmentId) {
        com.crm.demo.model.ReportAttachment att = reportAttachmentRepository.findById(attachmentId).orElse(null);
        if (att == null) return org.springframework.http.ResponseEntity.notFound().build();
        String ct = att.getContentType() != null ? att.getContentType() : "application/octet-stream";
        return org.springframework.http.ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + att.getOriginalFilename() + "\"")
                .header(HttpHeaders.CONTENT_TYPE, ct)
                .body(att.getFileData());
    }

    @GetMapping("/settings")
    public String settingsPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        injectStats(request, model);
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User hr = userRepository.findByUsername(currentUsername);
        model.addAttribute("hrEmail", hr != null ? hr.getEmail() : "");
        return "hr-settings";
    }

    @PostMapping("/settings/profile")
    public String updateProfile(@RequestParam(required = false) String username,
                                @RequestParam(required = false) String email,
                                @RequestParam(required = false) String password,
                                @RequestParam(required = false) String confirmPassword,
                                HttpServletResponse response,
                                RedirectAttributes ra) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User hr = userRepository.findByUsername(currentUsername);
        if (hr == null) return "redirect:/hr/settings";

        profileUpdateService.updateProfile(hr, username, email, password, confirmPassword, ra, response);
        return "redirect:/hr/settings";
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  TEAM MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════

    /** GET /hr/teams — list all teams for this tenant. */
    @GetMapping("/teams")
    public String teamsPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        String tenant = getTenantSegment(request);

        List<Team> teams = teamRepository.findByTenantSegmentOrderByNameAsc(tenant);

        // Managers and employees available in this tenant
        List<User> tenantUsers = tenant.isEmpty()
                ? userRepository.findAll()
                : userRepository.findByTenantSegment(tenant);

        List<User> managers  = tenantUsers.stream()
                .filter(u -> "MANAGER".equalsIgnoreCase(u.getRole()) && u.isActive()).toList();
        List<User> employees = tenantUsers.stream()
                .filter(u -> "EMPLOYEE".equalsIgnoreCase(u.getRole()) && u.isActive()).toList();

        model.addAttribute("teams",     teams);
        model.addAttribute("managers",  managers);
        model.addAttribute("employees", employees);
        model.addAttribute("teamCount", teams.size());
        model.addAttribute("activePage", "teams");
        return "hr-teams";
    }

    /** POST /hr/teams — create a new team. */
    @PostMapping("/teams")
    public String createTeam(@RequestParam String name,
                             @RequestParam(required = false) Long managerId,
                             HttpServletRequest request,
                             RedirectAttributes ra) {
        String tenant = getTenantSegment(request);

        if (name == null || name.isBlank()) {
            ra.addFlashAttribute("errorMessage", "Team name is required.");
            return "redirect:/hr/teams";
        }
        if (teamRepository.existsByNameAndTenantSegment(name.trim(), tenant)) {
            ra.addFlashAttribute("errorMessage", "A team named '" + name.trim() + "' already exists.");
            return "redirect:/hr/teams";
        }

        Team team = new Team();
        team.setName(name.trim());
        team.setTenantSegment(tenant);

        if (managerId != null) {
            userRepository.findById(managerId).ifPresent(team::setManager);
        }

        teamRepository.save(team);
        ra.addFlashAttribute("successMessage", "Team '" + name.trim() + "' created.");
        return "redirect:/hr/teams";
    }

    /** POST /hr/teams/{id}/assign-manager — assign or change the manager. */
    @PostMapping("/teams/{id}/assign-manager")
    public String assignManager(@PathVariable Long id,
                                @RequestParam Long managerId,
                                HttpServletRequest request,
                                RedirectAttributes ra) {
        String tenant = getTenantSegment(request);
        Team team = teamRepository.findById(id).orElse(null);
        if (team == null || !tenant.equals(team.getTenantSegment())) {
            ra.addFlashAttribute("errorMessage", "Team not found.");
            return "redirect:/hr/teams";
        }
        userRepository.findById(managerId).ifPresent(manager -> {
            team.setManager(manager);
            notificationService.notifyManagerAssigned(manager, team.getName());
        });
        teamRepository.save(team);
        ra.addFlashAttribute("successMessage", "Manager assigned to team '" + team.getName() + "'.");
        return "redirect:/hr/teams";
    }

    /** POST /hr/teams/{id}/add-member — add one or more employees to the team. */
    @PostMapping("/teams/{id}/add-member")
    public String addMember(@PathVariable Long id,
                            @RequestParam(value = "employeeIds", required = false) List<Long> employeeIds,
                            HttpServletRequest request,
                            RedirectAttributes ra) {
        String tenant = getTenantSegment(request);
        Team team = teamRepository.findById(id).orElse(null);
        if (team == null || !tenant.equals(team.getTenantSegment())) {
            ra.addFlashAttribute("errorMessage", "Team not found.");
            return "redirect:/hr/teams";
        }
        if (employeeIds == null || employeeIds.isEmpty()) {
            ra.addFlashAttribute("errorMessage", "Please select at least one employee.");
            return "redirect:/hr/teams";
        }

        int added = 0;
        List<String> skipped = new ArrayList<>();

        for (Long empId : employeeIds) {
            User emp = userRepository.findById(empId).orElse(null);
            if (emp == null || !"EMPLOYEE".equalsIgnoreCase(emp.getRole())) {
                skipped.add("ID " + empId + " not found");
                continue;
            }
            if (team.getMembers().contains(emp)) {
                skipped.add(emp.getUsername() + " already in team");
                continue;
            }
            team.getMembers().add(emp);
            notificationService.notifyTeamAdded(emp, team.getName());
            added++;
        }

        if (added > 0) {
            teamRepository.save(team);
            String msg = added + " member(s) added to team '" + team.getName() + "'.";
            if (!skipped.isEmpty()) msg += " Skipped: " + String.join(", ", skipped) + ".";
            ra.addFlashAttribute("successMessage", msg);
        } else {
            ra.addFlashAttribute("errorMessage",
                    "No members added. " + (skipped.isEmpty() ? "" : "Skipped: " + String.join(", ", skipped)));
        }
        return "redirect:/hr/teams";
    }

    /** POST /hr/teams/{id}/remove-member — remove an employee from the team. */
    @PostMapping("/teams/{id}/remove-member")
    public String removeMember(@PathVariable Long id,
                               @RequestParam Long employeeId,
                               HttpServletRequest request,
                               RedirectAttributes ra) {
        String tenant = getTenantSegment(request);
        Team team = teamRepository.findById(id).orElse(null);
        if (team == null || !tenant.equals(team.getTenantSegment())) {
            ra.addFlashAttribute("errorMessage", "Team not found.");
            return "redirect:/hr/teams";
        }
        team.getMembers().removeIf(m -> m.getId().equals(employeeId));
        teamRepository.save(team);
        ra.addFlashAttribute("successMessage", "Member removed from team '" + team.getName() + "'.");
        return "redirect:/hr/teams";
    }

    /** POST /hr/teams/{id}/delete — delete a team. */
    @PostMapping("/teams/{id}/delete")
    public String deleteTeam(@PathVariable Long id,
                             HttpServletRequest request,
                             RedirectAttributes ra) {
        String tenant = getTenantSegment(request);
        Team team = teamRepository.findById(id).orElse(null);
        if (team == null || !tenant.equals(team.getTenantSegment())) {
            ra.addFlashAttribute("errorMessage", "Team not found.");
            return "redirect:/hr/teams";
        }
        String name = team.getName();
        teamRepository.delete(team);
        ra.addFlashAttribute("successMessage", "Team '" + name + "' deleted.");
        return "redirect:/hr/teams";
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  MEETINGS
    // ═══════════════════════════════════════════════════════════════════════

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

    /** GET /hr/meetings — side-by-side schedule form + meetings list */
    @GetMapping("/meetings")
    public String meetingsPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        String tenant   = getTenantSegment(request);
        String username = (String) request.getAttribute("loggedInUser");

        model.addAttribute("upcomingMeetings", getUpcomingMeetings(tenant, username != null ? username : ""));
        model.addAttribute("pastMeetings", getPastMeetings(tenant, username != null ? username : ""));

        // All non-admin users in this tenant as potential participants
        List<User> tenantUsers = tenant.isEmpty()
                ? userRepository.findAll()
                : userRepository.findByTenantSegment(tenant);
        model.addAttribute("tenantUsers", tenantUsers.stream()
                .filter(u -> !"ADMIN".equalsIgnoreCase(u.getRole())
                          && !"SUPER_ADMIN".equalsIgnoreCase(u.getRole()))
                .toList());

        if (!model.containsAttribute("meetingForm")) {
            model.addAttribute("meetingForm", new Meeting());
        }
        return "hr-meetings";
    }

    /** POST /hr/meetings — create a new meeting */
    @PostMapping("/meetings")
    public String scheduleMeeting(@Valid @ModelAttribute("meetingForm") Meeting meetingForm,
                                  BindingResult result,
                                  HttpServletRequest request,
                                  Model model,
                                  RedirectAttributes ra) {
        String tenant   = getTenantSegment(request);
        String username = (String) request.getAttribute("loggedInUser");

        if (result.hasErrors()) {
            injectUser(request, model);
            model.addAttribute("upcomingMeetings", getUpcomingMeetings(tenant, username != null ? username : ""));
            model.addAttribute("pastMeetings", getPastMeetings(tenant, username != null ? username : ""));
            List<User> tenantUsers = tenant.isEmpty()
                    ? userRepository.findAll()
                    : userRepository.findByTenantSegment(tenant);
            model.addAttribute("tenantUsers", tenantUsers.stream()
                    .filter(u -> !"ADMIN".equalsIgnoreCase(u.getRole())
                              && !"SUPER_ADMIN".equalsIgnoreCase(u.getRole()))
                    .toList());
            model.addAttribute("errorMessage", "Please fix the errors below.");
            return "hr-meetings";
        }

        meetingForm.setTenantSegment(tenant);
        meetingForm.setScheduledBy(username != null ? username : "");
        meetingRepository.save(meetingForm);
        notificationService.notifyMeetingParticipants(meetingForm);
        ra.addFlashAttribute("successMessage", "Meeting scheduled successfully.");
        return "redirect:/hr/meetings";
    }

    /** GET /hr/meetings/edit/{id} — load meeting into form */
    @GetMapping("/meetings/edit/{id}")
    public String editMeetingPage(@PathVariable Long id,
                                  HttpServletRequest request,
                                  Model model,
                                  RedirectAttributes ra) {
        injectUser(request, model);
        String tenant   = getTenantSegment(request);
        String username = (String) request.getAttribute("loggedInUser");

        Meeting meeting = meetingRepository.findById(id).orElse(null);
        if (meeting == null || !tenant.equals(meeting.getTenantSegment())) {
            ra.addFlashAttribute("errorMessage", "Meeting not found.");
            return "redirect:/hr/meetings";
        }

        model.addAttribute("meetingForm", meeting);
        model.addAttribute("upcomingMeetings", getUpcomingMeetings(tenant, username != null ? username : ""));
        model.addAttribute("pastMeetings", getPastMeetings(tenant, username != null ? username : ""));
        List<User> tenantUsers = tenant.isEmpty()
                ? userRepository.findAll()
                : userRepository.findByTenantSegment(tenant);
        model.addAttribute("tenantUsers", tenantUsers.stream()
                .filter(u -> !"ADMIN".equalsIgnoreCase(u.getRole())
                          && !"SUPER_ADMIN".equalsIgnoreCase(u.getRole()))
                .toList());
        return "hr-meetings";
    }

    /** POST /hr/meetings/edit/{id} — update existing meeting */
    @PostMapping("/meetings/edit/{id}")
    public String updateMeeting(@PathVariable Long id,
                                @Valid @ModelAttribute("meetingForm") Meeting meetingForm,
                                BindingResult result,
                                HttpServletRequest request,
                                Model model,
                                RedirectAttributes ra) {
        String tenant   = getTenantSegment(request);
        String username = (String) request.getAttribute("loggedInUser");

        if (result.hasErrors()) {
            injectUser(request, model);
            model.addAttribute("upcomingMeetings", getUpcomingMeetings(tenant, username != null ? username : ""));
            model.addAttribute("pastMeetings", getPastMeetings(tenant, username != null ? username : ""));
            List<User> tenantUsers = tenant.isEmpty()
                    ? userRepository.findAll()
                    : userRepository.findByTenantSegment(tenant);
            model.addAttribute("tenantUsers", tenantUsers.stream()
                    .filter(u -> !"ADMIN".equalsIgnoreCase(u.getRole())
                              && !"SUPER_ADMIN".equalsIgnoreCase(u.getRole()))
                    .toList());
            model.addAttribute("errorMessage", "Please fix the errors below.");
            return "hr-meetings";
        }

        Meeting existing = meetingRepository.findById(id).orElse(null);
        if (existing == null || !tenant.equals(existing.getTenantSegment())) {
            ra.addFlashAttribute("errorMessage", "Meeting not found.");
            return "redirect:/hr/meetings";
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
        return "redirect:/hr/meetings";
    }

    /** POST /hr/meetings/delete/{id} — delete a meeting */
    @PostMapping("/meetings/delete/{id}")
    public String deleteMeeting(@PathVariable Long id,
                                HttpServletRequest request,
                                RedirectAttributes ra) {
        String tenant = getTenantSegment(request);
        Meeting meeting = meetingRepository.findById(id).orElse(null);
        if (meeting == null || !tenant.equals(meeting.getTenantSegment())) {
            ra.addFlashAttribute("errorMessage", "Meeting not found.");
        } else {
            meetingRepository.delete(meeting);
            ra.addFlashAttribute("successMessage", "Meeting deleted successfully.");
        }
        return "redirect:/hr/meetings";
    }

    @GetMapping("/performance")
    public String performancePage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        injectStats(request, model);
        return "hr-performance";
    }
}
