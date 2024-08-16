package com.propertyseacher.api;

import com.propertyseacher.domain.entity.DiscoveredProperty;
import com.propertyseacher.domain.entity.SearchTarget;
import com.propertyseacher.domain.repository.DiscoveredPropertyRepository;
import com.propertyseacher.domain.repository.SearchTargetRepository;
import com.propertyseacher.service.TrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PropertyController {

    private final DiscoveredPropertyRepository discoveredPropertyRepository;
    private final SearchTargetRepository searchTargetRepository;
    private final TrackingService trackingService;

    @GetMapping("/properties")
    public ResponseEntity<Page<DiscoveredProperty>> getProperties(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "discoveredAt"));
        return ResponseEntity.ok(discoveredPropertyRepository.findAll(pageRequest));
    }

    @GetMapping("/targets")
    public ResponseEntity<List<SearchTarget>> getTargets() {
        return ResponseEntity.ok(searchTargetRepository.findAll());
    }

    @PostMapping("/targets")
    public ResponseEntity<SearchTarget> createTarget(@RequestBody SearchTarget target) {
        target.setActive(true);
        SearchTarget saved = searchTargetRepository.save(target);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/track/trigger")
    public ResponseEntity<String> triggerManualScan() {
        new Thread(trackingService::trackAllTargets).start();
        return ResponseEntity.ok("Tracking cycle triggered manually in background.");
    }
}
