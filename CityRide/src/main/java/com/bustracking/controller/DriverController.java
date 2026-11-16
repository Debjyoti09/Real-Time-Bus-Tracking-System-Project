package com.bustracking.controller;

import com.bustracking.dto.DriverRequestDTO;
import com.bustracking.dto.DriverResponseDTO;
import com.bustracking.dto.DriverRouteRequestDTO;
import com.bustracking.dto.DriverStatusRequestDTO;
import com.bustracking.service.DriverService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/drivers")

public class DriverController {

    private final DriverService driverService;

    public DriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    // Create Driver
    @PostMapping()
    public ResponseEntity<DriverResponseDTO> createDriver(
            @Valid @RequestBody DriverRequestDTO request) {

        DriverResponseDTO response =
                driverService.createDriver(request);

        return new ResponseEntity<>(
                response, HttpStatus.CREATED);
    }

    @PostMapping("/{driverId}/preferred-routes")
    public ResponseEntity<String> selectPreferredRoutes(
            @PathVariable Long driverId,
            @Valid @RequestBody DriverRouteRequestDTO request) {

        driverService.selectPreferredRoutes(driverId, request);

        return ResponseEntity.ok("Preferred routes saved successfully");
    }

    // Get All Drivers
    @GetMapping
    public ResponseEntity<List<DriverResponseDTO>> getAllDrivers() {

        return ResponseEntity.ok(
                driverService.getAllDrivers());
    }

    // Get Driver By ID
    @GetMapping("/{id}")
    public ResponseEntity<DriverResponseDTO> getDriverById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                driverService.getDriverById(id));
    }

    // Update Driver
    @PutMapping("/{id}")
    public ResponseEntity<DriverResponseDTO> updateDriver(
            @PathVariable Long id,
            @Valid @RequestBody DriverRequestDTO request) {

        return ResponseEntity.ok(
                driverService.updateDriver(id, request));
    }

    @PatchMapping("/{driverId}/status")
    public ResponseEntity<String> updateDriverStatus(
            @PathVariable Long driverId,
            @Valid @RequestBody DriverStatusRequestDTO request) {

        driverService.updateDriverStatus(driverId, request);

        return ResponseEntity.ok("Driver status updated successfully");
    }

    // Delete Driver
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDriver(
            @PathVariable Long id) {

        driverService.deleteDriver(id);

        return ResponseEntity.noContent().build();
    }
}
