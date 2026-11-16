package com.bustracking.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

public class TripRequestDTO {

    @NotNull(message = "Bus Id cannot be null")
    private Long busId;

    @NotNull(message = "Route ID cannot be null")
    private Long routeId;

    private Long driverId;

    @NotNull(message = "Trip date is required")
    private LocalDate tripDate;

    @NotNull(message = "Scheduled departure time is required")
    private LocalTime scheduledTime;

    public Long getBusId() { return busId; }
    public void setBusId(Long busId) { this.busId = busId; }
    public Long getRouteId() { return routeId; }
    public void setRouteId(Long routeId) { this.routeId = routeId; }
    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }
    public LocalDate getTripDate() { return tripDate; }
    public void setTripDate(LocalDate tripDate) { this.tripDate = tripDate; }
    public LocalTime getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(LocalTime scheduledTime) { this.scheduledTime = scheduledTime; }
}
