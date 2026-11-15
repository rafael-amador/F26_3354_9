# TD-5: Sign in with username and password first, then verify only if needed

- **Date:** 2026-11-15
- **Decided by:** Team 9 (the team agreed; approved by Rafael Amador, project manager)
- **Changes:** Supersedes the sign-in screen layout in TD-1 and A-39 (code field shown before signing in; a login with neither code nor device token rejected as 400). TD-1's setup options and remember-device rule stay.

## Decision
1. **Sign in** asks only for username and password. The 6-digit code is never asked before the user presses Sign in.
2. After the password is accepted:
   - If this device is remembered from a past verified login (TD-1, 30 days, same user), the user is signed in. No second step.
   - Otherwise a second screen, **Verify it's you**, asks for the 6-digit code from Duo Mobile, with the "Remember this device for 30 days" checkbox. The password step alone must never create a full session (use a short-lived, single-use challenge that only the code can complete).
3. **Register** asks for the account details only (no code). After pressing Register, a second screen **Set up two-step verification** shows a **QR code to scan with Duo Mobile** (Duo Mobile accepts standard TOTP accounts; any authenticator app also works), plus the TD-1 "Add to authenticator app" and "Copy setup key" options. The user enters the first 6-digit code to finish. Show the QR code on every screen size at this step.
4. Method chosen: **TOTP via QR code in Duo Mobile.** It meets Deliverable 1's security requirement (password + TOTP) and matches the Deliverable 2 note about UTD Duo. Duo push and email codes are not used: push needs UTD's real Duo API and email needs a mail server, neither available for a local demo.
5. Every credential failure still gets one generic message (A-31). Server and client tests must cover: password-only login without a remembered device does not give a session; remembered device skips step 2; wrong code fails; challenge expires and is single-use.

## Why
Asking for a code before the user even signs in is confusing, and remembered devices should not see it at all.
