package com.bustracking.dto;

import com.bustracking.enums.BusStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public class BusRequestDTO {

    @NotBlank(message = "Bus number cannot be empty")
    private String busNumber;

    @NotNull(message = "Bus status cannot be null")
    private BusStatus status;

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

    public @NotNull(message = "Return route is required") Long getReturnRouteId() {
        return returnRouteId;
    }

    public void setReturnRouteId(@NotNull(message = "Return route is required") Long returnRouteId) {
        this.returnRouteId = returnRouteId;
    }

    @NotNull(message = "Route ID cannot be null")
    private Long routeId;

    @NotNull(message = "Going route departure time is required")
    private java.time.LocalTime departureTime;

    @NotNull(message = "Return route is required")
    private Long returnRouteId;

    @NotNull(message = "Return route departure time is required")
    private java.time.LocalTime returnDepartureTime;

    private Double currentLatitude;

    private Double currentLongitude;

    public String getBusNumber() {
        return busNumber;
    }

    public void setBusNumber(String busNumber) {
        this.busNumber = busNumber;
    }

    public BusStatus getStatus() {
        return status;
    }

    public void setStatus( BusStatus status) {
        this.status = status;
    }

    public Long getRouteId() {
        return routeId;
    }

    public void setRouteId( Long routeId) {
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
}
