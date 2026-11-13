package cometfit.stubs;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import cometfit.controllers.ActivityController;
import cometfit.controllers.ActivityController.VisitResult;
import cometfit.controllers.BaseController;
import cometfit.security.AuthService;
import cometfit.security.Permissions;
import cometfit.security.Student;

/**
 * *** STUB *** of the external "UREC Card Reader" actor (UC-2 "Check In at UREC"; SD-2 "swipe student card ->
 * recordVisit(studentId, time)"; §5.2 UrecCardReader.sendSwipe(studentId)).
 * There is no card hardware in a laptop demo, so the student's app offers a "simulate card swipe" button which
 * calls this endpoint; the stub then behaves exactly like the reader would and calls
 * ActivityController.recordVisit. It lives under /api/activity so the ACTIVITY feature flag still applies.
 * A real reader would call POST /api/activity/visits with the integration key instead.
 */
@RestController
@RequestMapping("/api/activity/stub-reader")
public class FakeUrecCardReader extends BaseController {
    public static final String DEFAULT_READER_ID = "UREC-FRONT-1";

    private final ActivityController activityController;

    public FakeUrecCardReader(AuthService authService, ActivityController activityController) {
        super(authService);
        this.activityController = activityController;
    }

    /** sendSwipe(studentId) for the signed-in student's own card. */
    @PostMapping("/swipe")
    @ResponseStatus(HttpStatus.CREATED)
    public VisitResult sendSwipe(@RequestHeader("Authorization") String token,
                                 @RequestBody(required = false) Map<String, String> body) {
        Student s = requireStudent(token, Permissions.ACTIVITY);
        String reader = body != null && body.get("readerId") != null ? body.get("readerId") : DEFAULT_READER_ID;
        return activityController.recordVisit(s.getUserId(), reader, Instant.now());
    }
}
