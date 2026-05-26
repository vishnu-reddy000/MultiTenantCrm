package com.crm.demo.controller;

import com.crm.demo.model.Attendance;
import com.crm.demo.model.AttendanceDay;
import com.crm.demo.model.Holiday;
import com.crm.demo.model.Team;
import com.crm.demo.model.User;
import com.crm.demo.repository.AttendanceRepository;
import com.crm.demo.repository.HolidayRepository;
import com.crm.demo.repository.TeamRepository;
import com.crm.demo.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@Controller
@RequestMapping("/hr")
public class HrController {

    @Autowired private UserRepository        userRepository;
    @Autowired private TeamRepository        teamRepository;
    @Autowired private AttendanceRepository  attendanceRepository;
    @Autowired private HolidayRepository     holidayRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;

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
        model.addAttribute("onLeaveToday",      0);
        model.addAttribute("openPositions",     0);
        model.addAttribute("leaveRequests",     Collections.emptyList());
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
        return "hr-dashboard";
    }

    @GetMapping("/employees")
    public String employeesPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        injectStats(request, model);
        return "hr-employees";
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

        if (userRepository.findByUsernameOrEmail(username, email) != null) {
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
    public String deleteUser(@PathVariable Long id, RedirectAttributes ra) {
        User user = userRepository.findById(id).orElse(null);
        if (user != null && isNonAdminRole(user.getRole())) {
            String name = user.getUsername();
            userRepository.delete(user);
            ra.addFlashAttribute("successMessage", "User '" + name + "' deleted.");
        }
        return "redirect:/hr/employees";
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  EDIT EMPLOYEE
    // ═══════════════════════════════════════════════════════════════════════

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
        User existing = userRepository.findByUsernameOrEmail(username, email);
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
        return "hr-leaves";
    }

    @GetMapping("/calendar")
    public String calendarPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        model.addAttribute("pageTitle",   "HR — Calendar");
        model.addAttribute("pageHeading", "Holiday Calendar");
        model.addAttribute("activePage",  "calendar");
        return "hr-calendar";
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
    public String updateProfile(@RequestParam String username,
                                @RequestParam String email,
                                @RequestParam(required = false) String password,
                                @RequestParam(required = false) String confirmPassword,
                                RedirectAttributes ra) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User hr = userRepository.findByUsername(currentUsername);
        if (hr == null) return "redirect:/hr/settings";

        User existing = userRepository.findByUsernameOrEmail(username, email);
        if (existing != null && !existing.getId().equals(hr.getId())) {
            ra.addFlashAttribute("errorMessage", "Username or email already in use.");
            return "redirect:/hr/settings";
        }
        hr.setUsername(username);
        hr.setEmail(email);
        if (password != null && !password.isBlank()) {
            if (!password.equals(confirmPassword)) {
                ra.addFlashAttribute("errorMessage", "Passwords do not match.");
                return "redirect:/hr/settings";
            }
            hr.setPassword(passwordEncoder.encode(password));
        }
        userRepository.save(hr);
        ra.addFlashAttribute("successMessage", "Profile updated successfully.");
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
        userRepository.findById(managerId).ifPresent(team::setManager);
        teamRepository.save(team);
        ra.addFlashAttribute("successMessage", "Manager assigned to team '" + team.getName() + "'.");
        return "redirect:/hr/teams";
    }

    /** POST /hr/teams/{id}/add-member — add an employee to the team. */
    @PostMapping("/teams/{id}/add-member")
    public String addMember(@PathVariable Long id,
                            @RequestParam Long employeeId,
                            HttpServletRequest request,
                            RedirectAttributes ra) {
        String tenant = getTenantSegment(request);
        Team team = teamRepository.findById(id).orElse(null);
        if (team == null || !tenant.equals(team.getTenantSegment())) {
            ra.addFlashAttribute("errorMessage", "Team not found.");
            return "redirect:/hr/teams";
        }
        User emp = userRepository.findById(employeeId).orElse(null);
        if (emp == null || !"EMPLOYEE".equalsIgnoreCase(emp.getRole())) {
            ra.addFlashAttribute("errorMessage", "Employee not found.");
            return "redirect:/hr/teams";
        }
        if (!team.getMembers().contains(emp)) {
            team.getMembers().add(emp);
            teamRepository.save(team);
            ra.addFlashAttribute("successMessage", emp.getUsername() + " added to team '" + team.getName() + "'.");
        } else {
            ra.addFlashAttribute("errorMessage", emp.getUsername() + " is already in this team.");
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
}
