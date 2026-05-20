package com.crm.demo.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "attendance")
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The user this record belongs to
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Date of attendance
    @Column(nullable = false)
    private LocalDate date;

    // Punch-in time
    private LocalTime checkIn;

    // Punch-out time (null until punched out)
    private LocalTime checkOut;

    // "present" | "absent" | "late"
    @Column(nullable = false, columnDefinition = "VARCHAR(20) DEFAULT 'present'")
    private String status = "present";

    // Tenant segment (e.g. "tcs") — for fast tenant-scoped queries
    @Column(nullable = false)
    private String tenantSegment;

    // ── Getters & Setters ──────────────────────────────────────────────────

    public Long getId()                        { return id; }
    public void setId(Long id)                 { this.id = id; }

    public User getUser()                      { return user; }
    public void setUser(User user)             { this.user = user; }

    public LocalDate getDate()                 { return date; }
    public void setDate(LocalDate date)        { this.date = date; }

    public LocalTime getCheckIn()              { return checkIn; }
    public void setCheckIn(LocalTime checkIn)  { this.checkIn = checkIn; }

    public LocalTime getCheckOut()             { return checkOut; }
    public void setCheckOut(LocalTime t)       { this.checkOut = t; }

    public String getStatus()                  { return status != null ? status : "present"; }
    public void setStatus(String status)       { this.status = status; }

    public String getTenantSegment()           { return tenantSegment; }
    public void setTenantSegment(String t)     { this.tenantSegment = t; }

    // ── Derived helpers used in templates ─────────────────────────────────

    /** Returns formatted check-in like "09:15" or "—" */
    public String getCheckInDisplay() {
        return checkIn != null
               ? String.format("%02d:%02d", checkIn.getHour(), checkIn.getMinute())
               : "—";
    }

    /** Returns formatted check-out like "18:30" or "—" */
    public String getCheckOutDisplay() {
        return checkOut != null
               ? String.format("%02d:%02d", checkOut.getHour(), checkOut.getMinute())
               : "—";
    }

    /** Returns worked hours as "Xh Ym" or "—" */
    public String getWorkedHours() {
        if (checkIn == null || checkOut == null) return "—";
        long totalMinutes = java.time.Duration.between(checkIn, checkOut).toMinutes();
        if (totalMinutes < 0) return "—";
        return (totalMinutes / 60) + "h " + (totalMinutes % 60) + "m";
    }
}
