/**
 * CG-DOC: Unit tests for the building CRUD in BuildingService.
 */
package com.circleguard.promotion.service;

import com.circleguard.promotion.model.Building;
import com.circleguard.promotion.model.Floor;
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
class BuildingServiceTest {

    @Mock
    private BuildingRepository buildingRepository;
    @Mock
    private FloorRepository floorRepository;

    private BuildingService service;

    @BeforeEach
    void setUp() {
        service = new BuildingService(buildingRepository, floorRepository);
    }

    @Test
    void createBuilding_persistsEveryField() {
        Building saved = Building.builder().id(UUID.randomUUID()).code("B1").build();
        when(buildingRepository.save(any(Building.class))).thenReturn(saved);

        Building result = service.createBuilding("Block B", "B1", "desc", 3.45, -76.53, "Calle 18");

        assertEquals(saved, result);
        verify(buildingRepository).save(argThat(b ->
                "Block B".equals(b.getName()) && "B1".equals(b.getCode())
                        && "desc".equals(b.getDescription())
                        && b.getLatitude() == 3.45 && b.getLongitude() == -76.53
                        && "Calle 18".equals(b.getAddress())));
    }

    @Test
    void getAllBuildings_returnsRepositoryResult() {
        List<Building> buildings = List.of(Building.builder().id(UUID.randomUUID()).build());
        when(buildingRepository.findAll()).thenReturn(buildings);

        assertEquals(buildings, service.getAllBuildings());
    }

    @Test
    void updateBuilding_overwritesAllFields() {
        UUID id = UUID.randomUUID();
        Building building = Building.builder().id(id).name("Old").code("OLD").build();
        when(buildingRepository.findById(id)).thenReturn(Optional.of(building));
        when(buildingRepository.save(building)).thenReturn(building);

        Building result = service.updateBuilding(id, "New", "NEW", "new", 1.0, 2.0, "addr");

        assertEquals("New", result.getName());
        assertEquals("NEW", result.getCode());
        assertEquals("new", result.getDescription());
        assertEquals(1.0, result.getLatitude());
        assertEquals(2.0, result.getLongitude());
        assertEquals("addr", result.getAddress());
    }

    @Test
    void updateBuilding_throwsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(buildingRepository.findById(id)).thenReturn(Optional.empty());

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.updateBuilding(id, "New", "NEW", "new", 1.0, 2.0, "addr"));

        assertEquals("Building not found", error.getMessage());
        verify(buildingRepository, never()).save(any(Building.class));
    }

    @Test
    void deleteBuilding_throwsWhenFloorsExist() {
        UUID id = UUID.randomUUID();
        when(floorRepository.findByBuildingId(id))
                .thenReturn(List.of(Floor.builder().id(UUID.randomUUID()).build()));

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.deleteBuilding(id));

        assertEquals("Cannot delete building with existing floors", error.getMessage());
        verify(buildingRepository, never()).deleteById(any(UUID.class));
    }

    @Test
    void deleteBuilding_deletesWhenEmpty() {
        UUID id = UUID.randomUUID();
        when(floorRepository.findByBuildingId(id)).thenReturn(List.of());

        service.deleteBuilding(id);

        verify(buildingRepository).deleteById(id);
    }
}
