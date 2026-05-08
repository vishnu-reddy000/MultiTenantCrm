package com.springboot1.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.springboot1.service.EmployeeService;
import com.springboot1.service.LeadService;

@Controller
@RequestMapping("/manager")
public class ManagerController {

	private final EmployeeService empService;
	private final LeadService leadService;

	public ManagerController(EmployeeService empService, LeadService leadService) {
		this.empService = empService;
		this.leadService = leadService;
	}

	// ════════════════════════════════════════════════════════════════════════
	// DASHBOARD
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/dashboard")
	public String dashboard(Model model) {
		model.addAttribute("totalLeads",    leadService.countTotal());
		model.addAttribute("approvedLeads", leadService.countApproved());
		model.addAttribute("pendingLeads",  leadService.countPending());
		model.addAttribute("approvedValue", leadService.sumApprovedValue());
		return "dashboard-manager";
	}

	// ════════════════════════════════════════════════════════════════════════
	// TEAM OVERVIEW
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/team/overview")
	public String teamOverview(Model model) {
		model.addAttribute("totalManagers",  empService.countManagers());
		model.addAttribute("totalSalesExecs", empService.countSalesExecutives());
		model.addAttribute("activeEmployees", empService.countActive());
		return "manager-team-overview";
	}

	@GetMapping("/team/sales")
	public String salesTeam(Model model) {
		model.addAttribute("salesExecs", empService.getSalesExecutives());
		return "manager-sales-team";
	}

	// ════════════════════════════════════════════════════════════════════════
	// LEAD MANAGEMENT
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/leads/assigned")
	public String assignedLeads(Model model) {
		model.addAttribute("leads",        leadService.getAllLeads());
		model.addAttribute("totalLeads",   leadService.countTotal());
		model.addAttribute("pendingCount", leadService.countPending());
		model.addAttribute("approvedCount", leadService.countApproved());
		return "manager-assigned-leads";
	}

	@GetMapping("/leads/tracking")
	public String leadTracking(Model model) {
		model.addAttribute("leads",        leadService.getAllLeads());
		model.addAttribute("approvedLeads", leadService.countApproved());
		model.addAttribute("pendingLeads",  leadService.countPending());
		return "manager-lead-tracking";
	}

	// ════════════════════════════════════════════════════════════════════════
	// SALES PIPELINE
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/pipeline/deals")
	public String dealTracking(Model model) {
		model.addAttribute("approvedValue", leadService.sumApprovedValue());
		model.addAttribute("approvedLeads", leadService.countApproved());
		return "manager-deal-tracking";
	}

	@GetMapping("/pipeline/stages")
	public String stageMonitoring(Model model) {
		model.addAttribute("totalLeads",    leadService.countTotal());
		model.addAttribute("approvedLeads", leadService.countApproved());
		return "manager-stage-monitoring";
	}

	// ════════════════════════════════════════════════════════════════════════
	// ACTIVITIES
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/activities/calls")
	public String calls(Model model) {
		return "manager-calls";
	}

	@GetMapping("/activities/meetings")
	public String meetings(Model model) {
		return "manager-meetings";
	}

	@GetMapping("/activities/followups")
	public String followups(Model model) {
		model.addAttribute("pendingLeads", leadService.countPending());
		return "manager-followups";
	}

	// ════════════════════════════════════════════════════════════════════════
	// REPORTS
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/reports/performance")
	public String teamPerformance(Model model) {
		model.addAttribute("totalSalesExecs", empService.countSalesExecutives());
		model.addAttribute("approvedLeads",   leadService.countApproved());
		return "manager-team-performance";
	}

	@GetMapping("/reports/revenue")
	public String revenue(Model model) {
		model.addAttribute("approvedValue", leadService.sumApprovedValue());
		model.addAttribute("approvedLeads", leadService.countApproved());
		return "manager-revenue";
	}

	@GetMapping("/reports/conversion")
	public String conversionReports(Model model) {
		model.addAttribute("totalLeads",    leadService.countTotal());
		model.addAttribute("approvedLeads", leadService.countApproved());
		model.addAttribute("pendingLeads",  leadService.countPending());
		return "manager-conversion-reports";
	}

	// ════════════════════════════════════════════════════════════════════════
	// SYSTEM
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/calendar")
	public String calendar(Model model) {
		return "manager-calendar";
	}

	@GetMapping("/notifications")
	public String notifications(Model model) {
		return "manager-notifications";
	}

	@GetMapping("/profile")
	public String profile(Model model) {
		return "manager-profile";
	}
}
