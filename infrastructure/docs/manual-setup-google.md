# Manual setup — Google Sign-In

The steps below configure everything Terraform deliberately does not
provision: the OAuth consent screen, OAuth client credentials, Android / iOS
app registrations in Firebase, and the Google Sign-In provider toggle.

Project values used throughout:

| Field            | Value                          |
|------------------|--------------------------------|
| GCP project ID   | `fridgey-tfg`                  |
| Android package  | `ule.jescuj00.fridgey`         |
| iOS bundle ID    | `ule.jescuj00.fridgey`         |
| Auth domain      | `fridgey-tfg.firebaseapp.com`  |

Run these **after** `terraform apply` has succeeded — the steps assume
Firebase is already initialized on the project.

---

## a) Configure the OAuth consent screen

1. Open [Google Cloud Console](https://console.cloud.google.com) and make
   sure the active project is `fridgey-tfg`.
2. **APIs & Services → OAuth consent screen.**
3. **User type:** External → *Create*.
4. **App information:**
   * App name: `Fridgey`
   * User support email: your email (`jescuj00@estudiantes.unileon.es`)
   * Developer contact information: your email
5. **Scopes:** add the three default ones —
   * `openid`
   * `.../auth/userinfo.email`
   * `.../auth/userinfo.profile`
6. **Test users:** add your own email. Until the app is verified by Google,
   only test users can sign in.
7. Click *Save and Continue* through the remaining screens, then
   *Back to Dashboard*.

---

## b) Get the Android debug SHA-1 fingerprint

Firebase needs the SHA-1 of the certificate that signs your debug APK to
issue OAuth tokens to it. For a stock Android Studio install:

```bash
keytool -list -v \
  -keystore ~/.android/debug.keystore \
  -alias androiddebugkey \
  -storepass android \
  -keypass android
```

Copy the line that starts with `SHA1:`. Keep it handy for step (d).

> The **release** keystore will have a different SHA-1, which has to be
> added to Firebase later when you produce a signed release build. Don't
> worry about it now.

---

## c) Create the OAuth 2.0 Web client (used internally by Firebase Auth)

Even on mobile, Firebase routes Google Sign-In through a Web OAuth client.
This is what produces the `client_id` / `client_secret` you paste into
Firebase.

1. **Cloud Console → APIs & Services → Credentials.**
2. *Create Credentials → OAuth client ID*.
3. **Application type:** Web application.
4. **Name:** `Fridgey Web Client`.
5. **Authorized redirect URIs**, add:

   ```
   https://fridgey-tfg.firebaseapp.com/__/auth/handler
   ```
6. *Create*. A modal pops up with **Client ID** and **Client secret** —
   copy both, you'll paste them in step (f).

---

## d) Register the Android app in Firebase

1. Open [Firebase Console](https://console.firebase.google.com/project/fridgey-tfg).
2. *Project Settings (gear icon) → General → Your apps → Add app → Android*.
3. **Android package name:** `ule.jescuj00.fridgey`
4. **App nickname:** `Fridgey Android`
5. **Debug signing certificate SHA-1:** paste the value from step (b).
6. *Register app*.
7. Download `google-services.json`.
8. Move it into the repo at:

   ```
   composeApp/google-services.json
   ```

   (This path is already gitignored.)

---

## e) Register the iOS app in Firebase

1. *Project Settings → General → Your apps → Add app → iOS*.
2. **Apple bundle ID:** `ule.jescuj00.fridgey`
3. **App nickname:** `Fridgey iOS`
4. **App Store ID:** leave empty for now.
5. *Register app*.
6. Download `GoogleService-Info.plist`.
7. Move it into the repo at:

   ```
   iosApp/iosApp/GoogleService-Info.plist
   ```

   (Also gitignored.)

---

## f) Enable Google Sign-In in Firebase

1. *Firebase Console → Build → Authentication → Sign-in method*.
2. Click **Google** → toggle *Enable*.
3. **Project public-facing name:** `Fridgey`
4. **Project support email:** your email.
5. Expand **Web SDK configuration** and paste:
   * **Web client ID** — from step (c)
   * **Web client secret** — from step (c)
6. *Save*.

---

## g) Add the support email and project domain

1. *Project Settings → General* — confirm **Support email** is set
   (defaults to the email you used to create the project, but the field
   must be populated for OAuth to work).
2. *Authentication → Settings → Authorized domains* — verify
   `fridgey-tfg.firebaseapp.com` is listed (it is by default). Add any
   custom domain you plan to deploy to later.

---

## Done

At this point an Android or iOS build that includes the downloaded config
file should be able to complete a Google Sign-In flow against this
Firebase project.

If you also need Apple Sign-In, continue with
[manual-setup-apple.md](manual-setup-apple.md).
