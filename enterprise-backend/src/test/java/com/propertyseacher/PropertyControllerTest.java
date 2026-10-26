package com.propertyseacher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.propertyseacher.api.PropertyController;
import com.propertyseacher.api.dto.CreateTargetRequest;
import com.propertyseacher.domain.entity.SearchTarget;
import com.propertyseacher.domain.repository.DiscoveredPropertyRepository;
import com.propertyseacher.domain.repository.PriceHistoryRepository;
import com.propertyseacher.domain.repository.SearchTargetRepository;
import com.propertyseacher.service.TrackingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PropertyController.class)
class PropertyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DiscoveredPropertyRepository discoveredPropertyRepository;

    @MockBean
    private SearchTargetRepository searchTargetRepository;

    @MockBean
    private PriceHistoryRepository priceHistoryRepository;

    @MockBean
    private TrackingService trackingService;

    @Test
    @DisplayName("GET /api/v1/targets returns 200 with list of targets")
    void shouldReturnTargetsList() throws Exception {
        SearchTarget target = SearchTarget.builder()
                .id(1L)
                .title("Одеса Квартири")
                .platform("OLX")
                .url("https://www.olx.ua/odessa")
                .active(true)
                .build();

        when(searchTargetRepository.findAll()).thenReturn(List.of(target));

        mockMvc.perform(get("/api/v1/targets"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].title").value("Одеса Квартири"))
                .andExpect(jsonPath("$[0].platform").value("OLX"));
    }

    @Test
    @DisplayName("POST /api/v1/targets with valid payload returns 201 Created")
    void shouldCreateTargetWhenValid() throws Exception {
        CreateTargetRequest request = new CreateTargetRequest(
                "Київ Будинки",
                "DOM.RIA",
                "https://dom.ria.com/uk/prodazha-domov/kiev"
        );

        SearchTarget saved = SearchTarget.builder()
                .id(2L)
                .title(request.title())
                .platform(request.platform())
                .url(request.url())
                .active(true)
                .build();

        when(searchTargetRepository.save(any(SearchTarget.class))).thenReturn(saved);

        mockMvc.perform(post("/api/v1/targets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.title").value("Київ Будинки"));
    }

    @Test
    @DisplayName("POST /api/v1/targets with invalid URL returns 400 Bad Request via GlobalExceptionHandler")
    void shouldReturnBadRequestWhenInvalidUrl() throws Exception {
        CreateTargetRequest invalid = new CreateTargetRequest(
                "Broken Target",
                "OLX",
                "not-a-valid-url"
        );

        mockMvc.perform(post("/api/v1/targets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed for incoming payload"));
    }

    @Test
    @DisplayName("GET /api/v1/analytics/summary returns aggregated market telemetry")
    void shouldReturnAnalyticsSummary() throws Exception {
        when(searchTargetRepository.countByActiveTrue()).thenReturn(3L);
        when(discoveredPropertyRepository.count()).thenReturn(150L);
        when(priceHistoryRepository.count()).thenReturn(25L);
        when(discoveredPropertyRepository.countByPlatform("OLX")).thenReturn(90L);
        when(discoveredPropertyRepository.countByPlatform("DOM.RIA")).thenReturn(60L);
        when(trackingService.getCurrentMode()).thenReturn("LIVE_SNIPE");

        mockMvc.perform(get("/api/v1/analytics/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalActiveTargets").value(3))
                .andExpect(jsonPath("$.totalPropertiesTracked").value(150))
                .andExpect(jsonPath("$.totalPriceChangeEvents").value(25))
                .andExpect(jsonPath("$.activeTrackingMode").value("LIVE_SNIPE"));
    }

    @Test
    @DisplayName("POST /api/v1/track/trigger accepts manual scan with 202 Accepted")
    void shouldTriggerManualScanAsync() throws Exception {
        when(trackingService.getCurrentMode()).thenReturn("LIVE_SNIPE");

        mockMvc.perform(post("/api/v1/track/trigger"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }
}
