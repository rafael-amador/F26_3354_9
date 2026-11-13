package cometfit.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cometfit.api.ApiException;
import cometfit.domain.DiningItem;
import cometfit.domain.DiningItemRepository;

/**
 * DiningMenuService — Deliverable 1 §5.2 "Services": fetchItems(location) : List&lt;DiningItem&gt;;
 * "provides" 0..* DiningItem. SD-1 "fetchItems(location) / items with macros". FR-8 / UC-1
 * "Browse Campus Dining Menu" which «include»s "Maintain Dining Menu &amp; Macros" (UTD Dining actor):
 * every fetch pulls the current menu from the UTD Dining feed and upserts it into dining_items so meal
 * entries can reference the item (MealEntry "based on" DiningItem).
 */
@Service
public class DiningMenuService {
    private final UtdDiningFeed feed;
    private final DiningItemRepository items;

    public DiningMenuService(UtdDiningFeed feed, DiningItemRepository items) {
        this.feed = feed;
        this.items = items;
    }

    public List<String> locations() {
        return feed.locations();
    }

    @Transactional
    public List<DiningItem> fetchItems(String location) {
        String loc = feed.locations().stream().filter(l -> l.equalsIgnoreCase(location)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Unknown dining location " + location));
        for (UtdDiningFeed.FeedItem f : feed.menu(loc)) {
            DiningItem item = items.findByExternalId(f.externalId())
                    .orElseGet(() -> new DiningItem(f.externalId(), f.name(), f.location(), f.calories(),
                            f.proteinG(), f.carbsG(), f.fatG()));
            item.update(f.name(), f.location(), f.calories(), f.proteinG(), f.carbsG(), f.fatG());
            items.save(item);
        }
        return items.findByLocationOrderByName(loc);
    }
}
