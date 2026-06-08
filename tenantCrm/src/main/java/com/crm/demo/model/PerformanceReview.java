package com.crm.demo.model;

import jakarta.persistence.*;

/**
 * Stores a manager's performance review for one employee for a specific month.
 */
@Entity
@Table(name = "performance_reviews",
       uniqueConstraints = @UniqueConstraint(
           columnNames = {"employee_id", "review_month", "tenant_segment"},
           name = "uq_perf_emp_month_tenant"))
public class PerformanceReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The employee being reviewed */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private User employee;

    /** Manager username who submitted the review */
    @Column(nullable = false)
    private String reviewedBy;

    /** Tenant segment — isolates reviews per company */
    @Column(nullable = false)
    private String tenantSegment;

    /**
     * Review month in "YYYY-MM" format, e.g. "2026-06".
     * Allows one review per employee per month per tenant.
     */
    @Column(nullable = false, length = 7, name = "review_month")
    private String reviewMonth;

    /** Overall rating 1–5 given by the manager */
    @Column(nullable = false)
    private int rating;

    /** Optional free-text feedback */
    @Column(length = 1000)
    private String remarks;

    /** Epoch-ms when this review was last saved */
    private Long reviewedAt = System.currentTimeMillis();

    // ── Getters & Setters ──────────────────────────────────────────────────

    public Long getId()                              { return id; }
    public void setId(Long id)                       { this.id = id; }

    public User getEmployee()                        { return employee; }
    public void setEmployee(User employee)           { this.employee = employee; }

    public String getReviewedBy()                    { return reviewedBy; }
    public void setReviewedBy(String r)              { this.reviewedBy = r; }

    public String getTenantSegment()                 { return tenantSegment; }
    public void setTenantSegment(String t)           { this.tenantSegment = t; }

    public String getReviewMonth()                   { return reviewMonth; }
    public void setReviewMonth(String m)             { this.reviewMonth = m; }

    public int getRating()                           { return rating; }
    public void setRating(int rating)                { this.rating = rating; }

    public String getRemarks()                       { return remarks; }
    public void setRemarks(String r)                 { this.remarks = r; }

    public Long getReviewedAt()                      { return reviewedAt; }
    public void setReviewedAt(Long t)                { this.reviewedAt = t; }

    public String getReviewedAtFormatted() {
        if (reviewedAt == null) return "—";
        java.time.Instant instant = java.time.Instant.ofEpochMilli(reviewedAt);
        java.time.LocalDateTime ldt = java.time.LocalDateTime.ofInstant(
                instant, java.time.ZoneId.systemDefault());
        return String.format("%04d-%02d-%02d %02d:%02d",
                ldt.getYear(), ldt.getMonthValue(), ldt.getDayOfMonth(),
                ldt.getHour(), ldt.getMinute());
    }
}
