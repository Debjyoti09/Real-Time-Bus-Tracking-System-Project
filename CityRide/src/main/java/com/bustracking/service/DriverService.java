package com.bustracking.service;

import com.bustracking.dto.DriverRequestDTO;
import com.bustracking.dto.DriverResponseDTO;
import com.bustracking.dto.DriverRouteRequestDTO;
import com.bustracking.dto.DriverStatusRequestDTO;

import java.util.List;

public interface DriverService {

    DriverResponseDTO createDriver(DriverRequestDTO request);

    List<DriverResponseDTO> getAllDrivers();

    DriverResponseDTO getDriverById(Long id);

    DriverResponseDTO updateDriver(Long id, DriverRequestDTO request);

    void deleteDriver(Long id);

    void selectPreferredRoutes(Long driverId, DriverRouteRequestDTO request);

    void updateDriverStatus(Long driverId, DriverStatusRequestDTO request);
}
