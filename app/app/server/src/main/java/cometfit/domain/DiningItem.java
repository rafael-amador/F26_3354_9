package cometfit.domain;

import jakarta.persistence.*;

/**
 * DiningItem — §5.2 Domain Data: itemId, name, location, calories, proteinG, carbsG, fatG.
 * DiningMenuService 1 "provides" 0..* DiningItem. FR-8 / UC-1 "Browse Campus Dining Menu".
 * externalId = id of the item in the UTD Dining feed so refreshed menus update instead of duplicating.
 * TD-6: plus building (shown next to the location) and serving size; proteinG / carbsG / fatG are null when UTD Dining
 * did not publish them ("not published", TD-6 §3). Columns: {@code V5__utd_dining_td6.sql}.
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
    private String building;
    @Column(name = "serving_size")
    private String serving;
    @Column(nullable = false)
    private int calories;
    @Column(name = "protein_g")
    private Double proteinG;
    @Column(name = "carbs_g")
    private Double carbsG;
    @Column(name = "fat_g")
    private Double fatG;

    protected DiningItem() {}

    public DiningItem(String externalId, String name, String location, String building, String serving, int calories,
                      Double proteinG, Double carbsG, Double fatG) {
        this.externalId = externalId;
        update(name, location, building, serving, calories, proteinG, carbsG, fatG);
    }

    public void update(String name, String location, String building, String serving, int calories,
                       Double proteinG, Double carbsG, Double fatG) {
        this.name = name;
        this.location = location;
        this.building = building;
        this.serving = serving;
        this.calories = calories;
        this.proteinG = proteinG;
        this.carbsG = carbsG;
        this.fatG = fatG;
    }

    public Long getItemId() { return itemId; }
    public String getExternalId() { return externalId; }
    public String getName() { return name; }
    public String getLocation() { return location; }
    public String getBuilding() { return building; }
    public String getServing() { return serving; }
    public int getCalories() { return calories; }
    public Double getProteinG() { return proteinG; }
    public Double getCarbsG() { return carbsG; }
    public Double getFatG() { return fatG; }
}
