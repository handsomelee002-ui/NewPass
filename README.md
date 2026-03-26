<div align="center">
  <h1>🔒 NewPass</h1>
  <p>A beautifully secure, fully offline Android Password Manager.</p>
</div>

---

## 📖 Overview

**NewPass** is an advanced, privacy-first Android application designed to securely store, organize, and manage your sensitive passwords completely offline. By eliminating cloud syncing and relying entirely on robust on-device encryption architecture, NewPass ensures your credentials never leave your custody. 

Whether you need to generate cryptographically secure passwords on the fly or utilize Android's Biometric hardware to seamlessly unlock your vault, NewPass is built with both impenetrable security and a beautifully intuitive User Experience in mind.

## ✨ Key Features

### 🛡️ Core Security Architecture
*   **Offline First:** Zero internet permissions. Your data physically never leaves your device.
*   **SQLCipher Database:** All password records are stored in an encrypted SQLite database using SQLCipher (256-bit AES), unlocked exclusively via a strong Master Password.
*   **Hardware-Backed Biometrics:** Fingerprint unlocking is bound cryptographically to Android's native `Hardware KeyStore`. A valid biometric prompt decrypts the master hash vault. Spoof-proof by design.
*   **Auto-Clearing Safe Clipboard:** Sensitive passwords copied from the vault are securely scrubbed from the system clipboard after 30 seconds to prevent background app snooping. Integrates Android 13+ strict UI flags (`EXTRA_IS_SENSITIVE`) to shield passwords from clipboard preview overlays.
*   **Activity Hardening:** Fortified against `BadTokenException` window leaks and strict ViewBinding lifecycle garbage collection to prevent memory-snooping attacks and OOM crashes.

### 🎨 User Experience & Interface
*   **Real-time Password Entropy Meter:** Dynamically calculates true computational Shannon entropy as you type, penalizing repetitive character spam and explicitly visualizing strength on a strict scale (Weak ➡️ Strong) via interactive Progress Bars.
*   **Last Updated Tracking:** Automatically tracks the precise modification lifecycle of your entries, conveniently displaying static `Last Updated` epochs within the Details View allowing you to easily spot aging passwords.
*   **Encrypted JSON Backups:** Export your perfectly encrypted password vault offline to a portable `.json` file for cold storage or device mitigation, and effortlessly import it right back when necessary.
*   **Custom Folder Organization:** Neatly compartmentalize your vault via customizable folders. 
*   **Password Generator:** Build rigorous randomized alphanumeric/symbolic passwords instantly adhering to custom length/flag criteria.

---

## 🚀 Getting Started

1.  **Clone the Repository:**
    ```bash
    git clone https://github.com/your-repo/NewPass.git
    ```
2.  **Open in Android Studio:**
    Import the project root into Android Studio.
3.  **Build & Run:**
    Sync your Gradle files and build the app directly to an Emulator or local testing device.

## 🛠️ Tech Stack
*   **Environment:** Android (Java) 
*   **Architecture:** XML Layouts, Fragment-Based Single Activity Structure, MVVM logic flows.
*   **Cryptography:** `SQLCipher`, `EncryptedSharedPreferences`, `AndroidKeyStore`, `BiometricPrompt.CryptoObject`.

## 📜 License
This project is for educational and personal use.
