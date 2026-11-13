# TD-1: Easier two-factor setup and sign-in

- **Date:** 2026-11-13
- **Decided by:** Team 9 (approved by Rafael Amador, project manager, after testing the stage-1 app)
- **Changes:** Assumptions A-3 and A-6 (account setup and login flow). Still meets NFR Security (username + password + TOTP).

## Decision
1. After sign-up, show an **"Add to authenticator app"** button that opens the phone's authenticator app with the CometFit account already filled in, and a **"Copy setup key"** button as a backup.
2. Show a **QR code** of the setup key only when the app runs on a larger screen (for example a laptop browser), where a phone can scan it.
3. At sign-in, offer **"Remember this device for 30 days."** On a remembered device the student signs in with username and password only; the 6-digit code is required again on a new device or after 30 days.

## Why
Testing showed that typing a raw secret into an authenticator and entering a code on every sign-in is too slow for a student app, and a QR code on the phone's own screen cannot be scanned by that phone.
