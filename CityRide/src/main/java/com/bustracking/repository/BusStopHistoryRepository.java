package com.bustracking.repository;

import com.bustracking.entity.BusStopHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BusStopHistoryRepository extends JpaRepository<BusStopHistory,Long> {

    boolean existsByBusIdAndStopIdAndTripId(Long busId, Long stopId, Long tripId);

    Optional<BusStopHistory> findByBusIdAndStopIdAndTripId(Long busId, Long stopId, Long tripId);
}
