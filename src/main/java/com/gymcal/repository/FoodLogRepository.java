package com.gymcal.repository;

import com.gymcal.model.FoodLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface FoodLogRepository extends MongoRepository<FoodLog, String> {
    List<FoodLog> findByUserIdAndLogDateOrderByCreatedAtDesc(String userId, LocalDate logDate);
    List<FoodLog> findByUserIdAndLogDateBetweenOrderByLogDateDesc(String userId, LocalDate startDate, LocalDate endDate);
    void deleteByIdAndUserId(String id, String userId);
}
