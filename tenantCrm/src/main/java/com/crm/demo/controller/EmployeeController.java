package com.crm.demo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Collections;

@Controller
@RequestMapping("/employee")
public class EmployeeController {

    @GetMapping("/dashboard")
    public String dashboard(Model model) {

        // Profile / topbar
        model.addAttribute("employeeName", "Employee");
        model.addAttribute("employeeRole", "Employee");
        model.addAttribute("employeeAvatar",
                "https://ui-avatars.com/api/?background=0D6EFD&color=fff&name=Employee");
        model.addAttribute("notificationCount", 0);
        model.addAttribute("notifications", Collections.emptyList());

        // Stat cards
        model.addAttribute("activeProjectsCount", 0);
        model.addAttribute("projectsTrend", "+0%");
        model.addAttribute("completedTasks", 0);
        model.addAttribute("tasksTrend", "+0%");
        model.addAttribute("attendanceRate", "0%");
        model.addAttribute("attendanceTrend", "0%");
        model.addAttribute("pendingLeaves", 0);
        model.addAttribute("leaveStatus", "pending");

        // Attendance
        model.addAttribute("attendanceMonth", "May 2026");
        model.addAttribute("presentDays", 0);
        model.addAttribute("absentDays", 0);
        model.addAttribute("leaveDays", 0);
        model.addAttribute("attendancePercent", 0);
        model.addAttribute("lastCheckin", "—");

        // Lists — wire up real data as you build features
        model.addAttribute("myProjects", Collections.emptyList());
        model.addAttribute("pendingTasks", Collections.emptyList());
        model.addAttribute("leaveRequests", Collections.emptyList());
        model.addAttribute("upcomingDeadlines", Collections.emptyList());

        return "employee";
    }
}
