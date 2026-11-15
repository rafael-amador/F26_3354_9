package cometfit.services;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cometfit.domain.ActivityRecord;
import cometfit.domain.ActivityRecordRepository;
import cometfit.domain.MealEntry;
import cometfit.domain.MealEntryRepository;
import cometfit.domain.WaterEntry;
import cometfit.domain.WaterEntryRepository;
import cometfit.security.Student;

/**
 * Realises Student.getDailySummary(date) : DailySummary (§5.2) — SD-1 "query daily totals vs goals / totals /
 * updated daily summary / display calories, macros, water progress". UC-1 "View Daily Summary &amp; Goals".
 * Steps are included because Student holds stepGoal and UC-2 syncs steps (FR-4).
 * FR-6 verification (RTM §4.4): "if the amount of water drank is too low, it will display a message" (A-19).
 */
@Service
public class DailySummaryService {
    private final MealEntryRepository meals;
    private final WaterEntryRepository water;
    private final ActivityRecordRepository activity;
    private final Clock clock;

    public DailySummaryService(MealEntryRepository meals, WaterEntryRepository water,
                               ActivityRecordRepository activity, Clock clock) {
        this.meals = meals;
        this.water = water;
        this.activity = activity;
        this.clock = clock;
    }

    public record DailySummary(LocalDate date, int calories, int calorieGoal, double proteinG, double carbsG,
                               double fatG, int waterMl, double waterLiters, int waterGoalMl, int bottleSizeMl, int steps, int stepGoal,
                               Integer latestHeartRate, int mealCount, int waterEntryCount, List<String> messages,
                               int mealsWithUnpublishedMacros) {}

    @Transactional(readOnly = true)
    public DailySummary getDailySummary(Student s, LocalDate date) {
        Instant from = clock.startOf(date), to = clock.endOf(date);
        List<MealEntry> m = meals.findByStudentIdAndLoggedAtBetweenOrderByLoggedAtDesc(s.getUserId(), from, to);
        List<WaterEntry> w = water.findByStudentIdAndLoggedAtBetweenOrderByLoggedAtDesc(s.getUserId(), from, to);
        List<ActivityRecord> a = activity.findByStudentIdAndTimestampBetweenOrderByTimestampDesc(s.getUserId(), from, to);

        int cal = m.stream().mapToInt(MealEntry::getCalories).sum();
        // TD-6 §3: a macro the source did not publish (null) counts as 0 g in the totals (A-55);
        // mealsWithUnpublishedMacros lets the client say so.
        double p = round1(m.stream().mapToDouble(e -> nz(e.getProteinG())).sum());
        double c = round1(m.stream().mapToDouble(e -> nz(e.getCarbsG())).sum());
        double f = round1(m.stream().mapToDouble(e -> nz(e.getFatG())).sum());
        int unpublished = (int) m.stream().filter(MealEntry::hasUnpublishedMacros).count();
        // Water is stored in liters (team repo code LogWater.java); goals stay in ml (Student.waterGoalMl, §5.2).
        double liters = Math.round(w.stream().mapToDouble(WaterEntry::getAmount).sum() * 100) / 100.0;
        int ml = (int) Math.round(liters * 1000);
        int steps = a.stream().mapToInt(ActivityRecord::getSteps).sum();
        Integer hr = a.stream().filter(r -> r.getHeartRate() != null).findFirst().map(ActivityRecord::getHeartRate).orElse(null);

        List<String> msgs = new ArrayList<>();
        if (ml < s.getWaterGoalMl()) {
            msgs.add("Your water intake is low: " + ml + " of " + s.getWaterGoalMl()
                    + " ml today. Drink some water to stay hydrated.");
        } else {
            msgs.add("Water goal reached. Nice work staying hydrated!");
        }
        if (cal > s.getCalorieGoal()) {
            msgs.add("You are " + (cal - s.getCalorieGoal()) + " kcal over your calorie goal today.");
        }
        msgs.addAll(Student.safetyWarnings(s.getCalorieGoal(), s.getWaterGoalMl(), s.getStepGoal()));

        return new DailySummary(date, cal, s.getCalorieGoal(), p, c, f, ml, liters, s.getWaterGoalMl(), s.getBottleSizeMl(),
                steps, s.getStepGoal(), hr, m.size(), w.size(), msgs, unpublished);
    }

    private static double round1(double v) { return Math.round(v * 10) / 10.0; }
    private static double nz(Double v) { return v == null ? 0 : v; }
}
