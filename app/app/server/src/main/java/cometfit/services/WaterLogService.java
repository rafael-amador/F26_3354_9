package cometfit.services;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cometfit.api.ApiException;
import cometfit.domain.WaterEntry;
import cometfit.domain.WaterEntryRepository;
import cometfit.security.Student;
import cometfit.security.UserRepository;

/**
 * The water-logging unit, following the team's own code: {@code spec/04-team-repo-code/LogWater.java}
 * ({@code enter(userId, amount, isLiter)}) and its tests TC-1..TC-3 (D2 §4.2, §4.3). That code wins over D1/D2 for
 * this unit (stage-2 precedence). Used by NutritionController.saveWaterEntry (SD-1 "saveWaterEntry -> insert
 * WaterEntry"); FR-6, UC-1 "Log Water Intake". D2 §4.1 test plan: valid, invalid, missing and multiple refill entries.
 *
 * Same behaviour as LogWater.enter:
 * <ul>
 *   <li>{@code amount <= 0} is rejected with "Negative amount." before anything else;</li>
 *   <li>liters and gallons are accepted; gallons are converted with x 3.785412; only liters are stored;</li>
 *   <li>the liters are truncated (floor) to two decimals;</li>
 *   <li>an unknown user is rejected with "User doesn't exist.";</li>
 *   <li>the result is "Added &lt;liters&gt;L to &lt;full name&gt;" (full name = the user's display name, A-34).</li>
 * </ul>
 * App additions (A-35): a missing amount gives "Amount is required." (D2 §4.1 "missing values should be rejected");
 * an amount that truncates to 0.00 L or is above 5 L per entry is rejected.
 */
@Service
public class WaterLogService {
    /** LogWater.java: {@code amount = amount * 3.785412}. */
    public static final double LITERS_PER_GALLON = 3.785412;
    /** A-35: largest single entry, in liters (the stage-1 limit was 5000 ml). */
    public static final double MAX_LITERS_PER_ENTRY = 5.0;

    private final UserRepository users;
    private final WaterEntryRepository water;

    public WaterLogService(UserRepository users, WaterEntryRepository water) {
        this.users = users;
        this.water = water;
    }

    public record Logged(WaterEntry entry, String message) {}

    /** LogWater.enter(userId, amount, isLiter). */
    @Transactional
    public Logged enter(long userId, Double amount, boolean isLiter) {
        if (amount == null) throw ApiException.badRequest("Amount is required.");
        if (amount.isNaN() || amount.isInfinite()) throw ApiException.badRequest("Amount must be a number.");
        if (amount <= 0) throw ApiException.badRequest("Negative amount.");

        double liters = toLiters(amount, isLiter);
        if (liters <= 0) throw ApiException.badRequest("Amount is too small: the smallest entry is 0.01 L.");
        if (liters > MAX_LITERS_PER_ENTRY)
            throw ApiException.badRequest("Amount is too large: at most " + MAX_LITERS_PER_ENTRY + " L per entry.");

        Student student = users.findById(userId)
                .filter(u -> u instanceof Student).map(u -> (Student) u)
                .orElseThrow(() -> ApiException.notFound("User doesn't exist."));

        WaterEntry saved = water.save(new WaterEntry(student.getUserId(), liters, Instant.now()));
        return new Logged(saved, "Added " + liters + "L to " + student.getDisplayName());
    }

    /**
     * LogWater.java: "convert to liters, truncate to two places". The 1e-9 guard keeps binary floating-point error
     * from truncating an exact input one step too low (0.29 * 100 = 28.999999999999996 would become 0.28 L; A-35).
     */
    public static double toLiters(double amount, boolean isLiter) {
        double a = isLiter ? amount : amount * LITERS_PER_GALLON;
        return Math.floor(a * 100 + 1e-9) / 100;
    }
}
