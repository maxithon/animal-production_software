package rw.animalproduct.animal.production.services;

import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.entity.Location;
import rw.animalproduct.animal.production.repository.LocationRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class LocationService {

    private final LocationRepository locationRepository;

    public LocationService(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    /**
     * Get all locations
     */
    public List<Location> getAllLocations() {
        return locationRepository.findAll();
    }

    /**
     * Get location by ID
     */
    public Optional<Location> getLocationById(UUID id) {
        return locationRepository.findById(id);
    }

    /**
     * Get all locations by type (PROVINCE, DISTRICT, SECTOR, CELL, VILLAGE)
     */
    public List<Location> getLocationsByType(String locationType) {
        return locationRepository.findByLocationType(locationType);
    }

    /**
     * Get child locations by parent ID
     * This is used for hierarchical selection (Province -> Districts, etc.)
     */
    public List<Location> getChildLocations(UUID parentId) {
        return locationRepository.findChildrenByParentId(parentId);
    }

    /**
     * Save a location
     */
    public Location saveLocation(Location location) {
        return locationRepository.save(location);
    }

    /**
     * Update a location
     */
    public Location updateLocation(UUID id, Location updatedLocation) {
        Optional<Location> existingOpt = locationRepository.findById(id);
        if (existingOpt.isPresent()) {
            Location existing = existingOpt.get();
            existing.setName(updatedLocation.getName());
            existing.setLocationType(updatedLocation.getLocationType());
            existing.setCode(updatedLocation.getCode());
            existing.setRegulatorCode(updatedLocation.getRegulatorCode());
            existing.setParent(updatedLocation.getParent());
            existing.setState(updatedLocation.getState());
            existing.setComments(updatedLocation.getComments());
            return locationRepository.save(existing);
        }
        return null;
    }

    /**
     * Delete a location
     */
    public void deleteLocation(UUID id) {
        locationRepository.deleteById(id);
    }

    /**
     * Get provinces (top-level locations)
     */
    public List<Location> getProvinces() {
        return getLocationsByType("PROVINCE");
    }

    /**
     * Get districts for a province
     */
    public List<Location> getDistrictsByProvince(UUID provinceId) {
        return getChildLocations(provinceId);
    }

    /**
     * Get sectors for a district
     */
    public List<Location> getSectorsByDistrict(UUID districtId) {
        return getChildLocations(districtId);
    }

    /**
     * Get cells for a sector
     */
    public List<Location> getCellsBySector(UUID sectorId) {
        return getChildLocations(sectorId);
    }

    /**
     * Get villages for a cell
     */
    public List<Location> getVillagesByCell(UUID cellId) {
        return getChildLocations(cellId);
    }
}