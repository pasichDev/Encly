# Security

Encly is a local, zero-knowledge encrypted notes app. This document describes how it
protects data and what it does — and does not — defend against.

## How data is protected

- **At rest:** notes live in a [SQLCipher](https://www.zetetic.net/sqlcipher/)
  (AES-256) database. The passphrase is derived from the user's seed phrase, never
  stored in plaintext.
- **Key hierarchy:**
  1. A 12-word BIP39 seed phrase is the root secret.
  2. `SHA-256(seed)` is sealed with an AndroidKeyStore AES-GCM key and kept in
     SharedPreferences.
  3. Per-purpose data keys are derived with **HKDF-SHA256** (RFC 5869) using a
     per-install random salt and a purpose label, then used as the DB passphrase.
- **App lock:** a mandatory PIN is stored as a salted **PBKDF2-HMAC-SHA256 (600k)**
  hash and gates database unlock. Repeated failures trigger a progressive lockout.
  Biometric unlock can be enabled on top of the PIN.
- **No data leaves the device:** the app requests no `INTERNET` permission, performs
  no network calls, and has no analytics. `allowBackup` is disabled and the encrypted
  database and security preferences are excluded from cloud backup / device transfer.

## Threat model

**Defends against:**

- Another app reading Encly's data (Android app sandbox + encryption at rest).
- Casual physical access to an unlocked-but-idle phone (PIN/biometric lock).
- Data exfiltration via backups (`allowBackup=false` + exclusion rules).

**Does not fully defend against:**

- A rooted / forensically-compromised device where an attacker can run code as the
  app. The AndroidKeyStore key that unseals the seed hash is not yet bound to
  per-use user authentication (`setUserAuthenticationRequired` + `CryptoObject`);
  this hardening is planned.
- A user who exports or screenshots their own seed phrase to an insecure location.
- Malware with accessibility/overlay abuse or a compromised OS.

## Recovery

There is **no backdoor**. If the seed phrase is lost and the local key material is
gone, the encrypted data cannot be recovered — this is intentional. The in-app
recovery flow only offers a full wipe and restart.

## Reporting a vulnerability

Please report security issues privately rather than opening a public issue. Contact
the maintainer via the repository's contact channels and allow reasonable time for a
fix before public disclosure.
