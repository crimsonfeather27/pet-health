package com.pethealth.repository;

import com.pethealth.entity.PetProfile;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PetProfileRepository extends MongoRepository<PetProfile, String> {
    List<PetProfile> findByOwnerId(String ownerId);
    long countByOwnerId(String ownerId);
}
