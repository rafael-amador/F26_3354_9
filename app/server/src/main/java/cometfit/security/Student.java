package cometfit.security;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * Student — Deliverable 1 §5.2, extends User.
 * Attributes: calorieGoal, waterGoalMl, stepGoal. Operations: setGoals(cal, water, steps),
 * getDailySummary(date) (realised by {@link cometfit.services.DailySummaryService} because it needs
 * the repositories; see SD-1 "query daily totals vs goals").
 */
@Entity
@DiscriminatorValue("STUDENT")
public class Student extends User {

    // Defaults: A-7
    public static final int DEFAULT_CALORIE_GOAL = 2000;
    public static final int DEFAULT_WATER_GOAL_ML = 2000;
    public static final int DEFAULT_STEP_GOAL = 10000;
    public static final int DEFAULT_BOTTLE_ML = 500;

    // Safety NFR thresholds ("warning whenever an unsafe goal is set"): A-10
    public static final int SAFE_MIN_CALORIES = 1200;
    public static final int SAFE_MAX_CALORIES = 4000;
    public static final int SAFE_MIN_WATER_ML = 1000;
    public static final int SAFE_MAX_WATER_ML = 4000;
    public static final int SAFE_MAX_STEPS = 30000;

    @Column(name = "calorie_goal")
    private Integer calorieGoal = DEFAULT_CALORIE_GOAL;

    @Column(name = "water_goal_ml")
    private Integer waterGoalMl = DEFAULT_WATER_GOAL_ML;

    @Column(name = "step_goal")
    private Integer stepGoal = DEFAULT_STEP_GOAL;

    /** Size of the student's water bottle so a "refill" can be logged in one tap (Proposal §4; A-8). */
    @Column(name = "bottle_size_ml")
    private Integer bottleSizeMl = DEFAULT_BOTTLE_ML;

    /**
     * setGoals(cal, water, steps) from §5.2. Returns safety warnings (NFR Safety) — goals are still
     * saved, the app only warns.
     */
    public List<String> setGoals(int cal, int water, int steps) {
        this.calorieGoal = cal;
        this.waterGoalMl = water;
        this.stepGoal = steps;
        return safetyWarnings(cal, water, steps);
    }

    public static List<String> safetyWarnings(int cal, int water, int steps) {
        List<String> w = new ArrayList<>();
        if (cal < SAFE_MIN_CALORIES)
            w.add("A daily calorie target below " + SAFE_MIN_CALORIES + " kcal may be unsafe.");
        if (cal > SAFE_MAX_CALORIES)
            w.add("A daily calorie target above " + SAFE_MAX_CALORIES + " kcal may be unsafe.");
        if (water < SAFE_MIN_WATER_ML)
            w.add("A daily water target below " + SAFE_MIN_WATER_ML + " ml may leave you dehydrated.");
        if (water > SAFE_MAX_WATER_ML)
            w.add("A daily water target above " + SAFE_MAX_WATER_ML + " ml is excessive water intake.");
        if (steps > SAFE_MAX_STEPS)
            w.add("A daily step target above " + SAFE_MAX_STEPS + " steps risks over-exertion.");
        return w;
    }

    @Override
    public String role() { return "STUDENT"; }

    public int getCalorieGoal() { return calorieGoal == null ? DEFAULT_CALORIE_GOAL : calorieGoal; }
    public int getWaterGoalMl() { return waterGoalMl == null ? DEFAULT_WATER_GOAL_ML : waterGoalMl; }
    public int getStepGoal() { return stepGoal == null ? DEFAULT_STEP_GOAL : stepGoal; }
    public int getBottleSizeMl() { return bottleSizeMl == null ? DEFAULT_BOTTLE_ML : bottleSizeMl; }
    public void setBottleSizeMl(int bottleSizeMl) { this.bottleSizeMl = bottleSizeMl; }
}
