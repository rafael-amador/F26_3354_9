package cometfit.services;

import java.util.List;

/**
 * AIFoodService — Deliverable 1 §5.2 "Services": estimateMacros(image) : List&lt;FoodEstimate&gt;.
 * Boundary to the external actor "AI Food Recognition Service" (UC-1 "Log Meal by Photo"; SD-1
 * "estimateMacros(image) / foodItems, calories, macros"). The only implementation shipped is the
 * labelled stub {@link cometfit.stubs.FakeAIFoodService} (CLAUDE.md rule 4).
 */
public interface AIFoodService {

    /** One recognised food in a photo. */
    record FoodEstimate(String foodName, int calories, double proteinG, double carbsG, double fatG, double confidence) {}

    List<FoodEstimate> estimateMacros(byte[] image);
}
