package com.crm.demo.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.List;

@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, columnDefinition = "VARCHAR(20) DEFAULT 'pending'")
    private String status = "pending";

    /** Verification status: 'pending', 'waiting-for-review', 'approved', 'rejected' */
    @Column(nullable = false, columnDefinition = "VARCHAR(20) DEFAULT 'pending'")
    private String verificationStatus = "pending";

    @Column(nullable = false, columnDefinition = "VARCHAR(10) DEFAULT 'Medium'")
    private String priority = "Medium";

    private String startDate;

    private String dueDate;

    /** Username of the employee this task is assigned to */
    private String assignedTo;

    /** ID of the assigned user (for lookup) */
    private Long assignedToId;

    /** Tenant segment — isolates tasks per company */
    private String tenantSegment;

    /** Username of the manager who created this task */
    private String createdBy;

    /** Comma-separated relative file paths of attachments (legacy, kept for backward compatibility) */
    @Column(length = 2000)
    private String attachmentPaths;

    /** One-to-many relationship with TaskAttachment entities (new system) */
    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<TaskAttachment> attachments;

    /** Username of the manager who verified/rejected the task */
    private String lastVerifiedBy;

    /** Timestamp when task was last verified/rejected */
    private String lastVerifiedAt;

    /** Reason for rejection (optional feedback from manager) */
    @Column(length = 500)
    private String verificationReason;

    public Long getId()                        { return id; }
    public void setId(Long id)                 { this.id = id; }

    public String getTitle()                   { return title; }
    public void setTitle(String title)         { this.title = title; }

    public String getDescription()             { return description; }
    public void setDescription(String d)       { this.description = d; }

    public String getStatus()                  { return status != null ? status : "pending"; }
    public void setStatus(String status)       { this.status = status; }

    public String getVerificationStatus()      { return verificationStatus != null ? verificationStatus : "pending"; }
    public void setVerificationStatus(String vs) { this.verificationStatus = vs; }

    public String getPriority()                { return priority != null ? priority : "Medium"; }
    public void setPriority(String p)          { this.priority = p; }

    public String getStartDate()               { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    public String getDueDate()                 { return dueDate; }
    public void setDueDate(String dueDate)     { this.dueDate = dueDate; }

    public String getAssignedTo()              { return assignedTo; }
    public void setAssignedTo(String a)        { this.assignedTo = a; }

    public Long getAssignedToId()              { return assignedToId; }
    public void setAssignedToId(Long id)       { this.assignedToId = id; }

    public String getTenantSegment()           { return tenantSegment; }
    public void setTenantSegment(String t)     { this.tenantSegment = t; }

    public String getCreatedBy()               { return createdBy; }
    public void setCreatedBy(String c)         { this.createdBy = c; }

    public String getAttachmentPaths()         { return attachmentPaths; }
    public void setAttachmentPaths(String a)   { this.attachmentPaths = a; }

    public List<TaskAttachment> getAttachments() { return attachments; }
    public void setAttachments(List<TaskAttachment> attachments) { this.attachments = attachments; }

    public String getLastVerifiedBy() { return lastVerifiedBy; }
    public void setLastVerifiedBy(String lastVerifiedBy) { this.lastVerifiedBy = lastVerifiedBy; }

    public String getLastVerifiedAt() { return lastVerifiedAt; }
    public void setLastVerifiedAt(String lastVerifiedAt) { this.lastVerifiedAt = lastVerifiedAt; }

    public String getVerificationReason() { return verificationReason; }
    public void setVerificationReason(String verificationReason) { this.verificationReason = verificationReason; }

    /** Returns list of individual file paths (never null) */
    public java.util.List<String> getAttachmentList() {
        if (attachmentPaths == null || attachmentPaths.isBlank()) return java.util.Collections.emptyList();
        return java.util.Arrays.asList(attachmentPaths.split(","));
    }
}
