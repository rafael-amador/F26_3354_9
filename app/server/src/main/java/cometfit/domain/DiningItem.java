package cometfit.domain;

import jakarta.persistence.*;

/**
 * DiningItem — §5.2 Domain Data: itemId, name, location, calories, proteinG, carbsG, fatG.
 * DiningMenuService 1 "provides" 0..* DiningItem. FR-8 / UC-1 "Browse Campus Dining Menu".
 * externalId = id of the item in the UTD Dining feed so refreshed menus update instead of duplicating (A-16).
 */
@Entity
@Table(name = "dining_items")
public class DiningItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id")
    private Long itemId;
    @Column(name = "external_id", nullable = false, unique = true)
    private String externalId;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String location;
    @Column(nullable = false)
    private int calories;
    @Column(name = "protein_g", nullable = false)
    private double proteinG;
    @Column(name = "carbs_g", nullable = false)
    private double carbsG;
    @Column(name = "fat_g", nullable = false)
    private double fatG;

    protected DiningItem() {}

    public DiningItem(String externalId, String name, String location, int calories,
                      double proteinG, double carbsG, double fatG) {
        this.externalId = externalId;
        update(name, location, calories, proteinG, carbsG, fatG);
    }

    public void update(String name, String location, int calories, double proteinG, double carbsG, double fatG) {
        this.name = name;
        this.location = location;
        this.calories = calories;
        this.proteinG = proteinG;
        this.carbsG = carbsG;
        this.fatG = fatG;
    }

    public Long getItemId() { return itemId; }
    public String getExternalId() { return externalId; }
    public String getName() { return name; }
    public String getLocation() { return location; }
    public int getCalories() { return calories; }
    public double getProteinG() { return proteinG; }
    public double getCarbsG() { return carbsG; }
    public double getFatG() { return fatG; }
}
