package com.springboot1.controller;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.springboot1.controller.Lead.LeadStatus;
import com.springboot1.repository.EmployeeRepository;
import com.springboot1.repository.LeadRepository;
import com.springboot1.repository.UserRepository;
import com.springboot1.service.LeadService;

@Controller
@RequestMapping("/sales")
public class SalesExecutiveController {

    private final LeadService leadService;
    private final LeadRepository leadRepo;
    private final UserRepository userRepo;
    private final EmployeeRepository empRepo;

    public SalesExecutiveController(LeadService leadService, LeadRepository leadRepo,
                                    UserRepository userRepo, EmployeeRepository empRepo) {
        this.leadService = leadService;
        this.leadRepo    = leadRepo;
        this.userRepo    = userRepo;
        this.empRepo     = empRepo;
    }

    // ── Resolve the logged-in sales exec's Employee ID ────────────────────────
    private Long resolveEmployeeId(Principal principal) {
        if (principal == null) return null;
        return userRepo.findByUsername(principal.getName())
                .flatMap(u -> {
                    // First try by userId link
                    if (u.getId() != null) {
                        var byUserId = empRepo.findByUserId(u.getId());
                        if (byUserId.isPresent()) return byUserId;
                    }
                    // Fallback: match by email
                    return empRepo.findByEmail(u.getEmail());
                })
                .map(Employee::getId)
                .orElse(null);
    }

    // ── Leads assigned to this sales exec ─────────────────────────────────────
    private List<Lead> myLeads(Principal principal) {
        Long empId = resolveEmployeeId(principal);
        if (empId == null) return Collections.emptyList();
        return leadRepo.findByAssignedToId(empId);
    }

    // ── Shared sidebar counts ─────────────────────────────────────────────────

    private void addCounts(Model model, Principal principal) {
        List<Lead> assigned = myLeads(principal);
        long total    = assigned.size();
        long pending  = assigned.stream().filter(l -> l.getStatus() == LeadStatus.PENDING).count();
        long approved = assigned.stream().filter(l -> l.getStatus() == LeadStatus.APPROVED).count();
        long rejected = assigned.stream().filter(l -> l.getStatus() == LeadStatus.REJECTED).count();
        BigDecimal revenue = assigned.stream()
                .filter(l -> l.getStatus() == LeadStatus.APPROVED || l.getStatus() == LeadStatus.WON)
                .map(Lead::getDealValue)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("totalLeads",        total);
        model.addAttribute("myLeadsCount",       total);
        model.addAttribute("followupCount",      pending);
        model.addAttribute("pendingLeads",       pending);
        model.addAttribute("approvedLeads",      approved);
        model.addAttribute("rejectedLeads",      rejected);
        model.addAttribute("activeDeals",        approved);
        model.addAttribute("wonDeals",           0);
        model.addAttribute("myRevenue",          revenue);
        model.addAttribute("pendingTasksCount",  pending);
        model.addAttribute("notifCount",         pending + rejected);
        model.addAttribute("approvedPct",        total > 0 ? (approved * 100 / total) : 0);

        // Performance defaults
        model.addAttribute("leadsConverted",  approved);
        model.addAttribute("conversionRate",  total > 0 ? (approved * 100 / total) : 0);
        model.addAttribute("callsMade",       0);
        model.addAttribute("callsTarget",     20);
        model.addAttribute("meetingsDone",    0);
        model.addAttribute("meetingsTarget",  10);
        model.addAttribute("revenueTarget",   BigDecimal.valueOf(500000));

        // Pipeline stages
        model.addAttribute("pipelineStages",
            List.of("Prospecting","Qualification","Proposal","Negotiation","Closed Won","Closed Lost"));
    }

    // ── Dashboard ─────────────────────────────────────────────────────────────

    @GetMapping("/dashboard")
    public String dashboard(Model model, Principal principal) {
        addCounts(model, principal);
        model.addAttribute("myLeads", myLeads(principal));
        model.addAttribute("pageSubtitle", "Welcome back — here's your daily overview.");
        return "dashboard-sales-executive";
    }

    // ── My Leads ──────────────────────────────────────────────────────────────

    @GetMapping("/leads")
    public String myLeadsPage(Model model, Principal principal) {
        addCounts(model, principal);
        model.addAttribute("myLeads", myLeads(principal));
        model.addAttribute("pageSubtitle", "All leads assigned to you.");
        return "sales-my-leads";
    }

    // ── Add Lead ──────────────────────────────────────────────────────────────

    @GetMapping("/leads/add")
    public String addLeadForm(Model model, Principal principal) {
        addCounts(model, principal);
        model.addAttribute("pageSubtitle", "Submit a new lead for manager approval.");
        return "sales-add-lead";
    }

    @PostMapping("/leads/submit")
    public String submitLead(
            @RequestParam String customerName,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false, defaultValue = "0") BigDecimal dealValue,
            @RequestParam(required = false, defaultValue = "OTHER") String source,
            @RequestParam(required = false) String notes,
            RedirectAttributes ra) {
        try {
            Lead lead = new Lead();
            lead.setCustomerName(customerName);
            lead.setCompany(company);
            lead.setEmail(email);
            lead.setPhone(phone);
            lead.setDealValue(dealValue);
            lead.setSource(Lead.Source.valueOf(source));
            lead.setNotes(notes);
            lead.setStatus(LeadStatus.PENDING);
            leadRepo.save(lead);
            ra.addFlashAttribute("success", "Lead '" + customerName + "' submitted for approval.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Failed to submit lead: " + e.getMessage());
        }
        return "redirect:/sales/leads";
    }

    // ── Follow-ups ────────────────────────────────────────────────────────────

    @GetMapping("/leads/followups")
    public String followups(Model model, Principal principal) {
        addCounts(model, principal);
        List<Lead> assigned = myLeads(principal);
        model.addAttribute("pendingLeadsList",
            assigned.stream().filter(l -> l.getStatus() == LeadStatus.PENDING).toList());
        model.addAttribute("pageSubtitle", "Leads awaiting your follow-up.");
        return "sales-followups";
    }

    // ── View Lead ─────────────────────────────────────────────────────────────

    @GetMapping("/leads/{id}")
    public String viewLead(@PathVariable Long id, Model model, Principal principal, RedirectAttributes ra) {
        return leadService.getById(id).map(lead -> {
            addCounts(model, principal);
            model.addAttribute("lead", lead);
            model.addAttribute("pageSubtitle", "Lead details for " + lead.getCustomerName());
            return "sales-view-lead";
        }).orElseGet(() -> {
            ra.addFlashAttribute("error", "Lead not found.");
            return "redirect:/sales/leads";
        });
    }

    // ── Pipeline: Deals ───────────────────────────────────────────────────────

    @GetMapping("/pipeline/deals")
    public String myDeals(Model model, Principal principal) {
        addCounts(model, principal);
        List<Lead> assigned = myLeads(principal);
        model.addAttribute("approvedLeadsList",
            assigned.stream().filter(l -> l.getStatus() == LeadStatus.APPROVED).toList());
        model.addAttribute("pageSubtitle", "Your active deals and pipeline.");
        return "sales-deals";
    }

    // ── Pipeline: Stages ──────────────────────────────────────────────────────

    @GetMapping("/pipeline/stages")
    public String dealStages(Model model, Principal principal) {
        addCounts(model, principal);
        model.addAttribute("pageSubtitle", "Overview of all deal stages.");
        return "sales-stages";
    }

    // ── Activities: Calls ─────────────────────────────────────────────────────

    @GetMapping("/activities/calls")
    public String calls(Model model, Principal principal) {
        addCounts(model, principal);
        model.addAttribute("myLeads", myLeads(principal));
        model.addAttribute("pageSubtitle", "Track and log your calls.");
        return "sales-calls";
    }

    // ── Activities: Meetings ──────────────────────────────────────────────────

    @GetMapping("/activities/meetings")
    public String meetings(Model model, Principal principal) {
        addCounts(model, principal);
        List<Lead> assigned = myLeads(principal);
        model.addAttribute("approvedLeadsList",
            assigned.stream().filter(l -> l.getStatus() == LeadStatus.APPROVED).toList());
        model.addAttribute("pageSubtitle", "Schedule and track meetings.");
        return "sales-meetings";
    }

    // ── Activities: Tasks ─────────────────────────────────────────────────────

    @GetMapping("/activities/tasks")
    public String tasks(Model model, Principal principal) {
        addCounts(model, principal);
        List<Lead> assigned = myLeads(principal);
        model.addAttribute("pendingLeadsList",
            assigned.stream().filter(l -> l.getStatus() == LeadStatus.PENDING).toList());
        model.addAttribute("myLeads", assigned);
        model.addAttribute("pageSubtitle", "Your pending tasks and actions.");
        return "sales-tasks";
    }

    // ── Calendar ──────────────────────────────────────────────────────────────

    @GetMapping("/calendar")
    public String calendar(Model model, Principal principal) {
        addCounts(model, principal);
        List<Lead> assigned = myLeads(principal);
        model.addAttribute("approvedLeadsList",
            assigned.stream().filter(l -> l.getStatus() == LeadStatus.APPROVED).toList());
        model.addAttribute("pageSubtitle", "Your schedule and upcoming events.");
        return "sales-calendar";
    }

    // ── Performance ───────────────────────────────────────────────────────────

    @GetMapping("/performance")
    public String performance(Model model, Principal principal) {
        addCounts(model, principal);
        model.addAttribute("pageSubtitle", "Your sales performance metrics.");
        return "sales-performance";
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    @GetMapping("/notifications")
    public String notifications(Model model, Principal principal) {
        addCounts(model, principal);
        List<Lead> assigned = myLeads(principal);
        model.addAttribute("approvedLeadsList",
            assigned.stream().filter(l -> l.getStatus() == LeadStatus.APPROVED).toList());
        model.addAttribute("rejectedLeadsList",
            assigned.stream().filter(l -> l.getStatus() == LeadStatus.REJECTED).toList());
        model.addAttribute("pendingLeadsList",
            assigned.stream().filter(l -> l.getStatus() == LeadStatus.PENDING).toList());
        model.addAttribute("pageSubtitle", "Your latest updates and alerts.");
        return "sales-notifications";
    }

    // ── Profile ───────────────────────────────────────────────────────────────

    @GetMapping("/profile")
    public String profile(Model model, Principal principal) {
        addCounts(model, principal);
        model.addAttribute("pageSubtitle", "Your account and performance summary.");

        if (principal != null) {
            // 1. Load the User row (gives us userId, email, tenantId)
            userRepo.findByUsername(principal.getName()).ifPresent(u -> {
                model.addAttribute("profileUser", u);

                // 2. Load the Employee row (gives us name, phone, department, status)
                empRepo.findByUserId(u.getId())
                       .or(() -> empRepo.findByEmail(u.getEmail()))
                       .ifPresent(e -> model.addAttribute("profileEmployee", e));
            });
        }

        return "sales-profile";
    }
}
