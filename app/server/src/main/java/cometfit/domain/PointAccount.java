package cometfit.domain;

import jakarta.persistence.*;

/**
 * PointAccount — §5.2 Domain Data: balance; operation addPoints(amount).
 * Student 1 "owns" 1 PointAccount; PointAccount "records" 0..* PointTransaction.
 * Proposal §3 "point based system that rewards students"; SD-2 "update point balance".
 */
@Entity
@Table(name = "point_accounts")
public class PointAccount {
    @Id @Column(name = "student_id")
    private Long studentId;
    @Column(nullable = false)
    private int balance;

    protected PointAccount() {}

    public PointAccount(Long studentId) {
        this.studentId = studentId;
    }

    public void addPoints(int amount) {
        this.balance += amount;
    }

    public Long getStudentId() { return studentId; }
    public int getBalance() { return balance; }
}
