package cometfit;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cometfit.domain.ActivityRecord;
import cometfit.domain.ActivityRecordRepository;
import cometfit.domain.ConsentSettings;
import cometfit.domain.ConsentSettingsRepository;
import cometfit.domain.DeviceLink;
import cometfit.domain.DeviceLinkRepository;
import cometfit.domain.Friendship;
import cometfit.domain.FriendshipRepository;
import cometfit.domain.MealEntry;
import cometfit.domain.MealEntryRepository;
import cometfit.domain.MealSource;
import cometfit.domain.PointAccount;
import cometfit.domain.PointAccountRepository;
import cometfit.domain.PointTransaction;
import cometfit.domain.PointTransactionRepository;
import cometfit.domain.RecVisit;
import cometfit.domain.RecVisitRepository;
import cometfit.domain.SmartDevice;
import cometfit.domain.SmartDeviceRepository;
import cometfit.domain.WaterEntry;
import cometfit.domain.WaterEntryRepository;
import cometfit.security.AccountService;
import cometfit.security.Permissions;
import cometfit.security.UserRepository;
import cometfit.services.Clock;
import cometfit.services.PointsService;

/**
 * Demo accounts for the recorded demo (A-14). Disabled with COMETFIT_SEED_DEMO=false.
 * Fixed TOTP secrets so the presenter can generate codes with app/tools/totp.mjs or any authenticator app.
 * Actors: Developer (UC-4), Student / User / Friend (UC-1..UC-3).
 *
 * TD-2 (stage 2): the demo students alice and bob start with all data sharing turned on (calories, water, steps,
 * UREC visits) and have some activity history, so UC-3 (friends' progress and leaderboard) shows real numbers.
 * They are seeded as friends so the leaderboard lists both (A-40). Real new accounts keep sharing off (A-18).
 * History is written only when the two students are created, i.e. on a fresh database.
 */
@Component
@ConditionalOnProperty(name = "cometfit.seed-demo", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    public static final String DEMO_PASSWORD = "CometFit123!";
    public static final String DEV_SECRET = "COMETFITDEVADMIN";
    public static final String ALICE_SECRET = "COMETFITALICE234";
    public static final String BOB_SECRET = "COMETFITBOBBY567";
    public static final String UREC_READER = "UREC-FRONT-1";
    /** Days of history before today (A-40). */
    public static final int HISTORY_DAYS = 6;

    private final AccountService accounts;
    private final UserRepository users;
    private final Clock clock;
    private final ConsentSettingsRepository consents;
    private final FriendshipRepository friendships;
    private final MealEntryRepository meals;
    private final WaterEntryRepository water;
    private final SmartDeviceRepository devices;
    private final DeviceLinkRepository links;
    private final ActivityRecordRepository activity;
    private final RecVisitRepository visits;
    private final PointAccountRepository pointAccounts;
    private final PointTransactionRepository pointTx;

    public DemoDataSeeder(AccountService accounts, UserRepository users, Clock clock,
                          ConsentSettingsRepository consents, FriendshipRepository friendships,
                          MealEntryRepository meals, WaterEntryRepository water, SmartDeviceRepository devices,
                          DeviceLinkRepository links, ActivityRecordRepository activity, RecVisitRepository visits,
                          PointAccountRepository pointAccounts, PointTransactionRepository pointTx) {
        this.accounts = accounts;
        this.users = users;
        this.clock = clock;
        this.consents = consents;
        this.friendships = friendships;
        this.meals = meals;
        this.water = water;
        this.devices = devices;
        this.links = links;
        this.activity = activity;
        this.visits = visits;
        this.pointAccounts = pointAccounts;
        this.pointTx = pointTx;
    }

    /** One demo student's week (index 0 = 6 days ago, last = today). Values are fixed so every demo is the same. */
    private record Week(int[] mealCalories, int[] refillsOf500ml, int[] steps, boolean[] urecVisit) {}

    private static final Week ALICE_WEEK = new Week(
            new int[]{1850, 2100, 1700, 1950, 2250, 1800, 1150},
            new int[]{4, 3, 5, 4, 2, 4, 2},
            new int[]{11200, 8400, 12650, 9800, 10400, 7600, 6300},
            new boolean[]{true, false, true, true, false, true, true});
    private static final Week BOB_WEEK = new Week(
            new int[]{2300, 2450, 2100, 2600, 2200, 2350, 900},
            new int[]{2, 3, 2, 4, 3, 2, 1},
            new int[]{6400, 10300, 5200, 8800, 11900, 7100, 4200},
            new boolean[]{false, true, false, false, true, false, false});

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!users.existsByUsernameIgnoreCase("dev")) {
            accounts.createDeveloper("dev", DEMO_PASSWORD, "dev@cometfit.local", "Dev Admin", "OPERATOR",
                    Permissions.DEVELOPER_DEFAULT, DEV_SECRET);
            log.info("Seeded demo developer 'dev' (TOTP secret {})", DEV_SECRET);
        }
        Long alice = null, bob = null;
        if (!users.existsByUsernameIgnoreCase("alice")) {
            alice = accounts.registerStudent("alice", DEMO_PASSWORD, "alice@cometfit.local", "Alice", ALICE_SECRET)
                    .user().getUserId();
            log.info("Seeded demo student 'alice' (TOTP secret {})", ALICE_SECRET);
        }
        if (!users.existsByUsernameIgnoreCase("bob")) {
            bob = accounts.registerStudent("bob", DEMO_PASSWORD, "bob@cometfit.local", "Bob", BOB_SECRET)
                    .user().getUserId();
            log.info("Seeded demo student 'bob' (TOTP secret {})", BOB_SECRET);
        }
        if (alice != null && bob != null) {
            seedDemoHistory(alice, bob);
        }
    }

    /** TD-2: sharing on, friends, and a week of meals, water, steps, UREC visits and points. */
    private void seedDemoHistory(Long alice, Long bob) {
        for (Long id : new Long[]{alice, bob}) {
            ConsentSettings c = consents.findById(id).orElseGet(() -> new ConsentSettings(id));
            c.set(true, true, true, true);
            consents.save(c);
        }
        Instant now = Instant.now();
        LocalDate today = clock.today();
        friendships.save(new Friendship(alice, bob, clock.startOf(today.minusDays(HISTORY_DAYS + 1))));
        seedWeek(alice, "DEMO-WATCH-ALICE", ALICE_WEEK, today, now);
        seedWeek(bob, "DEMO-WATCH-BOB", BOB_WEEK, today, now);
        log.info("Seeded TD-2 demo data: alice and bob are friends, share all stats, {} days of history", HISTORY_DAYS + 1);
    }

    private void seedWeek(Long student, String deviceId, Week w, LocalDate today, Instant now) {
        SmartDevice device = devices.save(new SmartDevice(deviceId, "watch", "simulated"));
        DeviceLink link = new DeviceLink(student, device);
        link.pair();
        links.save(link);

        PointAccount account = pointAccounts.findById(student).orElseGet(() -> new PointAccount(student));
        for (int i = 0; i <= HISTORY_DAYS; i++) {
            LocalDate day = today.minusDays(HISTORY_DAYS - i);
            Instant start = clock.startOf(day);

            int cal = w.mealCalories()[i];
            int[] split = {cal * 25 / 100, cal * 35 / 100, cal - cal * 25 / 100 - cal * 35 / 100};
            String[] names = {"Breakfast (demo)", "Lunch (demo)", "Dinner (demo)"};
            int[] hours = {8, 12, 18};
            for (int m = 0; m < 3; m++) {
                Instant t = at(start, hours[m], 0, now);
                if (t == null) continue;
                meals.save(new MealEntry(student, names[m], split[m], split[m] * 0.25 / 4, split[m] * 0.5 / 4,
                        split[m] * 0.25 / 9, MealSource.MANUAL, null, t));
            }
            for (int r = 0; r < w.refillsOf500ml()[i]; r++) {
                Instant t = at(start, 9 + r * 3, 0, now);
                if (t != null) water.save(new WaterEntry(student, 0.5, t));
            }
            int steps = w.steps()[i];
            int[] parts = {steps * 30 / 100, steps * 45 / 100, steps - steps * 30 / 100 - steps * 45 / 100};
            int[] syncHours = {10, 15, 20};
            int synced = 0;
            for (int p = 0; p < 3; p++) {
                Instant t = at(start, syncHours[p], 0, now);
                if (t == null) continue;
                activity.save(new ActivityRecord(student, device.getId(), parts[p], 70 + p * 12, t));
                synced += parts[p];
            }
            if (synced >= 10_000) award(account, student, PointsService.STEP_GOAL, at(start, 20, 1, now));
            if (w.urecVisit()[i]) {
                Instant t = at(start, 7, 30, now);
                if (t != null) {
                    visits.save(new RecVisit(student, UREC_READER, t));
                    award(account, student, PointsService.VISIT, t);
                }
            }
        }
        pointAccounts.save(account);
    }

    private void award(PointAccount account, Long student, String reason, Instant t) {
        if (t == null) return;
        int amount = PointsService.pointsFor(reason);
        account.addPoints(amount);
        pointTx.save(new PointTransaction(student, amount, reason, t));
    }

    /** start-of-day + h:m, or null when that time is still in the future (today's history stops at "now"). */
    private static Instant at(Instant startOfDay, int hour, int minute, Instant now) {
        Instant t = startOfDay.plus(Duration.ofHours(hour)).plus(Duration.ofMinutes(minute));
        return t.isAfter(now) ? null : t;
    }
}
