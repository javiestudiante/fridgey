# Manual setup — Sign in with Apple

Apple Sign-In is the part of the auth stack that has **zero Terraform
support** — none of the resources below can be created from code. The whole
flow goes through Apple's developer portal and the Firebase Console.

Project values used throughout:

| Field                           | Value                          |
|---------------------------------|--------------------------------|
| iOS bundle ID                   | `ule.jescuj00.fridgey`         |
| Apple Services ID (for Firebase)| `ule.jescuj00.fridgey.signin`  |
| Firebase auth domain            | `fridgey-tfg.firebaseapp.com`  |

---

## a) Prerequisites

* **Apple Developer Program membership** — $99/year, paid by the same
  Apple ID that will own the app on the App Store. Without it, you cannot
  create the App ID, Services ID or .p8 key needed below.
* **Mac with Xcode** (latest stable version) — needed for the final step
  that adds the *Sign in with Apple* capability to the iOS target.

---

## b) Create the App ID

1. Go to [developer.apple.com](https://developer.apple.com) →
   *Account → Certificates, Identifiers & Profiles*.
2. *Identifiers* → **+** → *App IDs* → *App* → *Continue*.
3. **Description:** `Fridgey iOS App`
4. **Bundle ID:** `Explicit` → `ule.jescuj00.fridgey`
5. Under **Capabilities**, tick **Sign In with Apple** (leave the default
   "Enable as a primary App ID").
6. *Continue → Register*.

---

## c) Create the Services ID (for the Firebase web flow)

Apple Sign-In on Firebase routes through a web redirect, which requires a
**Services ID** distinct from the App ID.

1. *Identifiers* → **+** → *Services IDs* → *Continue*.
2. **Description:** `Fridgey Sign In Service`
3. **Identifier:** `ule.jescuj00.fridgey.signin`
4. *Continue → Register*.
5. Re-open the Services ID you just created.
6. Tick **Sign In with Apple** → click **Configure**.
7. In the configuration dialog:
   * **Primary App ID:** select `Fridgey iOS App` from step (b).
   * **Domains and Subdomains:**
     ```
     fridgey-tfg.firebaseapp.com
     ```
   * **Return URLs:**
     ```
     https://fridgey-tfg.firebaseapp.com/__/auth/handler
     ```
8. *Save → Continue → Save*.

---

## d) Create the Sign In with Apple key

1. *Keys* → **+**.
2. **Key Name:** `Fridgey Apple Sign In Key`
3. Tick **Sign In with Apple** → *Configure*.
4. **Primary App ID:** `Fridgey iOS App`. *Save*.
5. *Continue → Register*.
6. **CRITICAL — download the `.p8` file now.** Apple will let you download
   it **only once**. Store it somewhere safe (a password manager / secret
   store). If you lose it, you have to revoke the key and create a new
   one.
7. From the same page, copy the **Key ID** (10 characters, shown next to
   the key name).

---

## e) Get your Team ID

1. [developer.apple.com](https://developer.apple.com) → *Membership* (or
   *Account → Membership details*).
2. Copy the **Team ID** (10 characters).

---

## f) Configure Apple Sign-In in the Firebase Console

1. [Firebase Console](https://console.firebase.google.com/project/fridgey-tfg)
   → *Authentication → Sign-in method*.
2. Click **Apple** → toggle *Enable*.
3. Fill in:
   * **Services ID:** `ule.jescuj00.fridgey.signin` (from step c)
   * **Apple Team ID:** value from step (e)
   * **Key ID:** value from step (d)
   * **Private Key:** paste the **entire contents** of the `.p8` file,
     including the `-----BEGIN PRIVATE KEY-----` and
     `-----END PRIVATE KEY-----` lines.
4. *Save*.

---

## g) Add the *Sign in with Apple* capability in Xcode

1. Open the iOS project in Xcode (`iosApp/iosApp.xcodeproj` or the
   workspace if one exists).
2. Select the project in the navigator → choose the `iosApp` target →
   **Signing & Capabilities** tab.
3. Click **+ Capability** and pick **Sign in with Apple**.
4. Confirm Xcode has updated the entitlements file (`*.entitlements`)
   with the `com.apple.developer.applesignin` entry. Commit that file.
5. Build the iOS target. There should be no signing errors; if there are,
   verify the **Team** in the *Signing* section matches the one that owns
   the App ID from step (b).

---

## Done

The iOS app can now perform a *Sign in with Apple* flow that resolves into
a Firebase user. Combined with the Google flow from
[manual-setup-google.md](manual-setup-google.md) and the email/password
provider that Terraform enabled, the auth surface is fully wired.

> 🔐 **Reminder:** the `.p8` private key is a credential. Don't commit it,
> don't paste it in chat, and don't store it in plaintext on disk. If it
> ever leaks, revoke the key in the Apple developer portal and repeat
> step (d).
