package com.bustracking.entity;

import com.bustracking.enums.DriverStatus;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "driver",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "licenseNumber"),
                @UniqueConstraint(columnNames = "phone")
        }
)
public class Driver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "username")
    private String username;

    @Column(nullable = false, unique = true)
    private String phone;

    @Column(nullable = false, unique = true)
    private String licenseNumber;

    @Column(nullable = false, length = 300)
    private String address;

    @ManyToMany
    @JoinTable(
            name = "driver_preferred_routes",
            joinColumns = @JoinColumn(name = "driver_id"),
            inverseJoinColumns = @JoinColumn(name = "route_id")
    )
    private List<Route> preferredRoutes = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private DriverStatus status = DriverStatus.AVAILABLE;

    public DriverStatus getStatus() {
        return status;
    }

    public void setStatus(DriverStatus status) {
        this.status = status;
    }

    public List<Route> getPreferredRoutes() {
        return preferredRoutes;
    }

    public void setPreferredRoutes(List<Route> preferredRoutes) {
        this.preferredRoutes = preferredRoutes;
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

    public void setAddress(String address) {
        this.address = address;
    }

    public String getAddress() {
        return address;
    }
}
