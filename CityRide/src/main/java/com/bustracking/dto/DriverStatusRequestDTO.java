package com.bustracking.dto;

import com.bustracking.enums.DriverStatus;
import jakarta.validation.constraints.NotNull;

public class DriverStatusRequestDTO {
    @NotNull(message = "Status is required")
    private DriverStatus status;

    public DriverStatus getStatus() {
        return status;
    }

    public void setStatus(DriverStatus status) {
        this.status = status;
    }
}
