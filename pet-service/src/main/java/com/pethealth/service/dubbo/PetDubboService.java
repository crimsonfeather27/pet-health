package com.pethealth.service.dubbo;

import com.pethealth.entity.PetProfile;

import java.util.List;

/**
 * 宠物档案 Dubbo 服务接口（Provider 侧）
 * <p>
 * 纯 Java interface，无 Dubbo 注解。方法签名与 pethealth-web Consumer 侧完全一致，
 * 由 PetDubboServiceImpl 标注 @DubboService 暴露服务。
 * <p>
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
