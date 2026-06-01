package com.gymcal.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Document(collection = "water_logs")
@CompoundIndex(name = "user_date_idx", def = "{'userId':1,'logDate':1}")
public class WaterLog {
    @Id private String id;
    private String userId;
    private LocalDate logDate;
    private double totalMl;          // total consumed today in ml
    private int glassCount;          // number of glasses added
    private double targetMl;         // daily target in ml
    private LocalDateTime updatedAt;
}
