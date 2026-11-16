package com.bustracking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class StopRequestDTO {

    @NotBlank(message= "Stop name can not be empty")
    private String name;

    @NotNull(message = "Latitude cannot be null")
    private Double latitude;

    @NotNull(message = "Longitude cannot be null")
    private Double longitude;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude( Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }
}
