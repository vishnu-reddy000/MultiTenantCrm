package com.crm.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.crm.demo.model.Holiday;

@Repository
public interface HolidayRepository
        extends JpaRepository<Holiday,String>
{

}