package com.crm.demo.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.crm.demo.model.Holiday;
import com.crm.demo.repository.HolidayRepository;

@Service
public class HolidayService {

    @Autowired
    private HolidayRepository repository;

    /** All holidays for a tenant, ordered by date. */
    public List<Holiday> getByTenant(String tenantSegment) {
        return repository.findByTenantSegmentOrderByDateAsc(tenantSegment);
    }

    /** Find a single holiday by date + tenant. */
    public Optional<Holiday> getByDateAndTenant(String date, String tenantSegment) {
        return repository.findByDateAndTenantSegment(date, tenantSegment);
    }

    /** Find by id. */
    public Optional<Holiday> getById(Long id) {
        return repository.findById(id);
    }

    /** Save (create or update). */
    public Holiday save(Holiday holiday) {
        return repository.save(holiday);
    }

    /** Delete by id. */
    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    /** Check if a date is already used by this tenant (for new records). */
    public boolean dateExists(String date, String tenantSegment) {
        return repository.existsByDateAndTenantSegment(date, tenantSegment);
    }

    /** Check if a date is already used by this tenant (excluding current record on edit). */
    public boolean dateExistsExcluding(String date, String tenantSegment, Long excludeId) {
        return repository.existsByDateAndTenantSegmentAndIdNot(date, tenantSegment, excludeId);
    }
}
