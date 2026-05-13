package com.springboot1.controller;

import java.security.Principal;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.springboot1.controller.Lead.LeadStatus;
import com.springboot1.model.User;
import com.springboot1.repository.LeadRepository;
import com.springboot1.repository.UserRepository;
import com.springboot1.service.LeadService;

@Controller
@RequestMapping("/user")
public class UserController {

    private final LeadService leadService;
    private final LeadRepository leadRepo;
    private final UserRepository userRepo;

    public UserController(LeadService leadService, LeadRepository leadRepo, UserRepository userRepo) {
        this.leadService = leadService;
        this.leadRepo = leadRepo;
        this.userRepo = userRepo;
    }

    // ── Fetch the logged-in user from DB and add to model ─────────────────────
    private void addCurrentUser(Model model, Principal principal) {
        if (principal != null) {
            Optional<User> userOpt = userRepo.findByUsername(principal.getName());
            userOpt.ifPresent(u -> model.addAttribute("currentUser", u));
        }
    }

    // ── Shared sidebar counts ─────────────────────────────────────────────────

    private void addCounts(Model model) {
        long total    = leadService.countTotal();
        long pending  = leadService.countPending();
        long approved = leadService.countApproved();
        long rejected = leadService.countRejected();

        model.addAttribute("totalLeads",    total);
        model.addAttribute("pendingLeads",  pending);
        model.addAttribute("approvedLeads", approved);
        model.addAttribute("rejectedLeads", rejected);
        model.addAttribute("approvedValue", leadService.sumApprovedValue());
        model.addAttribute("notifCount",    pending + rejected);
        model.addAttribute("requestCount",  total);
        model.addAttribute("dealCount",     approved);
    }

    // ════════════════════════════════════════════════════════════════════════
    // DASHBOARD — home
    // ════════════════════════════════════════════════════════════════════════

    @GetMapping({"/home", ""})
    public String home(Model model, Principal principal) {
        addCounts(model);
        addCurrentUser(model, principal);
        model.addAttribute("recentLeads", leadRepo.findAllByOrderByCreatedAtDesc());
        return "user-home";
    }

    // ════════════════════════════════════════════════════════════════════════
    // MY PROFILE
    // ════════════════════════════════════════════════════════════════════════

    @GetMapping("/profile")
    public String profile(Model model, Principal principal) {
        addCounts(model);
        addCurrentUser(model, principal);
        return "user-profile";
    }

    // ════════════════════════════════════════════════════════════════════════
    // MY REQUESTS
    // ════════════════════════════════════════════════════════════════════════

    @GetMapping("/requests")
    public String requests(Model model) {
        addCounts(model);
        model.addAttribute("allLeads",      leadRepo.findAllByOrderByCreatedAtDesc());
        model.addAttribute("pendingList",   leadRepo.findByStatusOrderByCreatedAtDesc(LeadStatus.PENDING));
        model.addAttribute("approvedList",  leadRepo.findByStatusOrderByCreatedAtDesc(LeadStatus.APPROVED));
        model.addAttribute("rejectedList",  leadRepo.findByStatusOrderByCreatedAtDesc(LeadStatus.REJECTED));
        return "user-requests";
    }

    // ════════════════════════════════════════════════════════════════════════
    // MY DEALS
    // ════════════════════════════════════════════════════════════════════════

    @GetMapping("/deals")
    public String deals(Model model) {
        addCounts(model);
        model.addAttribute("approvedList", leadRepo.findByStatusOrderByCreatedAtDesc(LeadStatus.APPROVED));
        return "user-deals";
    }

    // ════════════════════════════════════════════════════════════════════════
    // ACTIVITY HISTORY
    // ════════════════════════════════════════════════════════════════════════

    @GetMapping("/activity")
    public String activity(Model model) {
        addCounts(model);
        model.addAttribute("allLeads", leadRepo.findAllByOrderByCreatedAtDesc());
        return "user-activity";
    }

    // ════════════════════════════════════════════════════════════════════════
    // MEETINGS
    // ════════════════════════════════════════════════════════════════════════

    @GetMapping("/meetings")
    public String meetings(Model model) {
        addCounts(model);
        model.addAttribute("approvedList", leadRepo.findByStatusOrderByCreatedAtDesc(LeadStatus.APPROVED));
        return "user-meetings";
    }

    // ════════════════════════════════════════════════════════════════════════
    // NOTIFICATIONS
    // ════════════════════════════════════════════════════════════════════════

    @GetMapping("/notifications")
    public String notifications(Model model) {
        addCounts(model);
        model.addAttribute("approvedList", leadRepo.findByStatusOrderByCreatedAtDesc(LeadStatus.APPROVED));
        model.addAttribute("rejectedList", leadRepo.findByStatusOrderByCreatedAtDesc(LeadStatus.REJECTED));
        model.addAttribute("pendingList",  leadRepo.findByStatusOrderByCreatedAtDesc(LeadStatus.PENDING));
        return "user-notifications";
    }

    // ════════════════════════════════════════════════════════════════════════
    // SUPPORT TICKETS
    // ════════════════════════════════════════════════════════════════════════

    @GetMapping("/support")
    public String support(Model model) {
        addCounts(model);
        return "user-support";
    }

    @PostMapping("/support/submit")
    public String submitTicket(
            @RequestParam String subject,
            @RequestParam String category,
            @RequestParam String description,
            RedirectAttributes ra) {
        // In a real app this would persist to a SupportTicket entity
        ra.addFlashAttribute("success",
            "Ticket '" + subject + "' submitted successfully. Our team will respond within 24 hours.");
        return "redirect:/user/support";
    }

    // ════════════════════════════════════════════════════════════════════════
    // DOCUMENTS
    // ════════════════════════════════════════════════════════════════════════

    @GetMapping("/documents")
    public String documents(Model model) {
        addCounts(model);
        model.addAttribute("allLeads", leadRepo.findAllByOrderByCreatedAtDesc());
        return "user-documents";
    }

    // ════════════════════════════════════════════════════════════════════════
    // SETTINGS
    // ════════════════════════════════════════════════════════════════════════

    @GetMapping("/settings")
    public String settings(Model model, Principal principal) {
        addCounts(model);
        addCurrentUser(model, principal);
        return "user-settings";
    }

    @PostMapping("/settings/save")
    public String saveSettings(
            @RequestParam(required = false) String fullName,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone,
            RedirectAttributes ra) {
        ra.addFlashAttribute("success", "Settings saved successfully.");
        return "redirect:/user/settings";
    }

    // ════════════════════════════════════════════════════════════════════════
    // LEGACY ROUTES — kept for backward compatibility
    // ════════════════════════════════════════════════════════════════════════

    @GetMapping("/files")
    public String files(Model model) {
        return "redirect:/user/documents";
    }

    @GetMapping("/messages")
    public String messages(Model model) {
        return "redirect:/user/notifications";
    }

    @GetMapping("/location")
    public String location(Model model) {
        addCounts(model);
        return "redirect:/user/home";
    }

    @GetMapping("/graph")
    public String graph(Model model) {
        return "redirect:/user/activity";
    }
}
