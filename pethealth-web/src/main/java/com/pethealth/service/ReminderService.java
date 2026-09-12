package com.pethealth.service;

import com.pethealth.entity.PetProfile;
import com.pethealth.entity.Reminder;
import com.pethealth.exception.AccessDeniedException;
import com.pethealth.repository.PetProfileRepository;
import com.pethealth.repository.ReminderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 提醒服务
 * <p>
 * 到期触发由 ReminderScheduler 定时扫描完成（个人项目站内提醒，无需外部推送）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderService {

    private final ReminderRepository reminderRepository;
    private final PetProfileRepository petProfileRepository;

    /**
     * 创建提醒
     */
    public Reminder create(Reminder reminder) {
        if (reminder.getStatus() == null) reminder.setStatus("PENDING");
        if (reminder.getCreatedAt() == null) reminder.setCreatedAt(LocalDateTime.now());
        reminder.setUpdatedAt(LocalDateTime.now());

        // 补充宠物名和邮箱
        if (reminder.getPetId() != null) {
            petProfileRepository.findById(reminder.getPetId()).ifPresent(pet -> {
                if (reminder.getPetName() == null) reminder.setPetName(pet.getName());
                if (reminder.getOwnerId() == null) reminder.setOwnerId(pet.getOwnerId());
            });
        }
        Reminder saved = reminderRepository.save(reminder);
        log.info("创建提醒: id={}, title={}, remindAt={}", saved.getId(), saved.getTitle(), saved.getRemindAt());

        return saved;
    }

    /**
     * 为宠物的疫苗/驱虫记录自动创建到期提醒（advanceDays 天后到期）
     */
    public List<Reminder> createRemindersForPet(PetProfile pet) {
        List<Reminder> created = new ArrayList<>();

        // 疫苗提醒
        if (pet.getVaccines() != null) {
            pet.getVaccines().forEach(vaccine -> {
                if (vaccine.getNextDueAt() != null) {
                    LocalDateTime remindAt = vaccine.getNextDueAt().atStartOfDay().minusDays(7);
                    if (remindAt.isAfter(LocalDateTime.now())) {
                        Reminder r = Reminder.builder()
                                .ownerId(pet.getOwnerId())
                                .petId(pet.getId())
                                .petName(pet.getName())
                                .type("VACCINE")
                                .title(vaccine.getName() + " 即将到期")
                                .description(pet.getName() + " 的 " + vaccine.getName() + " 将于 " + vaccine.getNextDueAt() + " 到期，请尽快接种。")
                                .remindAt(remindAt)
                                .advanceDays(7)
                                .status("PENDING")
                                .notifyMethod(List.of("INAPP"))
                                .sourceRef(java.util.Map.of("collection", "pet_profiles", "vaccineName", vaccine.getName()))
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();
                        created.add(create(r));
                    }
                }
            });
        }

        // 驱虫提醒
        if (pet.getDewormings() != null) {
            pet.getDewormings().forEach(deworming -> {
                if (deworming.getNextDueAt() != null) {
                    LocalDateTime remindAt = deworming.getNextDueAt().atStartOfDay().minusDays(3);
                    if (remindAt.isAfter(LocalDateTime.now())) {
                        Reminder r = Reminder.builder()
                                .ownerId(pet.getOwnerId())
                                .petId(pet.getId())
                                .petName(pet.getName())
                                .type("DEWORMING")
                                .title(deworming.getType() + " 即将到期")
                                .description(pet.getName() + " 的 " + deworming.getType() + "（" + deworming.getMedicine() + "）将于 " + deworming.getNextDueAt() + " 到期。")
                                .remindAt(remindAt)
                                .advanceDays(3)
                                .status("PENDING")
                                .notifyMethod(List.of("INAPP"))
                                .sourceRef(java.util.Map.of("collection", "pet_profiles", "dewormingType", deworming.getType()))
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();
                        created.add(create(r));
                    }
                }
            });
        }

        log.info("为宠物 {} 自动创建 {} 条提醒", pet.getName(), created.size());
        return created;
    }

    /**
     * 查询某用户的提醒
     */
    public List<Reminder> findByOwner(String ownerId) {
        return reminderRepository.findByOwnerId(ownerId);
    }

    /**
     * 查询某宠物的提醒
     */
    public List<Reminder> findByPet(String petId) {
        return reminderRepository.findByPetId(petId);
    }

    /**
     * 查询即将到期的提醒（未来 N 天内）
     */
    public List<Reminder> findDueWithin(int days) {
        LocalDateTime end = LocalDateTime.now().plusDays(days);
        return reminderRepository.findByStatusAndRemindAtBefore("PENDING", end);
    }

    /**
     * 取消提醒（仅提醒属主）
     */
    public Reminder cancel(String id, String currentUserId) {
        return reminderRepository.findById(id).map(r -> {
            checkOwner(currentUserId, r.getOwnerId());
            r.setStatus("CANCELLED");
            r.setUpdatedAt(LocalDateTime.now());
            return reminderRepository.save(r);
        }).orElse(null);
    }

    /**
     * 确认提醒（仅提醒属主）
     */
    public Reminder acknowledge(String id, String currentUserId) {
        return reminderRepository.findById(id).map(r -> {
            checkOwner(currentUserId, r.getOwnerId());
            r.setStatus("ACKNOWLEDGED");
            r.setUpdatedAt(LocalDateTime.now());
            return reminderRepository.save(r);
        }).orElse(null);
    }

    /**
     * 编辑提醒（仅 PENDING 待发送状态允许修改，且仅提醒属主；已发送/已确认/已取消不可编辑）
     */
    public Reminder update(String id, Reminder patch, String currentUserId) {
        return reminderRepository.findById(id).map(r -> {
            if (!"PENDING".equals(r.getStatus())) {
                return null; // 非待发送状态锁定，不允许编辑
            }
            checkOwner(currentUserId, r.getOwnerId());
            // 逐字段覆盖（只允许编辑人可控字段）
            if (patch.getTitle() != null) r.setTitle(patch.getTitle());
            if (patch.getType() != null) r.setType(patch.getType());
            if (patch.getDescription() != null) r.setDescription(patch.getDescription());
            if (patch.getRemindAt() != null) r.setRemindAt(patch.getRemindAt());
            if (patch.getAdvanceDays() != null) r.setAdvanceDays(patch.getAdvanceDays());
            if (patch.getNotifyMethod() != null) r.setNotifyMethod(patch.getNotifyMethod());
            if (patch.getPetId() != null) r.setPetId(patch.getPetId());
            if (patch.getPetName() != null) r.setPetName(patch.getPetName());
            r.setUpdatedAt(LocalDateTime.now());
            Reminder saved = reminderRepository.save(r);
            log.info("编辑提醒: id={}, title={}", saved.getId(), saved.getTitle());
            return saved;
        }).orElse(null);
    }

    /**
     * 删除提醒（物理删除，仅提醒属主）
     */
    public boolean delete(String id, String currentUserId) {
        Reminder r = reminderRepository.findById(id).orElse(null);
        if (r == null) return false;
        checkOwner(currentUserId, r.getOwnerId());
        reminderRepository.deleteById(id);
        log.info("删除提醒: id={}", id);
        return true;
    }

    /** 属主校验：非属主操作他人提醒视为水平越权 */
    private void checkOwner(String currentUserId, String ownerId) {
        if (currentUserId == null || !currentUserId.equals(ownerId)) {
            throw new AccessDeniedException("无权操作他人提醒");
        }
    }
}
