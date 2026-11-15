package cometfit.services;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cometfit.api.ApiException;
import cometfit.domain.ConsentSettings;
import cometfit.domain.ConsentSettingsRepository;
import cometfit.domain.FriendshipRepository;
import cometfit.domain.RecVisitRepository;
import cometfit.security.Student;
import cometfit.security.User;
import cometfit.security.UserRepository;

/**
 * PrivacyService — Deliverable 1 §5.2 "Services": filterByConsent(friendId, userId) : FriendStats.
 * SD-3 "filterByConsent(friendId, userId) -> read ConsentSettings -> shared fields -> read stats for shared fields
 * -> stats -> consented stats only". NFR Legislative-Privacy: "Users will have to consent to sharing specific fields
 * of data before it is available for others to see". Unshared fields are returned as null — they are never read.
 */
@Service
public class PrivacyService {
    private final ConsentSettingsRepository consents;
    private final FriendshipRepository friendships;
    private final UserRepository users;
    private final DailySummaryService summaries;
    private final RecVisitRepository visits;
    private final PointsService points;
    private final Clock clock;

    public PrivacyService(ConsentSettingsRepository consents, FriendshipRepository friendships, UserRepository users,
                          DailySummaryService summaries, RecVisitRepository visits, PointsService points, Clock clock) {
        this.consents = consents;
        this.friendships = friendships;
        this.users = users;
        this.summaries = summaries;
        this.visits = visits;
        this.points = points;
        this.clock = clock;
    }

    /** FriendStats (return type of filterByConsent / getFriendStats in §5.2). Null = not shared. */
    public record FriendStats(Long userId, String username, String displayName, Integer caloriesToday,
                              Integer calorieGoal, Integer waterTodayMl, Integer waterGoalMl, Integer stepsToday,
                              Integer stepGoal, Integer visitsThisWeek, Integer points, Integer leaderboardRank) {
        public FriendStats withRank(Integer rank) {
            return new FriendStats(userId, username, displayName, caloriesToday, calorieGoal, waterTodayMl, waterGoalMl,
                    stepsToday, stepGoal, visitsThisWeek, points, rank);
        }
    }

    @Transactional
    public FriendStats filterByConsent(Long friendId, Long userId) {
        if (!friendId.equals(userId) && friendships.between(friendId, userId).isEmpty()) {
            throw ApiException.forbidden("You can only view statistics of your friends");
        }
        User u = users.findById(friendId).orElseThrow(() -> ApiException.notFound("User not found"));
        if (!(u instanceof Student friend)) throw ApiException.notFound("User not found");
        ConsentSettings c = friendId.equals(userId) ? allShared(friendId) : consents.forStudent(friendId);

        LocalDate today = clock.today();
        DailySummaryService.DailySummary sum = (c.isShareCalories() || c.isShareWater() || c.isShareSteps())
                ? summaries.getDailySummary(friend, today) : null;
        Integer visitsWeek = null;
        if (c.isShareVisits()) {
            Instant from = clock.startOf(today.minusDays(6));
            visitsWeek = (int) visits.countByStudentIdAndTimestampBetween(friendId, from, clock.endOf(today));
        }
        return new FriendStats(friend.getUserId(), friend.getUsername(), friend.getDisplayName(),
                c.isShareCalories() ? sum.calories() : null, c.isShareCalories() ? sum.calorieGoal() : null,
                c.isShareWater() ? sum.waterMl() : null, c.isShareWater() ? sum.waterGoalMl() : null,
                c.isShareSteps() ? sum.steps() : null, c.isShareSteps() ? sum.stepGoal() : null,
                visitsWeek, sharesPoints(c) ? points.getBalance(friendId) : null, null);
    }

    /** Points come only from visits and steps, so the balance is visible only if one of those is shared (A-21). */
    public static boolean sharesPoints(ConsentSettings c) {
        return c.isShareVisits() || c.isShareSteps();
    }

    private static ConsentSettings allShared(Long id) {
        ConsentSettings c = new ConsentSettings(id);
        c.set(true, true, true, true);
        return c;
    }
}
