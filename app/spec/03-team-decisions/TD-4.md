# TD-4: The app's look follows the team's Figma prototype

- **Date:** 2026-11-15
- **Decided by:** Team 9 (approved by Rafael Amador, project manager)
- **Changes:** Supersedes A-32 (visual design). Prototype screens by Sean (Deliverable 2, Section "Application Prototype", slide 9) are in `spec/05-ui-prototype/`.

## Decision
1. The client's visual design must match the prototype screens in `spec/05-ui-prototype/`: colors (dark green, orange accent, blue for water, warm off-white background), typography, card style, spacing, headers with a subtitle and an initials avatar, and the bottom tab bar with five tabs named **Dashboard, Food & Water, Activities, Social, Account**.
2. Map the existing features onto those five screens (for example: daily summary and UREC check-in/streak on Dashboard; meal and water logging with Today/Week/Month on Food & Water; exercise, smart device and UREC visits on Activities; friends and leaderboards on Social; profile, goals and units, sharing settings, 2FA and log out on Account). The developer dashboard (UC-4) keeps its own screen in the same visual style.
3. The prototype shows layout and style, not new requirements. Where it conflicts with Deliverable 1/2, the deliverables win on behavior:
   - Sign-in stays username + password + TOTP code (with the TD-1 setup and remember-device options), styled like the prototype's sign-in screen. Do not build "Sign in with Microsoft".
   - Water entry keeps the liters/gallons input from the team's LogWater unit; totals may be shown in ml as in the prototype.
   - Elements in the prototype with no matching FR/UC (for example scheduled classes or delete account) are shown only if the app already has the data; otherwise leave them out and list them in ASSUMPTIONS.md.
4. Do not change server behavior or remove any feature to fit the design.
