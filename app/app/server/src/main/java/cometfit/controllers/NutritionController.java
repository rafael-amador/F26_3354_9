package cometfit.controllers;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import cometfit.api.ApiException;
import cometfit.domain.DiningItem;
import cometfit.domain.DiningItemRepository;
import cometfit.domain.MealEntry;
import cometfit.domain.MealEntryRepository;
import cometfit.domain.MealSource;
import cometfit.domain.WaterEntry;
import cometfit.domain.WaterEntryRepository;
import cometfit.security.AuthService;
import cometfit.security.Permissions;
import cometfit.security.Student;
import cometfit.services.AIFoodService;
import cometfit.services.AIFoodService.FoodEstimate;
import cometfit.services.Clock;
import cometfit.services.DailySummaryService;
import cometfit.services.DailySummaryService.DailySummary;
import cometfit.services.DiningMenuService;
import cometfit.services.UtdDiningFeed;
import cometfit.services.WaterLogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * NutritionController — Deliverable 1 §5.2 "Controllers": submitMealPhoto(image) : MealEntry,
 * getMenu(location) : List&lt;DiningItem&gt;, saveMealEntry(entry), saveWaterEntry(volumeMl). Uses AIFoodService
 * and DiningMenuService. Follows SD-1 "Track Nutrition and Hydration (UC-1)".
 * FR-2 (calories), FR-6 (water), FR-8 (UTD Dining nutrition info). §5.3.2 "Nutrition Service: meals, water,
 * dining menu". The whole controller is the NUTRITION module (FR-5).
 */
@RestController
@RequestMapping("/api/nutrition")
public class NutritionController extends BaseController {
    private final AIFoodService aiFoodService;
    private final DiningMenuService diningMenuService;
    private final DailySummaryService summaries;
    private final MealEntryRepository meals;
    private final WaterEntryRepository water;
    private final DiningItemRepository diningItems;
    private final WaterLogService waterLog;
    private final Clock clock;

    public NutritionController(AuthService authService, AIFoodService aiFoodService, DiningMenuService diningMenuService,
                               DailySummaryService summaries, MealEntryRepository meals, WaterEntryRepository water,
                               DiningItemRepository diningItems, WaterLogService waterLog, Clock clock) {
        super(authService);
        this.aiFoodService = aiFoodService;
        this.diningMenuService = diningMenuService;
        this.summaries = summaries;
        this.meals = meals;
        this.water = water;
        this.diningItems = diningItems;
        this.waterLog = waterLog;
        this.clock = clock;
    }

    /** Body of saveMealEntry(entry). For DINING_MENU entries the macros are taken from the DiningItem. */
    public record MealEntryRequest(@Size(max = 120) String foodName,
                                   @Min(0) @Max(5000) Integer calories,
                                   @DecimalMin("0") @DecimalMax("1000") Double proteinG,
                                   @DecimalMin("0") @DecimalMax("1000") Double carbsG,
                                   @DecimalMin("0") @DecimalMax("1000") Double fatG,
                                   @NotNull MealSource source,
                                   Long diningItemId) {}

    /**
     * Body of saveWaterEntry. {@code amount} + {@code isLiter} = team repo code LogWater.enter(userId, amount, isLiter)
     * (liters or gallons; isLiter defaults to true). {@code volumeMl} = D1/D2 class diagram saveWaterEntry(volumeMl),
     * kept as an alternative input in milliliters (A-35). Validation messages come from WaterLogService.
     */
    public record WaterRequest(Double amount, Boolean isLiter, Integer volumeMl) {}

    /** Save result for water: entry + updated summary + the LogWater message "Added 1.13L to Jane Doe". */
    public record WaterSaveResult(WaterEntry saved, DailySummary summary, String message) {}

    /** "show estimate for review" (SD-1): the unsaved MealEntry draft plus the individual recognised foods. */
    public record PhotoEstimate(MealEntryView draft, List<FoodEstimate> foodItems, String notice) {}

    /** Macros are null when UTD Dining did not publish them (TD-6 §3); the client shows "not published". */
    public record MealEntryView(Long entryId, String foodName, int calories, Double proteinG, Double carbsG,
                                Double fatG, MealSource source, Long diningItemId, Instant loggedAt) {
        static MealEntryView of(MealEntry e) {
            return new MealEntryView(e.getEntryId(), e.getFoodName(), e.getCalories(), e.getProteinG(), e.getCarbsG(),
                    e.getFatG(), e.getSource(), e.getDiningItemId(), e.getLoggedAt());
        }
    }

    public record SaveResult<T>(T saved, DailySummary summary) {}

    // ---- alt [Photo entry] -------------------------------------------------------------------------------

    /**
     * submitMealPhoto(image) : MealEntry — SD-1 "submitMealPhoto(image) -> estimateMacros(image) -> show estimate
     * for review". Nothing is stored yet: the student confirms / edits the estimate and the client then calls
     * saveMealEntry with source PHOTO ("confirm / edit estimate").
     */
    @PostMapping(value = "/meals/photo", consumes = "multipart/form-data")
    public PhotoEstimate submitMealPhoto(@RequestHeader("Authorization") String token,
                                         @RequestParam("image") MultipartFile image) throws IOException {
        Student s = requireStudent(token, Permissions.NUTRITION);
        if (image.isEmpty()) throw ApiException.badRequest("Image is empty");
        String type = image.getContentType() == null ? "" : image.getContentType();
        if (!type.startsWith("image/")) throw ApiException.badRequest("File must be an image");
        List<FoodEstimate> items = aiFoodService.estimateMacros(image.getBytes());
        if (items.isEmpty()) throw ApiException.badRequest("No food recognised in the photo; enter it manually");
        String name = String.join(" + ", items.stream().map(FoodEstimate::foodName).toList());
        MealEntry draft = new MealEntry(s.getUserId(), name.length() > 120 ? name.substring(0, 120) : name,
                items.stream().mapToInt(FoodEstimate::calories).sum(),
                items.stream().mapToDouble(FoodEstimate::proteinG).sum(),
                items.stream().mapToDouble(FoodEstimate::carbsG).sum(),
                items.stream().mapToDouble(FoodEstimate::fatG).sum(),
                MealSource.PHOTO, null, Instant.now());
        return new PhotoEstimate(MealEntryView.of(draft), items,
                "Estimate only. Review and edit before saving. CometFit is a general wellness app.");
    }

    // ---- alt [Dining menu item] ---------------------------------------------------------------------------

    @GetMapping("/menu/locations")
    public List<String> menuLocations(@RequestHeader("Authorization") String token) {
        requireStudent(token, Permissions.NUTRITION);
        return diningMenuService.locations();
    }

    /**
     * TD-6 §2: each location with its building ("show the building next to each location name"), menu label and
     * source URL. Separate from /menu/locations so that endpoint keeps returning plain names (A-54).
     */
    @GetMapping("/menu/location-info")
    public List<UtdDiningFeed.Location> menuLocationInfo(@RequestHeader("Authorization") String token) {
        requireStudent(token, Permissions.NUTRITION);
        return diningMenuService.locationDetails();
    }

    /** getMenu(location) : List&lt;DiningItem&gt; — SD-1 "getMenu(location) -> fetchItems(location) -> menu list". FR-8. */
    @GetMapping("/menu")
    public List<DiningItem> getMenu(@RequestHeader("Authorization") String token, @RequestParam String location) {
        requireStudent(token, Permissions.NUTRITION);
        return diningMenuService.fetchItems(location);
    }

    // ---- after alt: saveMealEntry(entry) ------------------------------------------------------------------

    /** saveMealEntry(entry) — SD-1 "saveMealEntry(entry) -> insert MealEntry -> ok". FR-2. */
    @PostMapping("/meals")
    @ResponseStatus(HttpStatus.CREATED)
    public SaveResult<MealEntryView> saveMealEntry(@RequestHeader("Authorization") String token,
                                                   @Valid @RequestBody MealEntryRequest r) {
        Student s = requireStudent(token, Permissions.NUTRITION);
        MealEntry e;
        if (r.source() == MealSource.DINING_MENU) {
            if (r.diningItemId() == null) throw ApiException.badRequest("diningItemId is required for DINING_MENU");
            DiningItem d = diningItems.findById(r.diningItemId())
                    .orElseThrow(() -> ApiException.notFound("Dining item not found"));
            // TD-6 §3: unpublished macros are copied as null, not 0, so the log still says "not published".
            e = new MealEntry(s.getUserId(), d.getName(), d.getCalories(), d.getProteinG(), d.getCarbsG(), d.getFatG(),
                    MealSource.DINING_MENU, d.getItemId(), Instant.now());
        } else {
            if (r.foodName() == null || r.foodName().isBlank()) throw ApiException.badRequest("foodName is required");
            if (r.calories() == null) throw ApiException.badRequest("calories is required");
            e = new MealEntry(s.getUserId(), r.foodName().trim(), r.calories(), nz(r.proteinG()), nz(r.carbsG()),
                    nz(r.fatG()), r.source(), null, Instant.now());
        }
        e = meals.save(e);
        return new SaveResult<>(MealEntryView.of(e), summaries.getDailySummary(s, clock.today()));
    }

    @GetMapping("/meals")
    public List<MealEntryView> meals(@RequestHeader("Authorization") String token,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Student s = requireStudent(token, Permissions.NUTRITION);
        LocalDate d = date == null ? clock.today() : date;
        return meals.findByStudentIdAndLoggedAtBetweenOrderByLoggedAtDesc(s.getUserId(), clock.startOf(d), clock.endOf(d))
                .stream().map(MealEntryView::of).toList();
    }

    // ---- water ---------------------------------------------------------------------------------------------

    /**
     * saveWaterEntry(volumeMl) — SD-1 "log water (bottle size / refill) -> saveWaterEntry(volume) -> insert WaterEntry".
     * FR-6. The unit itself is team repo code LogWater.enter (liters or gallons, stored as liters): WaterLogService.
     */
    @PostMapping("/water")
    @ResponseStatus(HttpStatus.CREATED)
    public WaterSaveResult saveWaterEntry(@RequestHeader("Authorization") String token,
                                          @RequestBody WaterRequest r) {
        Student s = requireStudent(token, Permissions.NUTRITION);
        Double amount = r.amount();
        boolean isLiter = r.isLiter() == null || r.isLiter();
        if (amount == null && r.volumeMl() != null) {   // class-diagram form: milliliters -> liters
            amount = r.volumeMl() / 1000.0;
            isLiter = true;
        }
        WaterLogService.Logged logged = waterLog.enter(s.getUserId(), amount, isLiter);
        return new WaterSaveResult(logged.entry(), summaries.getDailySummary(s, clock.today()), logged.message());
    }

    @GetMapping("/water")
    public List<WaterEntry> waterEntries(@RequestHeader("Authorization") String token,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Student s = requireStudent(token, Permissions.NUTRITION);
        LocalDate d = date == null ? clock.today() : date;
        return water.findByStudentIdAndLoggedAtBetweenOrderByLoggedAtDesc(s.getUserId(), clock.startOf(d), clock.endOf(d));
    }

    // ---- daily summary -------------------------------------------------------------------------------------

    /** UC-1 "View Daily Summary &amp; Goals"; SD-1 "updated daily summary". */
    @GetMapping("/summary")
    public DailySummary summary(@RequestHeader("Authorization") String token,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Student s = requireStudent(token, Permissions.NUTRITION);
        return summaries.getDailySummary(s, date == null ? clock.today() : date);
    }

    private static double nz(Double d) { return d == null ? 0 : d; }
}
