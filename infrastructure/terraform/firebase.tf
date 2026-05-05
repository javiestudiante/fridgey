###############################################################################
# firebase.tf
#
# Initializes Firebase on top of the existing GCP project.
#
# Steps performed by this file:
#   1. Enables every Google Cloud API that Firebase, Firestore and
#      Identity Platform need at runtime.
#   2. Calls google_firebase_project, which is the resource that flips a plain
#      GCP project into a "Firebase-enabled" project (this is what the Firebase
#      Console does for you when you click "Add Firebase to a Google Cloud
#      project").
#
# WHAT IS DELIBERATELY NOT IN THIS FILE:
#   - Android app registration (google_firebase_android_app)
#   - iOS app registration (google_firebase_apple_app)
#   - Downloading google-services.json / GoogleService-Info.plist
#
#   These are done MANUALLY through the Firebase Console. The reasons:
#     * Their Terraform resources have known quirks (inconsistent plan/apply
#       results, drift on every refresh).
#     * Pulling the generated config files from Terraform requires fragile
#       local-exec provisioners that break on CI and in fresh clones.
#     * They are one-time clicks per platform — automating them would not
#       repay the cost.
#
#   Step-by-step instructions: ../docs/manual-setup-google.md
###############################################################################

# Set of Google Cloud APIs that must be enabled before any Firebase or
# Identity Platform resource can be created.
locals {
  required_apis = [
    "firebase.googleapis.com",
    "firestore.googleapis.com",
    "identitytoolkit.googleapis.com",
    "serviceusage.googleapis.com",
  ]
}

# Enable each required API. for_each + toset gives us one resource per API
# while keeping the configuration declarative and easy to extend.
resource "google_project_service" "required_apis" {
  for_each = toset(local.required_apis)

  project = var.project_id
  service = each.value

  # Keep APIs enabled even if this resource is destroyed: other resources
  # outside Terraform's view (manual app registrations, the Firebase Console,
  # etc.) may still depend on them.
  disable_on_destroy = false
}

# Turn the plain GCP project into a Firebase-enabled project. This is the
# Terraform equivalent of "Add Firebase to a Google Cloud project" in the
# Console.
resource "google_firebase_project" "default" {
  provider = google-beta
  project  = var.project_id

  depends_on = [google_project_service.required_apis]
}
