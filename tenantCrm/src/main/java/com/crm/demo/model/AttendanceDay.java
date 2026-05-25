package com.crm.demo.model;

import java.time.LocalDate;

/**
 * View-model for a single calendar day in the attendance log.
 * Either wraps a real Attendance record, or represents a synthetic
 * absent / weekend day when no punch-in exists.
 */
public class AttendanceDay {

    private final LocalDate date;
    private final Attendance record;   // null for synthetic days
    private final String     status;   // "present","late","absent","weekend"

    /** Wrap a real attendance record */
    public AttendanceDay(Attendance record) {
        this.date   = record.getDate();
        this.record = record;
        this.status = record.getStatus();
    }

    /** Synthetic day (absent or weekend) */
    public AttendanceDay(LocalDate date, String status) {
        this.date   = date;
        this.record = null;
        this.status = status;
    }

    public LocalDate  getDate()   { return date; }
    public Attendance getRecord() { return record; }
    public String     getStatus() { return status; }

    public boolean isReal()    { return record != null; }
    public boolean isWeekend() { return "weekend".equals(status); }
    public boolean isAbsent()  { return "absent".equals(status); }

    // ── Delegating helpers so Thymeleaf can call them directly ────────────

    public String getCheckInDisplay()  { return record != null ? record.getCheckInDisplay()  : "—"; }
    public String getCheckOutDisplay() { return record != null ? record.getCheckOutDisplay() : "—"; }
    public String getWorkedHours()     { return record != null ? record.getWorkedHours()     : "—"; }
    public String getBreakDuration()   { return record != null ? record.getBreakDuration()   : "—"; }
}
