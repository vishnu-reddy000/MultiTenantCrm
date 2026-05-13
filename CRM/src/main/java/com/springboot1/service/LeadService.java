package com.springboot1.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springboot1.controller.Employee;
import com.springboot1.controller.Lead;
import com.springboot1.controller.Lead.LeadStatus;
import com.springboot1.repository.EmployeeRepository;
import com.springboot1.repository.LeadRepository;
import com.springboot1.repository.UserRepository;

@Service
@Transactional
public class LeadService {

	private final LeadRepository leadRepo;
	private final EmployeeRepository empRepo;
	private final UserRepository userRepo;

	public LeadService(LeadRepository leadRepo, EmployeeRepository empRepo, UserRepository userRepo) {
		this.leadRepo = leadRepo;
		this.empRepo = empRepo;
		this.userRepo = userRepo;
	}

	// ── Save (create or update) ───────────────────────────────────────────────

	public Lead saveLead(Lead lead) {
		return leadRepo.save(lead);
	}

	// ── Read ──────────────────────────────────────────────────────────────────

	public List<Lead> getAllLeads() {
		return leadRepo.findAllWithRelations();
	}

	public List<Lead> getPendingLeads() {
		return leadRepo.findByStatusWithRelations(LeadStatus.PENDING);
	}

	public Optional<Lead> getById(Long id) {
		return leadRepo.findById(id);
	}

	// ── Approve ───────────────────────────────────────────────────────────────

	public Lead approveLead(Long leadId, Long assignToEmployeeId) {
		Lead lead = leadRepo.findById(leadId)
				.orElseThrow(() -> new IllegalArgumentException("Lead not found: " + leadId));

		if (lead.getStatus() != LeadStatus.PENDING) {
			throw new IllegalStateException("Only PENDING leads can be approved.");
		}

		lead.setStatus(LeadStatus.APPROVED);
		lead.setApprovedAt(LocalDateTime.now());
		lead.setRejectionNote(null);

		if (assignToEmployeeId != null) {
			Employee salesExec = empRepo.findById(assignToEmployeeId).orElseThrow(
					() -> new IllegalArgumentException("Sales Executive not found: " + assignToEmployeeId));
			lead.setAssignedTo(salesExec);
			// Also store the username directly for easy DB visibility
			String username = salesExec.getUserId() != null
					? userRepo.findById(salesExec.getUserId()).map(u -> u.getUsername()).orElse(salesExec.getEmail())
					: salesExec.getEmail();
			lead.setAssignedToUsername(username);
		}

		return leadRepo.save(lead);
	}

	// ── Reject ────────────────────────────────────────────────────────────────

	public Lead rejectLead(Long leadId, String rejectionNote) {
		Lead lead = leadRepo.findById(leadId)
				.orElseThrow(() -> new IllegalArgumentException("Lead not found: " + leadId));

		if (lead.getStatus() != LeadStatus.PENDING) {
			throw new IllegalStateException("Only PENDING leads can be rejected.");
		}

		lead.setStatus(LeadStatus.REJECTED);
		lead.setRejectionNote(rejectionNote);

		return leadRepo.save(lead);
	}

	// ── Reset to Pending (re-review) ──────────────────────────────────────────

	public Lead resetToPending(Long leadId) {
		Lead lead = leadRepo.findById(leadId)
				.orElseThrow(() -> new IllegalArgumentException("Lead not found: " + leadId));
		lead.setStatus(LeadStatus.PENDING);
		lead.setRejectionNote(null);
		lead.setApprovedAt(null);
		return leadRepo.save(lead);
	}

	// ── Delete ────────────────────────────────────────────────────────────────

	public void deleteLead(Long id) {
		leadRepo.deleteById(id);
	}

	// ── Stats ─────────────────────────────────────────────────────────────────

	public long countPending() {
		return leadRepo.countByStatus(LeadStatus.PENDING);
	}

	public long countApproved() {
		return leadRepo.countByStatus(LeadStatus.APPROVED);
	}

	public long countRejected() {
		return leadRepo.countByStatus(LeadStatus.REJECTED);
	}

	public long countTotal() {
		return leadRepo.count();
	}

	public BigDecimal sumApprovedValue() {
		return leadRepo.sumApprovedDealValue();
	}

	// ── Tenant-scoped stats ───────────────────────────────────────────────────

	public long countPendingByTenant(String tenantId) {
		if (tenantId == null) return 0L;
		return leadRepo.countByTenantIdAndStatus(tenantId, LeadStatus.PENDING);
	}

	public long countApprovedByTenant(String tenantId) {
		if (tenantId == null) return 0L;
		return leadRepo.countByTenantIdAndStatus(tenantId, LeadStatus.APPROVED);
	}

	public long countRejectedByTenant(String tenantId) {
		if (tenantId == null) return 0L;
		return leadRepo.countByTenantIdAndStatus(tenantId, LeadStatus.REJECTED);
	}

	public long countTotalByTenant(String tenantId) {
		if (tenantId == null) return 0L;
		return leadRepo.countByTenantId(tenantId);
	}

	public BigDecimal sumApprovedValueByTenant(String tenantId) {
		if (tenantId == null) return BigDecimal.ZERO;
		return leadRepo.sumApprovedDealValueByTenantId(tenantId);
	}

	public List<Lead> getAllLeadsByTenant(String tenantId) {
		if (tenantId == null) return java.util.Collections.emptyList();
		return leadRepo.findByTenantId(tenantId);
	}

	public List<Lead> getPendingLeadsByTenant(String tenantId) {
		if (tenantId == null) return java.util.Collections.emptyList();
		return leadRepo.findByTenantIdAndStatus(tenantId, LeadStatus.PENDING);
	}
}