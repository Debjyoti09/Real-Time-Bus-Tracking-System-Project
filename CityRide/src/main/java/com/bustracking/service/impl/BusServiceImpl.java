package com.bustracking.service.impl;

import com.bustracking.dto.*;
import com.bustracking.entity.*;
import com.bustracking.enums.BusStatus;
import com.bustracking.enums.TripStatus;
import com.bustracking.exception.ResourceNotFoundException;
import com.bustracking.repository.*;
import com.bustracking.service.BusService;
import com.bustracking.service.SpeedCalculationService;
import com.bustracking.websocket.BusLocationWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class BusServiceImpl implements BusService {

    private final BusRepository busRepository;
    private final RouteRepository routeRepository;
    private final RouteStopRepository routeStopRepository;
    private final TripRepository tripRepository;
    private final SpeedCalculationService speedCalculationService;
    private final BusLocationWebSocketHandler locationWebSocketHandler;
    private BusStopHistoryRepository busStopHistoryRepository;

    BusServiceImpl(BusRepository busRepository,
                   RouteRepository routeRepository,
                   RouteStopRepository routeStopRepository,
                   TripRepository tripRepository,
                   SpeedCalculationService speedCalculationService,
                   BusLocationWebSocketHandler locationWebSocketHandler) {
        this.busRepository = busRepository;
        this.routeRepository = routeRepository;
        this.routeStopRepository = routeStopRepository;
        this.tripRepository = tripRepository;
        this.speedCalculationService = speedCalculationService;
        this.locationWebSocketHandler = locationWebSocketHandler;
    }

    @Autowired(required = false)
    public void setBusStopHistoryRepository(BusStopHistoryRepository busStopHistoryRepository) {
        this.busStopHistoryRepository = busStopHistoryRepository;
    }


    @Override
    @Transactional
    public BusResponseDTO createBus(BusRequestDTO request) {

        Route route = routeRepository.findById(request.getRouteId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Route not found with id: " + request.getRouteId()));

        BusStatus status = request.getStatus() == null
                ? BusStatus.ACTIVE : request.getStatus();

        if (status == BusStatus.ON_TRIP) {
            throw new IllegalArgumentException(
                    "ON_TRIP status is managed automatically by the system");
        }

        Double lat = request.getCurrentLatitude();
        Double lon = request.getCurrentLongitude();

        // If request location is missing or invalid, use first stop location
        if (lat == null || lon == null
                || !Double.isFinite(lat) || !Double.isFinite(lon)) {

            List<RouteStop> stops =
                    routeStopRepository.findByRoute_IdOrderByStopOrderAsc(route.getId());

            if (!stops.isEmpty() && stops.get(0).getStop() != null) {
                Stop firstStop = stops.get(0).getStop();

                lat = firstStop.getLatitude();
                lon = firstStop.getLongitude();
            }
        }

        // Default location if first stop coordinates are unavailable
        if (lat == null || !Double.isFinite(lat)) lat = 22.5726;
        if (lon == null || !Double.isFinite(lon)) lon = 88.3639;

        Route returnRoute = routeRepository.findById(request.getReturnRouteId())
                .orElseThrow(() -> new ResourceNotFoundException("Return route not found with id: " + request.getReturnRouteId()));
        if (route.getId().equals(returnRoute.getId())) {
            throw new IllegalArgumentException("Going route and Return route must be different");
        }

        Bus bus = new Bus();
        bus.setBusNumber(request.getBusNumber().trim());
        bus.setRoute(route);
        bus.setDepartureTime(request.getDepartureTime());
        bus.setReturnRoute(returnRoute);
        bus.setReturnDepartureTime(request.getReturnDepartureTime());
        bus.setStatus(status);
        bus.setCurrentLatitude(lat);
        bus.setCurrentLongitude(lon);
        bus.setLastUpdated(LocalDateTime.now().toString());
        bus.setAverageSpeedKmh(0.0);
        bus.setCurrentBearing(0.0);

        return convertToResponse(busRepository.save(bus));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BusResponseDTO> getAllBuses() {
        List<Bus> buses = busRepository.findAll();
        List<BusResponseDTO> responseList = new ArrayList<>();
        for (Bus bus : buses) {
            responseList.add(convertToResponse(bus));
        }
        return responseList;
    }

    @Override
    @Transactional(readOnly = true)
    public BusResponseDTO getBusById(Long id) {
        Bus bus = busRepository.findById(id).orElse(null);
        if (bus == null) {
            throw new ResourceNotFoundException("Bus not found with id: " + id);
        }
        return convertToResponse(bus);
    }

    @Override
    @Transactional
    public BusResponseDTO updateBus(Long id, BusRequestDTO request) {
        Bus bus = busRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bus not found with id: " + id));

        Route route = routeRepository.findById(request.getRouteId())
                .orElseThrow(() -> new ResourceNotFoundException("Route not available with id: " + request.getRouteId()));

        boolean hasActiveOrRunningTrip = tripRepository.existsByBus_IdAndStatusIn(
                id, List.of(TripStatus.ACTIVE, TripStatus.RUNNING)
        );

        BusStatus requestedStatus = request.getStatus();

        if (requestedStatus == BusStatus.ON_TRIP && bus.getStatus() != BusStatus.ON_TRIP) {
            throw new IllegalStateException("ON_TRIP status can only be assigned automatically when a trip starts");
        }

        if (hasActiveOrRunningTrip) {
            if (requestedStatus != null && requestedStatus != bus.getStatus()) {
                throw new IllegalStateException("Cannot change bus status while it has an assigned or running trip");
            }
            if (!bus.getRoute().getId().equals(route.getId())) {
                throw new IllegalStateException("Cannot change bus route while it has an assigned or running trip");
            }
        }

        bus.setBusNumber(request.getBusNumber().trim());
        bus.setRoute(route);
        Route returnRoute = routeRepository.findById(request.getReturnRouteId())
                .orElseThrow(() -> new ResourceNotFoundException("Return route not found with id: " + request.getReturnRouteId()));
        if (route.getId().equals(returnRoute.getId())) {
            throw new IllegalArgumentException("Going route and Return route must be different");
        }
        bus.setDepartureTime(request.getDepartureTime());
        bus.setReturnRoute(returnRoute);
        bus.setReturnDepartureTime(request.getReturnDepartureTime());
        if (requestedStatus != null) {
            bus.setStatus(requestedStatus);
        }
        if (request.getCurrentLatitude() != null && request.getCurrentLongitude() != null) {
            bus.setCurrentLatitude(request.getCurrentLatitude());
            bus.setCurrentLongitude(request.getCurrentLongitude());
        }

        return convertToResponse(busRepository.save(bus));
    }


    @Override
    @Transactional
    public BusResponseDTO updateLocation(Long id, LocationUpdateRequestDTO request) {

        Bus bus = busRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bus not found with id: " + id));

        Trip trip = tripRepository.findFirstByBus_IdAndStatus(bus.getId(), TripStatus.RUNNING)
                .orElseThrow(() -> new IllegalStateException(
                        "Bus location can only be updated for a RUNNING trip"));

        List<RouteStop> routeStops =
                routeStopRepository.findByRoute_IdOrderByStopOrderAsc(trip.getRoute().getId());

        double newLat = request.getLatitude();
        double newLon = request.getLongitude();
        LocalDateTime now = LocalDateTime.now();

        Double speedKmh = speedCalculationService.calculateAverageSpeed(bus, newLat, newLon, now);

        if (speedKmh != null && speedKmh >= 0) {
            bus.setAverageSpeedKmh(speedKmh);
        }

        if (bus.getCurrentLatitude() != null && bus.getCurrentLongitude() != null) {
            Double bearing = speedCalculationService.calculateBearing(
                    bus.getCurrentLatitude(),
                    bus.getCurrentLongitude(),
                    newLat,
                    newLon
            );

            if (bearing != null) {
                bus.setCurrentBearing(bearing);
            }
        }

        int lastReached = trip.getLastReachedStopOrder() != null
                ? trip.getLastReachedStopOrder() : 0;

        Integer pending = trip.getPendingStopOrder();

        if (pending != null
                && bus.getCurrentLatitude() != null
                && bus.getCurrentLongitude() != null) {

            for (RouteStop rs : routeStops) {

                if (rs.getStopOrder().equals(pending)) {

                    Stop stop = rs.getStop();

                    double previousDistance = speedCalculationService.calculateDistance(
                            bus.getCurrentLatitude(),
                            bus.getCurrentLongitude(),
                            stop.getLatitude(),
                            stop.getLongitude()
                    );

                    double currentDistance = speedCalculationService.calculateDistance(
                            newLat,
                            newLon,
                            stop.getLatitude(),
                            stop.getLongitude()
                    );

                    if (currentDistance >= 0.06 && currentDistance > previousDistance) {

                        lastReached = pending;
                        trip.setLastReachedStopOrder(lastReached);
                        trip.setPendingStopOrder(null);
                        pending = null;

                        tripRepository.save(trip);
                        recordStopDeparture(bus, stop, trip, now);
                    }

                    break;
                }
            }
        }

        if (pending == null) {

            int nextStopOrder = lastReached + 1;

            for (RouteStop rs : routeStops) {


                if (!rs.getStopOrder().equals(nextStopOrder)) {
                    continue;
                }

                Stop stop = rs.getStop();

                double distance = speedCalculationService.calculateDistance(
                        newLat,
                        newLon,
                        stop.getLatitude(),
                        stop.getLongitude()
                );

                if (distance <= 0.06) {

                    trip.setPendingStopOrder(rs.getStopOrder());

                    tripRepository.save(trip);
                    recordStopArrival(bus, stop, trip, now);

                    pending = rs.getStopOrder();

                }
                break;
            }
        }

        bus.setCurrentLatitude(newLat);
        bus.setCurrentLongitude(newLon);
        bus.setLastUpdated(now.toString());

        BusResponseDTO response = convertToResponse(busRepository.save(bus));

        if (TransactionSynchronizationManager.isSynchronizationActive()) {

            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            locationWebSocketHandler.broadcast(response);
                        }
                    }
            );

        } else {
            locationWebSocketHandler.broadcast(response);
        }

        return response;
    }


    private void recordStopArrival(Bus bus, Stop stop, Trip trip, LocalDateTime now) {
        if (busStopHistoryRepository == null || bus == null || stop == null || trip == null) {
            return;
        }
        try {
            if (!busStopHistoryRepository.existsByBusIdAndStopIdAndTripId(bus.getId(), stop.getId(), trip.getId())) {
                BusStopHistory history = new BusStopHistory();
                history.setBus(bus);
                history.setStop(stop);
                history.setTrip(trip);
                history.setArrivalTime(now);
                busStopHistoryRepository.save(history);
            }
        } catch (Exception ignored) {
        }
    }

    private void recordStopDeparture(Bus bus, Stop stop, Trip trip, LocalDateTime now) {
        if (busStopHistoryRepository == null || bus == null || stop == null || trip == null) {
            return;
        }
        try {
            Optional<BusStopHistory> existing = busStopHistoryRepository.findByBusIdAndStopIdAndTripId(
                    bus.getId(), stop.getId(), trip.getId());
            if (existing.isPresent() && existing.get().getDepartureTime() == null) {
                existing.get().setDepartureTime(now);
                busStopHistoryRepository.save(existing.get());
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    @Transactional
    public void deleteBus(Long id) {
        Bus bus = busRepository.findById(id).orElse(null);
        if (bus == null) {
            throw new ResourceNotFoundException("Bus not found with id: " + id);
        }
        if (tripRepository.existsByBus_IdAndStatusIn(id, List.of(TripStatus.ACTIVE, TripStatus.RUNNING))) {
            throw new IllegalStateException("Cannot delete bus while it has an active or running trip");
        }
        busRepository.delete(bus);
    }

    @Override
    @Transactional(readOnly = true)
    public BusDetailsResponseDTO getBusDetails(Long busId, Long routeId) {
        Bus bus = busRepository.findById(busId)
                .orElseThrow(() -> new ResourceNotFoundException("Bus not found: " + busId));

        Long targetRouteId = (routeId != null) ? routeId : (bus.getRoute() != null ? bus.getRoute().getId() : null);
        if (targetRouteId == null) {
            throw new IllegalStateException("Route is not assigned to this bus");
        }

        Trip trip = tripRepository.findFirstByBus_IdAndRoute_IdAndStatus(busId, targetRouteId, TripStatus.RUNNING)
                .or(() -> tripRepository.findFirstByBus_IdAndRoute_IdOrderByIdDesc(busId, targetRouteId))
                .orElseThrow(() -> new ResourceNotFoundException("Running trip not found for bus: " + busId));

        List<RouteStop> routeStops = routeStopRepository.findByRoute_IdOrderByStopOrderAsc(targetRouteId);
        if (routeStops.isEmpty()) {
            throw new ResourceNotFoundException("No stops found for this route");
        }

        int lastReachedOrder = trip.getLastReachedStopOrder() != null ? trip.getLastReachedStopOrder() : 0;
        Integer pendingOrder = trip.getPendingStopOrder();
        boolean completedNormal = trip.getStatus() == TripStatus.COMPLETED
                && !Boolean.TRUE.equals(trip.getEmergencyAbort());

        LocalDateTime now = LocalDateTime.now();
        Double busLat = bus.getCurrentLatitude();
        Double busLon = bus.getCurrentLongitude();
        double cumulativeDistanceKm = 0.0;
        double cumulativeMinutes = 0.0;
        boolean firstUpcoming = true;

        List<StopETAResponseDTO> stopDetails = new ArrayList<>();

        for (int i = 0; i < routeStops.size(); i++) {
            RouteStop rs = routeStops.get(i);
            Stop stop = rs.getStop();
            Integer order = rs.getStopOrder();

            StopETAResponseDTO dto = new StopETAResponseDTO();
            dto.setStopId(stop.getId());
            dto.setStopName(stop.getName());
            dto.setStopOrder(order);

            if (completedNormal || (order != null && order <= lastReachedOrder)) {
                dto.setStatus("PASSED");
            } else if (pendingOrder != null && pendingOrder.equals(order)) {
                dto.setStatus("CURRENT");
                dto.setDistanceKm(0.0);
                dto.setEtaMinutes(0);
                dto.setExpectedArrivalTime(now);
            } else if (busLat != null && busLon != null && stop.getLatitude() != null && stop.getLongitude() != null) {
                double segmentKm;
                if (firstUpcoming) {
                    segmentKm = speedCalculationService.calculateDistance(busLat, busLon, stop.getLatitude(), stop.getLongitude());
                    firstUpcoming = false;
                } else {
                    Stop prev = routeStops.get(i - 1).getStop();
                    segmentKm = speedCalculationService.calculateDistance(prev.getLatitude(), prev.getLongitude(), stop.getLatitude(), stop.getLongitude());
                }

                if (cumulativeDistanceKm == 0.0 && segmentKm <= 0.06) {
                    dto.setStatus("CURRENT");
                    dto.setDistanceKm(0.0);
                    dto.setEtaMinutes(0);
                    dto.setExpectedArrivalTime(now);
                } else {
                    dto.setStatus("UPCOMING");
                    cumulativeDistanceKm += segmentKm;
                    cumulativeMinutes += (segmentKm / 28.0) * 60.0;
                    int etaMinutes = Math.max(1, (int) Math.ceil(cumulativeMinutes));
                    dto.setDistanceKm(Math.round(cumulativeDistanceKm * 100.0) / 100.0);
                    dto.setEtaMinutes(etaMinutes);
                    dto.setExpectedArrivalTime(now.plusMinutes(etaMinutes));
                }
            } else {
                dto.setStatus("UPCOMING");
            }

            stopDetails.add(dto);
        }

        BusDetailsResponseDTO response = new BusDetailsResponseDTO();
        response.setBusId(bus.getId());
        response.setBusNumber(bus.getBusNumber());
        response.setRouteId(targetRouteId);
        response.setTripStatus(trip.getStatus().name());
        response.setStops(stopDetails);
        return response;
    }
    private BusResponseDTO convertToResponse(Bus bus) {
        BusResponseDTO response = new BusResponseDTO();
        response.setId(bus.getId());
        response.setBusNumber(bus.getBusNumber());
        response.setStatus(bus.getStatus());
        if (bus.getRoute() != null) {
            response.setRouteId(bus.getRoute().getId());
        }
        response.setDepartureTime(bus.getDepartureTime());
        if (bus.getReturnRoute() != null) {
            response.setReturnRouteId(bus.getReturnRoute().getId());
        }
        response.setReturnDepartureTime(bus.getReturnDepartureTime());
        response.setCurrentLatitude(bus.getCurrentLatitude());
        response.setCurrentLongitude(bus.getCurrentLongitude());
        response.setLastUpdated(bus.getLastUpdated());
        response.setAverageSpeedKmh(bus.getAverageSpeedKmh());
        response.setCurrentBearing(bus.getCurrentBearing());
        return response;
    }
}
