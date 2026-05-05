# Fridgey — Infrastructure as Code

This directory holds the Terraform configuration that provisions the Firebase
backend for **Fridgey** (project ID `fridgey-tfg`, package
`ule.jescuj00.fridgey`), plus two manual setup guides for the parts that
cannot reasonably be automated.

---

## 1. Overview

```
infrastructure/
├── terraform/                  # Terraform configuration (run from here)
│   ├── main.tf                 # Provider configuration
│   ├── variables.tf            # Input variables
│   ├── terraform.tfvars.example
│   ├── firebase.tf             # Firebase project initialization + APIs
│   ├── firestore.tf            # Firestore DB, indexes, rules wiring
│   ├── auth.tf                 # Identity Platform base (email/password)
│   ├── outputs.tf              # Console URLs + post-apply checklist
│   ├── firestore.rules         # Versioned security rules
│   └── .gitignore              # Excludes state and tfvars
├── docs/
│   ├── manual-setup-google.md  # Google OAuth manual steps
│   └── manual-setup-apple.md   # Apple Sign-In manual steps
└── README.md                   # You are here
```

### Why Terraform here?

This is a TFG-scale project, so full-blown CI/CD on top of Terraform would be
overkill. The reason for using IaC anyway is that:

* The Firebase project setup is **otherwise undocumented** — clicking through
  the Console leaves no trail.
* Composite indexes, Firestore security rules and Identity Platform settings
  are **easy to drift** if changed by hand. Versioning them in Git makes
  every change reviewable.
* It demonstrates the DevOps practice the advisor recommended without
  paying the (high) cost of automating every Firebase corner.

### The "Terraform Minimum" approach

Firebase's Terraform support is uneven. Some resources work cleanly, some
have known plan/apply inconsistencies, and a few (Apple Sign-In, OAuth client
secrets) have no useful provider at all. Rather than fight that, this repo
draws a clear line:

* **Automate everything that benefits from versioning and is well-supported.**
* **Document everything else as a step-by-step manual procedure.**

That gives the safety of IaC where it actually pays off, and avoids fragile
local-exec hacks where it doesn't.

---

## 2. Architecture decisions

### Automated with Terraform

* Enabling required Google Cloud APIs (Firebase, Firestore, Identity Toolkit,
  Service Usage).
* Initializing Firebase on the GCP project (`google_firebase_project`).
* Creating the Firestore database in the chosen multi-region.
* Declaring the composite indexes the app's queries need.
* Versioning Firestore **security rules** (`firestore.rules`) and publishing
  them as a Firebase Rules release.
* Enabling Identity Platform with email/password sign-in.

### Manual (out of scope for Terraform)

| Step                                | Why it is manual                                                                                       |
|-------------------------------------|---------------------------------------------------------------------------------------------------------|
| Android app registration            | `google_firebase_android_app` has known drift; downloading `google-services.json` needs fragile hacks.  |
| iOS app registration                | Same story for `google_firebase_apple_app` and `GoogleService-Info.plist`.                              |
| Google OAuth client (ID + secret)   | The Client Secret cannot be retrieved after creation; the official flow is through the Cloud Console.   |
| Apple Sign-In (App ID, Services ID, .p8 key, Team ID) | No Terraform provider exists for the Apple developer portal; the .p8 key can only be downloaded once. |
| OAuth consent screen                | One-off configuration tied to the Google account; not something to re-create on every apply.            |

The manual procedures are fully scripted in
[docs/manual-setup-google.md](docs/manual-setup-google.md) and
[docs/manual-setup-apple.md](docs/manual-setup-apple.md).

---

## 3. Prerequisites

1. **Terraform 1.5 or newer**

   ```bash
   brew install terraform
   terraform -version
   ```

2. **gcloud CLI authenticated**

   ```bash
   brew install --cask google-cloud-sdk
   gcloud auth login                          # for gcloud commands
   gcloud auth application-default login      # for Terraform / SDKs
   ```

3. **GCP project with billing**

   The project `fridgey-tfg` already exists and has a billing account
   linked. If you are reproducing this on a clean account, create the
   project in the Console first and attach billing — Terraform will not do
   that for you here.

4. **(Optional, for Apple Sign-In) Apple Developer Program membership**
   — $99/year. Not required for the initial Google-only auth pass; you can
   defer this until you actually plan to ship to the App Store.

---

## 4. Quick start

```bash
cd infrastructure/terraform

# Copy the example values; project_id is already filled in.
cp terraform.tfvars.example terraform.tfvars
# (edit terraform.tfvars only if you want different region / Firestore location)

terraform init      # downloads providers, prepares local backend
terraform plan      # review what will be created
terraform apply     # create everything; type 'yes' to confirm
```

When `apply` finishes, scroll up: the `next_steps` output prints the manual
follow-up checklist.

---

## 5. Manual steps required AFTER `terraform apply`

1. **Register the Android app** in the Firebase Console
   (Project Settings → Your apps → Add app → Android).
   Package name: `ule.jescuj00.fridgey`. Download
   `google-services.json` and place it at
   `composeApp/google-services.json`.

2. **Register the iOS app** in the Firebase Console
   (Project Settings → Your apps → Add app → iOS).
   Bundle ID: `ule.jescuj00.fridgey`. Download
   `GoogleService-Info.plist` and place it at
   `iosApp/iosApp/GoogleService-Info.plist`.

3. **Download the config files** mentioned above and put them in the paths
   above. Both files are gitignored.

4. **Set up Google OAuth** → see
   [docs/manual-setup-google.md](docs/manual-setup-google.md).

5. **Set up Apple Sign-In** (only when you are ready to ship to iOS) → see
   [docs/manual-setup-apple.md](docs/manual-setup-apple.md).

---

## 6. Common operations

* **Update infrastructure** — edit the relevant `.tf` file and re-run
  `terraform plan` then `terraform apply`.

* **Update Firestore security rules** — edit
  `terraform/firestore.rules` and run `terraform apply`. The
  `google_firebaserules_ruleset` resource will re-publish a new release.

* **Inspect current state** — `terraform show` (read-only dump of state).
  `terraform state list` to enumerate resources.

* **Destroy everything**

  ```bash
  terraform destroy
  ```

  > ⚠️ **WARNING:** this deletes the Firestore database and any data inside
  > it. There is no undo. The GCP project itself is NOT deleted (it was
  > created out-of-band).

---

## 7. Troubleshooting

* **"API \[X\] not enabled / consumer project not enabled"**
  API enablement is asynchronous on Google's side. Wait ~60 seconds and run
  `terraform apply` again — the dependent resource will succeed on the
  retry.

* **"Permission denied" / "Application Default Credentials not found"**
  Make sure you ran `gcloud auth application-default login` (not just
  `gcloud auth login`) — Terraform uses the ADC, not the gcloud user creds.

* **"Firestore database already exists"**
  Firestore allows exactly one DB per project, named `(default)`. Either
  `terraform import google_firestore_database.default
  projects/<id>/databases/(default)` or destroy the existing DB through the
  Console (only safe if it is empty).

* **"Provider produced inconsistent final plan"**
  Known quirk of the `google-beta` Firebase resources. Re-running
  `terraform apply` almost always fixes it. If it persists, run
  `terraform refresh` first.

---

## 8. Security notes

* `terraform.tfstate` contains every value Terraform fetched from the
  provider, including some sensitive identifiers. **Never commit it.** It
  is already in `terraform/.gitignore`. If/when this moves to a team
  setting, switch to a remote backend (GCS bucket with versioning + uniform
  access).

* `terraform.tfvars` is gitignored too — only `terraform.tfvars.example` is
  committed.

* The downloaded Firebase config files contain identifiers that allow
  someone to invoke your Firebase project as a client. They are gitignored
  at the repo root:
    * `composeApp/google-services.json`
    * `iosApp/iosApp/GoogleService-Info.plist`

* The Apple `.p8` Sign In with Apple key can be downloaded **only once**.
  Treat it like a private key: store it in a password manager / secret
  store, never in Git.
