# NewPass Security Audit Report

Audit date: 2026-05-14  
Application version: 1.12.0  
Version code: 13  
Application ID: `com.gero.newpass`  
Scope: Current Android source tree, Gradle build configuration, manifest, signing setup, local storage, authentication, encryption, export/import, and generated release APK.  
Distribution model: Personal/family APK sharing. Not intended for Play Store release. Security is the primary acceptance criterion.

## Executive Summary

NewPass 1.12.0 is acceptable for personal/family distribution after a real-device smoke test. The previous critical blockers are resolved: the release APK is now signed with a private local release key, release signing fails closed when signing configuration is missing, the committed test keystore has been removed, and runtime signature verification is tied to the configured release certificate fingerprint.

The application uses appropriate baseline controls for a local password manager: SQLCipher, Android Keystore-backed encrypted preferences, AES-GCM encryption, PBKDF2-HMAC-SHA256 hashing, `FLAG_SECURE`, disabled Android backup, Android 12+ data extraction exclusions, biometric strong authentication, and timed clipboard clearing for copied secrets.

Overall status: **Pass with non-critical hardening items remaining**  
Critical findings: **0 open**  
High findings: **2 open**  
Medium findings: **3 open**  
Low findings: **2 open**

## Verification Results

| Check | Command | Result |
| --- | --- | --- |
| Unit test task | `.\gradlew.bat test` | Pass |
| Android lint | `.\gradlew.bat lintDebug` | Pass |
| Signed release build | `.\gradlew.bat assembleRelease` | Pass |
| APK signature verification | `apksigner verify --verbose --print-certs app\build\outputs\apk\release\app-release.apk` | Pass |

Release artifact:
- `app/build/outputs/apk/release/app-release.apk`

Release signing certificate:
- DN: `CN=NewPass Personal, OU=Personal, O=NewPass, L=Kuala Lumpur, ST=Kuala Lumpur, C=MY`
- SHA-256 digest: `69d5e1bfcd36d2ecf46a537f3249d84c23a522de8c9eb32211fb00f66e90df7d`
- Key algorithm: RSA
- Key size: 4096 bits
- APK signature scheme verified: v2

## Critical Findings

### C-01: Release Signing Was Missing

Severity: Critical  
Status: Fixed  
Evidence:
- Release build now produces `app/build/outputs/apk/release/app-release.apk`.
- `apksigner verify` reports `Verifies`.
- Gradle release packaging requires `NEWPASS_RELEASE_STORE_FILE`, `NEWPASS_RELEASE_STORE_PASSWORD`, `NEWPASS_RELEASE_KEY_ALIAS`, `NEWPASS_RELEASE_KEY_PASSWORD`, and `NEWPASS_RELEASE_SIGNATURE_SHA256`.

Risk addressed:
Unsigned APKs are not suitable for trusted distribution. This is now fixed for the current local release key.

Operational requirement:
Back up the private release keystore and signing property values. Future updates must use the same signing key.

### C-02: Committed Test Keystore and Hard-Coded Signing Passwords

Severity: Critical  
Status: Fixed  
Evidence:
- `app/testkey.keystore` is removed from the working tree.
- `app/build.gradle.kts` no longer hard-codes `testkey` signing passwords.
- Release signing uses ignored local properties or environment variables.

Risk addressed:
A shared app must not be signed by a repository-committed key. The release signing path no longer depends on the old test key.

## High Findings

### H-01: Vault Session Key Still Passes Through Immutable Strings

Severity: High  
Status: Open  
Evidence:
- `StringHelper` stores session key material in a static `char[]`, but `getSharedString()` returns a new immutable `String`.
- `DatabaseHelper` passes that `String` into SQLCipher open calls.

Current controls:
- The static `char[]` is wiped when replaced or cleared.
- `MainViewActivity.lockApp()` clears the stored value.

Residual risk:
Immutable string copies cannot be explicitly wiped and may remain in process memory until garbage collection. For personal/family use this is a tolerable residual risk, but it is not ideal for a high-assurance password manager.

Recommendation:
Keep the current mitigation for this version. For a future version, centralize vault session state and minimize the lifetime of SQLCipher key strings.

### H-02: Export Security Depends on User Export Password Strength

Severity: High  
Status: Open  
Evidence:
- `EncryptionHelper.encryptDatabase()` derives the export encryption key from the user-provided export password.
- Export data is plaintext JSON in memory before AES-GCM encryption.

Current controls:
- Export encryption uses AES-GCM.
- Key derivation uses PBKDF2-HMAC-SHA256 with random salt and 600,000 iterations.

Residual risk:
Weak export passwords produce weak encrypted backup files. Exported files may leave the app sandbox and be stored or forwarded elsewhere.

Recommendation:
Require strong export passwords and display explicit export-risk text before creating an export file.

## Medium Findings

### M-01: Password Login Throttling Is Process-Local

Severity: Medium  
Status: Open  
Evidence:
- `LoginViewModel.failedAttempts` is an in-memory counter.

Impact:
The password-login delay resets if the app process is killed. SQLCipher and PBKDF2 still slow offline attacks, but UI throttling is not persistent.

Recommendation:
Persist failed password attempts and last-failure timestamps in encrypted preferences, matching the current recovery-code throttling pattern.

### M-02: Biometric Unlock Adds a Secondary Vault Access Path

Severity: Medium  
Status: Accepted with controls  
Evidence:
- `biometric_wrapped_password` is stored in encrypted preferences.
- RSA private-key unwrap is gated by biometric authentication.

Current controls:
- Uses Android Keystore.
- Uses RSA OAEP.
- Requires `BIOMETRIC_STRONG`.
- Invalidates biometric key on enrollment changes where supported.
- Password changes invalidate the old biometric wrapped credential.

Recommendation:
Keep biometric unlock opt-in. For security-sensitive users, recommend disabling biometric unlock and using the master password only.

### M-03: Alpha AndroidX Dependencies

Severity: Medium  
Status: Open  
Evidence:
- `androidx.security:security-crypto:1.1.0-alpha06`
- `androidx.biometric:biometric:1.2.0-alpha05`

Impact:
Alpha dependencies increase maintenance and compatibility risk. This is not an immediate vulnerability, but stable dependencies are preferable for a password manager.

Recommendation:
Review stable alternatives and upgrade where behavior remains compatible.

## Low Findings

### L-01: Deprecated Gradle Properties

Severity: Low  
Status: Open  
Evidence:
- Build output reports multiple deprecated Android Gradle Plugin options from `gradle.properties`.

Impact:
Not a current security bug. Future AGP upgrades may break the build if these remain.

Recommendation:
Remove deprecated flags incrementally and rerun `test`, `lintDebug`, and `assembleRelease` after each cleanup.

### L-02: Limited Dedicated Security Regression Tests

Severity: Low  
Status: Open  
Evidence:
- The Gradle test task passes, but there is limited dedicated coverage for recovery throttling, export round trips, biometric credential unwrap behavior, release-signing validation, and lock/session clearing.

Recommendation:
Add focused tests for security-critical helpers and authentication state transitions where Android framework dependencies allow.

## Positive Controls

- `android:allowBackup="false"` is set.
- Android 12+ `data_extraction_rules.xml` excludes database, shared preferences, and root data from cloud backup and device transfer.
- `LoginActivity` and `MainViewActivity` apply `FLAG_SECURE`.
- SQLCipher protects the vault database.
- Encrypted preferences protect stored login metadata, recovery-code hash, and biometric wrapped credential.
- Password and recovery-code verification use PBKDF2-HMAC-SHA256.
- AES-GCM is used for field encryption and export encryption.
- Recovery reset consumes the old recovery code and creates a new code.
- Clipboard helper marks copied values as sensitive on Android 13+ and clears matching clipboard contents after timeout.
- Biometric login uses strong authenticators only.
- Release builds enable R8 minification and resource shrinking.
- Release builds strip `android.util.Log` calls through ProGuard rules.
- Release signing is configured through ignored local properties or environment variables.
- Runtime signature verification uses `BuildConfig.OFFICIAL_SIGNATURE_SHA256`.

## Distribution Decision

The current signed release APK is suitable for personal/family distribution after a device smoke test.

Before sharing:
1. Install `app/build/outputs/apk/release/app-release.apk` on a real Android device.
2. Create a new vault.
3. Lock and unlock with the master password.
4. Add, view, copy, and delete a test password.
5. Restart the app and verify the vault still unlocks.
6. Store the private signing key and signing properties in a secure backup location.

Do not share:
- Debug APKs.
- The release keystore.
- Signing passwords.
- `local.properties`.

## Residual Risk Summary

The remaining issues are hardening and maintainability items, not distribution blockers for the stated personal/family use case. The highest priority next fix is persistent password-login throttling, followed by export password strength enforcement and dependency stabilization.

