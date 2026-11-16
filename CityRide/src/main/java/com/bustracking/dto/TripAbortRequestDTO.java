package com.bustracking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class TripAbortRequestDTO {

    @NotBlank(message = "Abort reason is required")
    @Size(max = 500, message = "Abort reason cannot exceed 500 characters")
    private String reason;

    private Double latitude;

    private Double longitude;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

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
