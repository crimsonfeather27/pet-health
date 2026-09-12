package com.pethealth.service.dubbo;

import com.pethealth.entity.PetProfile;

import java.util.List;

/**
 * 宠物档案 Dubbo 服务接口（共享契约）
 * <p>
 * 由 pet-service 提供实现（@DubboService），pethealth-web 通过 @DubboReference 调用。
 * 接口在 Consumer / Provider 两侧共用本模块同一源码，避免签名漂移导致的运行时失败。
 * 设计文档 7.0 / 7.1 节。
 */
public interface PetDubboService {

    PetProfile create(PetProfile profile);

    PetProfile findById(String id);

    List<PetProfile> findAll();

    List<PetProfile> findByOwnerId(String ownerId);

    PetProfile update(String id, PetProfile partial);

    boolean delete(String id);

    // === 嵌套数组操作 ===
    PetProfile addVaccine(String petId, PetProfile.Vaccine vaccine);

    PetProfile addDeworming(String petId, PetProfile.Deworming deworming);

    PetProfile addCheckup(String petId, PetProfile.Checkup checkup);

    // === 到期查询（提醒服务调用） ===
    List<PetProfile> findDueVaccinesWithinDays(int days);

    List<PetProfile> findDueDewormingsWithinDays(int days);
}
