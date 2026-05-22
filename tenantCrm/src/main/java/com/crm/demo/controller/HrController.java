package com.crm.demo.controller;

import com.crm.demo.model.Team;
import com.crm.demo.model.User;
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

import java.util.Collections;
import java.util.List;

@Controller
@RequestMapping("/hr")
public class HrController {

    @Autowired private UserRepository        userRepository;
    @Autowired private TeamRepository        teamRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;

    // ── Helpers ───────────────────────────────────────────────────────────

    private void injectUser(HttpServletRequest request, Model model) {
        String username = (String) request.getAttribute("loggedInUser");
        model.addAttribute("adminName", username != null ? username : "HR User");
        model.addAttribute("adminRole", "HR");
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

    private void injectStats(HttpServletRequest request, Model model) {
        String tenant = getTenantSegment(request);
        List<User> employees = tenant.isEmpty()
                ? userRepository.findAll().stream().filter(u -> "EMPLOYEE".equalsIgnoreCase(u.getRole())).toList()
                : userRepository.findByTenantSegment(tenant).stream()
                        .filter(u -> "EMPLOYEE".equalsIgnoreCase(u.getRole())).toList();

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

    @GetMapping("/recruitment")
    public String recruitmentPage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        injectStats(request, model);
        return "hr-recruitment";
    }

    @GetMapping("/attendance")
    public String attendancePage(HttpServletRequest request, Model model) {
        injectUser(request, model);
        injectStats(request, model);
        return "hr-attendance";
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
