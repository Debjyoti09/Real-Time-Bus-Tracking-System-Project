package com.bustracking.dto;

import com.bustracking.enums.TripStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class TripResponseDTO {

    private Long id;

    private Long busId;

    private String busNumber;

    private Long routeId;

    private String routeName;

    private Long driverId;

    private String driverName;

    private TripStatus status;

    private LocalTime scheduledTime;

    private LocalTime startedAt;

    private LocalTime endedAt;

    private LocalDateTime startedAtDateTime;

    private LocalDateTime endedAtDateTime;

    public LocalDate date;

    private Boolean emergencyAbort;

    private String abortReason;

    private Double busLatitude;

    private Double busLongitude;

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDriverId() { return driverId; }

    public void setDriverId(Long driverId) { this.driverId = driverId; }

    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }

    public Long getRouteId() {
        return routeId;
    }

    public void setRouteId(Long routeId) {
        this.routeId = routeId;
    }

    public String getRouteName() {
        return routeName;
    }

    public void setRouteName(String routeName) {
        this.routeName = routeName;
    }

    public Long getBusId() {
        return busId;
    }

    public void setBusId(Long busId) {
        this.busId = busId;
    }

    public String getBusNumber() {
        return busNumber;
    }

    public void setBusNumber(String busNumber) {
        this.busNumber = busNumber;
    }

    public TripStatus getStatus() {
        return status;
    }

    public void setStatus(TripStatus status) {
        this.status = status;
    }

    public LocalTime getScheduledTime() { return scheduledTime; }

    public void setScheduledTime(LocalTime scheduledTime) { this.scheduledTime = scheduledTime; }

    public LocalTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(LocalTime endedAt) {
        this.endedAt = endedAt;
    }

    public LocalDateTime getStartedAtDateTime() {
        return startedAtDateTime;
    }

    public void setStartedAtDateTime(LocalDateTime startedAtDateTime) {
        this.startedAtDateTime = startedAtDateTime;
    }

    public LocalDateTime getEndedAtDateTime() {
        return endedAtDateTime;
    }

    public void setEndedAtDateTime(LocalDateTime endedAtDateTime) {
        this.endedAtDateTime = endedAtDateTime;
    }

    public Boolean getEmergencyAbort() {
        return emergencyAbort;
    }

    public void setEmergencyAbort(Boolean emergencyAbort) {
        this.emergencyAbort = emergencyAbort;
    }

    public String getAbortReason() {
        return abortReason;
    }

    public void setAbortReason(String abortReason) {
        this.abortReason = abortReason;
    }

    public Double getBusLatitude() {
        return busLatitude;
    }

    public void setBusLatitude(Double busLatitude) {
        this.busLatitude = busLatitude;
    }

    public Double getBusLongitude() {
        return busLongitude;
    }

    public void setBusLongitude(Double busLongitude) {
        this.busLongitude = busLongitude;
    }
}
