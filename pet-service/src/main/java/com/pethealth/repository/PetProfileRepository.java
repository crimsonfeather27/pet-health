package com.pethealth.repository;

import com.pethealth.entity.PetProfile;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface PetProfileRepository extends MongoRepository<PetProfile, String> {

    List<PetProfile> findByOwnerId(String ownerId);

    long countByOwnerId(String ownerId);

    @Query("{ 'vaccines.nextDueAt': { '$gte': ?0, '$lte': ?1 } }")
    List<PetProfile> findDueVaccinesBetween(LocalDate start, LocalDate end);

    @Query("{ 'dewormings.nextDueAt': { '$gte': ?0, '$lte': ?1 } }")
    List<PetProfile> findDueDewormingsBetween(LocalDate start, LocalDate end);
}
