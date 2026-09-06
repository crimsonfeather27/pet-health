package com.pethealth.service.dubbo;

import com.pethealth.entity.PetProfile;

import java.util.List;

/**
 * 宠物档案 Dubbo 服务接口（Consumer 侧）
 * <p>
 * 纯 Java interface，无 Dubbo 注解。Provider 侧（pet-service）会放一份相同签名的接口，
 * 并通过 PetDubboServiceImpl 标注 @DubboService 暴露服务。
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
