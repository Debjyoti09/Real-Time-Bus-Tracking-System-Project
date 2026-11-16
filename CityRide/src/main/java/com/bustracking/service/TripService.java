package com.bustracking.service;

import com.bustracking.dto.TripRequestDTO;
import com.bustracking.dto.TripResponseDTO;

import java.util.List;

public interface TripService {

    TripResponseDTO createTrip(TripRequestDTO request);

    TripResponseDTO updateTrip(Long id, TripRequestDTO request);

    TripResponseDTO startTrip(Long tripId);

    TripResponseDTO startTrip(Long tripId, Double driverLatitude, Double driverLongitude);

    TripResponseDTO completeTrip(Long tripId);

    TripResponseDTO completeTrip(Long tripId, Double driverLatitude, Double driverLongitude);

    TripResponseDTO abortTrip(Long tripId, String reason);

    List<TripResponseDTO> getAllTrips();

    TripResponseDTO getTripById(Long id);

    void deleteTrip(Long id);

}
