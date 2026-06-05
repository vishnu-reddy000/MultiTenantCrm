                package com.crm.demo.controller;

import java.io.IOException;
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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.crm.demo.model.Attendance;
import com.crm.demo.model.AttendanceDay;
import com.crm.demo.model.Holiday;
import com.crm.demo.model.LeaveRequest;
import com.crm.demo.model.Meeting;
import com.crm.demo.model.Task;
import com.crm.demo.model.TaskAttachment;
import com.crm.demo.model.Team;
import com.crm.demo.model.User;
import com.crm.demo.repository.AttendanceRepository;
import com.crm.demo.repository.HolidayRepository;
import com.crm.demo.repository.LeaveRequestRepository;
import com.crm.demo.repository.MeetingRepository;
import com.crm.demo.repository.TaskRepository;
import com.crm.demo.repository.TaskAttachmentRepository;
import com.crm.demo.repository.TeamRepository;
import com.crm.demo.repository.UserRepository;
import com.crm.demo.service.ProfileUpdateService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

import jakarta.servlet.http.HttpServletResponse;

@Controller
@RequestMapping("/employee")
public class EmployeeController {

    @Value("${app.upload.dir:uploads/tasks}")
    private String uploadDir;

    @Autowired private UserRepository        userRepository;
    @Autowired private AttendanceRepository  attendanceRepository;
    @Autowired private HolidayRepository     holidayRepository;
    @Autowired private TeamRepository        teamRepository;
    @Autowired private MeetingRepository     meetingRepository;
    @Autowired private TaskRepository        taskRepository;
    @Autowired private TaskAttachmentRepository taskAttachmentRepository;
    @Autowired private LeaveRequestRepository leaveRequestRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    @Autowired private ProfileUpdateService  profileUpdateService;

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

    private boolean hasApprovedLeave(User user, LocalDate date) {
        if (user == null || date == null) return false;
        return leaveRequestRepository.findByEmployeeOrderByCreatedAtDesc(user).stream()
                .anyMatch(leave -> "Approved".equalsIgnoreCase(leave.getStatus())
                        && !date.isBefore(leave.getFromDate())
                        && !date.isAfter(leave.getToDate()));
    }

    /**
     * Build a merged day list for the given date range.
     * Priority: holiday > weekend > real record > absent (past weekday with no record).
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

    private void injectStats(Model model) {
        model.addAttribute("activeProjectsCount", 0);
        model.addAttribute("completedTasks",       0);
        model.addAttribute("attendanceRate",      "0%");
        User emp = getCurrentEmployee();
        long pendingLeaves = emp != null ? leaveRequestRepository.countByEmployeeAndStatus(emp, "Pending") : 0;
        long approvedLeaves = emp != null ? leaveRequestRepository.countByEmployeeAndStatus(emp, "Approved") : 0;
        long rejectedLeaves = emp != null ? leaveRequestRepository.countByEmployeeAndStatus(emp, "Rejected") : 0;

        model.addAttribute("pendingLeaves",        pendingLeaves);
        model.addAttribute("approvedLeaves",       approvedLeaves);
        model.addAttribute("rejectedLeaves",       rejectedLeaves);
        model.addAttribute("attendanceMonth",     "May 2026");
        model.addAttribute("presentDays",          0);
        model.addAttribute("absentDays",           0);
        model.addAttribute("leaveDays",            0);
        model.addAttribute("attendancePercent",    0);
        model.addAttribute("lastCheckin",         "—");
        model.addAttribute("myProjects",           Collections.emptyList());
        model.addAttribute("pendingTasks",         Collections.emptyList());
        model.addAttribute("leaveRequests",        emp != null ? leaveRequestRepository.findByEmployeeOrderByCreatedAtDesc(emp) : Collections.emptyList());
    }

    // ── pages ─────────────────────────────────────────────────────────────

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        injectUser(model);
        injectStats(model);

        User emp = getCurrentEmployee();
        if (emp != null) {
            model.addAttribute("employeeStatus", emp.getStatus());
        } else {
            model.addAttribute("employeeStatus", "active");
        }

        // ── Team info ─────────────────────────────────────────────────────
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

        User emp = getCurrentEmployee();
        if (emp != null) {
            String tenant = getTenantSegment(emp);
            String username = emp.getUsername();

            // Load only tasks assigned to this employee in their tenant
            List<Task> myTasks = taskRepository.findByAssignedToAndTenantSegment(username, tenant);

            long completed  = myTasks.stream().filter(t -> "done".equalsIgnoreCase(t.getStatus())).count();
            long inProgress = myTasks.stream().filter(t -> "in-progress".equalsIgnoreCase(t.getStatus())).count();
            long pending    = myTasks.stream().filter(t -> "pending".equalsIgnoreCase(t.getStatus())).count();

            model.addAttribute("myTasks",         myTasks);
            model.addAttribute("taskHistory",     myTasks);
            model.addAttribute("taskHistoryCount", myTasks.size());
            model.addAttribute("completedTasks",  completed);
            model.addAttribute("inProgressTasks", inProgress);
            model.addAttribute("pendingTasks",   pending);
            model.addAttribute("totalTasks",     myTasks.size());
        } else {
            model.addAttribute("myTasks",          java.util.Collections.emptyList());
            model.addAttribute("taskHistory",      java.util.Collections.emptyList());
            model.addAttribute("taskHistoryCount", 0);
            model.addAttribute("completedTasks",   0);
            model.addAttribute("inProgressTasks",  0);
            model.addAttribute("pendingTasks",    0);
            model.addAttribute("totalTasks",      0);
        }

        return "employee-tasks";
    }

    @PostMapping("/tasks/update-status/{id}")
    public String updateTaskStatus(@PathVariable Long id,
                                   @RequestParam String status,
                                   @RequestParam(value = "attachment", required = false) MultipartFile attachment,
                                   RedirectAttributes ra) {
        User emp = getCurrentEmployee();
        if (emp == null) {
            ra.addFlashAttribute("errorMessage", "You need to sign in to update a task status.");
            return "redirect:/employee/tasks";
        }

        Task task = taskRepository.findById(id).orElse(null);
        if (task == null) {
            ra.addFlashAttribute("errorMessage", "Task not found.");
            return "redirect:/employee/tasks";
        }

        String tenant = getTenantSegment(emp);
        if (!emp.getUsername().equals(task.getAssignedTo()) || !tenant.equals(task.getTenantSegment())) {
            ra.addFlashAttribute("errorMessage", "You can only update your own assigned tasks.");
            return "redirect:/employee/tasks";
        }

        // Handle file attachment if provided (optional now)
        if (attachment != null && !attachment.isEmpty()) {
            try {
                byte[] fileData = attachment.getBytes();
                String contentType = attachment.getContentType();
                if (contentType == null) contentType = "application/octet-stream";
                
                // Create and save attachment in database
                TaskAttachment taskAttachment = new TaskAttachment(
                    task,
                    attachment.getOriginalFilename(),
                    fileData,
                    contentType,
                    "employee"
                );
                taskAttachmentRepository.save(taskAttachment);

                List<String> existingNames = new ArrayList<>();
                if (task.getAttachmentPaths() != null && !task.getAttachmentPaths().isBlank()) {
                    existingNames.addAll(java.util.Arrays.asList(task.getAttachmentPaths().split(",")));
                }
                existingNames.add(attachment.getOriginalFilename());
                task.setAttachmentPaths(String.join(",", existingNames));
            } catch (IOException e) {
                ra.addFlashAttribute("errorMessage", "File upload failed: " + e.getMessage());
                return "redirect:/employee/tasks";
            }
        }

        // Update task status
        String normalizedStatus = "done".equalsIgnoreCase(status) ? "done" : 
                                  "in-progress".equalsIgnoreCase(status) ? "in-progress" : "pending";
        task.setStatus(normalizedStatus);
        
        // If employee marks as done, set verification status to waiting-for-review
        if ("done".equalsIgnoreCase(normalizedStatus)) {
            task.setVerificationStatus("waiting-for-review");
        } else {
            // If marking as in-progress or pending, reset verification to pending
            task.setVerificationStatus("pending");
        }
        
        taskRepository.save(task);

        String message = "Task status updated to " + normalizedStatus + ".";
        ra.addFlashAttribute("successMessage", message);
        return "redirect:/employee/tasks";
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
        String tenant = getTenantSegment(emp);
        List<Attendance> records = emp != null
                ? attendanceRepository.findByUserAndDateBetweenOrderByDateDesc(emp, filterFrom, filterTo)
                : Collections.emptyList();

        // Holidays in range
        Map<LocalDate, String> holidays = fetchHolidays(tenant, filterFrom, filterTo);

        // Today's record — drives button state
        Optional<Attendance> todayOpt = emp != null
                ? attendanceRepository.findByUserAndDate(emp, today)
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
        boolean todayOnLeave = hasApprovedLeave(emp, today);

        List<AttendanceDay> allDays = buildDayList(records, filterFrom, filterTo, holidays, emp);

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

        return "employee-attendance";
    }

    @PostMapping("/attendance/punch-in")
    public String punchIn(RedirectAttributes ra) {
        User emp = getCurrentEmployee();
        if (emp == null) return "redirect:/employee/attendance";

        LocalDate today  = LocalDate.now();
        String    tenant = getTenantSegment(emp);

        // Block punch-in on holidays
        if (holidayRepository.findByDateAndTenantSegment(today.toString(), tenant).isPresent()) {
            ra.addFlashAttribute("errorMessage", "Today is a holiday. Punch-in is not allowed.");
            return "redirect:/employee/attendance";
        }

        if (hasApprovedLeave(emp, today)) {
            ra.addFlashAttribute("errorMessage", "You are on approved leave today. Punch-in is not allowed.");
            return "redirect:/employee/attendance";
        }

        if (attendanceRepository.findByUserAndDate(emp, today).isPresent()) {
            ra.addFlashAttribute("errorMessage", "You have already punched in today.");
            return "redirect:/employee/attendance";
        }

        LocalTime now   = LocalTime.now();
        String   status = now.isAfter(LocalTime.of(9, 30)) ? "late" : "present";

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
        if (hasApprovedLeave(emp, today)) {
            ra.addFlashAttribute("errorMessage", "You are on approved leave today. Punch-out is not allowed.");
            return "redirect:/employee/attendance";
        }

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
        if (hasApprovedLeave(emp, today)) {
            ra.addFlashAttribute("errorMessage", "You are on approved leave today. Break actions are not allowed.");
            return "redirect:/employee/attendance";
        }

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
        if (hasApprovedLeave(emp, today)) {
            ra.addFlashAttribute("errorMessage", "You are on approved leave today. Break actions are not allowed.");
            return "redirect:/employee/attendance";
        }

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

    @GetMapping("/meetings")
    public String meetingsPage(Model model) {
        injectUser(model);
        injectStats(model);
        User emp = getCurrentEmployee();
        if (emp != null) {
            String tenant   = getTenantSegment(emp);
            String username = emp.getUsername();
            List<Meeting> all = meetingRepository.findAllMeetingsForUserOrHost(tenant, username);
            LocalDate today = LocalDate.now();
            LocalTime now   = LocalTime.now();
            List<Meeting> active = all.stream().filter(m -> {
                if (m.getMeetingDate().isBefore(today)) return false;
                if (m.getMeetingDate().isAfter(today)) return true;
                if (m.getMeetingTime() == null) return true;
                int dur = (m.getDuration() != null) ? m.getDuration() : 0;
                return !m.getMeetingTime().plusMinutes(dur).isBefore(now);
            }).toList();
            List<Meeting> past = all.stream().filter(m -> {
                if (m.getMeetingDate().isBefore(today)) return true;
                if (!m.getMeetingDate().equals(today)) return false;
                if (m.getMeetingTime() == null) return false;
                int dur = (m.getDuration() != null) ? m.getDuration() : 0;
                return m.getMeetingTime().plusMinutes(dur).isBefore(now);
            }).toList();
            model.addAttribute("meetings", active);
            model.addAttribute("pastMeetings", past);
        } else {
            model.addAttribute("meetings", Collections.emptyList());
            model.addAttribute("pastMeetings", Collections.emptyList());
        }
        return "employee-meetings";
    }

    @GetMapping("/leaves")
    public String leavesPage(Model model) {
        injectUser(model);
        injectStats(model);
        return "employee-leaves";
    }

    @PostMapping("/leaves")
    public String submitLeave(@RequestParam String type,
                              @RequestParam String fromDate,
                              @RequestParam String toDate,
                              @RequestParam String reason,
                              @RequestParam(value = "attachment", required = false) MultipartFile attachment,
                              RedirectAttributes ra) {
        User emp = getCurrentEmployee();
        if (emp == null) {
            ra.addFlashAttribute("errorMessage", "Session expired. Please log in again.");
            return "redirect:/employee/leaves";
        }

        LocalDate from = LocalDate.parse(fromDate);
        LocalDate to = LocalDate.parse(toDate);
        if (to.isBefore(from)) {
            ra.addFlashAttribute("errorMessage", "To date cannot be before from date.");
            return "redirect:/employee/leaves";
        }
        if (type == null || type.isBlank() || reason == null || reason.isBlank()) {
            ra.addFlashAttribute("errorMessage", "Please fill all required leave details.");
            return "redirect:/employee/leaves";
        }

        LeaveRequest leave = new LeaveRequest();
        leave.setEmployee(emp);
        leave.setEmployeeName(emp.getUsername());
        leave.setTenantSegment(getTenantSegment(emp));
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
                return "redirect:/employee/leaves";
            }
        }

        leaveRequestRepository.save(leave);
        ra.addFlashAttribute("successMessage", "Leave request submitted to HR.");
        return "redirect:/employee/leaves";
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
    public String updateProfile(@RequestParam(required = false) String username,
                                @RequestParam(required = false) String email,
                                @RequestParam(required = false) String password,
                                @RequestParam(required = false) String confirmPassword,
                                HttpServletResponse response,
                                RedirectAttributes ra) {
        User emp = getCurrentEmployee();
        if (emp == null) return "redirect:/employee/settings";

        profileUpdateService.updateProfile(emp, username, email, password, confirmPassword, ra, response);
        return "redirect:/employee/settings";
    }

    // ── DOWNLOAD TASK ATTACHMENT ──────────────────────────────────────────
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

    // ── VIEW TASK ATTACHMENT INLINE ───────────────────────────────────────
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
}
