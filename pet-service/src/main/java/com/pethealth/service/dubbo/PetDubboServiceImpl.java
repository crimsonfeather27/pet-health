package com.pethealth.service.dubbo;

import com.pethealth.entity.PetProfile;
import com.pethealth.repository.PetProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 宠物档案 Dubbo 服务实现（Provider 侧）
 * <p>
 * 通过 @DubboService 暴露 PetDubboService 到 Dubbo 协议端口 20881。
 */
@Slf4j
@Service
@DubboService
@RequiredArgsConstructor
public class PetDubboServiceImpl implements PetDubboService {

    private final PetProfileRepository repository;

    @Override
    public PetProfile create(PetProfile profile) {
        LocalDateTime now = LocalDateTime.now();
        profile.setCreatedAt(now);
        profile.setUpdatedAt(now);
        return repository.save(profile);
    }

    @Override
    public PetProfile findById(String id) {
        return repository.findById(id).orElse(null);
    }

    @Override
    public List<PetProfile> findAll() {
        return repository.findAll();
    }

    @Override
    public List<PetProfile> findByOwnerId(String ownerId) {
        return repository.findByOwnerId(ownerId);
    }

    @Override
    public PetProfile update(String id, PetProfile partial) {
        PetProfile existing = repository.findById(id).orElse(null);
        if (existing == null) {
            return null;
        }
        if (partial.getName() != null) {
            existing.setName(partial.getName());
        }
        if (partial.getSpecies() != null) {
            existing.setSpecies(partial.getSpecies());
        }
        if (partial.getBreed() != null) {
            existing.setBreed(partial.getBreed());
        }
        if (partial.getGender() != null) {
            existing.setGender(partial.getGender());
        }
        if (partial.getNeutered() != null) {
            existing.setNeutered(partial.getNeutered());
        }
        if (partial.getBirthday() != null) {
            existing.setBirthday(partial.getBirthday());
        }
        if (partial.getAvatar() != null) {
            existing.setAvatar(partial.getAvatar());
        }
        if (partial.getDescription() != null) {
            existing.setDescription(partial.getDescription());
        }
        if (partial.getVaccines() != null) {
            existing.setVaccines(partial.getVaccines());
        }
        if (partial.getDewormings() != null) {
            existing.setDewormings(partial.getDewormings());
        }
        if (partial.getCheckups() != null) {
            existing.setCheckups(partial.getCheckups());
        }
        if (partial.getMedicalVisits() != null) {
            existing.setMedicalVisits(partial.getMedicalVisits());
        }
        existing.setUpdatedAt(LocalDateTime.now());
        return repository.save(existing);
    }

    @Override
    public boolean delete(String id) {
        if (!repository.existsById(id)) {
            return false;
        }
        repository.deleteById(id);
        return true;
    }

    @Override
    public PetProfile addVaccine(String petId, PetProfile.Vaccine vaccine) {
        PetProfile pet = repository.findById(petId).orElse(null);
        if (pet == null) {
            return null;
        }
        List<PetProfile.Vaccine> vaccines = pet.getVaccines();
        if (vaccines == null) {
            vaccines = new ArrayList<>();
            pet.setVaccines(vaccines);
        }
        vaccines.add(vaccine);
        pet.setUpdatedAt(LocalDateTime.now());
        return repository.save(pet);
    }

    @Override
    public PetProfile addDeworming(String petId, PetProfile.Deworming deworming) {
        PetProfile pet = repository.findById(petId).orElse(null);
        if (pet == null) {
            return null;
        }
        List<PetProfile.Deworming> dewormings = pet.getDewormings();
        if (dewormings == null) {
            dewormings = new ArrayList<>();
            pet.setDewormings(dewormings);
        }
        dewormings.add(deworming);
        pet.setUpdatedAt(LocalDateTime.now());
        return repository.save(pet);
    }

    @Override
    public PetProfile addCheckup(String petId, PetProfile.Checkup checkup) {
        PetProfile pet = repository.findById(petId).orElse(null);
        if (pet == null) {
            return null;
        }
        List<PetProfile.Checkup> checkups = pet.getCheckups();
        if (checkups == null) {
            checkups = new ArrayList<>();
            pet.setCheckups(checkups);
        }
        checkups.add(checkup);
        pet.setUpdatedAt(LocalDateTime.now());
        return repository.save(pet);
    }

    @Override
    public List<PetProfile> findDueVaccinesWithinDays(int days) {
        LocalDate now = LocalDate.now();
        return repository.findDueVaccinesBetween(now, now.plusDays(days));
    }

    @Override
    public List<PetProfile> findDueDewormingsWithinDays(int days) {
        LocalDate now = LocalDate.now();
        return repository.findDueDewormingsBetween(now, now.plusDays(days));
    }
}
