###############################################################################
# outputs.tf
#
# Convenience outputs printed after `terraform apply`. They serve two
# purposes:
#   1. Quick links into the Firebase / GCP Console for the things Terraform
#      cannot fully automate (app registration, OAuth, Apple).
#   2. A reminder of the manual follow-up steps the operator still needs to
#      perform — surfaced right at the bottom of the apply output where
#      they're hardest to ignore.
###############################################################################

output "project_id" {
  description = "The GCP project ID Firebase was initialized in."
  value       = var.project_id
}

output "firestore_location" {
  description = "Firestore multi-region location selected at apply time."
  value       = var.firestore_location
}

output "firebase_console_url" {
  description = "Firebase Console root for this project."
  value       = "https://console.firebase.google.com/project/${var.project_id}"
}

output "firestore_console_url" {
  description = "Direct link to the Firestore tab — useful for inspecting data after apply."
  value       = "https://console.firebase.google.com/project/${var.project_id}/firestore"
}

output "auth_console_url" {
  description = "Direct link to the Authentication tab — where Google / Apple providers are enabled manually."
  value       = "https://console.firebase.google.com/project/${var.project_id}/authentication"
}

output "next_steps" {
  description = "Manual steps that must be completed after `terraform apply`."
  value       = <<-EOT

    ============================================================
    Terraform finished. Manual follow-up steps:
    ============================================================

    1. Register the Android app in the Firebase Console:
         Project Settings → General → Your apps → Add app → Android
         Package name: ule.jescuj00.fridgey
         Then download google-services.json and place it at:
            composeApp/google-services.json

    2. Register the iOS app in the Firebase Console:
         Project Settings → General → Your apps → Add app → iOS
         Bundle ID:     ule.jescuj00.fridgey
         Then download GoogleService-Info.plist and place it at:
            iosApp/iosApp/GoogleService-Info.plist

    3. Configure Google Sign-In:
         Full walkthrough → infrastructure/docs/manual-setup-google.md

    4. Configure Apple Sign-In (requires paid Apple Developer
       membership; can be deferred while only Google is in use):
         Full walkthrough → infrastructure/docs/manual-setup-apple.md

    Quick links:
      Firebase Console: https://console.firebase.google.com/project/${var.project_id}
      Firestore:        https://console.firebase.google.com/project/${var.project_id}/firestore
      Auth providers:   https://console.firebase.google.com/project/${var.project_id}/authentication
    ============================================================
  EOT
}
