package cometfit.stubs;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import cometfit.services.AIFoodService;

/**
 * *** STUB *** of the external "AI Food Recognition Service" actor (UC-1; SD-1 AIFoodService lifeline).
 * It does NOT recognise food. It hashes the uploaded bytes and deterministically picks one or two entries from a
 * small fixed table so the same photo always gives the same estimate. The table values are placeholder demo
 * data, not nutrition facts (A-15). Replace with a real client of the trained model when one exists.
 */
@Component
public class FakeAIFoodService implements AIFoodService {

    private static final List<FoodEstimate> TABLE = List.of(
            new FoodEstimate("Grilled chicken breast (stub)", 280, 52, 0, 6, 0),
            new FoodEstimate("White rice, 1 cup (stub)", 205, 4, 45, 0.4, 0),
            new FoodEstimate("Garden salad (stub)", 120, 3, 10, 7, 0),
            new FoodEstimate("Cheese pizza slice (stub)", 285, 12, 36, 10, 0),
            new FoodEstimate("Banana (stub)", 105, 1.3, 27, 0.4, 0),
            new FoodEstimate("Turkey sandwich (stub)", 350, 24, 38, 10, 0),
            new FoodEstimate("Pasta with tomato sauce (stub)", 400, 13, 72, 6, 0),
            new FoodEstimate("Scrambled eggs, 2 (stub)", 180, 12, 2, 13, 0));

    @Override
    public List<FoodEstimate> estimateMacros(byte[] image) {
        byte[] h = sha256(image);
        int first = Math.floorMod(h[0], TABLE.size());
        List<FoodEstimate> out = new ArrayList<>();
        out.add(withConfidence(TABLE.get(first), 0.60 + (h[2] & 0x1f) / 100.0));
        if ((h[1] & 1) == 1) {
            int second = (first + 1 + Math.floorMod(h[3], TABLE.size() - 1)) % TABLE.size();
            out.add(withConfidence(TABLE.get(second), 0.50 + (h[4] & 0x1f) / 100.0));
        }
        return out;
    }

    private static FoodEstimate withConfidence(FoodEstimate e, double c) {
        return new FoodEstimate(e.foodName(), e.calories(), e.proteinG(), e.carbsG(), e.fatG(), Math.round(c * 100) / 100.0);
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
