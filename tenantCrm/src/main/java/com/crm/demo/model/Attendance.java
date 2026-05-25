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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDate date;

    private LocalTime checkIn;
    private LocalTime checkOut;

    // Break 1
    private LocalTime breakStart;
    private LocalTime breakEnd;

    // Break 2
    private LocalTime break2Start;
    private LocalTime break2End;

    @Column(nullable = false, columnDefinition = "VARCHAR(20) DEFAULT 'present'")
    private String status = "present";

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

    public LocalTime getBreakStart()           { return breakStart; }
    public void setBreakStart(LocalTime t)     { this.breakStart = t; }

    public LocalTime getBreakEnd()             { return breakEnd; }
    public void setBreakEnd(LocalTime t)       { this.breakEnd = t; }

    public LocalTime getBreak2Start()          { return break2Start; }
    public void setBreak2Start(LocalTime t)    { this.break2Start = t; }

    public LocalTime getBreak2End()            { return break2End; }
    public void setBreak2End(LocalTime t)      { this.break2End = t; }

    public String getStatus()                  { return status != null ? status : "present"; }
    public void setStatus(String status)       { this.status = status; }

    public String getTenantSegment()           { return tenantSegment; }
    public void setTenantSegment(String t)     { this.tenantSegment = t; }

    // ── Derived helpers ────────────────────────────────────────────────────

    public String getCheckInDisplay() {
        return checkIn != null ? String.format("%02d:%02d", checkIn.getHour(), checkIn.getMinute()) : "—";
    }

    public String getCheckOutDisplay() {
        return checkOut != null ? String.format("%02d:%02d", checkOut.getHour(), checkOut.getMinute()) : "—";
    }

    public String getBreakStartDisplay() {
        return breakStart != null ? String.format("%02d:%02d", breakStart.getHour(), breakStart.getMinute()) : "—";
    }

    public String getBreakEndDisplay() {
        return breakEnd != null ? String.format("%02d:%02d", breakEnd.getHour(), breakEnd.getMinute()) : "—";
    }

    public String getBreak2StartDisplay() {
        return break2Start != null ? String.format("%02d:%02d", break2Start.getHour(), break2Start.getMinute()) : "—";
    }

    public String getBreak2EndDisplay() {
        return break2End != null ? String.format("%02d:%02d", break2End.getHour(), break2End.getMinute()) : "—";
    }

    /** Total break minutes across both breaks */
    public long getTotalBreakMinutes() {
        long mins = 0;
        if (breakStart != null && breakEnd != null) {
            long d = java.time.Duration.between(breakStart, breakEnd).toMinutes();
            if (d > 0) mins += d;
        }
        if (break2Start != null && break2End != null) {
            long d = java.time.Duration.between(break2Start, break2End).toMinutes();
            if (d > 0) mins += d;
        }
        return mins;
    }

    /** Combined break duration like "45m" or "—" */
    public String getBreakDuration() {
        long mins = getTotalBreakMinutes();
        return mins > 0 ? mins + "m" : "—";
    }

    /** Worked hours excluding all breaks */
    public String getWorkedHours() {
        if (checkIn == null || checkOut == null) return "—";
        long total = java.time.Duration.between(checkIn, checkOut).toMinutes();
        if (total < 0) return "—";
        total -= getTotalBreakMinutes();
        if (total < 0) total = 0;
        return (total / 60) + "h " + (total % 60) + "m";
    }
}
