package rw.animalproduct.animal.production.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.dto.ModuleAssignmentRow;
import rw.animalproduct.animal.production.entity.Module;
import rw.animalproduct.animal.production.entity.UserTypeModule;
import rw.animalproduct.animal.production.entity.UsersType;
import rw.animalproduct.animal.production.repository.ModuleRepository;
import rw.animalproduct.animal.production.repository.UserTypeModuleRepository;
import rw.animalproduct.animal.production.repository.UsersTypeRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ModuleAssignmentService {

    private final ModuleRepository moduleRepository;
    private final UserTypeModuleRepository userTypeModuleRepository;
    private final UsersTypeRepository usersTypeRepository;

    public ModuleAssignmentService(ModuleRepository moduleRepository,
                                    UserTypeModuleRepository userTypeModuleRepository,
                                    UsersTypeRepository usersTypeRepository) {
        this.moduleRepository = moduleRepository;
        this.userTypeModuleRepository = userTypeModuleRepository;
        this.usersTypeRepository = usersTypeRepository;
    }

    public List<Module> getAllModulesOrdered() {
        return moduleRepository.findByActiveTrueOrderByDisplayOrderAsc();
    }

    public List<UsersType> getAllUserTypes() {
        return usersTypeRepository.findAll();
    }

    /**
     * Every module, joined against what this user type currently has.
     * Modules with no matching row simply come back with all four flags
     * false (i.e. "not assigned").
     */
    public List<ModuleAssignmentRow> getAssignmentMatrix(UUID userTypeId) {
        List<UserTypeModule> assigned = userTypeModuleRepository.findByUserTypeId(userTypeId);
        Map<Integer, UserTypeModule> byModule = assigned.stream()
                .collect(Collectors.toMap(UserTypeModule::getModuleId, m -> m, (a, b) -> a));

        List<ModuleAssignmentRow> rows = new ArrayList<>();
        for (Module module : getAllModulesOrdered()) {
            UserTypeModule utm = byModule.get(module.getModuleId());
            ModuleAssignmentRow row = new ModuleAssignmentRow();
            row.setModuleId(module.getModuleId());
            row.setModuleName(module.getModuleName());
            row.setModuleCode(module.getModuleCode());
            row.setParentModuleId(module.getParentModuleId());
            row.setIcon(module.getIcon());
            row.setDisplayOrder(module.getDisplayOrder());
            row.setCanView(utm != null && utm.isCanView());
            row.setCanCreate(utm != null && utm.isCanCreate());
            row.setCanEdit(utm != null && utm.isCanEdit());
            row.setCanDelete(utm != null && utm.isCanDelete());
            rows.add(row);
        }
        return rows;
    }

    /**
     * Saves the whole matrix for one user type in one go (upsert per row;
     * rows with every flag unchecked are deleted so stale grants don't
     * linger in user_type_modules).
     */
    @Transactional
    public void saveAssignmentMatrix(UUID userTypeId, List<ModuleAssignmentRow> rows, UUID assignedBy) {
        for (ModuleAssignmentRow row : rows) {
            boolean anyPermission = row.isCanView() || row.isCanCreate() || row.isCanEdit() || row.isCanDelete();
            Optional<UserTypeModule> existing =
                    userTypeModuleRepository.findFirstByUserTypeIdAndModuleId(userTypeId, row.getModuleId());

            if (!anyPermission) {
                existing.ifPresent(userTypeModuleRepository::delete);
                continue;
            }

            UserTypeModule utm = existing.orElseGet(UserTypeModule::new);
            utm.setUserTypeId(userTypeId);
            utm.setModuleId(row.getModuleId());
            utm.setCanView(row.isCanView());
            utm.setCanCreate(row.isCanCreate());
            utm.setCanEdit(row.isCanEdit());
            utm.setCanDelete(row.isCanDelete());
            utm.setAssignedAt(LocalDateTime.now());
            utm.setAssignedBy(assignedBy);
            userTypeModuleRepository.save(utm);
        }
    }
}
