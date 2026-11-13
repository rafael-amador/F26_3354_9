package cometfit.controllers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import cometfit.api.ApiException;
import cometfit.domain.ConsentSettings;
import cometfit.domain.ConsentSettingsRepository;
import cometfit.domain.FriendRequest;
import cometfit.domain.FriendRequestRepository;
import cometfit.domain.Friendship;
import cometfit.domain.FriendshipRepository;
import cometfit.security.AuthService;
import cometfit.security.Permissions;
import cometfit.security.Student;
import cometfit.security.User;
import cometfit.security.UserRepository;
import cometfit.services.PointsService;
import cometfit.services.PrivacyService;
import cometfit.services.PrivacyService.FriendStats;
import jakarta.validation.constraints.NotNull;

/**
 * SocialController — Deliverable 1 §5.2 "Controllers": sendRequest(targetId) : FriendRequest,
 * acceptRequest(requestId) : Friendship, setConsent(fields), getFriendStats(friendId) : FriendStats. Uses
 * PrivacyService. Follows SD-3 "View Friends' Progress (UC-3)". FR-3 (view own and others' profiles; friends'
 * statistics). §5.3.2 "Social Services: friends, leaderboard". The whole controller is the SOCIAL module (FR-5).
 */
@RestController
@RequestMapping("/api/social")
public class SocialController extends BaseController {
    private final PrivacyService privacyService;
    private final UserRepository users;
    private final FriendRequestRepository requests;
    private final FriendshipRepository friendships;
    private final ConsentSettingsRepository consents;
    private final PointsService points;

    public SocialController(AuthService authService, PrivacyService privacyService, UserRepository users,
                           FriendRequestRepository requests, FriendshipRepository friendships,
                           ConsentSettingsRepository consents, PointsService points) {
        super(authService);
        this.privacyService = privacyService;
        this.users = users;
        this.requests = requests;
        this.friendships = friendships;
        this.consents = consents;
        this.points = points;
    }

    public record SendRequest(@NotNull Long targetId) {}

    public record ConsentFields(boolean shareCalories, boolean shareWater, boolean shareSteps, boolean shareVisits) {
        static ConsentFields of(ConsentSettings c) {
            return new ConsentFields(c.isShareCalories(), c.isShareWater(), c.isShareSteps(), c.isShareVisits());
        }
    }

    /** Public profile of another user (FR-3). Private profiles show only the username to non-friends (A-22). */
    public record PublicProfile(Long userId, String username, String displayName, boolean isPublic,
                                String relation, Long pendingRequestId, Instant memberSince) {}

    public record RequestView(Long requestId, Long fromUserId, String fromUsername, Long toUserId, String toUsername,
                              String status, Instant sentAt) {}

    public record FriendView(Long userId, String username, String displayName, Instant since) {}

    public record LeaderboardEntry(int rank, Long userId, String username, String displayName, int points, boolean me) {}

    // ---- search & profiles (FR-3) --------------------------------------------------------------------------

    /** SD-3 "search username". */
    @GetMapping("/users/search")
    public List<PublicProfile> search(@RequestHeader("Authorization") String token, @RequestParam String q) {
        User me = requirePermission(token, Permissions.SOCIAL);
        if (q == null || q.trim().length() < 2) throw ApiException.badRequest("Type at least 2 characters");
        return users.searchStudents(q.trim()).stream().limit(20)
                .filter(u -> !u.getUserId().equals(me.getUserId()))
                .map(u -> profileOf(me, u)).toList();
    }

    /** RTM FR-3: "search for a user and open their profile". */
    @GetMapping("/users/{userId}")
    public PublicProfile profile(@RequestHeader("Authorization") String token, @PathVariable Long userId) {
        User me = requirePermission(token, Permissions.SOCIAL);
        User u = users.findById(userId).filter(x -> x instanceof Student)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        return profileOf(me, u);
    }

    private PublicProfile profileOf(User me, User u) {
        String relation;
        Long pending = null;
        if (u.getUserId().equals(me.getUserId())) relation = "SELF";
        else if (friendships.between(me.getUserId(), u.getUserId()).isPresent()) relation = "FRIEND";
        else {
            FriendRequest out = find(me.getUserId(), u.getUserId());
            FriendRequest in = find(u.getUserId(), me.getUserId());
            if (in != null) { relation = "REQUEST_RECEIVED"; pending = in.getRequestId(); }
            else if (out != null) { relation = "REQUEST_SENT"; pending = out.getRequestId(); }
            else relation = "NONE";
        }
        boolean full = u.isPublic() || relation.equals("FRIEND") || relation.equals("SELF");
        return new PublicProfile(u.getUserId(), u.getUsername(), full ? u.getDisplayName() : null, u.isPublic(),
                relation, pending, full ? u.getCreatedAt() : null);
    }

    private FriendRequest find(Long from, Long to) {
        return requests.findBySenderIdAndStatus(from, FriendRequest.PENDING).stream()
                .filter(r -> r.getReceiverId().equals(to)).findFirst().orElse(null);
    }

    // ---- friend requests -----------------------------------------------------------------------------------

    /**
     * sendRequest(targetId) : FriendRequest — SD-3 "sendRequest(targetId) -> insert FriendRequest (pending) ->
     * notify of request". Notification = the request appears in the friend's incoming list (A-23).
     */
    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public RequestView sendRequest(@RequestHeader("Authorization") String token, @RequestBody SendRequest r) {
        Student me = requireStudent(token, Permissions.SOCIAL);
        if (r == null || r.targetId() == null) throw ApiException.badRequest("targetId is required");
        Long target = r.targetId();
        if (target.equals(me.getUserId())) throw ApiException.badRequest("You cannot add yourself");
        User t = users.findById(target).filter(x -> x instanceof Student)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (friendships.between(me.getUserId(), target).isPresent()) throw ApiException.conflict("Already friends");
        if (requests.existsBySenderIdAndReceiverIdAndStatus(me.getUserId(), target, FriendRequest.PENDING))
            throw ApiException.conflict("Request already sent");
        if (requests.existsBySenderIdAndReceiverIdAndStatus(target, me.getUserId(), FriendRequest.PENDING))
            throw ApiException.conflict(t.getUsername() + " already sent you a request; accept it instead");
        return view(requests.save(new FriendRequest(me.getUserId(), target)));
    }

    @GetMapping("/requests")
    public List<RequestView> pendingRequests(@RequestHeader("Authorization") String token) {
        Student me = requireStudent(token, Permissions.SOCIAL);
        List<RequestView> out = new ArrayList<>();
        requests.findByReceiverIdAndStatus(me.getUserId(), FriendRequest.PENDING).forEach(r -> out.add(view(r)));
        requests.findBySenderIdAndStatus(me.getUserId(), FriendRequest.PENDING).forEach(r -> out.add(view(r)));
        return out;
    }

    /** acceptRequest(requestId) : Friendship — SD-3 "accept request -> acceptRequest(requestId) -> create Friendship". */
    @PostMapping("/requests/{requestId}/accept")
    @Transactional
    public FriendView acceptRequest(@RequestHeader("Authorization") String token, @PathVariable Long requestId) {
        Student me = requireStudent(token, Permissions.SOCIAL);
        FriendRequest req = pendingFor(me, requestId);
        Friendship f = friendships.save(req.accept());
        requests.save(req);
        User other = users.findById(req.getSenderId()).orElseThrow();
        return new FriendView(other.getUserId(), other.getUsername(), other.getDisplayName(), f.getSince());
    }

    /** FriendRequest.decline(). */
    @PostMapping("/requests/{requestId}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void declineRequest(@RequestHeader("Authorization") String token, @PathVariable Long requestId) {
        Student me = requireStudent(token, Permissions.SOCIAL);
        FriendRequest req = pendingFor(me, requestId);
        req.decline();
        requests.save(req);
    }

    private FriendRequest pendingFor(Student me, Long requestId) {
        FriendRequest req = requests.findById(requestId).orElseThrow(() -> ApiException.notFound("Request not found"));
        if (!req.getReceiverId().equals(me.getUserId())) throw ApiException.forbidden("Not your request");
        if (!FriendRequest.PENDING.equals(req.getStatus())) throw ApiException.conflict("Request is no longer pending");
        return req;
    }

    // ---- friends -------------------------------------------------------------------------------------------

    @GetMapping("/friends")
    public List<FriendView> friends(@RequestHeader("Authorization") String token) {
        Student me = requireStudent(token, Permissions.SOCIAL);
        return friendships.findByUserAIdOrUserBId(me.getUserId(), me.getUserId()).stream().map(f -> {
            User o = users.findById(f.otherThan(me.getUserId())).orElseThrow();
            return new FriendView(o.getUserId(), o.getUsername(), o.getDisplayName(), f.getSince());
        }).sorted(Comparator.comparing(FriendView::username)).toList();
    }

    /** UC-3 "Remove Friend". */
    @DeleteMapping("/friends/{friendId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void removeFriend(@RequestHeader("Authorization") String token, @PathVariable Long friendId) {
        Student me = requireStudent(token, Permissions.SOCIAL);
        Friendship f = friendships.between(me.getUserId(), friendId)
                .orElseThrow(() -> ApiException.notFound("Not friends"));
        friendships.delete(f);
    }

    // ---- consent -------------------------------------------------------------------------------------------

    @GetMapping("/consent")
    public ConsentFields getConsent(@RequestHeader("Authorization") String token) {
        Student me = requireStudent(token, Permissions.SOCIAL);
        return ConsentFields.of(consents.forStudent(me.getUserId()));
    }

    /** setConsent(fields) — SD-3 "choose fields to share -> setConsent(fields) -> save ConsentSettings". */
    @PutMapping("/consent")
    @Transactional
    public ConsentFields setConsent(@RequestHeader("Authorization") String token, @RequestBody ConsentFields fields) {
        Student me = requireStudent(token, Permissions.SOCIAL);
        ConsentSettings c = consents.forStudent(me.getUserId());
        c.set(fields.shareCalories(), fields.shareWater(), fields.shareSteps(), fields.shareVisits());
        return ConsentFields.of(consents.save(c));
    }

    // ---- statistics & leaderboard ----------------------------------------------------------------------------

    /**
     * getFriendStats(friendId) : FriendStats — SD-3 "open Friends' Progress -> getFriendStats(friendId) ->
     * filterByConsent(friendId, userId) -> consented stats only -> friend stats and leaderboard rank".
     */
    @GetMapping("/friends/{friendId}/stats")
    public FriendStats getFriendStats(@RequestHeader("Authorization") String token, @PathVariable Long friendId) {
        Student me = requireStudent(token, Permissions.SOCIAL);
        FriendStats stats = privacyService.filterByConsent(friendId, me.getUserId());
        Integer rank = leaderboardOf(me).stream().filter(e -> e.userId().equals(friendId))
                .map(LeaderboardEntry::rank).findFirst().orElse(null);
        return stats.withRank(rank);
    }

    /**
     * UC-3 "View Leaderboard" («extend» View Friend's Statistics). Ranks the student and their friends by point
     * balance; a friend is listed only if their consent makes points visible (A-21).
     */
    @GetMapping("/leaderboard")
    public List<LeaderboardEntry> leaderboard(@RequestHeader("Authorization") String token) {
        return leaderboardOf(requireStudent(token, Permissions.SOCIAL));
    }

    private List<LeaderboardEntry> leaderboardOf(Student me) {
        record Row(User u, int pts) {}
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(me, points.getBalance(me.getUserId())));
        for (Long id : friendships.friendIdsOf(me.getUserId())) {
            if (!PrivacyService.sharesPoints(consents.forStudent(id))) continue;
            users.findById(id).ifPresent(u -> rows.add(new Row(u, points.getBalance(id))));
        }
        rows.sort(Comparator.comparingInt(Row::pts).reversed().thenComparing(r -> r.u().getUsername()));
        List<LeaderboardEntry> out = new ArrayList<>();
        int rank = 0, prevPts = Integer.MIN_VALUE;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (r.pts() != prevPts) { rank = i + 1; prevPts = r.pts(); }
            out.add(new LeaderboardEntry(rank, r.u().getUserId(), r.u().getUsername(), r.u().getDisplayName(), r.pts(),
                    r.u().getUserId().equals(me.getUserId())));
        }
        return out;
    }

    private RequestView view(FriendRequest r) {
        User from = users.findById(r.getSenderId()).orElseThrow();
        User to = users.findById(r.getReceiverId()).orElseThrow();
        return new RequestView(r.getRequestId(), from.getUserId(), from.getUsername(), to.getUserId(), to.getUsername(),
                r.getStatus(), r.getSentAt());
    }
}
