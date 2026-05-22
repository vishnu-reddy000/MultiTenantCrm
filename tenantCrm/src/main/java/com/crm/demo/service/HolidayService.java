package com.crm.demo.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.crm.demo.model.Holiday;
import com.crm.demo.repository.HolidayRepository;

@Service
public class HolidayService
{

    @Autowired
    private HolidayRepository repository;



    public List<Holiday> getAll()
    {
        return repository.findAll();
    }



    public Holiday getByDate(String date)
    {
        return repository.findById(date)
                .orElse(null);
    }



    public void save(Holiday holiday)
    {
        repository.save(holiday);
    }



    public void delete(String date)
    {
        repository.deleteById(date);
    }

}
