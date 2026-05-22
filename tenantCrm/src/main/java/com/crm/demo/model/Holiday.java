package com.crm.demo.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

@Entity
@Data
public class Holiday
{

    @Id
    private String date;

    private String name;

    private String type;

    
}
