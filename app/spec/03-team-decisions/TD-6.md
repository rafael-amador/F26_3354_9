# TD-6: Real UTD Dining locations and menu items

- **Date:** 2026-11-15
- **Decided by:** Team 9 (the team agreed; approved by Rafael Amador, project manager)
- **Changes:** Supersedes A-16 (stub feed with two placeholder locations and placeholder items). FR-8, UC-1 "Browse Campus Dining Menu".

## Decision
1. The dining feed must serve the real UTD Dining locations and items in `spec/06-utd-dining/utd-dining-menus-2026-11-16.json`: Dining Hall West, Chick-fil-A, Panda Express, The Halal Shack, Firehouse Subs, Taco Bell Cantina and Starbucks (54 items). Use the item names, serving sizes and nutrition numbers exactly as given. Remove the old placeholder locations and items ("Dining Hall", "Campus Store").
2. Every item in that file was on UTD Dining's official menu site (Dine On Campus, linked from services.utdallas.edu/dining/menus/) for Monday, Nov 16, 2026. The source URL is listed per location. Show the building next to each location name.
3. Where the source publishes only calories (null fat/carbs/protein), store and show the macros as "not published"; do not invent numbers, and daily totals must still work (count them as 0 g for macro totals, and say so in ASSUMPTIONS.md).
4. Keep it a stub of the external "UTD Dining (menu data)" actor (no live calls to Dine On Campus); only its data changes. Existing meal entries that point at removed placeholder items may be cleared when the demo data is reset.
5. The demo data (TD-2) should use some of these real items in alice's and bob's meal history.

## Why
The team wanted the dining feature to show places students can actually eat on campus, with items that are actually sold there.
