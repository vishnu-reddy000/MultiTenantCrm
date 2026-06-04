package com.crm.demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
@Table(name = "task_attachments")
public class TaskAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "task_id", nullable = false, foreignKey = @ForeignKey(name = "fk_attachment_task"))
    private Task task;

    private String originalFilename;

    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] fileData;

    private String contentType;

    /** "employee" or "manager" */
    private String uploadedBy;

    /** Timestamp of upload */
    private Long uploadedAt = System.currentTimeMillis();

    public TaskAttachment() {}

    public TaskAttachment(Task task, String originalFilename, byte[] fileData, String contentType, String uploadedBy) {
        this.task = task;
        this.originalFilename = originalFilename;
        this.fileData = fileData;
        this.contentType = contentType;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = System.currentTimeMillis();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Task getTask() { return task; }
    public void setTask(Task task) { this.task = task; }

    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }

    public byte[] getFileData() { return fileData; }
    public void setFileData(byte[] fileData) { this.fileData = fileData; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }

    public Long getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Long uploadedAt) { this.uploadedAt = uploadedAt; }
}
