package com.bustracking.controller;

import com.bustracking.dto.*;
import com.bustracking.entity.Driver;
import com.bustracking.entity.Route;
import com.bustracking.entity.Trip;
import com.bustracking.enums.TripStatus;
import com.bustracking.exception.ResourceNotFoundException;
import com.bustracking.repository.DriverRepository;
import com.bustracking.repository.TripRepository;
import com.bustracking.service.BusService;
import com.bustracking.service.DriverService;
import com.bustracking.service.TripService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/driver")
public class DriverPortalController {
    private final DriverRepository driverRepository;
    private final TripRepository tripRepository;
    private final TripService tripService;
    private final BusService busService;
    private final DriverService driverService;

    public DriverPortalController(DriverRepository driverRepository,
                                  TripRepository tripRepository,
                                  TripService tripService,
                                  BusService busService,
                                  DriverService driverService) {
        this.driverRepository = driverRepository;
        this.tripRepository = tripRepository;
        this.tripService = tripService;
        this.busService = busService;
        this.driverService = driverService;
    }

    @GetMapping("/me")
    @Transactional(readOnly = true)
    public DriverResponseDTO me(Authentication auth) {
        Driver driver = driver(auth.getName());
        DriverResponseDTO dto = new DriverResponseDTO();
        dto.setId(driver.getId());
        dto.setName(driver.getName());
        dto.setUsername(driver.getUsername() != null && !driver.getUsername().isBlank()
                ? driver.getUsername()
                : driver.getName());
        dto.setPhone(driver.getPhone());
        dto.setLicenseNumber(driver.getLicenseNumber());
        dto.setAddress(driver.getAddress());
        dto.setStatus(driver.getStatus());
        List<Long> routeIds = new ArrayList<>();
        if (driver.getPreferredRoutes() != null) {
            for (Route r : driver.getPreferredRoutes()) {
                if (r != null && r.getId() != null) {
                    routeIds.add(r.getId());
                }
            }
        }
        dto.setPreferredRouteIds(routeIds);
        return dto;
    }

    @PatchMapping("/status")
    @Transactional
    public DriverResponseDTO updateMyStatus(@RequestBody @Valid DriverStatusRequestDTO request,
                                            Authentication auth) {
        Driver driver = driver(auth.getName());
        driverService.updateDriverStatus(driver.getId(), request);
        return me(auth);
    }

    @GetMapping("/trips")
    @Transactional(readOnly = true)
    public List<TripResponseDTO> trips(Authentication auth) {
        List<TripResponseDTO> result = new ArrayList<>();
        for (Trip trip : tripRepository.findByDriver_PhoneOrderByIdDesc(auth.getName())) {
            result.add(toDto(trip));
        }
        return result;
    }

    @PutMapping("/trips/{tripId}/start")
    @Transactional
    public TripResponseDTO start(@PathVariable Long tripId,
                                 @RequestBody(required = false) LocationUpdateRequestDTO driverLocation,
                                 Authentication auth) {
        ownedTrip(tripId, auth.getName());
        Double lat = driverLocation != null ? driverLocation.getLatitude() : null;
        Double lon = driverLocation != null ? driverLocation.getLongitude() : null;
        return tripService.startTrip(tripId, lat, lon);
    }

    @PutMapping("/trips/{tripId}/complete")
    @Transactional
    public TripResponseDTO complete(@PathVariable Long tripId,
                                    @RequestBody(required = false) LocationUpdateRequestDTO driverLocation,
                                    Authentication auth) {
        ownedTrip(tripId, auth.getName());
        Double lat = driverLocation != null ? driverLocation.getLatitude() : null;
        Double lon = driverLocation != null ? driverLocation.getLongitude() : null;
        return tripService.completeTrip(tripId, lat, lon);
    }

    @PutMapping("/trips/{tripId}/abort")
    @Transactional
    public TripResponseDTO abort(@PathVariable Long tripId,
                                 @RequestBody @Valid TripAbortRequestDTO request,
                                 Authentication auth) {
        ownedTrip(tripId, auth.getName());
        return tripService.abortTrip(tripId, request.getReason());
    }

    @PutMapping("/location")
    public synchronized BusResponseDTO updateLocation(@RequestBody @Valid LocationUpdateRequestDTO request,
                                                      Authentication auth) {
        Trip trip = tripRepository.findFirstByDriver_PhoneAndStatus(auth.getName(), TripStatus.RUNNING)
                .orElseThrow(() -> new IllegalStateException("You do not have a running trip"));
        return busService.updateLocation(trip.getBus().getId(), request);
    }

    private Driver driver(String phone) {
        return driverRepository.findByPhone(phone)
                .orElseThrow(() -> new ResourceNotFoundException("Driver account not found"));
    }

    private Trip ownedTrip(Long id, String phone) {
        Trip trip = tripRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found with id: " + id));
        if (trip.getDriver() == null || !trip.getDriver().getPhone().equals(phone)) {
            throw new org.springframework.security.access.AccessDeniedException("This trip is not assigned to your account");
        }
        return trip;
    }

    private TripResponseDTO toDto(Trip trip) {
        TripResponseDTO dto = new TripResponseDTO();
        dto.setId(trip.getId());
        if (trip.getBus() != null) {
            dto.setBusId(trip.getBus().getId());
            dto.setBusNumber(trip.getBus().getBusNumber());
            dto.setBusLatitude(trip.getBus().getCurrentLatitude());
            dto.setBusLongitude(trip.getBus().getCurrentLongitude());
        }
        if (trip.getRoute() != null) {
            dto.setRouteId(trip.getRoute().getId());
            dto.setRouteName(trip.getRoute().getName());
        }
        if (trip.getDriver() != null) {
            dto.setDriverId(trip.getDriver().getId());
            dto.setDriverName(trip.getDriver().getName());
        }
        dto.setStatus(trip.getStatus());
        dto.setStartedAt(trip.getStartedAt());
        dto.setEndedAt(trip.getEndedAt());
        dto.setStartedAtDateTime(trip.getStartedAtDateTime());
        dto.setEndedAtDateTime(trip.getEndedAtDateTime());
        dto.setDate(trip.getTripDate());
        dto.setScheduledTime(trip.getScheduledTime());
        dto.setEmergencyAbort(Boolean.TRUE.equals(trip.getEmergencyAbort())
                || (trip.getAbortReason() != null && !trip.getAbortReason().isBlank()));
        dto.setAbortReason(trip.getAbortReason());
        return dto;
    }
}
