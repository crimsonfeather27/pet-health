package com.pethealth.repository;

import com.pethealth.entity.Reply;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ReplyRepository extends MongoRepository<Reply, String> {
    List<Reply> findByPostIdOrderByCreatedAtAsc(String postId);
    long countByPostId(String postId);
}
