# TD-7: CometFit logo

- **Date:** 2026-11-15
- **Decided by:** Team 9 (the team agreed; approved by Rafael Amador, project manager)
- **Changes:** Replaces the prototype's sign-in logo and the default Expo template icons.

## Decision
1. The CometFit logo is the dark-green dumbbell with two orange swooshes in `spec/07-logo/`
   (`cometfit-logo-original.png` on white, `cometfit-logo-transparent.png` trimmed with a transparent background).
2. Use it on the Sign in, Verify it's you, Register and Set up two-step verification screens next to the "CometFit"
   wordmark (where prototype 1-sign-in shows its logo), and as the app icon, splash icon, Android adaptive icon and
   web favicon, replacing every default Expo template icon in `app/client/assets/`.
3. Icons must be square with the logo centered and padded on the app's off-white background (the transparent logo
   on a square canvas); do not stretch it. Keep the original colors.
