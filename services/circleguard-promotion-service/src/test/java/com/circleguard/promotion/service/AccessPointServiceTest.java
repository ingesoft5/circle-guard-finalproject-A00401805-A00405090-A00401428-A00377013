/**
 * CG-DOC: Unit tests for the access-point CRUD in AccessPointService.
 */
package com.circleguard.promotion.service;

import com.circleguard.promotion.model.AccessPoint;
import com.circleguard.promotion.model.Floor;
import com.circleguard.promotion.repository.jpa.AccessPointRepository;
import com.circleguard.promotion.repository.jpa.FloorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessPointServiceTest {

    @Mock
    private AccessPointRepository accessPointRepository;
    @Mock
    private FloorRepository floorRepository;

    private AccessPointService service;

    @BeforeEach
    void setUp() {
        service = new AccessPointService(accessPointRepository, floorRepository);
    }

    @Test
    void registerAccessPoint_persistsWhenFloorExists() {
        UUID floorId = UUID.randomUUID();
        Floor floor = Floor.builder().id(floorId).build();
        AccessPoint saved = AccessPoint.builder().id(UUID.randomUUID()).floor(floor).build();
        when(floorRepository.findById(floorId)).thenReturn(Optional.of(floor));
        when(accessPointRepository.save(any(AccessPoint.class))).thenReturn(saved);

        AccessPoint result = service.registerAccessPoint(floorId, "AA:BB", 1.0, 2.0, "AP");

        assertEquals(saved, result);
        verify(accessPointRepository).save(argThat(ap ->
                ap.getFloor() == floor && "AA:BB".equals(ap.getMacAddress())
                        && ap.getCoordinateX() == 1.0 && ap.getCoordinateY() == 2.0
                        && "AP".equals(ap.getName())));
    }

    @Test
    void registerAccessPoint_throwsWhenFloorMissing() {
        UUID floorId = UUID.randomUUID();
        when(floorRepository.findById(floorId)).thenReturn(Optional.empty());

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.registerAccessPoint(floorId, "AA:BB", 1.0, 2.0, "AP"));

        assertEquals("Floor not found", error.getMessage());
        verify(accessPointRepository, never()).save(any(AccessPoint.class));
    }

    @Test
    void getAccessPoint_delegatesToRepository() {
        UUID id = UUID.randomUUID();
        AccessPoint ap = AccessPoint.builder().id(id).build();
        when(accessPointRepository.findById(id)).thenReturn(Optional.of(ap));

        assertEquals(Optional.of(ap), service.getAccessPoint(id));
    }

    @Test
    void getAccessPointsByFloor_delegatesToRepository() {
        UUID floorId = UUID.randomUUID();
        List<AccessPoint> accessPoints = List.of(AccessPoint.builder().id(UUID.randomUUID()).build());
        when(accessPointRepository.findByFloorId(floorId)).thenReturn(accessPoints);

        assertEquals(accessPoints, service.getAccessPointsByFloor(floorId));
    }

    @Test
    void updateAccessPoint_overwritesAllFields() {
        UUID id = UUID.randomUUID();
        AccessPoint ap = AccessPoint.builder().id(id).build();
        when(accessPointRepository.findById(id)).thenReturn(Optional.of(ap));
        when(accessPointRepository.save(ap)).thenReturn(ap);

        AccessPoint result = service.updateAccessPoint(id, "CC:DD", 3.0, 4.0, "New AP");

        assertEquals("CC:DD", result.getMacAddress());
        assertEquals(3.0, result.getCoordinateX());
        assertEquals(4.0, result.getCoordinateY());
        assertEquals("New AP", result.getName());
    }

    @Test
    void updateAccessPoint_throwsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(accessPointRepository.findById(id)).thenReturn(Optional.empty());

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.updateAccessPoint(id, "CC:DD", 3.0, 4.0, "New AP"));

        assertEquals("Access Point not found", error.getMessage());
        verify(accessPointRepository, never()).save(any(AccessPoint.class));
    }

    @Test
    void deleteAccessPoint_delegatesToRepository() {
        UUID id = UUID.randomUUID();

        service.deleteAccessPoint(id);

        verify(accessPointRepository).deleteById(id);
    }
}
