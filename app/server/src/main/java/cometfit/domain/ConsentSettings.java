package cometfit.domain;

import jakarta.persistence.*;

/**
 * ConsentSettings — §5.2 Domain Data: shareCalories, shareWater, shareSteps, shareVisits.
 * Student 1 "configures" 1 ConsentSettings. UC-3 "Set Data-Sharing Consent"; SD-3 "save ConsentSettings".
 * NFR Legislative-Privacy: nothing is shared unless the user consents, so every flag defaults to false (A-18).
 */
@Entity
@Table(name = "consent_settings")
public class ConsentSettings {
    @Id @Column(name = "student_id")
    private Long studentId;
    @Column(name = "share_calories", nullable = false)
    private boolean shareCalories;
    @Column(name = "share_water", nullable = false)
    private boolean shareWater;
    @Column(name = "share_steps", nullable = false)
    private boolean shareSteps;
    @Column(name = "share_visits", nullable = false)
    private boolean shareVisits;

    protected ConsentSettings() {}

    public ConsentSettings(Long studentId) {
        this.studentId = studentId;
    }

    public void set(boolean calories, boolean water, boolean steps, boolean visits) {
        this.shareCalories = calories;
        this.shareWater = water;
        this.shareSteps = steps;
        this.shareVisits = visits;
    }

    public Long getStudentId() { return studentId; }
    public boolean isShareCalories() { return shareCalories; }
    public boolean isShareWater() { return shareWater; }
    public boolean isShareSteps() { return shareSteps; }
    public boolean isShareVisits() { return shareVisits; }
}
