package cometfit;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import cometfit.domain.DiningItem;
import cometfit.domain.FriendRequest;
import cometfit.domain.FriendRequestRepository;
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
import cometfit.services.DiningMenuService;
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
 *
 * TD-6 §5 (stage 4): their meal history uses real UTD Dining items from the stub feed (spec/06-utd-dining) instead
 * of the old "Breakfast (demo)" manual entries; calorie totals follow from the items' published calories.
 *
 * TD-8 (stage 6, demo data only): four more demo students (starryskies23, nebulanomad, lunarlifter, orbitrunner); all
 * six are friends with each other, each has a week of different levels so alice is first and last on no board
 * (UC-3 "View Leaderboard"), and cosmocomet has a pending friend request to alice (UC-3 "Add / Accept Friend").
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
    private final FriendRequestRepository requests;
    private final MealEntryRepository meals;
    private final WaterEntryRepository water;
    private final SmartDeviceRepository devices;
    private final DeviceLinkRepository links;
    private final ActivityRecordRepository activity;
    private final RecVisitRepository visits;
    private final PointAccountRepository pointAccounts;
    private final PointTransactionRepository pointTx;
    private final DiningMenuService diningMenu;

    public DemoDataSeeder(AccountService accounts, UserRepository users, Clock clock,
                          ConsentSettingsRepository consents, FriendshipRepository friendships,
                          FriendRequestRepository requests, MealEntryRepository meals, WaterEntryRepository water, SmartDeviceRepository devices,
                          DeviceLinkRepository links, ActivityRecordRepository activity, RecVisitRepository visits,
                          PointAccountRepository pointAccounts, PointTransactionRepository pointTx,
                          DiningMenuService diningMenu) {
        this.diningMenu = diningMenu;
        this.accounts = accounts;
        this.users = users;
        this.clock = clock;
        this.consents = consents;
        this.friendships = friendships;
        this.requests = requests;
        this.meals = meals;
        this.water = water;
        this.devices = devices;
        this.links = links;
        this.activity = activity;
        this.visits = visits;
        this.pointAccounts = pointAccounts;
        this.pointTx = pointTx;
    }

    /**
     * One demo student's week (index 0 = 6 days ago, last = today). Values are fixed so every demo is the same.
     * TD-6 §5: meals[day][breakfast|lunch|dinner] lists real UTD Dining items ("Location|Item" from
     * spec/06-utd-dining); each item is logged as its own DINING_MENU meal entry with the item's published numbers.
     */
    public record Week(String[][][] meals, int[] refillsOf500ml, int[] steps, boolean[] urecVisit) {}

    private static final String DHW = "Dining Hall West|", CFA = "Chick-fil-A|", PANDA = "Panda Express|",
            HALAL = "The Halal Shack|", FIRE = "Firehouse Subs|", TACO = "Taco Bell Cantina|", SBUX = "Starbucks|";

    private static final Week ALICE_WEEK = new Week(
            new String[][][]{
                    {{SBUX + "Spinach, Feta & Egg White Wrap", SBUX + "Caffe Americano (Grande)"},
                     {CFA + "Cool Wrap", CFA + "Fruit Cup"},
                     {DHW + "Grilled Chicken", DHW + "Jasmine Rice", DHW + "Roasted Brussels Sprouts"}},
                    {{CFA + "Chicken Biscuit"},
                     {FIRE + "Firehouse Chopped Salad with Turkey", FIRE + "Chocolate Chip Cookie"},
                     {PANDA + "String Bean Chicken Breast", PANDA + "Broccoli Beef"}},
                    {{SBUX + "Spinach, Feta & Egg White Wrap", SBUX + "Coffee Frappuccino (Grande)"},
                     {HALAL + "Halal Hummus Rice Bowl"},
                     {DHW + "Spaghetti", DHW + "Beef Bolognese", DHW + "Roasted Brussels Sprouts"}},
                    {{CFA + "Chicken Biscuit", CFA + "Fruit Cup"},
                     {CFA + "Grilled Chicken Sandwich"},
                     {HALAL + "Halal Chicken Rice Bowl"}},
                    {{SBUX + "Sausage, Cheddar & Egg Sandwich", SBUX + "Caffe Americano (Grande)"},
                     {FIRE + "Firehouse Chopped Salad with Grilled Chicken"},
                     {TACO + "Chicken Chalupa", TACO + "Burrito Supreme"}},
                    {{SBUX + "Spinach, Feta & Egg White Wrap"},
                     {PANDA + "Mushroom Chicken", PANDA + "String Bean Chicken Breast"},
                     {DHW + "Cheese Pizza", DHW + "Roasted Brussels Sprouts", FIRE + "Brownie"}},
                    {{SBUX + "Spinach, Feta & Egg White Wrap", SBUX + "Caffe Americano (Grande)"},
                     {DHW + "Grilled Chicken", DHW + "Creamy Polenta", DHW + "Roasted Brussels Sprouts"},
                     {HALAL + "Halal Rice Bowl with Falafel"}}},
            new int[]{4, 3, 5, 4, 2, 4, 2},
            new int[]{11200, 8400, 12650, 9800, 10400, 7600, 6300},
            new boolean[]{true, false, true, true, false, true, true});
    private static final Week BOB_WEEK = new Week(
            new String[][][]{
                    {{CFA + "Chicken Biscuit", CFA + "Hash Browns"},
                     {PANDA + "The Original Orange Chicken", PANDA + "Beijing Beef"},
                     {HALAL + "Halal Beef Naan Roll"}},
                    {{SBUX + "Sausage, Cheddar & Egg Sandwich", SBUX + "Caramel Frappuccino (Grande)"},
                     {TACO + "Cheesy Gordita Crunch", TACO + "Beefy 5-Layer Burrito"},
                     {DHW + "Pepperoni Pizza", DHW + "Chicken Broccoli Alfredo Pizza", DHW + "Potato Wedges"}},
                    {{CFA + "Chicken Biscuit"},
                     {CFA + "Deluxe Sandwich", CFA + "Hash Browns"},
                     {HALAL + "Halal Beef & Chicken Rice Bowl", FIRE + "Brownie"}},
                    {{SBUX + "Sausage, Cheddar & Egg Sandwich", SBUX + "Mocha Frappuccino (Grande)"},
                     {PANDA + "Kung Pao Chicken", PANDA + "Black Pepper Chicken"},
                     {TACO + "Beef Chalupa", TACO + "Cheesy Bean and Rice Burrito"}},
                    {{CFA + "Chicken Biscuit", CFA + "Hash Browns"},
                     {FIRE + "Chopped Hook & Ladder Salad", FIRE + "Chocolate Chip Cookie"},
                     {DHW + "Italian Nachos", DHW + "Beef Bolognese", DHW + "Spaghetti", DHW + "Potato Wedges"}},
                    {{SBUX + "Hot Chocolate (Grande)", SBUX + "Sausage, Cheddar & Egg Sandwich"},
                     {HALAL + "Halal Rice Bowl with Beef"},
                     {PANDA + "The Original Orange Chicken", PANDA + "SweetFire Chicken Breast"}},
                    {{CFA + "Chicken Biscuit", CFA + "Hash Browns"},
                     {TACO + "Steak Chalupa", TACO + "Burrito Supreme"},
                     {HALAL + "Halal Chicken Naan Roll"}}},
            new int[]{2, 3, 2, 4, 3, 2, 1},
            new int[]{6400, 10300, 5200, 8800, 11900, 7100, 4200},
            new boolean[]{false, true, false, false, true, false, false});

    // ---- TD-8 (stage 6): four more demo friends + one pending request to alice ----------------------------------
    // Today's levels (index 6) are chosen so alice is neither first nor last on any board and each board has a
    // different leader (A-62). Water today: starryskies23 3,000 > nebulanomad 2,500 > lunarlifter 2,000 >
    // orbitrunner 1,500 > alice 1,000 > bob 500 ml. Calories today: nebulanomad 2,790 > bob 2,177 > lunarlifter 1,675
    // > orbitrunner 1,565 > alice 1,100 > starryskies23 895. UREC visits (7 days): orbitrunner 7 > starryskies23 6 >
    // alice 5 > lunarlifter 4 > nebulanomad 3 > bob 2. Points: orbitrunner 100 > starryskies23 80 > alice 65 >
    // lunarlifter 45 > nebulanomad 40 > bob 30.

    /** TD-8 §1: starryskies23 (Sky), prototype 4-social: the water leader, light eater, UREC most days. */
    private static final Week SKY_WEEK = new Week(
            new String[][][]{
                    {{SBUX + "Spinach, Feta & Egg White Wrap", SBUX + "Caffe Americano (Grande)"},
                     {FIRE + "Firehouse Chopped Salad with Turkey"},
                     {DHW + "Grilled Chicken", DHW + "Jasmine Rice", DHW + "Roasted Brussels Sprouts"}},
                    {{CFA + "Fruit Cup", SBUX + "Coffee Frappuccino (Grande)"},
                     {HALAL + "Halal Hummus Rice Bowl"},
                     {PANDA + "String Bean Chicken Breast", PANDA + "Broccoli Beef"}},
                    {{SBUX + "Spinach, Feta & Egg White Wrap"},
                     {CFA + "Cool Wrap", CFA + "Fruit Cup"},
                     {DHW + "Spaghetti", DHW + "Beef Bolognese", DHW + "Roasted Brussels Sprouts"}},
                    {{SBUX + "Spinach, Feta & Egg White Wrap", SBUX + "Caffe Americano (Grande)"},
                     {FIRE + "Firehouse Chopped Salad with Grilled Chicken"},
                     {HALAL + "Halal Rice Bowl with Falafel"}},
                    {{CFA + "Fruit Cup", SBUX + "Caffe Americano (Grande)"},
                     {TACO + "Chicken Chalupa"},
                     {DHW + "Grilled Chicken", DHW + "Creamy Polenta", DHW + "Roasted Brussels Sprouts"}},
                    {{SBUX + "Spinach, Feta & Egg White Wrap"},
                     {PANDA + "Mushroom Chicken", PANDA + "Broccoli Beef"},
                     {DHW + "Cheese Pizza", FIRE + "Firehouse Chopped Salad with Turkey"}},
                    {{SBUX + "Spinach, Feta & Egg White Wrap", SBUX + "Caffe Americano (Grande)"},
                     {FIRE + "Firehouse Chopped Salad with Turkey"},
                     {PANDA + "Mushroom Chicken", PANDA + "Broccoli Beef"}}},
            new int[]{6, 5, 6, 5, 6, 5, 6},
            new int[]{10400, 8900, 11800, 12300, 7400, 10050, 9200},
            new boolean[]{true, true, true, false, true, true, true});
    /** TD-8 §1: nebulanomad (Nova), prototype 4-social: logs the most calories. */
    private static final Week NOVA_WEEK = new Week(
            new String[][][]{
                    {{CFA + "Chicken Biscuit", CFA + "Hash Browns"},
                     {HALAL + "Halal Beef & Chicken Rice Bowl", FIRE + "Brownie"},
                     {PANDA + "The Original Orange Chicken", PANDA + "Beijing Beef"}},
                    {{SBUX + "Sausage, Cheddar & Egg Sandwich", SBUX + "Caramel Frappuccino (Grande)"},
                     {TACO + "Beefy 5-Layer Burrito", TACO + "Cheesy Gordita Crunch"},
                     {DHW + "Pepperoni Pizza", DHW + "Italian Nachos", DHW + "Potato Wedges"}},
                    {{CFA + "Chicken Biscuit", CFA + "Hash Browns"},
                     {CFA + "Grilled Chicken Club Sandwich", FIRE + "Chocolate Chip Cookie"},
                     {HALAL + "Halal Beef Naan Roll"}},
                    {{SBUX + "Mocha Frappuccino (Grande)", SBUX + "Sausage, Cheddar & Egg Sandwich"},
                     {PANDA + "SweetFire Chicken Breast", PANDA + "The Original Orange Chicken"},
                     {TACO + "Cheesy Bean and Rice Burrito", TACO + "Beef Chalupa"}},
                    {{CFA + "Chicken Biscuit"},
                     {HALAL + "Halal Rice Bowl with Beef", FIRE + "Brownie"},
                     {DHW + "Chicken Broccoli Alfredo Pizza", DHW + "Pepperoni Pizza", DHW + "Potato Wedges"}},
                    {{SBUX + "Hot Chocolate (Grande)", SBUX + "Sausage, Cheddar & Egg Sandwich"},
                     {TACO + "Steak Chalupa", TACO + "Burrito Supreme"},
                     {PANDA + "Beijing Beef", PANDA + "Kung Pao Chicken"}},
                    {{CFA + "Chicken Biscuit", CFA + "Hash Browns"},
                     {TACO + "Cheesy Gordita Crunch", TACO + "Beefy 5-Layer Burrito"},
                     {HALAL + "Halal Beef Naan Roll", FIRE + "Chocolate Chip Cookie"}}},
            new int[]{3, 4, 2, 3, 4, 3, 5},
            new int[]{7200, 10800, 6100, 5400, 11300, 6900, 5800},
            new boolean[]{false, true, false, false, true, false, true});
    /** TD-8 §1: lunarlifter (Luna): high-protein plates, steady water, lifts at UREC every other day. */
    private static final Week LUNA_WEEK = new Week(
            new String[][][]{
                    {{SBUX + "Sausage, Cheddar & Egg Sandwich"},
                     {CFA + "Grilled Chicken Club Sandwich", CFA + "Fruit Cup"},
                     {HALAL + "Halal Chicken Rice Bowl"}},
                    {{CFA + "Chicken Biscuit", CFA + "Fruit Cup"},
                     {PANDA + "Black Pepper Chicken", PANDA + "String Bean Chicken Breast"},
                     {DHW + "Grilled Chicken", DHW + "Jasmine Rice", DHW + "Roasted Brussels Sprouts"}},
                    {{SBUX + "Spinach, Feta & Egg White Wrap", SBUX + "Coffee Frappuccino (Grande)"},
                     {FIRE + "Firehouse Chopped Salad with Grilled Chicken"},
                     {HALAL + "Halal Beef & Chicken Rice Bowl"}},
                    {{SBUX + "Sausage, Cheddar & Egg Sandwich"},
                     {CFA + "Grilled Chicken Sandwich", CFA + "Fruit Cup"},
                     {TACO + "Chicken Chalupa", TACO + "Steak Chalupa"}},
                    {{CFA + "Chicken Biscuit"},
                     {HALAL + "Halal Rice Bowl with Beef"},
                     {DHW + "Grilled Chicken", DHW + "Spaghetti", DHW + "Beef Bolognese"}},
                    {{SBUX + "Spinach, Feta & Egg White Wrap"},
                     {PANDA + "Kung Pao Chicken", PANDA + "Broccoli Beef"},
                     {CFA + "Grilled Chicken Club Sandwich"}},
                    {{SBUX + "Sausage, Cheddar & Egg Sandwich"},
                     {CFA + "Grilled Chicken Club Sandwich", CFA + "Fruit Cup"},
                     {HALAL + "Halal Chicken Rice Bowl", DHW + "Roasted Brussels Sprouts"}}},
            new int[]{4, 4, 5, 3, 4, 4, 4},
            new int[]{8300, 7600, 9100, 10200, 6800, 8800, 7400},
            new boolean[]{true, false, true, false, true, false, true});
    /** TD-8 §1: orbitrunner (Theo): the UREC regular, runs most days (most steps and points). */
    private static final Week THEO_WEEK = new Week(
            new String[][][]{
                    {{SBUX + "Coffee Frappuccino (Grande)", SBUX + "Spinach, Feta & Egg White Wrap"},
                     {TACO + "Burrito Supreme", TACO + "Chicken Chalupa"},
                     {DHW + "Spaghetti", DHW + "Beef Bolognese", DHW + "Jasmine Rice"}},
                    {{CFA + "Chicken Biscuit", CFA + "Fruit Cup"},
                     {HALAL + "Halal Chicken Rice Bowl"},
                     {PANDA + "Black Pepper Chicken", PANDA + "Mushroom Chicken"}},
                    {{SBUX + "Sausage, Cheddar & Egg Sandwich"},
                     {FIRE + "Firehouse Chopped Salad with Ham", FIRE + "Chocolate Chip Cookie"},
                     {DHW + "Cheese Pizza", DHW + "Grilled Chicken", DHW + "Potato Wedges"}},
                    {{SBUX + "Spinach, Feta & Egg White Wrap", CFA + "Fruit Cup"},
                     {CFA + "Chicken Sandwich", CFA + "Fruit Cup"},
                     {HALAL + "Halal Hummus Rice Bowl"}},
                    {{CFA + "Chicken Biscuit"},
                     {PANDA + "String Bean Chicken Breast", PANDA + "Kung Pao Chicken"},
                     {TACO + "Cheesy Bean and Rice Burrito"}},
                    {{SBUX + "Coffee Frappuccino (Grande)", SBUX + "Spinach, Feta & Egg White Wrap"},
                     {HALAL + "Halal Rice Bowl with Falafel"},
                     {DHW + "Spaghetti", DHW + "Beef Bolognese", DHW + "Roasted Brussels Sprouts"}},
                    {{SBUX + "Coffee Frappuccino (Grande)", SBUX + "Spinach, Feta & Egg White Wrap"},
                     {PANDA + "Black Pepper Chicken", PANDA + "String Bean Chicken Breast"},
                     {DHW + "Grilled Chicken", DHW + "Jasmine Rice", DHW + "Roasted Brussels Sprouts"}}},
            new int[]{4, 5, 3, 4, 5, 4, 3},
            new int[]{14200, 12800, 15600, 9600, 13400, 16100, 12500},
            new boolean[]{true, true, true, true, true, true, true});
    /**
     * TD-8 §4: cosmocomet (Cass): not a friend yet, only a pending request to alice. Mid-range levels so that if
     * alice accepts during the demo she is still first and last on no board (A-63). Today: water 1,500 ml,
     * 1,350 cal, 3 visits, 35 points.
     */
    private static final Week CASS_WEEK = new Week(
            new String[][][]{
                    {{SBUX + "Spinach, Feta & Egg White Wrap"},
                     {CFA + "Cool Wrap"},
                     {PANDA + "Mushroom Chicken", PANDA + "String Bean Chicken Breast"}},
                    {{CFA + "Chicken Biscuit"},
                     {FIRE + "Firehouse Chopped Salad with Ham"},
                     {TACO + "Chicken Chalupa", TACO + "Burrito Supreme"}},
                    {{SBUX + "Caramel Frappuccino (Grande)"},
                     {HALAL + "Halal Hummus Rice Bowl"},
                     {DHW + "Cheese Pizza", DHW + "Roasted Brussels Sprouts"}},
                    {{SBUX + "Spinach, Feta & Egg White Wrap", SBUX + "Caffe Americano (Grande)"},
                     {CFA + "Grilled Chicken Sandwich"},
                     {HALAL + "Halal Rice Bowl with Falafel"}},
                    {{CFA + "Fruit Cup", SBUX + "Mocha Frappuccino (Grande)"},
                     {PANDA + "Broccoli Beef", PANDA + "Black Pepper Chicken"},
                     {DHW + "Spaghetti", DHW + "Beef Bolognese"}},
                    {{SBUX + "Sausage, Cheddar & Egg Sandwich"},
                     {FIRE + "Firehouse Chopped Salad with Turkey"},
                     {TACO + "Steak Chalupa"}},
                    {{SBUX + "Spinach, Feta & Egg White Wrap", SBUX + "Caramel Frappuccino (Grande)"},
                     {FIRE + "Firehouse Chopped Salad with Ham"},
                     {TACO + "Chicken Chalupa"}}},
            new int[]{3, 2, 3, 4, 2, 3, 3},
            new int[]{6200, 9400, 10600, 7100, 5900, 8300, 7700},
            new boolean[]{false, true, false, true, false, false, true});

    public static final String SKY_SECRET = "COMETFITSTARRY23";
    public static final String NOVA_SECRET = "COMETFITNEBULA45";
    public static final String LUNA_SECRET = "COMETFITLUNAR567";
    public static final String THEO_SECRET = "COMETFITORBIT234";
    public static final String CASS_SECRET = "COMETFITCOSMO345";

    /** One seeded demo student (TD-2, TD-8 §1): username, display name, fixed TOTP secret, simulated watch, week. */
    public record DemoStudent(String username, String displayName, String secret, String deviceId, Week week) {}

    /** TD-8 §2: the six demo students who are all friends with each other (alice and bob from TD-2 first). */
    public static final List<DemoStudent> FRIEND_GROUP = List.of(
            new DemoStudent("alice", "Alice", ALICE_SECRET, "DEMO-WATCH-ALICE", ALICE_WEEK),
            new DemoStudent("bob", "Bob", BOB_SECRET, "DEMO-WATCH-BOB", BOB_WEEK),
            new DemoStudent("starryskies23", "Sky", SKY_SECRET, "DEMO-WATCH-SKY", SKY_WEEK),
            new DemoStudent("nebulanomad", "Nova", NOVA_SECRET, "DEMO-WATCH-NOVA", NOVA_WEEK),
            new DemoStudent("lunarlifter", "Luna", LUNA_SECRET, "DEMO-WATCH-LUNA", LUNA_WEEK),
            new DemoStudent("orbitrunner", "Theo", THEO_SECRET, "DEMO-WATCH-THEO", THEO_WEEK));
    /** TD-8 §4: sends alice a friend request that stays pending. */
    public static final DemoStudent REQUESTER =
            new DemoStudent("cosmocomet", "Cass", CASS_SECRET, "DEMO-WATCH-CASS", CASS_WEEK);

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!users.existsByUsernameIgnoreCase("dev")) {
            accounts.createDeveloper("dev", DEMO_PASSWORD, "dev@cometfit.local", "Dev Admin", "OPERATOR",
                    Permissions.DEVELOPER_DEFAULT, DEV_SECRET);
            log.info("Seeded demo developer 'dev' (TOTP secret {})", DEV_SECRET);
        }
        // A demo student and their history are created only when the account does not exist yet (fresh database or
        // after a reset), so restarting the server never duplicates demo data (TD-8 §5, A-64).
        Instant now = Instant.now();
        LocalDate today = clock.today();
        Map<String, DiningItem> dining = null;
        Map<String, Long> ids = new HashMap<>();
        Set<Long> created = new HashSet<>();
        List<DemoStudent> all = new ArrayList<>(FRIEND_GROUP);
        all.add(REQUESTER);
        for (DemoStudent s : all) {
            var existing = users.findByUsernameIgnoreCase(s.username());
            if (existing.isPresent()) {
                ids.put(s.username(), existing.get().getUserId());
                continue;
            }
            Long id = accounts.registerStudent(s.username(), DEMO_PASSWORD, s.username() + "@cometfit.local",
                    s.displayName(), s.secret()).user().getUserId();
            log.info("Seeded demo student '{}' (TOTP secret {})", s.username(), s.secret());
            ids.put(s.username(), id);
            created.add(id);
            if (dining == null) dining = diningItems();
            shareEverything(id);
            seedWeek(id, s.deviceId(), s.week(), today, now, dining);
        }
        if (created.isEmpty()) return;
        // TD-2 / TD-8 §2: every pair of the six is friends. Only pairs with a newly created member are added, so a
        // friendship a presenter removed on a kept database is not silently re-added on restart.
        Instant since = clock.startOf(today.minusDays(HISTORY_DAYS + 1));
        for (int i = 0; i < FRIEND_GROUP.size(); i++) {
            for (int j = i + 1; j < FRIEND_GROUP.size(); j++) {
                Long a = ids.get(FRIEND_GROUP.get(i).username());
                Long b = ids.get(FRIEND_GROUP.get(j).username());
                if ((created.contains(a) || created.contains(b)) && friendships.between(a, b).isEmpty()) {
                    friendships.save(new Friendship(a, b, since));
                }
            }
        }
        // TD-8 §4: one pending friend request cosmocomet -> alice, so alice's Friend requests list is not empty.
        Long cass = ids.get(REQUESTER.username());
        Long alice = ids.get("alice");
        if (created.contains(cass) && friendships.between(cass, alice).isEmpty()
                && !requests.existsBySenderIdAndReceiverIdAndStatus(cass, alice, FriendRequest.PENDING)) {
            requests.save(new FriendRequest(cass, alice));
        }
        log.info("Seeded TD-2/TD-8 demo data: {} new demo students with {} days of history, friend group of {}, "
                + "pending request cosmocomet -> alice", created.size(), HISTORY_DAYS + 1, FRIEND_GROUP.size());
    }

    /** TD-2: all data sharing on (calories, water, steps, UREC visits) for the seeded demo students only. */
    private void shareEverything(Long id) {
        ConsentSettings c = consents.findById(id).orElseGet(() -> new ConsentSettings(id));
        c.set(true, true, true, true);
        consents.save(c);
    }

    /** TD-6 §5: the UTD Dining items (stub feed) keyed "Location|Item", so demo meals point at real dining_items rows. */
    private Map<String, DiningItem> diningItems() {
        Map<String, DiningItem> dining = new HashMap<>();
        for (String loc : diningMenu.locations()) {
            for (DiningItem d : diningMenu.fetchItems(loc)) dining.put(d.getLocation() + "|" + d.getName(), d);
        }
        return dining;
    }

    private void seedWeek(Long student, String deviceId, Week w, LocalDate today, Instant now,
                          Map<String, DiningItem> dining) {
        SmartDevice device = devices.save(new SmartDevice(deviceId, "watch", "simulated"));
        DeviceLink link = new DeviceLink(student, device);
        link.pair();
        links.save(link);

        PointAccount account = pointAccounts.findById(student).orElseGet(() -> new PointAccount(student));
        for (int i = 0; i <= HISTORY_DAYS; i++) {
            LocalDate day = today.minusDays(HISTORY_DAYS - i);
            Instant start = clock.startOf(day);

            int[] hours = {8, 12, 18};
            for (int m = 0; m < 3; m++) {
                String[] plate = w.meals()[i][m];
                for (int k = 0; k < plate.length; k++) {
                    Instant t = at(start, hours[m], k * 2, now);
                    if (t == null) continue;
                    DiningItem d = dining.get(plate[k]);
                    if (d == null) throw new IllegalStateException("Demo meal item not in the UTD Dining feed: " + plate[k]);
                    // TD-6 §3: unpublished macros stay null, exactly as the dining item has them.
                    meals.save(new MealEntry(student, d.getName(), d.getCalories(), d.getProteinG(), d.getCarbsG(),
                            d.getFatG(), MealSource.DINING_MENU, d.getItemId(), t));
                }
            }
            int refills = w.refillsOf500ml()[i];
            for (int r = 0; r < refills; r++) {
                // Up to 4 refills at 9, 12, 15, 18 h (as before); TD-8's 5-6 refills every 2 h from 8 h (same day).
                Instant t = at(start, refills <= 4 ? 9 + r * 3 : 8 + r * 2, 0, now);
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

    /**
     * start-of-day + h:m. TD-8 §3 "Today's numbers must be filled in too": a time still in the future (only possible
     * today) is moved back to one minute before "now", but not before midnight, so today's whole plan is logged at
     * whatever time the demo data is reset (A-61; replaces the stage-2 rule "today's history stops at now"). Never
     * returns null; the callers' null checks are kept for safety.
     */
    private static Instant at(Instant startOfDay, int hour, int minute, Instant now) {
        Instant t = startOfDay.plus(Duration.ofHours(hour)).plus(Duration.ofMinutes(minute));
        if (!t.isAfter(now)) return t;
        Instant earlier = now.minus(Duration.ofMinutes(1));
        return earlier.isBefore(startOfDay) ? startOfDay : earlier;
    }
}
