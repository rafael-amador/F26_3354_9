package cometfit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import cometfit.api.ApiException;
import cometfit.domain.WaterEntry;
import cometfit.domain.WaterEntryRepository;
import cometfit.security.AccountService;
import cometfit.services.WaterLogService;

/**
 * Port of the team's unit test {@code spec/04-team-repo-code/LogWaterTest.java} (D2 §4.2) and test cases
 * TC-1..TC-3 (D2 §4.3) to the app's water-logging unit {@link WaterLogService#enter}, plus the D2 §4.1 test plan
 * ("valid inputs, invalid inputs, and multiple refill entries"; "three 500 mL refills should result in a daily total
 * of 1,500 mL"; "negative or missing values should be rejected with an appropriate validation message").
 * The team's users "Jane Doe" (user 1) and "John Smith" (user 2) from db/init.sql are created per test; "user 3"
 * (unknown) is an id that does not exist.
 */
@SpringBootTest
@ActiveProfiles("test")
class WaterLogTest {
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired WaterLogService logger;
    @Autowired AccountService accounts;
    @Autowired WaterEntryRepository water;

    long jane;
    long john;

    @BeforeEach
    void setUp() {
        int n = SEQ.incrementAndGet();
        jane = accounts.registerStudent("jane" + n + "_" + System.nanoTime() % 100000, "Password123!",
                "jane" + n + "@test.local", "Jane Doe").user().getUserId();
        john = accounts.registerStudent("john" + n + "_" + System.nanoTime() % 100000, "Password123!",
                "john" + n + "@test.local", "John Smith").user().getUserId();
    }

    /** TC-1 / testInputGallons: userId 1, amount 0.3, isLiter false -> store 1.13 L, "Added 1.13L to Jane Doe". */
    @Test
    void tc1_inputGallons() {
        WaterLogService.Logged r = logger.enter(jane, 0.3, false);
        assertThat(r.message()).isEqualTo("Added 1.13L to Jane Doe");
        WaterEntry stored = water.findById(r.entry().getEntryId()).orElseThrow();
        assertThat(stored.getAmount()).isEqualTo(1.13);          // only liters are entered into the db
        assertThat(stored.getStudentId()).isEqualTo(jane);
    }

    /** TC-2 / testNegativeAmount: userId 2, amount -0.3, isLiter true -> "Negative amount.", nothing added. */
    @Test
    void tc2_negativeAmount() {
        long before = water.countByStudentId(john);
        assertThatThrownBy(() -> logger.enter(john, -0.3, true))
                .isInstanceOf(ApiException.class).hasMessage("Negative amount.");
        assertThat(water.countByStudentId(john)).isEqualTo(before);
    }

    /** TC-3 / testUnknownUser: userId 3 (does not exist), amount 0.5, isLiter true -> "User doesn't exist.". */
    @Test
    void tc3_unknownUser() {
        long unknown = Long.MAX_VALUE;
        long total = water.count();
        assertThatThrownBy(() -> logger.enter(unknown, 0.5, true))
                .isInstanceOf(ApiException.class).hasMessage("User doesn't exist.");
        assertThat(water.count()).isEqualTo(total);
    }

    /** LogWater: {@code amount <= 0} -> "Negative amount." (zero included). */
    @Test
    void zeroIsRejectedLikeANegativeAmount() {
        assertThatThrownBy(() -> logger.enter(jane, 0.0, true)).hasMessage("Negative amount.");
    }

    /** D2 §4.1: missing values are rejected with an appropriate validation message. */
    @Test
    void missingAmountIsRejected() {
        assertThatThrownBy(() -> logger.enter(jane, null, true)).hasMessage("Amount is required.");
    }

    /** LogWater: liters are truncated (not rounded) to two decimals. */
    @Test
    void litersAreTruncatedToTwoDecimals() {
        assertThat(WaterLogService.toLiters(0.3, false)).isEqualTo(1.13);   // 1.1356236 L
        assertThat(WaterLogService.toLiters(0.759, true)).isEqualTo(0.75);
        assertThat(WaterLogService.toLiters(0.29, true)).isEqualTo(0.29);   // no floating-point off-by-one (A-35)
        assertThat(WaterLogService.toLiters(1, false)).isEqualTo(3.78);
        assertThat(logger.enter(jane, 0.759, true).message()).isEqualTo("Added 0.75L to Jane Doe");
    }

    /** A-35: an entry that truncates to 0.00 L or exceeds 5 L is rejected. */
    @Test
    void tooSmallOrTooLargeIsRejected() {
        assertThatThrownBy(() -> logger.enter(jane, 0.004, true)).hasMessageContaining("too small");
        assertThatThrownBy(() -> logger.enter(jane, 2.0, false)).hasMessageContaining("too large");   // 7.57 L
        assertThat(logger.enter(jane, 5.0, true).entry().getAmount()).isEqualTo(5.0);
    }
}
