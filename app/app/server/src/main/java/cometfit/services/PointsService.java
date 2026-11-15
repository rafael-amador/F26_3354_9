package cometfit.services;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cometfit.domain.PointAccount;
import cometfit.domain.PointAccountRepository;
import cometfit.domain.PointTransaction;
import cometfit.domain.PointTransactionRepository;

/**
 * PointsService — Deliverable 1 §5.2 "Services": awardPoints(studentId, reason) : PointTransaction,
 * getBalance(studentId) : int. SD-2 "awardPoints(studentId, VISIT) -> update point balance -> new balance".
 * UC-2 "Earn Points" (included by "Check In at UREC", extended by "Sync Steps &amp; Movement Data").
 * Proposal §3: "point based system that rewards students ... swiping your card at the UREC center or completing
 * tasks that encourage activity". Point values: A-20.
 */
@Service
public class PointsService {
    public static final String VISIT = "VISIT";
    public static final String STEP_GOAL = "STEP_GOAL";
    public static final int VISIT_POINTS = 10;
    public static final int STEP_GOAL_POINTS = 5;

    private final PointAccountRepository accounts;
    private final PointTransactionRepository transactions;

    public PointsService(PointAccountRepository accounts, PointTransactionRepository transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }

    public static int pointsFor(String reason) {
        return switch (reason) {
            case VISIT -> VISIT_POINTS;
            case STEP_GOAL -> STEP_GOAL_POINTS;
            default -> throw new IllegalArgumentException("Unknown point reason " + reason);
        };
    }

    @Transactional
    public PointTransaction awardPoints(Long studentId, String reason) {
        PointAccount acct = accounts.findForUpdate(studentId).orElseGet(() -> accounts.save(new PointAccount(studentId)));
        int amount = pointsFor(reason);
        acct.addPoints(amount);
        accounts.save(acct);
        return transactions.save(new PointTransaction(studentId, amount, reason, Instant.now()));
    }

    @Transactional(readOnly = true)
    public int getBalance(Long studentId) {
        return accounts.findById(studentId).map(PointAccount::getBalance).orElse(0);
    }

    @Transactional(readOnly = true)
    public List<PointTransaction> recent(Long studentId) {
        return transactions.findTop20ByStudentIdOrderByTimestampDesc(studentId);
    }

    @Transactional(readOnly = true)
    public boolean alreadyAwarded(Long studentId, String reason, Instant from, Instant to) {
        return transactions.existsByStudentIdAndReasonAndTimestampBetween(studentId, reason, from, to);
    }
}
