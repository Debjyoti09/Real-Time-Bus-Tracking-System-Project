package com.bustracking.service.impl;

import com.bustracking.dto.TripRequestDTO;
import com.bustracking.dto.TripResponseDTO;
import com.bustracking.entity.Bus;
import com.bustracking.entity.Driver;
import com.bustracking.entity.Route;
import com.bustracking.entity.RouteStop;
import com.bustracking.entity.Stop;
import com.bustracking.entity.Trip;
import com.bustracking.enums.DriverStatus;
import com.bustracking.enums.BusStatus;
import com.bustracking.enums.TripStatus;
import com.bustracking.exception.ResourceNotFoundException;
import com.bustracking.repository.BusRepository;
import com.bustracking.repository.DriverRepository;
import com.bustracking.repository.RouteRepository;
import com.bustracking.repository.RouteStopRepository;
import com.bustracking.repository.TripRepository;
import com.bustracking.service.SpeedCalculationService;
import com.bustracking.service.TripService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class TripServiceImpl implements TripService {

    // Maximum allowed distance between driver's live GPS and assigned bus (30 meters = 0.03 km)
    private static final double MAX_ALLOWED_DISTANCE_KM = 0.03;

    public final TripRepository tripRepository;
    public final BusRepository busRepository;
    public final RouteRepository routeRepository;
    private final DriverRepository driverRepository;
    private RouteStopRepository routeStopRepository;
    private SpeedCalculationService speedCalculationService = new SpeedCalculationService();

    TripServiceImpl(TripRepository tripRepository,
                    BusRepository busRepository,
                    RouteRepository routeRepository,
                    DriverRepository driverRepository) {
        this.tripRepository = tripRepository;
        this.busRepository = busRepository;
        this.routeRepository = routeRepository;
        this.driverRepository = driverRepository;
    }

    @Autowired(required = false)
    public void setRouteStopRepository(RouteStopRepository routeStopRepository) {
        this.routeStopRepository = routeStopRepository;
    }

    @Autowired(required = false)
    public void setSpeedCalculationService(SpeedCalculationService speedCalculationService) {
        if (speedCalculationService != null) {
            this.speedCalculationService = speedCalculationService;
        }
    }

    @Override
    @Transactional
    public TripResponseDTO createTrip(TripRequestDTO request) {
        Bus bus = busRepository.findById(request.getBusId()).orElse(null);
        if (bus == null) {
            throw new ResourceNotFoundException("Bus not found with id: " + request.getBusId());
        }

        if (bus.getStatus() != BusStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Only ACTIVE buses can be assigned to a trip"
            );
        }

        Route route = routeRepository.findById(request.getRouteId()).orElse(null);
        if (route == null) {
            throw new ResourceNotFoundException("Route not found with route id: " + request.getRouteId());
        }

        boolean matchesGoing = bus.getRoute() != null && bus.getRoute().getId().equals(route.getId());
        boolean matchesReturn = bus.getReturnRoute() != null && bus.getReturnRoute().getId().equals(route.getId());
        if (!matchesGoing && !matchesReturn) {
            throw new IllegalStateException("Trip route must match either the Going Route or Return Route assigned to the bus");
        }

        boolean hasActiveTrip = tripRepository.existsByBus_IdAndStatusIn(
                bus.getId(), List.of(TripStatus.ACTIVE, TripStatus.RUNNING));
        if (hasActiveTrip) {
            throw new IllegalStateException("This bus already has an active or running trip");
        }

        Driver selectedDriver = null;
        if (request.getDriverId() != null) {
            Driver candidate = driverRepository.findById(request.getDriverId())
                    .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id: " + request.getDriverId()));
            boolean isDriverBusy = tripRepository.existsByDriver_IdAndStatusIn(
                    candidate.getId(), List.of(TripStatus.ACTIVE, TripStatus.RUNNING));
            if (candidate.getStatus() != DriverStatus.AVAILABLE || isDriverBusy) {
                throw new IllegalStateException("Selected driver is not currently AVAILABLE for assignment");
            }
            boolean matchesRoute = candidate.getPreferredRoutes() != null
                    && candidate.getPreferredRoutes().stream().anyMatch(r -> r != null && r.getId().equals(route.getId()));
            if (!matchesRoute) {
                throw new IllegalStateException("Selected driver is not assigned to Route #" + route.getId());
            }
            selectedDriver = candidate;
        } else {
            List<Driver> drivers = driverRepository.findByPreferredRoutes_Id(route.getId());
            for (Driver driver : drivers) {
                boolean isDriverBusy = tripRepository.existsByDriver_IdAndStatusIn(
                        driver.getId(),
                        List.of(TripStatus.ACTIVE, TripStatus.RUNNING)
                );
                if ((driver.getStatus() == DriverStatus.AVAILABLE) && !isDriverBusy) {
                    selectedDriver = driver;
                    break;
                }
            }
        }

        if (selectedDriver == null) {
            throw new IllegalStateException(
                    "No available driver found for this route. Ensure at least one driver is AVAILABLE and assigned to Route #" + route.getId()
            );
        }

        ensureBusStandingCoordinates(bus, route);

        Trip trip = new Trip();
        trip.setBus(bus);
        trip.setRoute(route);
        trip.setDriver(selectedDriver);
        trip.setStatus(TripStatus.ACTIVE);
        trip.setTripDate(request.getTripDate());
        trip.setScheduledTime(request.getScheduledTime());
        trip.setEmergencyAbort(false);

        selectedDriver.setStatus(DriverStatus.UNAVAILABLE);
        driverRepository.save(selectedDriver);

        Trip savedTrip = tripRepository.save(trip);
        return convertToResponse(savedTrip);
    }

    @Override
    @Transactional
    public TripResponseDTO updateTrip(Long id, TripRequestDTO request) {
        Trip trip = tripRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found with id: " + id));

        if (trip.getStatus() != TripStatus.ACTIVE) {
            throw new IllegalStateException("Only an ACTIVE (not yet started) trip can be edited");
        }

        Route route = trip.getRoute();
        Driver oldDriver = trip.getDriver();

        if (request.getDriverId() != null && (oldDriver == null || !oldDriver.getId().equals(request.getDriverId()))) {
            Driver newDriver = driverRepository.findById(request.getDriverId())
                    .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id: " + request.getDriverId()));

            boolean isDriverBusy = tripRepository.existsByDriver_IdAndStatusIn(
                    newDriver.getId(), List.of(TripStatus.ACTIVE, TripStatus.RUNNING));
            if (newDriver.getStatus() != DriverStatus.AVAILABLE || isDriverBusy) {
                throw new IllegalStateException("Selected driver is not currently AVAILABLE for assignment");
            }
            if (route != null) {
                boolean matchesRoute = newDriver.getPreferredRoutes() != null
                        && newDriver.getPreferredRoutes().stream().anyMatch(r -> r != null && r.getId().equals(route.getId()));
                if (!matchesRoute) {
                    throw new IllegalStateException("Selected driver is not assigned to Route #" + route.getId());
                }
            }

            if (oldDriver != null) {
                oldDriver.setStatus(DriverStatus.AVAILABLE);
                driverRepository.save(oldDriver);
            }
            newDriver.setStatus(DriverStatus.UNAVAILABLE);
            driverRepository.save(newDriver);
            trip.setDriver(newDriver);
        }

        if (request.getTripDate() != null) {
            trip.setTripDate(request.getTripDate());
        }
        if (request.getScheduledTime() != null) {
            trip.setScheduledTime(request.getScheduledTime());
        }

        return convertToResponse(tripRepository.save(trip));
    }

    @Override
    @Transactional
    public TripResponseDTO startTrip(Long tripId) {
        return startTrip(tripId, null, null);
    }

    @Override
    @Transactional
    public TripResponseDTO startTrip(Long tripId, Double driverLatitude, Double driverLongitude) {
        Trip trip = tripRepository.findById(tripId).orElse(null);
        if (trip == null) {
            throw new ResourceNotFoundException("Trip not found with id: " + tripId);
        }

        if (trip.getStatus() != TripStatus.ACTIVE) {
            throw new IllegalStateException("Only an ACTIVE trip can be started");
        }

        Bus bus = trip.getBus();
        if (bus == null || bus.getStatus() != BusStatus.ACTIVE) {
            throw new IllegalStateException(
                    "This bus is not available to start the trip"
            );
        }

        ensureBusStandingCoordinates(bus, trip.getRoute());

        // 1. Check Driver Live GPS Location & Distance to Bus (15–20m, up to 30m max for GPS accuracy)
        if (driverLatitude == null || driverLongitude == null) {
            throw new IllegalStateException(
                    "Please turn on 'Start Live Browser GPS' first so your live location can be verified before starting the trip."
            );
        }

        if (bus.getCurrentLatitude() != null && bus.getCurrentLongitude() != null) {
            double distanceKm = speedCalculationService.calculateDistance(
                    driverLatitude,
                    driverLongitude,
                    bus.getCurrentLatitude(),
                    bus.getCurrentLongitude()
            );
            if (distanceKm > MAX_ALLOWED_DISTANCE_KM) {
                long meters = Math.round(distanceKm * 1000.0);
                throw new IllegalStateException(
                        "Cannot start trip: You are " + meters + " meters away from the assigned bus (must be within 15–30 meters). Please go near the bus and try again, or contact the Admin."
                );
            }
        }

        // 2. Compare current Date & Time with Scheduled Date & Time
        LocalDate today = LocalDate.now();
        LocalTime nowTime = LocalTime.now();
        if (trip.getTripDate() != null) {
            if (today.isBefore(trip.getTripDate())) {
                throw new IllegalStateException(
                        "Trip cannot be started before the scheduled date (" + trip.getTripDate() + "). Please wait until the scheduled date and time."
                );
            }
            if (today.isEqual(trip.getTripDate())
                    && trip.getScheduledTime() != null
                    && nowTime.isBefore(trip.getScheduledTime())) {
                throw new IllegalStateException(
                        "Trip cannot be started before the scheduled time (" + trip.getScheduledTime() + "). Please wait for the scheduled departure time."
                );
            }
        }

        LocalDateTime now = LocalDateTime.now();
        bus.setStatus(BusStatus.ON_TRIP);
        bus.setLastUpdated(now.toString());
        busRepository.save(bus);

        trip.setStartedAt(now.toLocalTime());
        trip.setStartedAtDateTime(now);
        trip.setStatus(TripStatus.RUNNING);
        trip.setLastReachedStopOrder(0);
        trip.setPendingStopOrder(null);

        Trip startTrip = tripRepository.save(trip);
        return convertToResponse(startTrip);
    }

    @Override
    @Transactional
    public TripResponseDTO completeTrip(Long tripId) {
        return completeTrip(tripId, null, null);
    }

    @Override
    @Transactional
    public TripResponseDTO completeTrip(Long tripId, Double driverLatitude, Double driverLongitude) {
        Trip trip = tripRepository.findById(tripId).orElse(null);
        if (trip == null) {
            throw new ResourceNotFoundException("Trip not found with id: " + tripId);
        }

        if (trip.getStatus() != TripStatus.RUNNING) {
            throw new IllegalStateException("Only a RUNNING trip can be completed");
        }

        if (trip.getStartedAt() == null) {
            throw new IllegalStateException("Start the trip before completing it");
        }

        if (trip.getEndedAt() != null) {
            throw new IllegalStateException("Trip has already been completed");
        }

        Bus bus = trip.getBus();

        if (bus != null && bus.getCurrentLatitude() != null && bus.getCurrentLongitude() != null
                && driverLatitude != null && driverLongitude != null) {
            double distanceKm = speedCalculationService.calculateDistance(
                    driverLatitude,
                    driverLongitude,
                    bus.getCurrentLatitude(),
                    bus.getCurrentLongitude()
            );
            if (distanceKm > MAX_ALLOWED_DISTANCE_KM) {
                long meters = Math.round(distanceKm * 1000.0);
                throw new IllegalStateException(
                        "Cannot complete trip: You are " + meters + " meters away from the bus (must be within 15–30 meters)."
                );
            }
        }

        LocalDateTime now = LocalDateTime.now();
        trip.setEndedAt(now.toLocalTime());
        trip.setEndedAtDateTime(now);
        trip.setStatus(TripStatus.COMPLETED);
        trip.setEmergencyAbort(false);

        if (bus != null) {
            bus.setStatus(BusStatus.ACTIVE);
            bus.setAverageSpeedKmh(0.0);
            busRepository.save(bus);
        }

        Trip completeTrip = tripRepository.save(trip);

        Driver driver = trip.getDriver();
        if (driver != null) {
            driver.setStatus(DriverStatus.AVAILABLE);
            driverRepository.save(driver);
        }
        return convertToResponse(completeTrip);
    }

    @Override
    @Transactional
    public TripResponseDTO abortTrip(Long tripId, String reason) {
        Trip trip = tripRepository.findById(tripId).orElse(null);
        if (trip == null) {
            throw new ResourceNotFoundException("Trip not found with id: " + tripId);
        }

        if (trip.getStatus() != TripStatus.RUNNING && trip.getStatus() != TripStatus.ACTIVE) {
            throw new IllegalStateException("Only an ACTIVE or RUNNING trip can be aborted");
        }

        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Abort reason is mandatory. Please describe why the trip is being aborted.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (trip.getStartedAt() == null) {
            trip.setStartedAt(now.toLocalTime());
            trip.setStartedAtDateTime(now);
        }
        trip.setEndedAt(now.toLocalTime());
        trip.setEndedAtDateTime(now);
        // Mark trip as COMPLETED with emergencyAbort = true and preserve the abortReason
        trip.setStatus(TripStatus.COMPLETED);
        trip.setEmergencyAbort(true);
        trip.setAbortReason(reason.trim());

        Bus bus = trip.getBus();
        if (bus != null) {
            bus.setStatus(BusStatus.ACTIVE);
            bus.setAverageSpeedKmh(0.0);
            busRepository.save(bus);
        }

        Trip abortedTrip = tripRepository.save(trip);

        Driver driver = trip.getDriver();
        if (driver != null) {
            driver.setStatus(DriverStatus.AVAILABLE);
            driverRepository.save(driver);
        }

        return convertToResponse(abortedTrip);
    }

    private void ensureBusStandingCoordinates(Bus bus, Route route) {
        if (bus == null || (bus.getCurrentLatitude() != null && bus.getCurrentLongitude() != null)) {
            return;
        }
        if (routeStopRepository != null && route != null) {
            List<RouteStop> stops = routeStopRepository.findByRoute_IdOrderByStopOrderAsc(route.getId());
            if (!stops.isEmpty() && stops.get(0).getStop() != null) {
                Stop firstStop = stops.get(0).getStop();
                if (firstStop.getLatitude() != null && firstStop.getLongitude() != null) {
                    bus.setCurrentLatitude(firstStop.getLatitude());
                    bus.setCurrentLongitude(firstStop.getLongitude());
                    bus.setLastUpdated(LocalDateTime.now().toString());
                    busRepository.save(bus);
                }
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TripResponseDTO> getAllTrips() {
        List<Trip> trips = tripRepository.findAll();
        List<TripResponseDTO> responseList = new ArrayList<>();
        for (Trip trip : trips) {
            responseList.add(convertToResponse(trip));
        }
        return responseList;
    }

    @Override
    @Transactional(readOnly = true)
    public TripResponseDTO getTripById(Long id) {
        Trip trip = tripRepository.findById(id).orElse(null);
        if (trip == null) {
            throw new ResourceNotFoundException("Trip not found with id: " + id);
        }
        return convertToResponse(trip);
    }

    @Override
    @Transactional
    public void deleteTrip(Long id) {
        Trip trip = tripRepository.findById(id).orElse(null);
        if (trip == null) {
            throw new ResourceNotFoundException("Trip not found with id: " + id);
        }

        if (trip.getStatus() == TripStatus.RUNNING) {
            throw new IllegalStateException("A running trip cannot be deleted");
        }
        if (trip.getStatus() == TripStatus.ACTIVE && trip.getDriver() != null) {
            trip.getDriver().setStatus(DriverStatus.AVAILABLE);
            driverRepository.save(trip.getDriver());
        }
        tripRepository.delete(trip);
    }

    private TripResponseDTO convertToResponse(Trip trip) {
        TripResponseDTO response = new TripResponseDTO();
        response.setId(trip.getId());
        if (trip.getRoute() != null) {
            response.setRouteId(trip.getRoute().getId());
            response.setRouteName(trip.getRoute().getName());
        }
        if (trip.getBus() != null) {
            response.setBusId(trip.getBus().getId());
            response.setBusNumber(trip.getBus().getBusNumber());
            response.setBusLatitude(trip.getBus().getCurrentLatitude());
            response.setBusLongitude(trip.getBus().getCurrentLongitude());
        }
        if (trip.getDriver() != null) {
            response.setDriverId(trip.getDriver().getId());
            response.setDriverName(trip.getDriver().getName());
        }
        response.setStatus(trip.getStatus());
        response.setStartedAt(trip.getStartedAt());
        response.setEndedAt(trip.getEndedAt());
        response.setStartedAtDateTime(trip.getStartedAtDateTime());
        response.setEndedAtDateTime(trip.getEndedAtDateTime());
        response.setDate(trip.getTripDate());
        response.setScheduledTime(trip.getScheduledTime());
        response.setEmergencyAbort(Boolean.TRUE.equals(trip.getEmergencyAbort())
                || (trip.getAbortReason() != null && !trip.getAbortReason().isBlank()));
        response.setAbortReason(trip.getAbortReason());
        return response;
    }
}
