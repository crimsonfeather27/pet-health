package com.pethealth.controller;

import com.pethealth.dto.ApiResponse;
import com.pethealth.entity.PetProfile;
import com.pethealth.exception.AccessDeniedException;
import com.pethealth.interceptor.AuthContext;
import com.pethealth.repository.PetProfileRepository;
import com.pethealth.service.HealthRecordStatsService;
import com.pethealth.service.ReminderService;
import com.pethealth.service.dubbo.HealthRecordDubboService;
import com.pethealth.service.dubbo.PetDubboService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/pets")
@RequiredArgsConstructor
public class PetController {

    private final PetProfileRepository petProfileRepository;
    private final ReminderService reminderService;
    private final HealthRecordStatsService statsService;

    /**
     * Dubbo Consumer —— 直连 pet-service（端口 20881）
     * check=false：Provider 未启动时不会让 pethealth-web 启动失败
     * 运行期调用异常会走 try/catch 降级到本地 Repository
     */
    @DubboReference(
            url = "dubbo://localhost:20881/com.pethealth.service.dubbo.PetDubboService",
            check = false,
            timeout = 5000
    )
    private PetDubboService petDubboService;

    /**
     * Dubbo Consumer —— 直连 health-record-service（端口 20885），仅用于删除宠物时清统计缓存
     */
    @DubboReference(
            url = "dubbo://localhost:20885/com.pethealth.service.dubbo.HealthRecordDubboService",
            check = false,
            timeout = 5000
    )
    private HealthRecordDubboService healthRecordDubboService;

    @Value("${app.dubbo.enabled:true}")
    private boolean dubboEnabled;

    @GetMapping
    public ApiResponse<List<PetProfile>> list(@RequestParam(required = false) String ownerId) {
        if (dubboEnabled && ownerId != null && !ownerId.isBlank()) {
            try {
                return ApiResponse.success(petDubboService.findByOwnerId(ownerId));
            } catch (Exception e) {
                log.warn("Dubbo findByOwnerId 调用失败，降级本地: {}", e.getMessage());
            }
        }
        if (ownerId != null && !ownerId.isBlank()) {
            return ApiResponse.success(petProfileRepository.findByOwnerId(ownerId));
        }
        // 无 ownerId 时走 Dubbo findAll，保证与 create/update/delete 操作数据一致
        if (dubboEnabled) {
            try {
                return ApiResponse.success(petDubboService.findAll());
            } catch (Exception e) {
                log.warn("Dubbo findAll 调用失败，降级本地: {}", e.getMessage());
            }
        }
        return ApiResponse.success(petProfileRepository.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<PetProfile> get(@PathVariable String id) {
        if (dubboEnabled) {
            try {
                PetProfile pet = petDubboService.findById(id);
                if (pet != null) return ApiResponse.success(pet);
                return ApiResponse.error(404, "宠物档案不存在");
            } catch (Exception e) {
                log.warn("Dubbo findById 调用失败，降级本地: {}", e.getMessage());
            }
        }
        return petProfileRepository.findById(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error(404, "宠物档案不存在"));
    }

    @PostMapping
    public ApiResponse<PetProfile> create(@Valid @RequestBody PetProfile pet, HttpServletRequest request) {
        // ownerId 由服务端登录态注入，不信任客户端
        pet.setOwnerId(AuthContext.requireUserId(request));
        pet.setCreatedAt(LocalDateTime.now());
        pet.setUpdatedAt(LocalDateTime.now());

        PetProfile saved;
        if (dubboEnabled) {
            try {
                saved = petDubboService.create(pet);
            } catch (Exception e) {
                log.warn("Dubbo create 调用失败，降级本地: {}", e.getMessage());
                saved = petProfileRepository.save(pet);
            }
        } else {
            saved = petProfileRepository.save(pet);
        }

        // 自动为疫苗/驱虫记录创建到期提醒（pethealth-web 仍持有 ReminderService 的本地实现）
        try {
            List<?> reminders = reminderService.createRemindersForPet(saved);
            log.info("为宠物 {} 自动创建 {} 条提醒", saved.getName(), reminders.size());
        } catch (Exception e) {
            log.warn("自动创建提醒失败（非关键）: {}", e.getMessage());
        }

        return ApiResponse.success(saved);
    }

    @PutMapping("/{id}")
    public ApiResponse<PetProfile> update(@PathVariable String id, @RequestBody PetProfile updates,
                                          HttpServletRequest request) {
        checkPetOwner(id, request);
        if (dubboEnabled) {
            try {
                PetProfile updated = petDubboService.update(id, updates);
                if (updated != null) return ApiResponse.success(updated);
                return ApiResponse.error(404, "宠物档案不存在");
            } catch (Exception e) {
                log.warn("Dubbo update 调用失败，降级本地: {}", e.getMessage());
            }
        }
        return petProfileRepository.findById(id).map(existing -> {
            if (updates.getName() != null) existing.setName(updates.getName());
            if (updates.getSpecies() != null) existing.setSpecies(updates.getSpecies());
            if (updates.getBreed() != null) existing.setBreed(updates.getBreed());
            if (updates.getGender() != null) existing.setGender(updates.getGender());
            if (updates.getNeutered() != null) existing.setNeutered(updates.getNeutered());
            if (updates.getBirthday() != null) existing.setBirthday(updates.getBirthday());
            if (updates.getAvatar() != null) existing.setAvatar(updates.getAvatar());
            if (updates.getDescription() != null) existing.setDescription(updates.getDescription());
            if (updates.getVaccines() != null) existing.setVaccines(updates.getVaccines());
            if (updates.getDewormings() != null) existing.setDewormings(updates.getDewormings());
            if (updates.getCheckups() != null) existing.setCheckups(updates.getCheckups());
            if (updates.getMedicalVisits() != null) existing.setMedicalVisits(updates.getMedicalVisits());
            existing.setUpdatedAt(LocalDateTime.now());
            return ApiResponse.success(petProfileRepository.save(existing));
        }).orElse(ApiResponse.error(404, "宠物档案不存在"));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id, HttpServletRequest request) {
        checkPetOwner(id, request);
        if (dubboEnabled) {
            try {
                if (petDubboService.delete(id)) {
                    invalidatePetStats(id);
                    return ApiResponse.success("删除成功", null);
                }
                return ApiResponse.error(404, "宠物档案不存在");
            } catch (Exception e) {
                log.warn("Dubbo delete 调用失败，降级本地: {}", e.getMessage());
            }
        }
        if (!petProfileRepository.existsById(id)) {
            return ApiResponse.error(404, "宠物档案不存在");
        }
        petProfileRepository.deleteById(id);
        invalidatePetStats(id);
        return ApiResponse.success("删除成功", null);
    }

    /**
     * #12：宠物删除后清理其周/月统计缓存，避免残留孤儿键
     * （本地与 health-record-service 共用同一 Redis keyspace，双侧调用保证对称）
     */
    private void invalidatePetStats(String petId) {
        try {
            statsService.invalidateCache(petId);
        } catch (Exception e) {
            log.debug("本地统计缓存清理失败（非关键）: petId={}, {}", petId, e.getMessage());
        }
        if (dubboEnabled) {
            try {
                healthRecordDubboService.invalidateStats(petId);
            } catch (Exception e) {
                log.debug("Dubbo invalidateStats 失败（非关键）: petId={}, {}", petId, e.getMessage());
            }
        }
    }

    /** 属主校验：仅宠物主人可编辑/删除档案（Dubbo 调用前先校验，避免越权穿透） */
    private void checkPetOwner(String petId, HttpServletRequest request) {
        String userId = AuthContext.requireUserId(request);
        PetProfile pet = petProfileRepository.findById(petId).orElse(null);
        if (pet == null) {
            throw new com.pethealth.config.GlobalExceptionHandler.ResourceNotFoundException("宠物档案不存在: " + petId);
        }
        if (!userId.equals(pet.getOwnerId())) {
            throw new AccessDeniedException("无权操作他人宠物档案");
        }
    }
}
