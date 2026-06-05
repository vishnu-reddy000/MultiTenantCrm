package com.crm.demo.model;

import java.time.LocalDate;

/**
 * View-model for a single calendar day in the attendance log.
 * Either wraps a real Attendance record, or represents a synthetic
 * absent / weekend / holiday day when no punch-in exists.
 */
public class AttendanceDay {

    private final LocalDate date;
    private final Attendance record;      // null for synthetic days
    private final String     status;      // "present","late","half-day","absent","leave","weekend","holiday"
    private final String     holidayName; // non-null only when status == "holiday"

    /** Wrap a real attendance record */
    public AttendanceDay(Attendance record) {
        this.date        = record.getDate();
        this.record      = record;
        this.holidayName = null;
        // Derive status: if worked < 4h and punched out → half-day
        long mins = record.getWorkedMinutes();
        String base = record.getStatus(); // "present" or "late"
        if (mins >= 0 && mins < 240 && record.getCheckOut() != null) {
            this.status = "half-day";
        } else {
            this.status = base;
        }
    }

    /** Synthetic day (absent, weekend, or holiday) */
    public AttendanceDay(LocalDate date, String status) {
        this.date        = date;
        this.record      = null;
        this.status      = status;
        this.holidayName = null;
    }

    /** Holiday day */
    public AttendanceDay(LocalDate date, String holidayName, boolean isHoliday) {
        this.date        = date;
        this.record      = null;
        this.status      = "holiday";
        this.holidayName = holidayName;
    }

    public LocalDate  getDate()        { return date; }
    public Attendance getRecord()      { return record; }
    public String     getStatus()      { return status; }
    public String     getHolidayName() { return holidayName; }

    public boolean isReal()     { return record != null; }
    public boolean isWeekend()  { return "weekend".equals(status); }
    public boolean isAbsent()   { return "absent".equals(status); }
    public boolean isOnLeave()  { return "leave".equals(status); }
    public boolean isHoliday()  { return "holiday".equals(status); }
    public boolean isHalfDay()  { return "half-day".equals(status); }

    // ── Delegating helpers so Thymeleaf can call them directly ────────────

    public String getCheckInDisplay()  { return record != null ? record.getCheckInDisplay()  : "—"; }
    public String getCheckOutDisplay() { return record != null ? record.getCheckOutDisplay() : "—"; }
    public String getWorkedHours()     { return record != null ? record.getWorkedHours()     : "—"; }
    public String getBreakDuration()   { return record != null ? record.getBreakDuration()   : "—"; }
    public String getBreakSummary()    { return record != null ? record.getBreakSummary()    : "—"; }
    public String getBreak1Summary()   { return record != null ? record.getBreak1Summary()   : "—"; }
    public String getBreak2Summary()   { return record != null ? record.getBreak2Summary()   : "—"; }
    public String getDayType()         { return record != null ? record.getDayType()         : "—"; }
}
