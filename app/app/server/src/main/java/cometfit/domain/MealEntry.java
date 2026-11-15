package cometfit.domain;

import java.time.Instant;

import jakarta.persistence.*;

/**
 * MealEntry — §5.2 Domain Data: entryId, foodName, calories, proteinG, carbsG, fatG, source : MealSource,
 * loggedAt. Student 1 "logs" 0..* MealEntry; MealEntry 0..* "based on" 0..1 DiningItem.
 * FR-2 / UC-1; SD-1 "insert MealEntry".
 */
@Entity
@Table(name = "meal_entries")
public class MealEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id")
    private Long entryId;
    @Column(name = "student_id", nullable = false)
    private Long studentId;
    @Column(name = "food_name", nullable = false)
    private String foodName;
    @Column(nullable = false)
    private int calories;
    // TD-6 §3: null = "not published" for a UTD Dining item that only lists calories (V5__utd_dining_td6.sql).
    @Column(name = "protein_g")
    private Double proteinG;
    @Column(name = "carbs_g")
    private Double carbsG;
    @Column(name = "fat_g")
    private Double fatG;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private MealSource source;
    @Column(name = "dining_item_id")
    private Long diningItemId;
    @Column(name = "logged_at", nullable = false)
    private Instant loggedAt;

    protected MealEntry() {}

    public MealEntry(Long studentId, String foodName, int calories, Double proteinG, Double carbsG, Double fatG,
                     MealSource source, Long diningItemId, Instant loggedAt) {
        this.studentId = studentId;
        this.foodName = foodName;
        this.calories = calories;
        this.proteinG = proteinG;
        this.carbsG = carbsG;
        this.fatG = fatG;
        this.source = source;
        this.diningItemId = diningItemId;
        this.loggedAt = loggedAt;
    }

    public Long getEntryId() { return entryId; }
    public Long getStudentId() { return studentId; }
    public String getFoodName() { return foodName; }
    public int getCalories() { return calories; }
    public Double getProteinG() { return proteinG; }
    public Double getCarbsG() { return carbsG; }
    public Double getFatG() { return fatG; }
    /** TD-6 §3: true when at least one macro was not published by the source. */
    public boolean hasUnpublishedMacros() { return proteinG == null || carbsG == null || fatG == null; }
    public MealSource getSource() { return source; }
    public Long getDiningItemId() { return diningItemId; }
    public Instant getLoggedAt() { return loggedAt; }
}
