# TD-2: Demo accounts share their data

- **Date:** 2026-11-13
- **Decided by:** Team 9 (approved by Rafael Amador, project manager, after testing the stage-1 app)
- **Changes:** Assumption A-18 (default sharing) for the seeded demo accounts only.

## Decision
The seeded demo students (alice and bob) start with all data sharing turned on (calories, water, steps, UREC visits) and have some activity history, so UC-3 (friends' progress and leaderboard) shows real numbers in the recorded demo.
Real new accounts keep the current behavior: nothing is shared until the student turns it on (NFR Ethical / Legislative-Privacy).

## Why
In testing, the friends screen showed "Not shared" for every stat and the leaderboard listed only one person, so UC-3 could not be demonstrated.
