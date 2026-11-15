package cometfit.controllers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import cometfit.api.ApiException;
import cometfit.domain.ActivityRecord;
import cometfit.domain.ActivityRecordRepository;
import cometfit.domain.DeviceLink;
import cometfit.domain.DeviceLinkRepository;
import cometfit.domain.PointTransaction;
import cometfit.domain.RecVisit;
import cometfit.domain.RecVisitRepository;
import cometfit.domain.SmartDevice;
import cometfit.domain.SmartDeviceRepository;
import cometfit.domain.UrecCardReaderRepository;
import cometfit.security.AuthService;
import cometfit.security.IntegrationKeyVerifier;
import cometfit.security.Permissions;
import cometfit.security.Student;
import cometfit.security.User;
import cometfit.security.UserRepository;
import cometfit.services.Clock;
import cometfit.services.PointsService;
import cometfit.services.SmartDeviceGateway;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * ActivityController — Deliverable 1 §5.2 "Controllers": linkDevice(deviceId) : DeviceLink,
 * pushSteps(steps, heartRate, ts), recordVisit(studentId, ts) : RecVisit. Uses PointsService.
 * Follows SD-2 "Track Activity and Rec Center Visits (UC-2)". FR-4 (smart devices), FR-7 (rec center visits).
 * §5.3.2 "Activity Service: UREC, points". The whole controller is the ACTIVITY module (FR-5).
 */
@RestController
@RequestMapping("/api/activity")
public class ActivityController extends BaseController {
    private final SmartDeviceGateway deviceGateway;
    private final PointsService pointsService;
    private final SmartDeviceRepository devices;
    private final DeviceLinkRepository links;
    private final ActivityRecordRepository records;
    private final RecVisitRepository visits;
    private final UrecCardReaderRepository readers;
    private final UserRepository users;
    private final IntegrationKeyVerifier integrationKey;
    private final Clock clock;

    public ActivityController(AuthService authService, SmartDeviceGateway deviceGateway, PointsService pointsService,
                              SmartDeviceRepository devices, DeviceLinkRepository links, ActivityRecordRepository records,
                              RecVisitRepository visits, UrecCardReaderRepository readers, UserRepository users,
                              IntegrationKeyVerifier integrationKey, Clock clock) {
        super(authService);
        this.deviceGateway = deviceGateway;
        this.pointsService = pointsService;
        this.devices = devices;
        this.links = links;
        this.records = records;
        this.visits = visits;
        this.readers = readers;
        this.users = users;
        this.integrationKey = integrationKey;
        this.clock = clock;
    }

    public record LinkDeviceRequest(@NotBlank @Size(max = 80) String deviceId,
                                    @NotBlank @Size(max = 40) String deviceType,
                                    @NotBlank @Size(max = 40) String platform,
                                    boolean healthPermissionGranted) {}

    public record DeviceLinkView(Long linkId, String deviceId, String deviceType, String platform, String status,
                                 Instant linkedAt) {
        static DeviceLinkView of(DeviceLink l) {
            return new DeviceLinkView(l.getId(), l.getDevice().getDeviceId(), l.getDevice().getDeviceType(),
                    l.getDevice().getPlatform(), l.getStatus(), l.getLinkedAt());
        }
    }

    public record PushStepsRequest(@NotBlank String deviceId, @NotNull @Min(0) @Max(100000) Integer steps,
                                   @Min(20) @Max(250) Integer heartRate, Instant timestamp) {}

    public record PushStepsResult(ActivityRecord record, int stepsToday, int stepGoal, PointTransaction pointsAwarded,
                                  int balance) {}

    public record RecordVisitRequest(@NotNull Long studentId, @NotBlank String readerId, Instant timestamp) {}

    public record VisitResult(RecVisit visit, PointTransaction pointsAwarded, int balance, String message) {}

    public record VisitHistory(List<RecVisit> visits, int visitsThisWeek, int balance, List<PointTransaction> transactions) {}

    // ---- Connect and sync device ------------------------------------------------------------------------

    /**
     * linkDevice(deviceId) : DeviceLink — SD-2 "request health permissions / grant permission / linkDevice(deviceId)
     * -> pair (Bluetooth / cloud) -> pairing confirmed -> save DeviceLink". UC-2 "Connect Smart Device" «include»
     * "Grant Health Permissions". FR-4.
     */
    @PostMapping("/devices")
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceLinkView linkDevice(@RequestHeader("Authorization") String token, @Valid @RequestBody LinkDeviceRequest r) {
        Student s = requireStudent(token, Permissions.ACTIVITY);
        if (!r.healthPermissionGranted()) {
            throw ApiException.badRequest("Health data permission must be granted before a device can be linked");
        }
        if (!deviceGateway.pair(r.deviceId(), r.deviceType(), r.platform())) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAIRING_FAILED", "The device did not confirm pairing");
        }
        SmartDevice device = devices.findByDeviceId(r.deviceId())
                .orElseGet(() -> devices.save(new SmartDevice(r.deviceId(), r.deviceType(), r.platform())));
        boolean linkedElsewhere = links.findByDevice_DeviceIdAndStatus(r.deviceId(), DeviceLink.LINKED).stream()
                .anyMatch(l -> !l.getStudentId().equals(s.getUserId()));
        if (linkedElsewhere) throw ApiException.conflict("This device is linked to another account");
        DeviceLink link = links.findByStudentIdAndDevice_DeviceId(s.getUserId(), r.deviceId())
                .orElseGet(() -> new DeviceLink(s.getUserId(), device));
        link.pair();
        return DeviceLinkView.of(links.save(link));
    }

    @GetMapping("/devices")
    public List<DeviceLinkView> devices(@RequestHeader("Authorization") String token) {
        Student s = requireStudent(token, Permissions.ACTIVITY);
        return links.findByStudentIdAndStatus(s.getUserId(), DeviceLink.LINKED).stream().map(DeviceLinkView::of).toList();
    }

    /** DeviceLink.unlink(). */
    @DeleteMapping("/devices/{deviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlinkDevice(@RequestHeader("Authorization") String token, @PathVariable String deviceId) {
        Student s = requireStudent(token, Permissions.ACTIVITY);
        DeviceLink link = links.findByStudentIdAndDevice_DeviceId(s.getUserId(), deviceId)
                .orElseThrow(() -> ApiException.notFound("Device not linked"));
        link.unlink();
        links.save(link);
    }

    /**
     * pushSteps(steps, heartRate, ts) — SD-2 loop [periodic sync] "pushSteps(steps, heartRate, timestamp) -> insert
     * ActivityRecord". The device's data reaches the server through the student's phone (§5.3.2 "Smart device
     * connection via Bluetooth -> Client"), so the call carries the student's session. UC-2 "Sync Steps &amp;
     * Movement Data" «extend» "Earn Points": reaching the daily step goal awards points once per day (A-20).
     */
    @PostMapping("/steps")
    @ResponseStatus(HttpStatus.CREATED)
    public PushStepsResult pushSteps(@RequestHeader("Authorization") String token, @Valid @RequestBody PushStepsRequest r) {
        Student s = requireStudent(token, Permissions.ACTIVITY);
        DeviceLink link = links.findByStudentIdAndDevice_DeviceId(s.getUserId(), r.deviceId())
                .filter(DeviceLink::isLinked)
                .orElseThrow(() -> ApiException.badRequest("Device is not linked; connect it first"));
        Instant ts = r.timestamp() == null ? Instant.now() : r.timestamp();
        if (ts.isAfter(Instant.now().plusSeconds(300))) throw ApiException.badRequest("Timestamp is in the future");
        ActivityRecord rec = records.save(new ActivityRecord(s.getUserId(), link.getDevice().getId(), r.steps(),
                r.heartRate(), ts));

        LocalDate today = clock.today();
        int stepsToday = records.findByStudentIdAndTimestampBetweenOrderByTimestampDesc(
                s.getUserId(), clock.startOf(today), clock.endOf(today)).stream().mapToInt(ActivityRecord::getSteps).sum();
        PointTransaction awarded = null;
        if (stepsToday >= s.getStepGoal() && s.getStepGoal() > 0
                && !pointsService.alreadyAwarded(s.getUserId(), PointsService.STEP_GOAL, clock.startOf(today), clock.endOf(today))) {
            awarded = pointsService.awardPoints(s.getUserId(), PointsService.STEP_GOAL);
        }
        return new PushStepsResult(rec, stepsToday, s.getStepGoal(), awarded, pointsService.getBalance(s.getUserId()));
    }

    @GetMapping("/records")
    public List<ActivityRecord> records(@RequestHeader("Authorization") String token,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Student s = requireStudent(token, Permissions.ACTIVITY);
        LocalDate d = date == null ? clock.today() : date;
        return records.findByStudentIdAndTimestampBetweenOrderByTimestampDesc(s.getUserId(), clock.startOf(d), clock.endOf(d));
    }

    // ---- Rec center check-in -------------------------------------------------------------------------------

    /**
     * HTTP entry point for the external UREC card reader (§5.3.2 External Systems). Authenticated with the
     * integration key (A-12), not a student session.
     */
    @PostMapping("/visits")
    @ResponseStatus(HttpStatus.CREATED)
    public VisitResult readerSwipe(@RequestHeader(value = IntegrationKeyVerifier.HEADER, required = false) String key,
                                   @Valid @RequestBody RecordVisitRequest r) {
        integrationKey.verify(key);
        return recordVisit(r.studentId(), r.readerId(), r.timestamp() == null ? Instant.now() : r.timestamp());
    }

    /**
     * recordVisit(studentId, ts) : RecVisit — SD-2 "recordVisit(studentId, time) -> insert RecVisit ->
     * awardPoints(studentId, VISIT) -> update point balance -> new balance -> visit logged, points earned". FR-7.
     * Points for the first visit of each day only (A-20). studentId = CometFit user id on the card (A-27).
     */
    public VisitResult recordVisit(Long studentId, String readerId, Instant ts) {
        User u = users.findById(studentId).orElseThrow(() -> ApiException.notFound("Unknown student card"));
        if (!(u instanceof Student)) throw ApiException.badRequest("Card does not belong to a student");
        if (!readers.existsById(readerId)) throw ApiException.forbidden("Unknown card reader " + readerId);
        LocalDate day = clock.dateOf(ts);
        boolean firstToday = visits.countByStudentIdAndTimestampBetween(studentId, clock.startOf(day), clock.endOf(day)) == 0;
        RecVisit v = visits.save(new RecVisit(studentId, readerId, ts));
        PointTransaction pts = firstToday ? pointsService.awardPoints(studentId, PointsService.VISIT) : null;
        String msg = pts != null ? "Visit logged, +" + pts.getAmount() + " points"
                : "Visit logged (points are earned once per day)";
        return new VisitResult(v, pts, pointsService.getBalance(studentId), msg);
    }

    /** UC-2 "View Visit History &amp; Point Balance"; SD-2 "show visit history and points". FR-7. */
    @GetMapping("/visits")
    public VisitHistory visitHistory(@RequestHeader("Authorization") String token) {
        Student s = requireStudent(token, Permissions.ACTIVITY);
        List<RecVisit> all = visits.findByStudentIdOrderByTimestampDesc(s.getUserId());
        LocalDate today = clock.today();
        Instant weekStart = clock.startOf(today.minusDays(6));
        int week = (int) all.stream().filter(v -> !v.getTimestamp().isBefore(weekStart)).count();
        return new VisitHistory(all, week, pointsService.getBalance(s.getUserId()), pointsService.recent(s.getUserId()));
    }
}
