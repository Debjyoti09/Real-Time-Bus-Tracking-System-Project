package com.bustracking.dto;

import com.bustracking.enums.DriverStatus;
import java.util.ArrayList;
import java.util.List;

public class DriverResponseDTO {

    private Long id;
    private String name;
    private String username;
    private String phone;
    private String licenseNumber;
    private String address;
    private DriverStatus status;
    private List<Long> preferredRouteIds = new ArrayList<>();

    public DriverResponseDTO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
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

    public DriverStatus getStatus() { return status; }

    public void setStatus(DriverStatus status) { this.status = status; }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public List<Long> getPreferredRouteIds() {
        return preferredRouteIds;
    }

    public void setPreferredRouteIds(List<Long> preferredRouteIds) {
        this.preferredRouteIds = preferredRouteIds;
    }
}
