package com.bustracking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RouteRequestDTO {

    @NotBlank(message = "Route name cannot be empty")
    @Size(max = 150, message = "Route name cannot exceed 150 characters")
    private String name;

    @NotBlank(message = "Route description cannot be empty")
    @Size(max = 255, message = "Description cannot exceed 255 characters")
    private String description;

    public String getName(){
        return name;
    }

    public void setName(String name){
        this.name = name;
    }

    public String getDescription(){
        return description;
    }

    public void setDescription(String description){
        this.description = description;
    }
}
