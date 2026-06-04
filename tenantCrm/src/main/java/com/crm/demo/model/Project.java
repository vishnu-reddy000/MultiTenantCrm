package com.crm.demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, columnDefinition = "VARCHAR(20) DEFAULT 'active'")
    private String status = "active";

    public Long getId()                  { return id; }
    public void setId(Long id)           { this.id = id; }

    public String getName()              { return name; }
    public void setName(String name)     { this.name = name; }

    public String getDescription()       { return description; }
    public void setDescription(String d) { this.description = d; }

    public String getStatus()            { return status != null ? status : "active"; }
    public void setStatus(String status) { this.status = status; }
}
