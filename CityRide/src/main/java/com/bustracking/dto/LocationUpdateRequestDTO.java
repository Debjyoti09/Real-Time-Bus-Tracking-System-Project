package com.bustracking.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public class LocationUpdateRequestDTO {

    @NotNull(message = "Latitude cannot be null")
    @DecimalMin(value = "-90.0", message = "Latitude must be at least -90")
    @DecimalMax(value = "90.0", message = "Latitude must not exceed 90")
    private Double latitude;

    @NotNull(message = "Longitude cannot be null")
    @DecimalMin(value = "-180.0", message = "Longitude must be at least -180")
    @DecimalMax(value = "180.0", message = "Longitude must not exceed 180")
    private Double longitude;

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }
}
