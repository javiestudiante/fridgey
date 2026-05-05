###############################################################################
# main.tf
#
# Top-level Terraform and provider configuration for the Fridgey project.
#
# This file declares:
#   - The minimum Terraform version required to run this configuration.
#   - The Google Cloud providers (google + google-beta). The beta provider is
#     needed because several Firebase resources are still only exposed through
#     the beta API surface.
#   - A local backend for state. State is intentionally kept out of remote
#     storage for this academic project; see infrastructure/README.md for the
#     security implications and recommended next step (GCS backend) if this
#     ever moves to a team setting.
###############################################################################

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 6.0"
    }
    google-beta = {
      source  = "hashicorp/google-beta"
      version = "~> 6.0"
    }
  }

  backend "local" {
    path = "terraform.tfstate"
  }
}

# Stable provider — used for resources fully supported in GA.
provider "google" {
  project = var.project_id
  region  = var.region
}

# Beta provider — required by google_firebase_project and other Firebase
# resources whose Terraform support is only in google-beta.
provider "google-beta" {
  project = var.project_id
  region  = var.region
}
