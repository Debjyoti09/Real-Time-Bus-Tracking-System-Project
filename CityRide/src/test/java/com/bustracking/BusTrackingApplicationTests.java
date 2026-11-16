package com.bustracking;

import com.bustracking.dto.BusResponseDTO;
import com.bustracking.entity.Bus;
import com.bustracking.entity.Driver;
import com.bustracking.repository.DriverRepository;
import com.bustracking.security.DatabaseUserDetailsService;
import com.bustracking.service.SpeedCalculationService;
import com.bustracking.websocket.BusLocationWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class BusTrackingApplicationTests {

    @Autowired
    private BusLocationWebSocketHandler locationHandler;

    @Autowired
    private SpeedCalculationService speedCalculationService;

    @Test
    void contextLoads() {
    }

    @Test
    void broadcastsBusLocationAsJson() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("test-location-session");
        when(session.isOpen()).thenReturn(true);
        locationHandler.afterConnectionEstablished(session);

        BusResponseDTO location = new BusResponseDTO();
        location.setId(42L);
        location.setBusNumber("BUS-42");
        location.setCurrentLatitude(22.5);
        location.setCurrentLongitude(88.3);

        locationHandler.broadcast(location);

        var message = forClass(TextMessage.class);
        verify(session).sendMessage(message.capture());
        assertTrue(message.getValue().getPayload().contains("\"id\":42"));
        assertTrue(message.getValue().getPayload().contains("\"currentLatitude\":22.5"));
        locationHandler.afterConnectionClosed(session, CloseStatus.NORMAL);
    }

    @Test
    void preEncodedAdminAndDriverAuthenticationWorksFast() {
        DriverRepository repo = mock(DriverRepository.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        DatabaseUserDetailsService service = new DatabaseUserDetailsService(
                repo, encoder, "admin", "Admin@12345");

        UserDetails admin = service.loadUserByUsername("admin");
        assertEquals("admin", admin.getUsername());
        assertTrue(encoder.matches("Admin@12345", admin.getPassword()));

        Driver driver = new Driver();
        driver.setPhone("9876543210");
        driver.setPasswordHash(encoder.encode("DriverPass1"));
        when(repo.findByPhone("9876543210")).thenReturn(Optional.of(driver));

        UserDetails driverDetails = service.loadUserByUsername("9876543210");
        assertEquals("9876543210", driverDetails.getUsername());
        assertTrue(encoder.matches("DriverPass1", driverDetails.getPassword()));
    }

    @Test
    void speedCalculationServiceComputesDistanceAndSpeed() {
        Bus bus = new Bus();
        LocalDateTime t0 = LocalDateTime.now().minusSeconds(60);
        bus.setCurrentLatitude(22.5726);
        bus.setCurrentLongitude(88.3639);
        bus.setLastUpdated(t0.toString());

        Double speed = speedCalculationService.calculateAverageSpeed(bus, 22.5776, 88.3689, t0.plusSeconds(60));
        assertNotNull(speed);
        assertTrue(speed > 0.0 && speed <= 100.0);
    }
}
