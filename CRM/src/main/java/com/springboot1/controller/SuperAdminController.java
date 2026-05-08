package com.springboot1.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.springboot1.controller.Lead.LeadStatus;
import com.springboot1.repository.LeadRepository;
import com.springboot1.repository.UserRepository;
import com.springboot1.service.LeadService;

@Controller
@RequestMapping("/super-admin")
public class SuperAdminController {

    private final LeadService leadService;
    private final LeadRepository leadRepo;
    private final UserRepository userRepo;

    public SuperAdminController(LeadService leadService, LeadRepository leadRepo, UserRepository userRepo) {
        this.leadService = leadService;
        this.leadRepo = leadRepo;
        this.userRepo = userRepo;
    }

    /** Populates common model attributes used across all super-admin pages. */
    private void addCommonAttrs(Model model) {
        long totalLeads = leadService.countTotal();
        long pending    = leadService.countPending();
        long approved   = leadService.countApproved();
        long rejected   = leadService.countRejected();
        BigDecimal revenue = leadService.sumApprovedValue();
        long totalUsers = userRepo.count();

        model.addAttribute("totalLeads",     totalLeads);
        model.addAttribute("pendingLeads",   pending);
        model.addAttribute("approvedLeads",  approved);
        model.addAttribute("rejectedLeads",  rejected);
        model.addAttribute("totalRevenue",   revenue);
        model.addAttribute("totalUsers",     totalUsers);
        model.addAttribute("totalTenants",   0L);   // placeholder until TenantRepository is wired
        model.addAttribute("conversionRate", totalLeads > 0 ? (approved * 100 / totalLeads) : 0);
    }

    // ── Dashboard ─────────────────────────────────────────────────────────────

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        addCommonAttrs(model);
        return "dashboard-super-admin";
    }

    // ── Analytics ─────────────────────────────────────────────────────────────

    @GetMapping("/analytics")
    public String analytics(Model model) {
        addCommonAttrs(model);
        model.addAttribute("allLeads", leadRepo.findAllByOrderByCreatedAtDesc());
        return "sa-analytics";
    }

    // Note: /super-admin/tenants/page is handled by DashboardController / TenantController

    // ── Admin Management ──────────────────────────────────────────────────────

    @GetMapping("/admins")
    public String admins(Model model) {
        addCommonAttrs(model);
        model.addAttribute("allAdmins", userRepo.findAll());
        return "sa-admins";
    }

    // ── User Management ───────────────────────────────────────────────────────

    @GetMapping("/users")
    public String users(Model model) {
        addCommonAttrs(model);
        model.addAttribute("allUsers", userRepo.findAll());
        return "sa-users";
    }

    // ── Roles & Permissions ───────────────────────────────────────────────────

    @GetMapping("/roles")
    public String roles(Model model) {
        addCommonAttrs(model);
        return "sa-roles";
    }

    // ── Subscription Plans ────────────────────────────────────────────────────

    @GetMapping("/subscriptions")
    public String subscriptions(Model model) {
        addCommonAttrs(model);
        return "sa-subscriptions";
    }

    // ── Lead Management ───────────────────────────────────────────────────────

    @GetMapping("/leads")
    public String leads(Model model) {
        addCommonAttrs(model);
        model.addAttribute("allLeads", leadRepo.findAllWithRelations());
        return "sa-leads";
    }

    // ── Sales Pipeline ────────────────────────────────────────────────────────

    @GetMapping("/pipeline")
    public String pipeline(Model model) {
        addCommonAttrs(model);
        model.addAttribute("allLeads",    leadRepo.findAllWithRelations());
        model.addAttribute("pendingList", leadRepo.findByStatusWithRelations(LeadStatus.PENDING));
        model.addAttribute("approvedList",leadRepo.findByStatusWithRelations(LeadStatus.APPROVED));
        model.addAttribute("rejectedList",leadRepo.findByStatusWithRelations(LeadStatus.REJECTED));
        return "sa-pipeline";
    }

    // ── Activity Tracking ─────────────────────────────────────────────────────

    @GetMapping("/activities")
    public String activities(Model model) {
        addCommonAttrs(model);
        model.addAttribute("allLeads", leadRepo.findAllByOrderByCreatedAtDesc());
        return "sa-activities";
    }

    // ── Reports & Analytics ───────────────────────────────────────────────────

    @GetMapping("/reports")
    public String reports(Model model) {
        addCommonAttrs(model);
        model.addAttribute("allLeads", leadRepo.findAllWithRelations());
        return "sa-reports";
    }

    // ── Revenue Reports ───────────────────────────────────────────────────────

    @GetMapping("/revenue")
    public String revenue(Model model) {
        addCommonAttrs(model);
        model.addAttribute("approvedList", leadRepo.findByStatusWithRelations(LeadStatus.APPROVED));
        return "sa-revenue";
    }

    // ── System Settings ───────────────────────────────────────────────────────

    @GetMapping("/settings")
    public String settings(Model model) {
        addCommonAttrs(model);
        return "sa-settings";
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    @GetMapping("/notifications")
    public String notifications(Model model) {
        addCommonAttrs(model);
        model.addAttribute("approvedList", leadRepo.findByStatusOrderByCreatedAtDesc(LeadStatus.APPROVED));
        model.addAttribute("rejectedList", leadRepo.findByStatusOrderByCreatedAtDesc(LeadStatus.REJECTED));
        model.addAttribute("pendingList",  leadRepo.findByStatusOrderByCreatedAtDesc(LeadStatus.PENDING));
        return "sa-notifications";
    }

    // ── Audit Logs ────────────────────────────────────────────────────────────

    @GetMapping("/audit")
    public String audit(Model model) {
        addCommonAttrs(model);
        model.addAttribute("allLeads", leadRepo.findAllByOrderByCreatedAtDesc());
        return "sa-audit";
    }

    // ── Profile ───────────────────────────────────────────────────────────────

    @GetMapping("/profile")
    public String profile(Model model) {
        addCommonAttrs(model);
        return "sa-profile";
    }
}
