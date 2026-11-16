package com.bustracking.dto;

import java.util.List;

public class BusDetailsResponseDTO {
    private Long busId;
    private String busNumber;
    private Long routeId;
    private String tripStatus;

    private List<StopETAResponseDTO> stops;

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

    public Long getRouteId() {
        return routeId;
    }

    public void setRouteId(Long routeId) {
        this.routeId = routeId;
    }

    public String getTripStatus() {
        return tripStatus;
    }

    public void setTripStatus(String tripStatus) {
        this.tripStatus = tripStatus;
    }

    public List<StopETAResponseDTO> getStops() {
        return stops;
    }

    public void setStops(List<StopETAResponseDTO> stops) {
        this.stops = stops;
    }
}
