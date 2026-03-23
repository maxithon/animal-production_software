package rw.animalproduct.animal.production.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import rw.animalproduct.animal.production.entity.Location;
import rw.animalproduct.animal.production.services.LocationService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/locations")
public class LocationController {

    private final LocationService locationService;

    @Autowired
    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    /**
     * Get all provinces
     */
    @GetMapping("/provinces")
    public ResponseEntity<List<Map<String, Object>>> getProvinces() {
        List<Location> provinces = locationService.getLocationsByType("PROVINCE");
        return ResponseEntity.ok(convertToMap(provinces));
    }

    /**
     * Get districts by province ID
     */
    @GetMapping("/districts/{provinceId}")
    public ResponseEntity<List<Map<String, Object>>> getDistricts(@PathVariable UUID provinceId) {
        List<Location> districts = locationService.getChildLocations(provinceId);
        return ResponseEntity.ok(convertToMap(districts));
    }

    /**
     * Get sectors by district ID
     */
    @GetMapping("/sectors/{districtId}")
    public ResponseEntity<List<Map<String, Object>>> getSectors(@PathVariable UUID districtId) {
        List<Location> sectors = locationService.getChildLocations(districtId);
        return ResponseEntity.ok(convertToMap(sectors));
    }

    /**
     * Get cells by sector ID
     */
    @GetMapping("/cells/{sectorId}")
    public ResponseEntity<List<Map<String, Object>>> getCells(@PathVariable UUID sectorId) {
        List<Location> cells = locationService.getChildLocations(sectorId);
        return ResponseEntity.ok(convertToMap(cells));
    }

    /**
     * Get villages by cell ID
     */
    @GetMapping("/villages/{cellId}")
    public ResponseEntity<List<Map<String, Object>>> getVillages(@PathVariable UUID cellId) {
        List<Location> villages = locationService.getChildLocations(cellId);
        return ResponseEntity.ok(convertToMap(villages));
    }

    /**
     * Get location details by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getLocationById(@PathVariable UUID id) {
        var location = locationService.getLocationById(id);
        if (location.isPresent()) {
            Location loc = location.get();
            Map<String, Object> map = new HashMap<>();
            map.put("id", loc.getId().toString());
            map.put("name", loc.getName());
            map.put("type", loc.getLocationType());
            map.put("code", loc.getCode());
            if (loc.getParent() != null) {
                map.put("parentId", loc.getParent().getId().toString());
                map.put("parentName", loc.getParent().getName());
            }
            return ResponseEntity.ok(map);
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Helper method to convert Location list to Map
     */
    private List<Map<String, Object>> convertToMap(List<Location> locations) {
        return locations.stream().map(loc -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", loc.getId().toString());
            map.put("name", loc.getName());
            map.put("type", loc.getLocationType());
            map.put("code", loc.getCode());
            return map;
        }).collect(Collectors.toList());
    }
}