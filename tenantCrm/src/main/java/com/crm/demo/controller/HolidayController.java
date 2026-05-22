package com.crm.demo.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.crm.demo.model.Holiday;
import com.crm.demo.service.HolidayService;

@RestController
public class HolidayController
{

    @Autowired
    private HolidayService service;



    @GetMapping("/getHolidays")
    public List<Holiday> getHolidays()
    {
        return service.getAll();
    }



    @GetMapping("/getHolidayByDate")
    public Map<String,Object> getHolidayByDate(
            @RequestParam String date)
    {

        Holiday holiday=
                service.getByDate(date);

        Map<String,Object> map=
                new HashMap<>();


        if(holiday!=null)
        {
            map.put("exists",true);
            map.put("name",holiday.getName());
            map.put("type",holiday.getType());
        }
        else
        {
            map.put("exists",false);
        }

        return map;
    }



    @PostMapping("/addHoliday")
    public String addHoliday(
            @RequestParam String date,
            @RequestParam String name,
            @RequestParam String type)
    {

        Holiday holiday=
                new Holiday();

        holiday.setDate(date);
        holiday.setName(name);
        holiday.setType(type);

        service.save(holiday);

        return "Holiday added successfully";
    }



    @PostMapping("/updateHoliday")
    public String updateHoliday(
            @RequestParam String date,
            @RequestParam String name,
            @RequestParam String type)
    {

        Holiday holiday=
                service.getByDate(date);

        if(holiday==null)
        {
            return "Holiday not found";
        }

        holiday.setName(name);
        holiday.setType(type);

        service.save(holiday);

        return "Holiday updated";
    }



    @PostMapping("/deleteHoliday")
    public String deleteHoliday(
            @RequestParam String date)
    {

        service.delete(date);

        return "Holiday deleted";
    }

}
