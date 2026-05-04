package com.springboot1.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.springboot1.dto.TenantRequest;
import com.springboot1.dto.TenantResponse;
import com.springboot1.service.TenantService;

import jakarta.validation.Valid;

/**
 * All endpoints are under /super-admin/tenants and restricted to SUPER_ADMIN.
 *
 * Endpoints: GET /super-admin/tenants/generate-id → fresh unique tenant ID for
 * the form GET /super-admin/tenants/stats → KPI card counts GET
 * /super-admin/tenants → list all tenants GET /super-admin/tenants/{id} →
 * single tenant by DB id POST /super-admin/tenants → create tenant PUT
 * /super-admin/tenants/{id} → update tenant DELETE /super-admin/tenants/{id} →
 * delete tenant
 */
@RestController
@RequestMapping("/super-admin/tenants")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class TenantController {

	private final TenantService tenantService;

	public TenantController(TenantService tenantService) {
		this.tenantService = tenantService;
	}

	// ── Generate a fresh unique tenant ID (called on modal open) ─────────
	@GetMapping("/generate-id")
	public ResponseEntity<Map<String, String>> generateId() {
		return ResponseEntity.ok(Map.of("tenantId", tenantService.generateUniqueTenantId()));
	}

	// ── KPI stats for the 4 cards ─────────────────────────────────────────
	@GetMapping("/stats")
	public ResponseEntity<Map<String, Long>> stats() {
		return ResponseEntity.ok(tenantService.getStats());
	}

	// ── List all tenants ──────────────────────────────────────────────────
	@GetMapping
	public ResponseEntity<List<TenantResponse>> listAll() {
		return ResponseEntity.ok(tenantService.getAllTenants());
	}

	// ── Get single tenant ─────────────────────────────────────────────────
	@GetMapping("/{id}")
	public ResponseEntity<TenantResponse> getOne(@PathVariable Long id) {
		return ResponseEntity.ok(tenantService.getTenantById(id));
	}

	// ── Create tenant ─────────────────────────────────────────────────────
	@PostMapping
	public ResponseEntity<?> create(@Valid @RequestBody TenantRequest req) {
		try {
			TenantResponse created = tenantService.createTenant(req);
			return ResponseEntity.status(HttpStatus.CREATED).body(created);
		} catch (IllegalArgumentException ex) {
			return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
		}
	}

	// ── Update tenant ─────────────────────────────────────────────────────
	@PutMapping("/{id}")
	public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody TenantRequest req) {
		try {
			return ResponseEntity.ok(tenantService.updateTenant(id, req));
		} catch (IllegalArgumentException ex) {
			return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
		}
	}

	// ── Delete tenant ─────────────────────────────────────────────────────
	@DeleteMapping("/{id}")
	public ResponseEntity<?> delete(@PathVariable Long id) {
		try {
			tenantService.deleteTenant(id);
			return ResponseEntity.ok(Map.of("message", "Tenant deleted successfully"));
		} catch (IllegalArgumentException ex) {
			return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
		}
	}
}