###############################################################################
# auth.tf
#
# Enables Identity Platform on the project. Identity Platform is the GCP
# service Firebase Authentication is built on top of — turning it on is the
# precondition for every Firebase Auth feature (email/password, Google,
# Apple, etc.).
#
# WHAT IS NOT IN THIS FILE — and why:
#
#   * Google OAuth provider configuration.
#       Configuring Google Sign-In requires an OAuth 2.0 Client ID and Client
#       Secret created in the Google Cloud Console (APIs & Services →
#       Credentials). These cannot be created idempotently from Terraform,
#       and the Client Secret cannot be retrieved after creation. The
#       Client ID / Secret are then pasted into Firebase manually.
#       Step-by-step: ../docs/manual-setup-google.md
#
#   * Apple Sign-In provider configuration.
#       Apple Sign-In needs an App ID, Services ID, Sign In with Apple Key
#       (.p8) and Team ID, all of which must be created in Apple's developer
#       portal.
#       There is no Terraform provider for the Apple developer portal, so
#       this is 100% manual.
#       Step-by-step: ../docs/manual-setup-apple.md
#
# What this file DOES configure: email/password sign-in (no external
# dependencies, fully supported by Terraform).
###############################################################################

resource "google_identity_platform_config" "default" {
  provider = google-beta
  project  = var.project_id

  # Anonymous users are not auto-deleted; we don't use anonymous auth right
  # now but disabling auto-delete avoids surprises if we add it later.
  autodelete_anonymous_users = false

  sign_in {
    # Force unique email addresses across providers — prevents account
    # collisions when a user signs in with email and later with Google.
    allow_duplicate_emails = false

    email {
      enabled          = true
      password_required = true
    }
  }

  depends_on = [google_project_service.required_apis]
}
