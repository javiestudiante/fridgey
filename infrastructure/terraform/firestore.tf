###############################################################################
# firestore.tf
#
# Provisions the Firestore database, the composite indexes the app needs for
# its non-trivial queries, and the security rules ruleset + release.
#
# About indexes:
#   Firestore auto-creates single-field indexes, but composite (multi-field)
#   indexes must be declared up front, otherwise the first query that needs
#   them fails at runtime with FAILED_PRECONDITION. Declaring them here makes
#   them part of the infrastructure spec instead of an ad-hoc copy/paste from
#   the error link Firestore prints in the logs.
#
# About security rules:
#   The actual rules live in firestore.rules (a versioned text file) so that
#   they are easy to read, diff and review without HCL noise. This file just
#   wraps that text into a ruleset and points the live release at it.
###############################################################################

# Firestore database, single per project, must be named "(default)".
resource "google_firestore_database" "default" {
  provider = google-beta
  project  = var.project_id
  name     = "(default)"

  location_id = var.firestore_location
  type        = "FIRESTORE_NATIVE"

  # OPTIMISTIC concurrency mode is the modern default and the one Firestore
  # uses when the database is created from the Console.
  concurrency_mode = "OPTIMISTIC"

  # We are not using App Engine — keep it disabled to avoid the legacy
  # integration creating spurious resources.
  app_engine_integration_mode = "DISABLED"

  # PITR is a paid feature and unnecessary at this stage of the project.
  point_in_time_recovery_enablement = "POINT_IN_TIME_RECOVERY_DISABLED"

  depends_on = [google_firebase_project.default]
}

# Composite index for the most common product query:
#   "Give me the products in fridge X, ordered by expiry date".
resource "google_firestore_index" "products_by_fridge_and_expiry" {
  provider   = google-beta
  project    = var.project_id
  database   = google_firestore_database.default.name
  collection = "productos"

  query_scope = "COLLECTION"

  fields {
    field_path = "idNevera"
    order      = "ASCENDING"
  }

  fields {
    field_path = "fechaCaducidad"
    order      = "ASCENDING"
  }
}

# Composite index for the "fridges I collaborate on" listing,
# sorted by creation date (newest first).
resource "google_firestore_index" "fridges_by_collaborator" {
  provider   = google-beta
  project    = var.project_id
  database   = google_firestore_database.default.name
  collection = "neveras"

  query_scope = "COLLECTION"

  fields {
    field_path   = "colaboradores"
    array_config = "CONTAINS"
  }

  fields {
    field_path = "fechaCreacion"
    order      = "DESCENDING"
  }
}

# Wrap the rules text file into a Firebase Rules ruleset.
resource "google_firebaserules_ruleset" "firestore" {
  provider = google-beta
  project  = var.project_id

  source {
    files {
      content = file("${path.module}/firestore.rules")
      name    = "firestore.rules"
    }
  }

  depends_on = [google_firestore_database.default]
}

# Activate the ruleset for Firestore. The release name "cloud.firestore" is
# fixed by the Firebase platform.
resource "google_firebaserules_release" "firestore" {
  provider     = google-beta
  project      = var.project_id
  name         = "cloud.firestore"
  ruleset_name = google_firebaserules_ruleset.firestore.name
}
