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
    private double totalMl;       // total = drinks + food water
    private double fromFoodMl;    // auto-added from food logs (juice, fruits etc.)
    private int glassCount;       // manually added glasses
    private double targetMl;
    private LocalDateTime updatedAt;
}
