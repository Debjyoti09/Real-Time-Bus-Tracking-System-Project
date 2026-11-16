package com.bustracking.service.impl;

import com.bustracking.dto.AvailableBusResponseDTO;
import com.bustracking.dto.BusTrackingResponseDTO;
import com.bustracking.entity.Bus;
import com.bustracking.entity.RouteStop;
import com.bustracking.entity.Stop;
import com.bustracking.entity.Trip;
import com.bustracking.enums.BusStatus;
import com.bustracking.enums.TripStatus;
import com.bustracking.exception.ResourceNotFoundException;
import com.bustracking.repository.BusRepository;
import com.bustracking.repository.RouteStopRepository;
import com.bustracking.repository.TripRepository;
import com.bustracking.service.PassengerService;
import com.bustracking.service.SpeedCalculationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class PassengerServiceImpl implements PassengerService {

    private final BusRepository busRepository;
    private final TripRepository tripRepository;
    private final RouteStopRepository routeStopRepository;
    private final SpeedCalculationService speedCalculationService;

    public PassengerServiceImpl(
            BusRepository busRepository,
            TripRepository tripRepository,
            RouteStopRepository routeStopRepository,
            SpeedCalculationService speedCalculationService) {

        this.busRepository = busRepository;
        this.tripRepository = tripRepository;
        this.routeStopRepository = routeStopRepository;
        this.speedCalculationService = speedCalculationService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AvailableBusResponseDTO> searchBuses(String from, String to) {

        if (from == null || from.isBlank() || to == null || to.isBlank()) {
            throw new IllegalArgumentException("From and To stops are required");
        }

        from = from.trim();
        to = to.trim();

        if (from.equalsIgnoreCase(to)) {
            throw new IllegalArgumentException("From and To stops cannot be the same");
        }

        List<RouteStop> fromStops = routeStopRepository.findByStop_Name(from);
        List<RouteStop> toStops = routeStopRepository.findByStop_Name(to);

        if (fromStops.isEmpty()) {
            throw new ResourceNotFoundException("From stop not found: " + from);
        }

        if (toStops.isEmpty()) {
            throw new ResourceNotFoundException("To stop not found: " + to);
        }

        Set<Long> validRouteIds = new HashSet<>();

        for (RouteStop fromStop : fromStops) {
            for (RouteStop toStop : toStops) {
                if (fromStop.getRoute() == null || toStop.getRoute() == null) {
                    continue;
                }

                Long fromRouteId = fromStop.getRoute().getId();
                Long toRouteId = toStop.getRoute().getId();

                if (fromRouteId.equals(toRouteId)
                        && fromStop.getStopOrder() != null
                        && toStop.getStopOrder() != null
                        && fromStop.getStopOrder() < toStop.getStopOrder()) {
                    validRouteIds.add(fromRouteId);
                }
            }
        }

        if (validRouteIds.isEmpty()) {
            throw new ResourceNotFoundException("No route found from " + from + " to " + to);
        }

        List<Bus> buses = busRepository.findByStatusIn(
                List.of(BusStatus.ACTIVE, BusStatus.ON_TRIP)
        );

        List<AvailableBusResponseDTO> result = new ArrayList<>();
        for (Bus bus : buses) {
            boolean matchesGoing = bus.getRoute() != null && validRouteIds.contains(bus.getRoute().getId());
            boolean matchesReturn = bus.getReturnRoute() != null && validRouteIds.contains(bus.getReturnRoute().getId());

            Optional<Trip> runningTrip = tripRepository.findFirstByBus_IdAndStatus(bus.getId(), TripStatus.RUNNING);

            if (matchesGoing) {
                AvailableBusResponseDTO dto = new AvailableBusResponseDTO();
                dto.setBusId(bus.getId());
                dto.setBusNumber(bus.getBusNumber());
                dto.setRouteId(bus.getRoute().getId());
                dto.setRoutename(bus.getRoute().getName());
                dto.setDepartureTime(bus.getDepartureTime() != null ? bus.getDepartureTime().toString() : null);
                boolean isRunningHere = runningTrip.isPresent() && runningTrip.get().getRoute().getId().equals(bus.getRoute().getId());
                dto.setStatus(isRunningHere ? "RUNNING" : "ACTIVE");
                if (isRunningHere) dto.setTripId(runningTrip.get().getId());
                result.add(dto);
            }
            if (matchesReturn) {
                AvailableBusResponseDTO dto = new AvailableBusResponseDTO();
                dto.setBusId(bus.getId());
                dto.setBusNumber(bus.getBusNumber());
                dto.setRouteId(bus.getReturnRoute().getId());
                dto.setRoutename(bus.getReturnRoute().getName());
                dto.setDepartureTime(bus.getReturnDepartureTime() != null ? bus.getReturnDepartureTime().toString() : null);
                boolean isRunningHere = runningTrip.isPresent() && runningTrip.get().getRoute().getId().equals(bus.getReturnRoute().getId());
                dto.setStatus(isRunningHere ? "RUNNING" : "ACTIVE");
                if (isRunningHere) dto.setTripId(runningTrip.get().getId());
                result.add(dto);
            }
        }
        result.sort(java.util.Comparator.comparing(
                dto -> dto.getDepartureTime() != null ? dto.getDepartureTime() : "99:99"
        ));
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public BusTrackingResponseDTO getBusTracking(Long busId, Long routeId) {

        Bus bus = busRepository.findById(busId)
                .orElseThrow(() -> new ResourceNotFoundException("Bus not found with id: " + busId));

        Long targetRouteId = (routeId != null) ? routeId : (bus.getRoute() != null ? bus.getRoute().getId() : null);
        if (targetRouteId == null) {
            throw new IllegalStateException("Route is not assigned to this bus");
        }

        Trip trip = tripRepository.findFirstByBus_IdAndRoute_IdAndStatus(busId, targetRouteId, TripStatus.RUNNING)
                .or(() -> tripRepository.findFirstByBus_IdAndRoute_IdOrderByIdDesc(busId, targetRouteId))
                .orElseThrow(() -> new ResourceNotFoundException("No trip started yet for this route"));

        List<RouteStop> routeStops = routeStopRepository.findByRoute_IdOrderByStopOrderAsc(targetRouteId);
        if (routeStops.isEmpty()) {
            throw new ResourceNotFoundException("No stops found for this route");
        }

        Double currentLat = bus.getCurrentLatitude();
        Double currentLon = bus.getCurrentLongitude();

        BusTrackingResponseDTO response = new BusTrackingResponseDTO();
        response.setBusId(bus.getId());
        response.setBusNumber(bus.getBusNumber());
        response.setRouteId(trip.getRoute().getId());
        response.setRouteName(trip.getRoute().getName());
        response.setTripId(trip.getId());
        response.setTripStatus(trip.getStatus().name());
        response.setAbortReason(trip.getAbortReason());
        response.setSpeedKmh(bus.getAverageSpeedKmh());
        response.setBearing(bus.getCurrentBearing());
        response.setLastUpdated(bus.getLastUpdated());

        if (currentLat == null || currentLon == null) {
            Stop firstStop = routeStops.get(0).getStop();
            if (firstStop != null && firstStop.getLatitude() != null && firstStop.getLongitude() != null) {
                currentLat = firstStop.getLatitude();
                currentLon = firstStop.getLongitude();
            } else {
                response.setCurrentStop(routeStops.get(0).getStop().getName());
                response.setNextStop(routeStops.size() > 1
                        ? routeStops.get(1).getStop().getName()
                        : routeStops.get(0).getStop().getName());
                response.setDistanceToNextStopKm(null);
                response.setEstimatedMinutes(null);
                return response;
            }
        }

        Integer lastReached = trip.getLastReachedStopOrder();
        if (lastReached == null) {
            lastReached = 0;
        }

        Integer pending = trip.getPendingStopOrder();

        RouteStop currentRouteStop = null;
        RouteStop nextRouteStop = null;

        for (RouteStop routeStop : routeStops) {
            if (pending != null && routeStop.getStopOrder().equals(pending)) {
                currentRouteStop = routeStop;
            }
            if (pending == null && routeStop.getStopOrder() <= lastReached) {
                currentRouteStop = routeStop;
            }
            if (nextRouteStop == null
                    && routeStop.getStopOrder() > (pending != null ? pending : lastReached)) {
                nextRouteStop = routeStop;
            }
        }

        response.setCurrentLatitude(currentLat);
        response.setCurrentLongitude(currentLon);

        if (currentRouteStop != null) {
            response.setCurrentStop(currentRouteStop.getStop().getName());
        } else {
            response.setCurrentStop(routeStops.get(0).getStop().getName());
        }

        if (nextRouteStop == null || trip.getStatus() == TripStatus.COMPLETED || trip.getStatus() == TripStatus.ABORTED) {
            response.setNextStop(trip.getStatus() == TripStatus.ABORTED ? "Trip Aborted" : "No upcoming stop");
            response.setDistanceToNextStopKm(0.0);
            response.setEstimatedMinutes(0);
            return response;
        }

        Stop nextStop = nextRouteStop.getStop();
        double distance = speedCalculationService.calculateDistance(
                currentLat,
                currentLon,
                nextStop.getLatitude(),
                nextStop.getLongitude());

        response.setNextStop(nextStop.getName());
        response.setDistanceToNextStopKm(Math.round(distance * 100.0) / 100.0);

        double effectiveSpeed = 28.0;
        int minutes = Math.max(1, (int) Math.ceil(distance / effectiveSpeed * 60.0));
        response.setEstimatedMinutes(minutes);

        return response;
    }
}
