package com.crm.demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

    /** Comma-separated relative file paths of attachments */
    @Column(length = 2000)
    private String attachmentPaths;

    public Long getId()                        { return id; }
    public void setId(Long id)                 { this.id = id; }

    public String getTitle()                   { return title; }
    public void setTitle(String title)         { this.title = title; }

    public String getDescription()             { return description; }
    public void setDescription(String d)       { this.description = d; }

    public String getStatus()                  { return status != null ? status : "pending"; }
    public void setStatus(String status)       { this.status = status; }

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

    /** Returns list of individual file paths (never null) */
    public java.util.List<String> getAttachmentList() {
        if (attachmentPaths == null || attachmentPaths.isBlank()) return java.util.Collections.emptyList();
        return java.util.Arrays.asList(attachmentPaths.split(","));
    }
}
