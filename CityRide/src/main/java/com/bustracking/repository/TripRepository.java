package com.bustracking.repository;

import com.bustracking.entity.Trip;
import com.bustracking.enums.TripStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.List;

public interface TripRepository extends JpaRepository<Trip,Long> {

    Optional<Trip> findByBusIdAndStatus(Long busId, String status);

    boolean existsByBus_IdAndStatusIn(Long busId, Collection<TripStatus> statuses);

    boolean existsByBus_IdAndStatus(Long busId, TripStatus status);

    Optional<Trip> findFirstByBus_IdAndStatus(Long busId, TripStatus status);

    Optional<Trip> findFirstByBus_IdOrderByIdDesc(Long busId);

    boolean existsByDriver_IdAndStatusIn(Long driverId, Collection<TripStatus> statuses);

    List<Trip> findByDriver_PhoneOrderByIdDesc(String phone);

    Optional<Trip> findFirstByDriver_PhoneAndStatus(String phone, TripStatus status);

    Optional<Trip> findFirstByBus_IdAndRoute_IdAndStatus(Long busId, Long routeId, TripStatus status);

    Optional<Trip> findFirstByBus_IdAndRoute_IdOrderByIdDesc(Long busId, Long routeId);
}
