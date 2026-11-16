package com.bustracking.dto;

import com.bustracking.enums.DriverStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public class DriverRequestDTO {
    @NotBlank(message = "Driver full name is required")
    @Size(max = 100, message = "Name cannot exceed 100 characters")
    private String name;

    @NotBlank(message = "Driver username is required")
    @Size(max = 100, message = "Username cannot exceed 100 characters")
    private String username;

    @NotBlank(message = "Phone number is required")
    @Pattern(
            regexp = "^[0-9]{10}$",
            message = "Phone number must contain exactly 10 digits"
    )
    private String phone;

    @NotBlank(message = "License number is required")
    private String licenseNumber;

    @NotBlank(message = "Driver address is required")
    @Size(max = 300, message = "Address cannot exceed 300 characters")
    private String address;

    private DriverStatus status;

    private List<Long> routeIds;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public DriverStatus getStatus() {
        return status;
    }

    public void setStatus(DriverStatus status) {
        this.status = status;
    }

    public List<Long> getRouteIds() {
        return routeIds;
    }

    public void setRouteIds(List<Long> routeIds) {
        this.routeIds = routeIds;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getLicenseNumber() {
        return licenseNumber;
    }

    public void setLicenseNumber(String licenseNumber) {
        this.licenseNumber = licenseNumber;
    }
}
