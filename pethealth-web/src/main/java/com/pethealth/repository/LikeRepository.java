package com.pethealth.repository;

import com.pethealth.entity.Like;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface LikeRepository extends MongoRepository<Like, String> {
    Optional<Like> findByUserIdAndTargetTypeAndTargetId(String userId, String targetType, String targetId);
    boolean existsByUserIdAndTargetTypeAndTargetId(String userId, String targetType, String targetId);
    long countByTargetTypeAndTargetId(String targetType, String targetId);
}
