package com.bustracking.controller;

import com.bustracking.dto.TripRequestDTO;
import com.bustracking.dto.TripResponseDTO;
import com.bustracking.service.TripService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    @PostMapping
    public TripResponseDTO createTrip(@Valid @RequestBody TripRequestDTO request) {
        return tripService.createTrip(request);
    }

    @PutMapping("/{id}")
    public TripResponseDTO updateTrip(@PathVariable Long id, @Valid @RequestBody TripRequestDTO request) {
        return tripService.updateTrip(id, request);
    }

    @GetMapping
    public List<TripResponseDTO> getAllTrips() {
        return tripService.getAllTrips();
    }

    @GetMapping("/{id}")
    public TripResponseDTO getTripById(@PathVariable Long id) {
        return tripService.getTripById(id);
    }

    @DeleteMapping("/{id}")
    public String deleteTrip(@PathVariable Long id) {
        tripService.deleteTrip(id);
        return "Trip deleted sucessfully";
    }
}
