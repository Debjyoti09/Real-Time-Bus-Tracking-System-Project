package com.bustracking.dto;

public class AvailableBusResponseDTO {

    private Long busId;
    private String busNumber;
    private Long routeId;
    private String status;
    private String routename;
    private Long tripId;
    private String departureTime;

    public AvailableBusResponseDTO() {
    }

    public Long getBusId() { return busId; }

    public void setBusId(Long busId) { this.busId = busId; }

    public String getBusNumber() {
        return busNumber;
    }

    public void setBusNumber(String busNumber) {
        this.busNumber = busNumber;
    }

    public Long getRouteId() {
        return routeId;
    }

    public void setRouteId(Long routeId) {
        this.routeId = routeId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRoutename() { return routename; }

    public void setRoutename(String routename) { this.routename = routename; }

    public Long getTripId() {
        return tripId;
    }

    public void setTripId(Long tripId) {
        this.tripId = tripId;
    }

    public String getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(String departureTime) {
        this.departureTime = departureTime;
    }
}
