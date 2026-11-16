package com.bustracking.entity;

import com.bustracking.enums.BusStatus;
import jakarta.persistence.*;

import java.time.LocalTime;

@Entity
public class Bus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String busNumber;

    @Enumerated(EnumType.STRING)
    private BusStatus status;

    @ManyToOne
    @JoinColumn(name = "route_id")
    private Route route;

    public LocalTime getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(LocalTime departureTime) {
        this.departureTime = departureTime;
    }

    public LocalTime getReturnDepartureTime() {
        return returnDepartureTime;
    }

    public void setReturnDepartureTime(LocalTime returnDepartureTime) {
        this.returnDepartureTime = returnDepartureTime;
    }

    public Route getReturnRoute() {
        return returnRoute;
    }

    public void setReturnRoute(Route returnRoute) {
        this.returnRoute = returnRoute;
    }

    @Column(name = "departure_time")
    private java.time.LocalTime departureTime;

    @ManyToOne
    @JoinColumn(name = "return_route_id")
    private Route returnRoute;

    @Column(name = "return_departure_time")
    private java.time.LocalTime returnDepartureTime;

    private Double currentLatitude;

    private Double currentLongitude;

    private String lastUpdated;

    private Double averageSpeedKmh;

    private Double currentBearing;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBusNumber() {
        return busNumber;
    }

    public void setBusNumber(String busNumber) {
        this.busNumber = busNumber;
    }

    public BusStatus getStatus() {
        return status;
    }

    public void setStatus(BusStatus status) {
        this.status = status;
    }

    public Route getRoute() {
        return route;
    }

    public void setRoute(Route route) {
        this.route = route;
    }

    public Double getAverageSpeedKmh() {
        return averageSpeedKmh;
    }

    public void setAverageSpeedKmh(Double averageSpeedKmh) {
        this.averageSpeedKmh = averageSpeedKmh;
    }

    public Double getCurrentBearing() {
        return currentBearing;
    }

    public void setCurrentBearing(Double currentBearing) {
        this.currentBearing = currentBearing;
    }

    public Double getCurrentLatitude() {
        return currentLatitude;
    }

    public void setCurrentLatitude(Double currentLatitude) {
        this.currentLatitude = currentLatitude;
    }

    public Double getCurrentLongitude() {
        return currentLongitude;
    }

    public void setCurrentLongitude(Double currentLongitude) {
        this.currentLongitude = currentLongitude;
    }

    public String getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

}
