package com.gymcal.repository;

import com.gymcal.model.WorkoutPlan;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface WorkoutPlanRepository extends MongoRepository<WorkoutPlan, String> {
    List<WorkoutPlan> findByUserIdOrderByCreatedAtDesc(String userId);
    Optional<WorkoutPlan> findByUserIdAndIsActiveTrue(String userId);
}
