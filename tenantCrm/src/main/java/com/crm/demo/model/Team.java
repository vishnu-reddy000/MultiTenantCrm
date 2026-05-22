package com.crm.demo.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "teams")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** Tenant segment (e.g. "tcs") — isolates teams per company. */
    @Column(nullable = false)
    private String tenantSegment;

    /** The manager assigned to this team. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private User manager;

    /** Employees who belong to this team. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "team_members",
        joinColumns = @JoinColumn(name = "team_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private List<User> members = new ArrayList<>();

    // ── Getters & Setters ──────────────────────────────────────────────────

    public Long getId()                        { return id; }
    public void setId(Long id)                 { this.id = id; }

    public String getName()                    { return name; }
    public void setName(String name)           { this.name = name; }

    public String getTenantSegment()           { return tenantSegment; }
    public void setTenantSegment(String t)     { this.tenantSegment = t; }

    public User getManager()                   { return manager; }
    public void setManager(User manager)       { this.manager = manager; }

    public List<User> getMembers()             { return members; }
    public void setMembers(List<User> members) { this.members = members; }
}
