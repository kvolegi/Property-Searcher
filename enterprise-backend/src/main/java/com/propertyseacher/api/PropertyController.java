package com.propertyseacher.api;

import com.propertyseacher.api.dto.AnalyticsSummaryDto;
import com.propertyseacher.api.dto.CreateTargetRequest;
import com.propertyseacher.api.dto.ErrorResponse;
import com.propertyseacher.domain.entity.DiscoveredProperty;
import com.propertyseacher.domain.entity.PriceHistory;
import com.propertyseacher.domain.entity.SearchTarget;
import com.propertyseacher.domain.repository.DiscoveredPropertyRepository;
import com.propertyseacher.domain.repository.PriceHistoryRepository;
import com.propertyseacher.domain.repository.SearchTargetRepository;
import com.propertyseacher.service.TrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Real Estate Intelligence API", description = "Endpoints for managing targets, querying indexed listings, tracking price history, and inspecting telemetry")
public class PropertyController {

    private final DiscoveredPropertyRepository discoveredPropertyRepository;
    private final SearchTargetRepository searchTargetRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final TrackingService trackingService;

    @Operation(summary = "Get paginated property listings", description = "Retrieves discovered real estate listings sorted by discovery timestamp in descending order.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved listings page"),
            @ApiResponse(responseCode = "400", description = "Invalid pagination parameters", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/properties")
    public ResponseEntity<Page<DiscoveredProperty>> getProperties(
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size capacity") @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be >= 0 and size between 1 and 100");
        }
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "discoveredAt"));
        return ResponseEntity.ok(discoveredPropertyRepository.findAll(pageRequest));
    }

    @Operation(summary = "Get property price change history", description = "Fetches all recorded price mutations for a specific property identified by its database ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historical prices retrieved"),
            @ApiResponse(responseCode = "404", description = "Property ID not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/properties/{id}/history")
    public ResponseEntity<List<PriceHistory>> getPriceHistory(
            @Parameter(description = "Internal database property ID") @PathVariable Long id) {
        if (!discoveredPropertyRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Property with ID " + id + " does not exist");
        }
        return ResponseEntity.ok(priceHistoryRepository.findByPropertyIdOrderByRecordedAtAsc(id));
    }

    @Operation(summary = "List all search targets", description = "Returns all configured monitoring targets across platforms.")
    @ApiResponse(responseCode = "200", description = "List of search targets")
    @GetMapping("/targets")
    public ResponseEntity<List<SearchTarget>> getTargets() {
        return ResponseEntity.ok(searchTargetRepository.findAll());
    }

    @Operation(summary = "Create a new monitoring target", description = "Validates and persists a new search target URL for background sniping and indexing.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Target successfully created"),
            @ApiResponse(responseCode = "400", description = "Invalid payload or non-URL format", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/targets")
    public ResponseEntity<SearchTarget> createTarget(@Valid @RequestBody CreateTargetRequest req) {
        SearchTarget target = SearchTarget.builder()
                .title(req.title().trim())
                .platform(req.platform().toUpperCase().trim())
                .url(req.url().trim())
                .active(true)
                .build();

        SearchTarget saved = searchTargetRepository.save(target);
        log.info("Created new search target ID {}: [{}] {}", saved.getId(), saved.getPlatform(), saved.getTitle());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @Operation(summary = "Toggle target active state", description = "Enables or pauses monitoring for a specific target ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Target state toggled"),
            @ApiResponse(responseCode = "404", description = "Target not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/targets/{id}/toggle")
    public ResponseEntity<SearchTarget> toggleTarget(
            @Parameter(description = "Target ID to toggle") @PathVariable Long id) {
        SearchTarget target = searchTargetRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target ID " + id + " not found"));

        target.setActive(!target.isActive());
        SearchTarget updated = searchTargetRepository.save(target);
        log.info("Toggled target ID {} active status to: {}", id, updated.isActive());
        return ResponseEntity.ok(updated);
    }

    @Operation(summary = "Delete a search target", description = "Removes a monitoring target from the background tracking schedule.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Target deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Target not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/targets/{id}")
    public ResponseEntity<Void> deleteTarget(
            @Parameter(description = "Target ID to delete") @PathVariable Long id) {
        if (!searchTargetRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Target ID " + id + " not found");
        }
        searchTargetRepository.deleteById(id);
        log.info("Deleted target ID {}", id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Market telemetry & analytics summary", description = "Returns aggregated statistics including total targets, indexed properties, and platform breakdown.")
    @ApiResponse(responseCode = "200", description = "Telemetry summary")
    @GetMapping("/analytics/summary")
    public ResponseEntity<AnalyticsSummaryDto> getAnalyticsSummary() {
        long activeTargets = searchTargetRepository.countByActiveTrue();
        long totalProperties = discoveredPropertyRepository.count();
        long totalPriceChanges = priceHistoryRepository.count();

        Map<String, Long> byPlatform = new HashMap<>();
        byPlatform.put("OLX", discoveredPropertyRepository.countByPlatform("OLX"));
        byPlatform.put("DOM.RIA", discoveredPropertyRepository.countByPlatform("DOM.RIA"));

        AnalyticsSummaryDto summary = new AnalyticsSummaryDto(
                activeTargets,
                totalProperties,
                totalPriceChanges,
                byPlatform,
                trackingService.getCurrentMode()
        );
        return ResponseEntity.ok(summary);
    }

    @Operation(summary = "Trigger asynchronous scan cycle", description = "Offloads a complete monitoring scan to background worker threads.")
    @ApiResponse(responseCode = "202", description = "Scan cycle accepted and processing in background")
    @PostMapping("/track/trigger")
    public ResponseEntity<Map<String, String>> triggerManualScan() {
        CompletableFuture.runAsync(trackingService::trackAllTargets);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "status", "ACCEPTED",
                "message", "Tracking cycle offloaded to background executor.",
                "mode", trackingService.getCurrentMode()
        ));
    }
}
