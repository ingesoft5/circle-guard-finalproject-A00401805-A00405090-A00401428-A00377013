package com.circleguard.promotion.controller;

import com.circleguard.promotion.model.AccessPoint;
import com.circleguard.promotion.model.Building;
import com.circleguard.promotion.model.Floor;
import com.circleguard.promotion.model.graph.CircleNode;
import com.circleguard.promotion.model.jpa.SystemSettings;
import com.circleguard.promotion.repository.graph.UserNodeRepository;
import com.circleguard.promotion.repository.jpa.SystemSettingsRepository;
import com.circleguard.promotion.service.AccessPointService;
import com.circleguard.promotion.service.AutoCircleService;
import com.circleguard.promotion.service.BuildingService;
import com.circleguard.promotion.service.CircleService;
import com.circleguard.promotion.service.FloorService;
import com.circleguard.promotion.service.HealthStatusService;
import com.circleguard.promotion.service.LocationResolutionService;
import com.circleguard.promotion.service.MacSessionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone web-layer coverage for every controller in promotion-service.
 * Uses plain Mockito doubles so the mapping logic is exercised without a Spring context.
 */
class ControllersStandaloneTest {

    private static final String ID = "11111111-1111-1111-1111-111111111111";

    private BuildingService buildingService;
    private FloorService floorService;
    private AccessPointService accessPointService;
    private CircleService circleService;
    private HealthStatusService healthStatusService;
    private LocationResolutionService locationResolutionService;
    private AutoCircleService autoCircleService;
    private MacSessionRegistry sessionRegistry;
    private SystemSettingsRepository settingsRepository;
    private UserNodeRepository userRepository;
    private Neo4jClient neo4jClient;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        buildingService = mock(BuildingService.class);
        floorService = mock(FloorService.class);
        accessPointService = mock(AccessPointService.class);
        circleService = mock(CircleService.class);
        healthStatusService = mock(HealthStatusService.class);
        locationResolutionService = mock(LocationResolutionService.class);
        autoCircleService = mock(AutoCircleService.class);
        sessionRegistry = mock(MacSessionRegistry.class);
        settingsRepository = mock(SystemSettingsRepository.class);
        userRepository = mock(UserNodeRepository.class);
        neo4jClient = mock(Neo4jClient.class);

        mockMvc = MockMvcBuilders.standaloneSetup(
                new BuildingController(buildingService, floorService),
                new CircleController(circleService),
                new AdminController(settingsRepository),
                new HealthStatsController(neo4jClient),
                new HealthStatusController(healthStatusService),
                new FloorController(floorService, accessPointService),
                new AccessPointController(accessPointService),
                new EncounterController(userRepository, autoCircleService),
                new MeshController(userRepository),
                new SessionHandshakeController(sessionRegistry),
                new LocationSignalController(locationResolutionService)
        ).build();
    }

    private Building building() {
        return Building.builder().id(UUID.fromString(ID)).name("Bloque A").code("A").build();
    }

    private Floor floor() {
        return Floor.builder().id(UUID.fromString(ID)).building(building()).floorNumber(1).name("Piso 1").build();
    }

    private AccessPoint accessPoint() {
        return AccessPoint.builder().id(UUID.fromString(ID)).macAddress("aa:bb").coordinateX(1.0).coordinateY(2.0).build();
    }

    @Test
    void createBuilding_returnsDto() throws Exception {
        when(buildingService.createBuilding(anyString(), anyString(), any(), any(), any(), any())).thenReturn(building());

        mockMvc.perform(post("/api/v1/buildings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bloque A\",\"code\":\"A\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("A"));
    }

    @Test
    void listBuildings_returnsList() throws Exception {
        when(buildingService.getAllBuildings()).thenReturn(List.of(building()));

        mockMvc.perform(get("/api/v1/buildings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Bloque A"));
    }

    @Test
    void getFloors_returnsList() throws Exception {
        when(floorService.getFloorsByBuilding(any())).thenReturn(List.of(floor()));

        mockMvc.perform(get("/api/v1/buildings/{id}/floors", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].floorNumber").value(1));
    }

    @Test
    void addFloor_returnsDto() throws Exception {
        when(floorService.addFloor(any(), any(), anyString())).thenReturn(floor());

        mockMvc.perform(post("/api/v1/buildings/{id}/floors", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"floorNumber\":1,\"name\":\"Piso 1\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void updateBuilding_returnsDto() throws Exception {
        when(buildingService.updateBuilding(any(), any(), any(), any(), any(), any(), any())).thenReturn(building());

        mockMvc.perform(put("/api/v1/buildings/{id}", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bloque A\",\"code\":\"A\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteBuilding_returnsOk() throws Exception {
        mockMvc.perform(delete("/api/v1/buildings/{id}", ID)).andExpect(status().isOk());
        verify(buildingService).deleteBuilding(UUID.fromString(ID));
    }

    @Test
    void createCircle_returnsNode() throws Exception {
        when(circleService.createCircle(anyString(), anyString()))
                .thenReturn(CircleNode.builder().id(1L).inviteCode("ABC").build());

        mockMvc.perform(post("/api/v1/circles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Familia\",\"locationId\":\"A-101\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inviteCode").value("ABC"));
    }

    @Test
    void joinCircle_returnsNode() throws Exception {
        when(circleService.joinCircle(anyString(), anyString())).thenReturn(CircleNode.builder().id(1L).build());

        mockMvc.perform(post("/api/v1/circles/join/{code}/user/{id}", "ABC", "user-1"))
                .andExpect(status().isOk());
    }

    @Test
    void addMember_returnsNode() throws Exception {
        when(circleService.addMember(anyLong(), anyString())).thenReturn(CircleNode.builder().id(1L).build());

        mockMvc.perform(post("/api/v1/circles/{id}/members/{a}", 1L, "user-1"))
                .andExpect(status().isOk());
    }

    @Test
    void getUserCircles_returnsList() throws Exception {
        when(circleService.getUserCircles(anyString())).thenReturn(List.of(CircleNode.builder().id(1L).build()));

        mockMvc.perform(get("/api/v1/circles/user/{id}", "user-1")).andExpect(status().isOk());
    }

    @Test
    void toggleCircleValidity_returnsOk() throws Exception {
        mockMvc.perform(patch("/api/v1/circles/{id}/validity", 1L)).andExpect(status().isOk());
        verify(circleService).toggleCircleValidity(1L);
    }

    @Test
    void forceFenceCircle_returnsOk() throws Exception {
        mockMvc.perform(post("/api/v1/circles/{id}/force-fence", 1L)).andExpect(status().isOk());
        verify(circleService).forceFenceCircle(1L);
    }

    @Test
    void getSettings_returnsSettings() throws Exception {
        when(settingsRepository.getSettings())
                .thenReturn(Optional.of(SystemSettings.builder().mandatoryFenceDays(14).build()));

        mockMvc.perform(get("/api/v1/admin/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mandatoryFenceDays").value(14));
    }

    @Test
    void getSettings_initializesDefaults() throws Exception {
        when(settingsRepository.getSettings()).thenReturn(Optional.empty());
        when(settingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(get("/api/v1/admin/settings")).andExpect(status().isOk());
        verify(settingsRepository).save(any());
    }

    @Test
    void updateSettings_appliesFields() throws Exception {
        when(settingsRepository.getSettings())
                .thenReturn(Optional.of(SystemSettings.builder().mandatoryFenceDays(14).build()));
        when(settingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/admin/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"encounterWindowDays\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encounterWindowDays").value(7));
    }

    @Test
    void toggleUnconfirmedFencing_returnsSettings() throws Exception {
        when(settingsRepository.getSettings())
                .thenReturn(Optional.of(SystemSettings.builder().unconfirmedFencingEnabled(true).build()));
        when(settingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/admin/settings/toggle-unconfirmed-fencing").param("enabled", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unconfirmedFencingEnabled").value(false));
    }

    @Test
    void getStats_aggregatesRows() throws Exception {
        Neo4jClient.UnboundRunnableSpec spec = mock(Neo4jClient.UnboundRunnableSpec.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        when(neo4jClient.query(anyString())).thenReturn(spec);
        when(spec.fetch().all()).thenReturn(List.of(Map.of("status", "CONFIRMED", "total", 3L)));

        mockMvc.perform(get("/api/v1/health-status/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedCount").value(3))
                .andExpect(jsonPath("$.totalUsers").value(3));
    }

    @Test
    void getStatsByDepartment_filtersRows() throws Exception {
        Neo4jClient.UnboundRunnableSpec spec = mock(Neo4jClient.UnboundRunnableSpec.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        when(neo4jClient.query(anyString())).thenReturn(spec);
        when(spec.bind(anyString()).to(anyString()).fetch().all())
                .thenReturn(List.of(Map.of("status", "SUSPECT", "total", 2L)));

        mockMvc.perform(get("/api/v1/health-status/stats/department/{d}", "Sistemas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suspectCount").value(2));
    }

    @Test
    void healthEndpoints_delegateToService() throws Exception {
        mockMvc.perform(post("/api/v1/health/confirmed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anonymousId\":\"user-1\"}"))
                .andExpect(status().isOk());
        verify(healthStatusService).updateStatus("user-1", "CONFIRMED");

        mockMvc.perform(post("/api/v1/health/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anonymousId\":\"user-1\",\"adminOverride\":true}"))
                .andExpect(status().isOk());
        verify(healthStatusService).resolveStatus("user-1", true);

        mockMvc.perform(post("/api/v1/health/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anonymousId\":\"user-2\",\"status\":\"SUSPECT\"}"))
                .andExpect(status().isOk());
        verify(healthStatusService).updateStatus("user-2", "SUSPECT", false);

        mockMvc.perform(post("/api/v1/health/recovery/{id}", "user-3")).andExpect(status().isOk());
        verify(healthStatusService).promoteToRecovered("user-3");
    }

    @Test
    void addAccessPoint_returnsDto() throws Exception {
        when(accessPointService.registerAccessPoint(any(), anyString(), any(), any(), any())).thenReturn(accessPoint());

        mockMvc.perform(post("/api/v1/floors/{id}/access-points", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"macAddress\":\"aa:bb\",\"coordinateX\":1.0,\"coordinateY\":2.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.macAddress").value("aa:bb"));
    }

    @Test
    void getAccessPoints_returnsList() throws Exception {
        when(accessPointService.getAccessPointsByFloor(any())).thenReturn(List.of(accessPoint()));

        mockMvc.perform(get("/api/v1/floors/{id}/access-points", ID)).andExpect(status().isOk());
    }

    @Test
    void updateFloor_returnsDto() throws Exception {
        when(floorService.updateFloor(any(), any(), anyString(), any())).thenReturn(floor());

        mockMvc.perform(put("/api/v1/floors/{id}", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"floorNumber\":1,\"name\":\"Piso 1\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteFloor_returnsOk() throws Exception {
        mockMvc.perform(delete("/api/v1/floors/{id}", ID)).andExpect(status().isOk());
        verify(floorService).deleteFloor(UUID.fromString(ID));
    }

    @Test
    void getAccessPoint_returnsDtoOr404() throws Exception {
        when(accessPointService.getAccessPoint(any())).thenReturn(Optional.of(accessPoint()));
        mockMvc.perform(get("/api/v1/access-points/{id}", ID)).andExpect(status().isOk());

        when(accessPointService.getAccessPoint(any())).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/v1/access-points/{id}", ID)).andExpect(status().isNotFound());
    }

    @Test
    void updateAccessPoint_returnsDto() throws Exception {
        when(accessPointService.updateAccessPoint(any(), anyString(), any(), any(), any())).thenReturn(accessPoint());

        mockMvc.perform(put("/api/v1/access-points/{id}", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"macAddress\":\"aa:bb\",\"coordinateX\":1.0,\"coordinateY\":2.0}"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteAccessPoint_returnsOk() throws Exception {
        mockMvc.perform(delete("/api/v1/access-points/{id}", ID)).andExpect(status().isOk());
        verify(accessPointService).deleteAccessPoint(UUID.fromString(ID));
    }

    @Test
    void reportEncounter_recordsAndEvaluates() throws Exception {
        mockMvc.perform(post("/api/v1/encounters/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceId\":\"a\",\"targetId\":\"b\",\"locationId\":\"A-101\"}"))
                .andExpect(status().isOk());

        verify(userRepository).recordEncounter(eq("a"), eq("b"), anyLong(), eq("A-101"));
        verify(autoCircleService).evaluateEncounter("a", "b");
    }

    @Test
    void encounterValidityEndpoints_delegate() throws Exception {
        mockMvc.perform(patch("/api/v1/encounters/{id}/validity", 7L)).andExpect(status().isOk());
        verify(userRepository).toggleEncounterValidity(7L);

        mockMvc.perform(post("/api/v1/encounters/{id}/force-fence", 7L)).andExpect(status().isOk());
        verify(userRepository).forceEncounterFence(7L);
    }

    @Test
    void getMeshStats_returnsCounts() throws Exception {
        when(userRepository.getConfirmedConnectionCount(anyString())).thenReturn(2L);
        when(userRepository.getUnconfirmedConnectionCount(anyString())).thenReturn(1L);

        mockMvc.perform(get("/api/v1/mesh/stats/{id}", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedCount").value(2))
                .andExpect(jsonPath("$.unconfirmedCount").value(1));
    }

    @Test
    void handshake_registersAndRejectsIncomplete() throws Exception {
        mockMvc.perform(post("/api/v1/sessions/handshake")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"macAddress\":\"aa:bb\",\"anonymousId\":\"user-1\"}"))
                .andExpect(status().isOk());
        verify(sessionRegistry).registerSession("aa:bb", "user-1");

        mockMvc.perform(post("/api/v1/sessions/handshake")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"macAddress\":\"aa:bb\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void closeSession_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/sessions/{mac}", "aa:bb")).andExpect(status().isNoContent());
        verify(sessionRegistry).closeSession("aa:bb");
    }

    @Test
    void receiveSignal_processesDetection() throws Exception {
        mockMvc.perform(post("/api/v1/location/signal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apMac\":\"ap-1\",\"deviceMac\":\"dev-1\",\"rssi\":-65}"))
                .andExpect(status().isOk());

        verify(locationResolutionService).processSignal("ap-1", "dev-1", -65.0);
    }
}
