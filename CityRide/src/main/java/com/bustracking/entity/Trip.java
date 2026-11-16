package com.bustracking.entity;
import com.bustracking.enums.TripStatus;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bus_id", nullable = false)
    private Bus bus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TripStatus status = TripStatus.ACTIVE;

    @Column(nullable = false)
    private LocalDate tripDate;

    @Column(name = "scheduled_time")
    private LocalTime scheduledTime;

    @Column(nullable = false)
    private Integer lastReachedStopOrder = 0;

    @Column
    private Integer pendingStopOrder;

    @ManyToOne
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @Column(name = "started_at")
    private LocalTime startedAt;

    @Column(name = "ended_at")
    private LocalTime endedAt;

    @Column(name = "started_at_datetime")
    private LocalDateTime startedAtDateTime;

    @Column(name = "ended_at_datetime")
    private LocalDateTime endedAtDateTime;

    @Column(name = "emergency_abort")
    private Boolean emergencyAbort = false;

    @Column(name = "abort_reason", length = 500)
    private String abortReason;

    public Integer getLastReachedStopOrder() {
        return lastReachedStopOrder;
    }

    public void setLastReachedStopOrder(Integer lastReachedStopOrder) {
        this.lastReachedStopOrder = lastReachedStopOrder;
    }

    public Integer getPendingStopOrder() {
        return pendingStopOrder;
    }

    public void setPendingStopOrder(Integer pendingStopOrder) {
        this.pendingStopOrder = pendingStopOrder;
    }

    public Route getRoute() {
        return route;
    }

    public void setRoute(Route route) {
        this.route = route;
    }

    public LocalDate getTripDate() {
        return tripDate;
    }

    public void setTripDate(LocalDate tripDate) {
        this.tripDate = tripDate;
    }

    public LocalTime getScheduledTime() { return scheduledTime; }

    public void setScheduledTime(LocalTime scheduledTime) { this.scheduledTime = scheduledTime; }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Bus getBus() {
        return bus;
    }

    public void setBus(Bus bus) {
        this.bus = bus;
    }

    public TripStatus getStatus() {
        return status;
    }

    public void setStatus(TripStatus status) {
        this.status = status;
    }

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

    public Driver getDriver() {
        return driver;
    }

    public void setDriver(Driver driver) {
        this.driver = driver;
    }

    public String getAbortReason() {
        return abortReason;
    }

    public void setAbortReason(String abortReason) {
        this.abortReason = abortReason;
    }
}
