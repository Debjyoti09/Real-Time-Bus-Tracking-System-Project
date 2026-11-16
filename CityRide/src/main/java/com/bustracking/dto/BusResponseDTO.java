package com.bustracking.dto;

import com.bustracking.enums.BusStatus;

import java.time.LocalTime;

public class BusResponseDTO {

    private Long id;
    private String busNumber;
    private BusStatus status;
    private Long routeId;
    private Double currentLatitude;
    private Double currentLongitude;
    private String lastUpdated;
    private Double averageSpeedKmh;
    private Double currentBearing;
    private java.time.LocalTime departureTime;
    private Long returnRouteId;
    private java.time.LocalTime returnDepartureTime;

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

    public Long getRouteId() {
        return routeId;
    }

    public void setRouteId(Long routeId) {
        this.routeId = routeId;
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

    public LocalTime getReturnDepartureTime() {
        return returnDepartureTime;
    }

    public void setReturnDepartureTime(LocalTime returnDepartureTime) {
        this.returnDepartureTime = returnDepartureTime;
    }

    public Long getReturnRouteId() {
        return returnRouteId;
    }

    public void setReturnRouteId(Long returnRouteId) {
        this.returnRouteId = returnRouteId;
    }

    public LocalTime getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(LocalTime departureTime) {
        this.departureTime = departureTime;
    }
}
