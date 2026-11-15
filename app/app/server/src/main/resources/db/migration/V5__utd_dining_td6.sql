-- TD-6: real UTD Dining locations and items (spec/06-utd-dining/utd-dining-menus-2026-11-16.json). Supersedes A-16.
-- Runs on MySQL 8.4 and on H2 in MySQL mode (tests).

-- TD-6 §2: building shown next to each location; serving size as published.
ALTER TABLE dining_items ADD COLUMN building VARCHAR(80);
ALTER TABLE dining_items ADD COLUMN serving_size VARCHAR(40);
-- Feed ids are now "UTD-<location slug>-<item slug>" (StubUtdDiningFeed.externalId), longer than 60 characters.
ALTER TABLE dining_items MODIFY COLUMN external_id VARCHAR(160) NOT NULL;

-- TD-6 §3: macros the source did not publish are stored as NULL ("not published"), never invented.
ALTER TABLE dining_items MODIFY COLUMN protein_g DOUBLE PRECISION NULL;
ALTER TABLE dining_items MODIFY COLUMN carbs_g DOUBLE PRECISION NULL;
ALTER TABLE dining_items MODIFY COLUMN fat_g DOUBLE PRECISION NULL;
ALTER TABLE meal_entries MODIFY COLUMN protein_g DOUBLE PRECISION NULL;
ALTER TABLE meal_entries MODIFY COLUMN carbs_g DOUBLE PRECISION NULL;
ALTER TABLE meal_entries MODIFY COLUMN fat_g DOUBLE PRECISION NULL;

-- TD-6 §1, §4: remove the A-16 placeholder items ("Dining Hall" DH-*, "Campus Store" CS-*). Meal entries that were
-- logged from them keep their copied name, calories and macros but no longer point at an item (A-56).
UPDATE meal_entries SET dining_item_id = NULL
 WHERE dining_item_id IN (SELECT id FROM dining_items WHERE external_id LIKE 'DH-%' OR external_id LIKE 'CS-%');
DELETE FROM dining_items WHERE external_id LIKE 'DH-%' OR external_id LIKE 'CS-%';
