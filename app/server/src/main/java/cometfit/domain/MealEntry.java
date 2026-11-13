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
    @Column(name = "protein_g", nullable = false)
    private double proteinG;
    @Column(name = "carbs_g", nullable = false)
    private double carbsG;
    @Column(name = "fat_g", nullable = false)
    private double fatG;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private MealSource source;
    @Column(name = "dining_item_id")
    private Long diningItemId;
    @Column(name = "logged_at", nullable = false)
    private Instant loggedAt;

    protected MealEntry() {}

    public MealEntry(Long studentId, String foodName, int calories, double proteinG, double carbsG, double fatG,
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
    public double getProteinG() { return proteinG; }
    public double getCarbsG() { return carbsG; }
    public double getFatG() { return fatG; }
    public MealSource getSource() { return source; }
    public Long getDiningItemId() { return diningItemId; }
    public Instant getLoggedAt() { return loggedAt; }
}
