package com.bustracking.dto;

public class BusTrackingResponseDTO {

    private Long busId;
    private String busNumber;
    private Long routeId;
    private String routeName;
    private Long tripId;
    private String tripStatus;
    private String abortReason;
    private String currentStop;
    private String nextStop;
    private Double distanceToNextStopKm;
    private Integer estimatedMinutes;
    private Double currentLatitude;
    private Double currentLongitude;
    private Double speedKmh;
    private Double bearing;
    private String lastUpdated;

    public BusTrackingResponseDTO() {
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

    public Long getTripId() {
        return tripId;
    }

    public void setTripId(Long tripId) {
        this.tripId = tripId;
    }

    public String getTripStatus() {
        return tripStatus;
    }

    public void setTripStatus(String tripStatus) {
        this.tripStatus = tripStatus;
    }

    public String getAbortReason() {
        return abortReason;
    }

    public void setAbortReason(String abortReason) {
        this.abortReason = abortReason;
    }

    public String getCurrentStop() {
        return currentStop;
    }

    public void setCurrentStop(String currentStop) {
        this.currentStop = currentStop;
    }

    public String getNextStop() {
        return nextStop;
    }

    public void setNextStop(String nextStop) {
        this.nextStop = nextStop;
    }

    public Double getDistanceToNextStopKm() {
        return distanceToNextStopKm;
    }

    public void setDistanceToNextStopKm(Double distanceToNextStopKm) {
        this.distanceToNextStopKm = distanceToNextStopKm;
    }

    public Integer getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public void setEstimatedMinutes(Integer estimatedMinutes) {
        this.estimatedMinutes = estimatedMinutes;
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

    public Double getSpeedKmh() {
        return speedKmh;
    }

    public void setSpeedKmh(Double speedKmh) {
        this.speedKmh = speedKmh;
    }

    public Double getBearing() {
        return bearing;
    }

    public void setBearing(Double bearing) {
        this.bearing = bearing;
    }

    public String getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
