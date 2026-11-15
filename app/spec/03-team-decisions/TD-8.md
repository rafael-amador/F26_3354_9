# TD-8: More demo friends so the leaderboard looks like a real friend group

- **Date:** 2026-11-15
- **Decided by:** Team 9 (the team agreed; approved by Rafael Amador, project manager)
- **Changes:** Extends TD-2 (demo accounts). Demo data only; no behavior change.

## Decision
1. Seed four more demo students: **starryskies23** (Sky), **nebulanomad** (Nova) (both usernames from prototype 4-social), **lunarlifter** (Luna) and **orbitrunner** (Theo). Same rules as TD-2: all sharing on, password and TOTP secret listed in `app/README.md` like alice and bob.
2. All six demo students (alice, bob and the four new ones) are friends with each other, so alice's Social screen ranks six people.
3. Give each a week of realistic history (meals from the real TD-6 dining items, water, steps, UREC visits and points) with different levels, so the rankings differ by board and alice is first on none of them and last on none of them. For example one friend is the water leader, another the UREC regular, another logs the most calories. Today's numbers must be filled in too, since the boards show "today".
4. Include one pending friend request to alice from one more demo student, **cosmocomet** (Cass), so the Friend requests list is not empty in the demo.
5. Resetting the demo data recreates all of this cleanly.

## Why
With only bob as a friend, the leaderboard showed two names, which does not demonstrate UC-3 well in the recorded demo.
