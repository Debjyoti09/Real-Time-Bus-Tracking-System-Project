package com.bustracking.controller;

import com.bustracking.dto.BusDetailsResponseDTO;
import com.bustracking.dto.BusRequestDTO;
import com.bustracking.dto.BusResponseDTO;
import com.bustracking.dto.LocationUpdateRequestDTO;
import com.bustracking.service.BusService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/buses")

public class BusController {

    private final BusService busService;

    public BusController(BusService busService) {
        this.busService = busService;
    }

    @PostMapping
    public BusResponseDTO createBus(@Valid @RequestBody BusRequestDTO request) {

        return busService.createBus(request);
    }

    @GetMapping
    public List<BusResponseDTO> getAllBuses() {
        return busService.getAllBuses();
    }

    @GetMapping("/{id}")
    public BusResponseDTO getBusById(@PathVariable Long id) {
        return busService.getBusById(id);
    }

    @PutMapping("/{id}")
    public BusResponseDTO updateBus(@PathVariable Long id, @Valid @RequestBody BusRequestDTO request) {

        return busService.updateBus(id, request);
    }

    @PutMapping("/{id}/location")
    public BusResponseDTO updateLocation(@PathVariable Long id, @Valid @RequestBody LocationUpdateRequestDTO request) {

        return busService.updateLocation(id, request);
    }

    @GetMapping("/{id}/details")
    public BusDetailsResponseDTO getBusDetails(
            @PathVariable Long id,
            @RequestParam(required = false) Long routeId) {
        return busService.getBusDetails(id, routeId);
    }

    @DeleteMapping("/{id}")
    public String deleteBus(@PathVariable Long id) {
        busService.deleteBus(id);
        return "Bus deleted successfully";
    }

}
