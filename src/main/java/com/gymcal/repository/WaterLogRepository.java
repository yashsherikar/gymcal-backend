package com.gymcal.repository;

import com.gymcal.model.WaterLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.time.LocalDate;
import java.util.Optional;

public interface WaterLogRepository extends MongoRepository<WaterLog, String> {
    Optional<WaterLog> findByUserIdAndLogDate(String userId, LocalDate logDate);
}
