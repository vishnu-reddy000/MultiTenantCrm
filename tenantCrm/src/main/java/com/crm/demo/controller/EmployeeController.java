package com.crm.demo.controller;

import com.crm.demo.model.Attendance;
import com.crm.demo.model.AttendanceDay;
import com.crm.demo.model.Team;
import com.crm.demo.model.User;
import com.crm.demo.repository.AttendanceRepository;
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
@RequestMapping("/employee")
public class EmployeeController {

    @Autowired private UserRepository        userRepository;
    @Autowired private AttendanceRepository  attendanceRepository;
    @Autowired private TeamRepository        teamRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;

    // ── helpers ───────────────────────────────────────────────────────────

    private void injectUser(Model model) {
        User emp = getCurrentEmployee();
        model.addAttribute("employeeName", emp != null ? emp.getUsername() : "Employee");
        model.addAttribute("employeeRole", "Employee");
    }

    private User getCurrentEmployee() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username);
    }

    private String getTenantSegment(User user) {
        if (user == null || user.getEmail() == null) return "";
        try {
            String email = user.getEmail();
            String local = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
            int dot = local.lastIndexOf('.');
            return dot >= 0 ? local.substring(dot + 1) : local;
        } catch (Exception e) { return ""; }
    }

    /**
     * Build a merged day list for the given date range.
     * Real records are used where they exist; missing weekdays become "absent";
     * Saturday/Sunday become "weekend". Result is sorted newest-first.
     */
    private List<AttendanceDay> buildDayList(List<Attendance> records, LocalDate from, LocalDate to) {
        Map<LocalDate, Attendance> byDate = new LinkedHashMap<>();
        for (Attendance a : records) byDate.put(a.getDate(), a);

        List<AttendanceDay> days = new ArrayList<>();
        LocalDate today  = LocalDate.now();
        LocalDate cursor = to;
        while (!cursor.isBefore(from)) {
            DayOfWeek dow = cursor.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                days.add(new AttendanceDay(cursor, "weekend"));
            } else if (byDate.containsKey(cursor)) {
                days.add(new AttendanceDay(byDate.get(cursor)));
            } else if (!cursor.isAfter(today)) {
                days.add(new AttendanceDay(cursor, "absent"));
            }
            cursor = cursor.minusDays(1);
        }
        return days;
    }

    private void injectStats(Model model) {
        model.addAttribute("activeProjectsCount", 0);
        model.addAttribute("completedTasks",       0);
        model.addAttribute("attendanceRate",      "0%");
        model.addAttribute("pendingLeaves",        0);
        model.addAttribute("attendanceMonth",     "May 2026");
        model.addAttribute("presentDays",          0);
        model.addAttribute("absentDays",           0);
        model.addAttribute("leaveDays",            0);
        model.addAttribute("attendancePercent",    0);
        model.addAttribute("lastCheckin",         "—");
        model.addAttribute("myProjects",           Collections.emptyList());
        model.addAttribute("pendingTasks",         Collections.emptyList());
        model.addAttribute("leaveRequests",        Collections.emptyList());
    }

    // ── pages ─────────────────────────────────────────────────────────────

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        injectUser(model);
        injectStats(model);

        // ── Team info ─────────────────────────────────────────────────────
        User emp = getCurrentEmployee();
        if (emp != null) {
            String tenant = getTenantSegment(emp);
            List<Team> myTeams = teamRepository.findByMemberAndTenant(emp, tenant);
            if (!myTeams.isEmpty()) {
                Team team = myTeams.get(0);
                model.addAttribute("myTeamName",    team.getName());
                model.addAttribute("myTeamManager", team.getManager() != null ? team.getManager().getUsername() : "—");
                model.addAttribute("myTeamMembers", team.getMembers());
                model.addAttribute("myTeamSize",    team.getMembers().size());
            } else {
                model.addAttribute("myTeamName",    null);
                model.addAttribute("myTeamManager", "—");
                model.addAttribute("myTeamMembers", Collections.emptyList());
                model.addAttribute("myTeamSize",    0);
            }
        } else {
            model.addAttribute("myTeamName",    null);
            model.addAttribute("myTeamManager", "—");
            model.addAttribute("myTeamMembers", Collections.emptyList());
            model.addAttribute("myTeamSize",    0);
        }

        return "employee-dashboard";
    }

    @GetMapping("/projects")
    public String projectsPage(Model model) {
        injectUser(model);
        injectStats(model);
        return "employee-projects";
    }

    @GetMapping("/tasks")
    public String tasksPage(Model model) {
        injectUser(model);
        injectStats(model);
        return "employee-tasks";
    }

    // ── ATTENDANCE ────────────────────────────────────────────────────────

    @GetMapping("/attendance")
    public String attendancePage(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String status,
            Model model) {

        injectUser(model);
        injectStats(model);

        User emp = getCurrentEmployee();
        LocalDate today = LocalDate.now();

        // Date range (default: last 30 days)
        LocalDate filterFrom = (from != null && !from.isBlank()) ? LocalDate.parse(from) : today.minusDays(29);
        LocalDate filterTo   = (to   != null && !to.isBlank())   ? LocalDate.parse(to)   : today;
        if (filterTo.isAfter(today))      filterTo   = today;
        if (filterFrom.isAfter(filterTo)) filterFrom = filterTo;

        // Fetch real records in range
        List<Attendance> records = emp != null
                ? attendanceRepository.findByUserAndDateBetweenOrderByDateDesc(emp, filterFrom, filterTo)
                : Collections.emptyList();

        // Today's record — drives button state
        Optional<Attendance> todayOpt = emp != null
                ? attendanceRepository.findByUserAndDate(emp, today)
                : Optional.empty();

        boolean punchedIn  = todayOpt.isPresent();
        boolean punchedOut = todayOpt.map(a -> a.getCheckOut() != null).orElse(false);
        boolean onBreak    = todayOpt.map(a ->
                (a.getBreakStart() != null && a.getBreakEnd() == null) ||
                (a.getBreak2Start() != null && a.getBreak2End() == null)).orElse(false);
        boolean breakDone  = todayOpt.map(a -> a.getBreak2End() != null).orElse(false);
        boolean canStartBreak = punchedIn && !punchedOut && !onBreak && !breakDone &&
                todayOpt.map(a -> a.getBreakStart() == null ||
                        (a.getBreakEnd() != null && a.getBreak2Start() == null)).orElse(false);

        // Build merged day list (fills absent/weekend gaps)
        List<AttendanceDay> allDays = buildDayList(records, filterFrom, filterTo);

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
        List<Attendance> allRecords = emp != null
                ? attendanceRepository.findByUserOrderByDateDesc(emp)
                : Collections.emptyList();
        long presentCount = allRecords.stream()
                .filter(a -> "present".equalsIgnoreCase(a.getStatus()) || "late".equalsIgnoreCase(a.getStatus()))
                .count();
        long lateCount = allRecords.stream()
                .filter(a -> "late".equalsIgnoreCase(a.getStatus()))
                .count();

        model.addAttribute("attendanceDays",  filteredDays);
        model.addAttribute("totalRecords",    filteredDays.size());
        model.addAttribute("todayRecord",     todayOpt.orElse(null));
        model.addAttribute("punchedIn",       punchedIn);
        model.addAttribute("punchedOut",      punchedOut);
        model.addAttribute("onBreak",         onBreak);
        model.addAttribute("breakDone",       breakDone);
        model.addAttribute("canStartBreak",   canStartBreak);
        model.addAttribute("presentCount",    presentCount);
        model.addAttribute("lateCount",       lateCount);
        model.addAttribute("filterFrom",      filterFrom.toString());
        model.addAttribute("filterTo",        filterTo.toString());
        model.addAttribute("filterStatus",    status != null ? status : "all");

        return "employee-attendance";
    }

    @PostMapping("/attendance/punch-in")
    public String punchIn(RedirectAttributes ra) {
        User emp = getCurrentEmployee();
        if (emp == null) return "redirect:/employee/attendance";

        LocalDate today = LocalDate.now();
        if (attendanceRepository.findByUserAndDate(emp, today).isPresent()) {
            ra.addFlashAttribute("errorMessage", "You have already punched in today.");
            return "redirect:/employee/attendance";
        }

        LocalTime now    = LocalTime.now();
        String   status  = now.isAfter(LocalTime.of(9, 30)) ? "late" : "present";
        String   tenant  = getTenantSegment(emp);

        Attendance att = new Attendance();
        att.setUser(emp);
        att.setDate(today);
        att.setCheckIn(now);
        att.setStatus(status);
        att.setTenantSegment(tenant);
        attendanceRepository.save(att);

        ra.addFlashAttribute("successMessage",
                "Punched in at " + String.format("%02d:%02d", now.getHour(), now.getMinute()) + ".");
        return "redirect:/employee/attendance";
    }

    @PostMapping("/attendance/punch-out")
    public String punchOut(RedirectAttributes ra) {
        User emp = getCurrentEmployee();
        if (emp == null) return "redirect:/employee/attendance";

        LocalDate today = LocalDate.now();
        Optional<Attendance> opt = attendanceRepository.findByUserAndDate(emp, today);

        if (opt.isEmpty()) {
            ra.addFlashAttribute("errorMessage", "You haven't punched in today.");
            return "redirect:/employee/attendance";
        }
        Attendance att = opt.get();
        if (att.getCheckOut() != null) {
            ra.addFlashAttribute("errorMessage", "You have already punched out today.");
            return "redirect:/employee/attendance";
        }

        LocalTime now = LocalTime.now();
        att.setCheckOut(now);
        attendanceRepository.save(att);

        ra.addFlashAttribute("successMessage",
                "Punched out at " + String.format("%02d:%02d", now.getHour(), now.getMinute()) + ".");
        return "redirect:/employee/attendance";
    }

    @PostMapping("/attendance/break-start")
    public String breakStart(RedirectAttributes ra) {
        User emp = getCurrentEmployee();
        if (emp == null) return "redirect:/employee/attendance";

        LocalDate today = LocalDate.now();
        Optional<Attendance> opt = attendanceRepository.findByUserAndDate(emp, today);

        if (opt.isEmpty()) {
            ra.addFlashAttribute("errorMessage", "You haven't punched in today.");
            return "redirect:/employee/attendance";
        }
        Attendance att = opt.get();
        if (att.getCheckOut() != null) {
            ra.addFlashAttribute("errorMessage", "You have already punched out.");
            return "redirect:/employee/attendance";
        }

        LocalTime now = LocalTime.now();

        // Use break 1 slot if not yet started
        if (att.getBreakStart() == null) {
            att.setBreakStart(now);
            attendanceRepository.save(att);
            ra.addFlashAttribute("successMessage",
                    "Break 1 started at " + String.format("%02d:%02d", now.getHour(), now.getMinute()) + ".");
            return "redirect:/employee/attendance";
        }

        // Use break 2 slot if break 1 is done and break 2 not yet started
        if (att.getBreakEnd() != null && att.getBreak2Start() == null) {
            att.setBreak2Start(now);
            attendanceRepository.save(att);
            ra.addFlashAttribute("successMessage",
                    "Break 2 started at " + String.format("%02d:%02d", now.getHour(), now.getMinute()) + ".");
            return "redirect:/employee/attendance";
        }

        ra.addFlashAttribute("errorMessage", "No more breaks available today.");
        return "redirect:/employee/attendance";
    }

    @PostMapping("/attendance/break-end")
    public String breakEnd(RedirectAttributes ra) {
        User emp = getCurrentEmployee();
        if (emp == null) return "redirect:/employee/attendance";

        LocalDate today = LocalDate.now();
        Optional<Attendance> opt = attendanceRepository.findByUserAndDate(emp, today);

        if (opt.isEmpty()) {
            ra.addFlashAttribute("errorMessage", "You haven't punched in today.");
            return "redirect:/employee/attendance";
        }
        Attendance att = opt.get();

        LocalTime now = LocalTime.now();

        // End break 2 if it's active
        if (att.getBreak2Start() != null && att.getBreak2End() == null) {
            att.setBreak2End(now);
            attendanceRepository.save(att);
            ra.addFlashAttribute("successMessage",
                    "Break 2 ended at " + String.format("%02d:%02d", now.getHour(), now.getMinute()) + ".");
            return "redirect:/employee/attendance";
        }

        // End break 1 if it's active
        if (att.getBreakStart() != null && att.getBreakEnd() == null) {
            att.setBreakEnd(now);
            attendanceRepository.save(att);
            ra.addFlashAttribute("successMessage",
                    "Break 1 ended at " + String.format("%02d:%02d", now.getHour(), now.getMinute()) + ".");
            return "redirect:/employee/attendance";
        }

        ra.addFlashAttribute("errorMessage", "No active break to end.");
        return "redirect:/employee/attendance";
    }

    // ── other pages ───────────────────────────────────────────────────────

    @GetMapping("/calendar")
    public String calendarPage(Model model) {
        injectUser(model);
        injectStats(model);
        return "employee-calendar";
    }

    @GetMapping("/leaves")
    public String leavesPage(Model model) {
        injectUser(model);
        injectStats(model);
        return "employee-leaves";
    }

    @GetMapping("/settings")
    public String settingsPage(Model model) {
        injectUser(model);
        injectStats(model);
        User emp = getCurrentEmployee();
        model.addAttribute("employeeEmail", emp != null ? emp.getEmail() : "");
        return "employee-settings";
    }

    @PostMapping("/settings/profile")
    public String updateProfile(@RequestParam String username,
                                @RequestParam String email,
                                @RequestParam(required = false) String password,
                                @RequestParam(required = false) String confirmPassword,
                                RedirectAttributes ra) {
        User emp = getCurrentEmployee();
        if (emp == null) return "redirect:/employee/settings";

        User existing = userRepository.findByUsernameOrEmail(username, email);
        if (existing != null && !existing.getId().equals(emp.getId())) {
            ra.addFlashAttribute("errorMessage", "Username or email already in use.");
            return "redirect:/employee/settings";
        }
        emp.setUsername(username);
        emp.setEmail(email);
        if (password != null && !password.isBlank()) {
            if (!password.equals(confirmPassword)) {
                ra.addFlashAttribute("errorMessage", "Passwords do not match.");
                return "redirect:/employee/settings";
            }
            emp.setPassword(passwordEncoder.encode(password));
        }
        userRepository.save(emp);
        ra.addFlashAttribute("successMessage", "Profile updated successfully.");
        return "redirect:/employee/settings";
    }
}
