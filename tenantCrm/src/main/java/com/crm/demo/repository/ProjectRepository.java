package com.crm.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.crm.demo.model.Project;

public interface ProjectRepository extends JpaRepository<Project, Long> {
}
