/**
 * CG-DOC: Unit tests for the building, floor, and access-point CRUD in SpatialService.
 */
package com.circleguard.promotion.service;

import com.circleguard.promotion.model.AccessPoint;
import com.circleguard.promotion.model.Building;
import com.circleguard.promotion.model.Floor;
import com.circleguard.promotion.repository.jpa.AccessPointRepository;
import com.circleguard.promotion.repository.jpa.BuildingRepository;
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
class SpatialServiceTest {

    @Mock
    private BuildingRepository buildingRepository;
    @Mock
    private FloorRepository floorRepository;
    @Mock
    private AccessPointRepository accessPointRepository;

    private SpatialService service;

    @BeforeEach
    void setUp() {
        service = new SpatialService(buildingRepository, floorRepository, accessPointRepository);
    }

    @Test
    void createBuilding_persistsAndReturnsEntity() {
        Building saved = Building.builder().id(UUID.randomUUID()).name("A").code("A1").build();
        when(buildingRepository.save(any(Building.class))).thenReturn(saved);

        Building result = service.createBuilding("A", "A1", "desc");

        assertEquals(saved, result);
        verify(buildingRepository).save(argThat(b ->
                "A".equals(b.getName()) && "A1".equals(b.getCode()) && "desc".equals(b.getDescription())));
    }

    @Test
    void addFloor_persistsWhenBuildingExists() {
        UUID buildingId = UUID.randomUUID();
        Building building = Building.builder().id(buildingId).build();
        Floor saved = Floor.builder().id(UUID.randomUUID()).building(building).floorNumber(2).name("Second").build();
        when(buildingRepository.findById(buildingId)).thenReturn(Optional.of(building));
        when(floorRepository.save(any(Floor.class))).thenReturn(saved);

        Floor result = service.addFloor(buildingId, 2, "Second");

        assertEquals(saved, result);
        verify(floorRepository).save(argThat(f ->
                f.getBuilding() == building && f.getFloorNumber() == 2 && "Second".equals(f.getName())));
    }

    @Test
    void addFloor_throwsWhenBuildingDoesNotExist() {
        UUID buildingId = UUID.randomUUID();
        when(buildingRepository.findById(buildingId)).thenReturn(Optional.empty());

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.addFloor(buildingId, 1, "First"));

        assertEquals("Building not found", error.getMessage());
        verify(floorRepository, never()).save(any(Floor.class));
    }

    @Test
    void getAllBuildings_returnsRepositoryResult() {
        List<Building> buildings = List.of(Building.builder().id(UUID.randomUUID()).build());
        when(buildingRepository.findAll()).thenReturn(buildings);

        assertEquals(buildings, service.getAllBuildings());
    }

    @Test
    void getFloorsByBuilding_returnsRepositoryResult() {
        UUID buildingId = UUID.randomUUID();
        List<Floor> floors = List.of(Floor.builder().id(UUID.randomUUID()).build());
        when(floorRepository.findByBuildingId(buildingId)).thenReturn(floors);

        assertEquals(floors, service.getFloorsByBuilding(buildingId));
    }

    @Test
    void updateBuilding_updatesAllFields() {
        UUID id = UUID.randomUUID();
        Building building = Building.builder().id(id).name("Old").code("OLD").description("old").build();
        when(buildingRepository.findById(id)).thenReturn(Optional.of(building));
        when(buildingRepository.save(building)).thenReturn(building);

        Building result = service.updateBuilding(id, "New", "NEW", "new");

        assertEquals("New", result.getName());
        assertEquals("NEW", result.getCode());
        assertEquals("new", result.getDescription());
    }

    @Test
    void updateBuilding_throwsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(buildingRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> service.updateBuilding(id, "New", "NEW", "new"));
        verify(buildingRepository, never()).save(any(Building.class));
    }

    @Test
    void deleteBuilding_throwsWhenFloorsExist() {
        UUID id = UUID.randomUUID();
        when(floorRepository.findByBuildingId(id)).thenReturn(List.of(Floor.builder().id(UUID.randomUUID()).build()));

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.deleteBuilding(id));

        assertEquals("Cannot delete building with existing floors", error.getMessage());
        verify(buildingRepository, never()).deleteById(id);
    }

    @Test
    void deleteBuilding_deletesWhenNoFloorsExist() {
        UUID id = UUID.randomUUID();
        when(floorRepository.findByBuildingId(id)).thenReturn(List.of());

        service.deleteBuilding(id);

        verify(buildingRepository).deleteById(id);
    }

    @Test
    void updateFloor_updatesNumberAndName() {
        UUID id = UUID.randomUUID();
        Floor floor = Floor.builder().id(id).floorNumber(1).name("Old").build();
        when(floorRepository.findById(id)).thenReturn(Optional.of(floor));
        when(floorRepository.save(floor)).thenReturn(floor);

        Floor result = service.updateFloor(id, 3, "New");

        assertEquals(3, result.getFloorNumber());
        assertEquals("New", result.getName());
    }

    @Test
    void updateFloor_throwsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(floorRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.updateFloor(id, 3, "New"));
        verify(floorRepository, never()).save(any(Floor.class));
    }

    @Test
    void deleteFloor_throwsWhenAccessPointsExist() {
        UUID id = UUID.randomUUID();
        when(accessPointRepository.findByFloorId(id))
                .thenReturn(List.of(AccessPoint.builder().id(UUID.randomUUID()).build()));

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.deleteFloor(id));

        assertEquals("Cannot delete floor with existing access points", error.getMessage());
        verify(floorRepository, never()).deleteById(id);
    }

    @Test
    void deleteFloor_deletesWhenNoAccessPointsExist() {
        UUID id = UUID.randomUUID();
        when(accessPointRepository.findByFloorId(id)).thenReturn(List.of());

        service.deleteFloor(id);

        verify(floorRepository).deleteById(id);
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

        assertThrows(RuntimeException.class,
                () -> service.registerAccessPoint(floorId, "AA:BB", 1.0, 2.0, "AP"));
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
    void updateAccessPoint_updatesAllFields() {
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

        assertThrows(RuntimeException.class,
                () -> service.updateAccessPoint(id, "CC:DD", 3.0, 4.0, "New AP"));
        verify(accessPointRepository, never()).save(any(AccessPoint.class));
    }

    @Test
    void deleteAccessPoint_delegatesToRepository() {
        UUID id = UUID.randomUUID();

        service.deleteAccessPoint(id);

        verify(accessPointRepository).deleteById(id);
    }
}
