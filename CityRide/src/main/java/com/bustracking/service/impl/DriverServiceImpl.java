package com.bustracking.service.impl;

import com.bustracking.dto.DriverRequestDTO;
import com.bustracking.dto.DriverResponseDTO;
import com.bustracking.dto.DriverRouteRequestDTO;
import com.bustracking.dto.DriverStatusRequestDTO;
import com.bustracking.entity.Driver;
import com.bustracking.entity.Route;
import com.bustracking.enums.DriverStatus;
import com.bustracking.enums.TripStatus;
import com.bustracking.exception.ResourceNotFoundException;
import com.bustracking.repository.DriverRepository;
import com.bustracking.repository.RouteRepository;
import com.bustracking.repository.TripRepository;
import com.bustracking.service.DriverService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class DriverServiceImpl implements DriverService {

    private final DriverRepository driverRepository;
    private final RouteRepository routeRepository;
    private TripRepository tripRepository;

    public DriverServiceImpl(DriverRepository driverRepository,
                             RouteRepository routeRepository) {
        this.driverRepository = driverRepository;
        this.routeRepository = routeRepository;
    }

    @Autowired(required = false)
    public void setTripRepository(TripRepository tripRepository) {
        this.tripRepository = tripRepository;
    }

    @Override
    @Transactional
    public DriverResponseDTO createDriver(DriverRequestDTO request) {
        String phone = request.getPhone() != null ? request.getPhone().trim() : "";
        String license = request.getLicenseNumber() != null ? request.getLicenseNumber().trim() : "";
        String username = request.getUsername() != null ? request.getUsername().trim() : "";

        if (username.isEmpty()) {
            throw new IllegalArgumentException("Driver username is required");
        }

        if (driverRepository.existsByPhone(phone)) {
            throw new IllegalArgumentException("Phone number already exists. Two drivers cannot share the same mobile number.");
        }

        if (driverRepository.existsByLicenseNumber(license)) {
            throw new IllegalArgumentException("License number already exists. Two drivers cannot share the same license number.");
        }

        List<Route> assignedRoutes = resolvePreferredRoutesMaxTwo(request.getRouteIds());

        Driver driver = new Driver();
        driver.setName(request.getName().trim());
        driver.setUsername(username);
        driver.setPhone(phone);
        driver.setLicenseNumber(license);
        driver.setAddress(request.getAddress().trim());
        driver.setStatus(request.getStatus() != null ? request.getStatus() : DriverStatus.AVAILABLE);
        driver.setPreferredRoutes(assignedRoutes);

        Driver savedDriver = driverRepository.save(driver);
        return convertToResponse(savedDriver);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DriverResponseDTO> getAllDrivers() {
        List<Driver> drivers = driverRepository.findAll();
        List<DriverResponseDTO> result = new ArrayList<>();
        for (Driver driver : drivers) {
            result.add(convertToResponse(driver));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public DriverResponseDTO getDriverById(Long id) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found with ID: " + id));
        return convertToResponse(driver);
    }

    @Override
    @Transactional
    public DriverResponseDTO updateDriver(Long id, DriverRequestDTO request) {
        Driver driver = driverRepository.findById(id).orElse(null);
        if (driver == null) {
            throw new ResourceNotFoundException("Driver not found with ID: " + id);
        }

        String phone = request.getPhone().trim();
        String license = request.getLicenseNumber().trim();
        String username = request.getUsername() != null ? request.getUsername().trim() : "";

        if (username.isEmpty()) {
            throw new IllegalArgumentException("Driver username is required");
        }

        Optional<Driver> phoneDriver = driverRepository.findByPhone(phone);
        if (phoneDriver.isPresent() && !phoneDriver.get().getId().equals(id)) {
            throw new IllegalArgumentException("Phone number already exists");
        }

        Optional<Driver> licenseDriver = driverRepository.findByLicenseNumber(license);
        if (licenseDriver.isPresent() && !licenseDriver.get().getId().equals(id)) {
            throw new IllegalArgumentException("License number already exists");
        }

        driver.setName(request.getName().trim());
        driver.setUsername(username);
        driver.setPhone(phone);
        driver.setLicenseNumber(license);
        driver.setAddress(request.getAddress().trim());

        if (request.getRouteIds() != null && !request.getRouteIds().isEmpty()) {
            driver.setPreferredRoutes(resolvePreferredRoutesMaxTwo(request.getRouteIds()));
        }

        Driver updatedDriver = driverRepository.save(driver);
        return convertToResponse(updatedDriver);
    }

    @Override
    @Transactional
    public void deleteDriver(Long id) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found with ID: " + id));

        if (tripRepository != null && tripRepository.existsByDriver_IdAndStatusIn(
                id, List.of(TripStatus.ACTIVE, TripStatus.RUNNING))) {
            throw new IllegalStateException("Cannot delete driver while they have an active or running trip");
        }

        if (driver.getPreferredRoutes() != null) {
            driver.getPreferredRoutes().clear();
        }
        driverRepository.delete(driver);
    }

    @Override
    @Transactional
    public void selectPreferredRoutes(Long driverId, DriverRouteRequestDTO request) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id: " + driverId));

        List<Route> routes = resolvePreferredRoutesMaxTwo(request.getRouteIds());
        driver.setPreferredRoutes(routes);
        driverRepository.save(driver);
    }

    private List<Route> resolvePreferredRoutesMaxTwo(List<Long> routeIds) {
        if (routeIds == null || routeIds.isEmpty()) {
            throw new IllegalArgumentException("Please select preferred routes for the driver");
        }
        Set<Long> uniqueIds = new LinkedHashSet<>();
        for (Long id : routeIds) {
            if (id != null && id > 0) {
                uniqueIds.add(id);
            }
        }
        if (uniqueIds.isEmpty()) {
            throw new IllegalArgumentException("Please select at least one valid preferred route");
        }
        if (uniqueIds.size() > 2) {
            throw new IllegalArgumentException("A driver can have at most 2 preferred routes");
        }
        List<Route> result = new ArrayList<>();
        for (Long routeId : uniqueIds) {
            Route route = routeRepository.findById(routeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Route not found with id: " + routeId));
            result.add(route);
        }
        return result;
    }

    @Override
    @Transactional
    public void updateDriverStatus(Long driverId, DriverStatusRequestDTO request) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id: " + driverId));

        if (tripRepository != null && tripRepository.existsByDriver_IdAndStatusIn(
                driverId, List.of(TripStatus.ACTIVE, TripStatus.RUNNING))) {
            throw new IllegalStateException("Driver status cannot be changed while an active or running trip is assigned");
        }

        driver.setStatus(request.getStatus());
        driverRepository.save(driver);
    }

    private DriverResponseDTO convertToResponse(Driver driver) {
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
            for (Route route : driver.getPreferredRoutes()) {
                if (route != null && route.getId() != null) {
                    routeIds.add(route.getId());
                }
            }
        }
        dto.setPreferredRouteIds(routeIds);
        return dto;
    }
}
