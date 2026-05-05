###############################################################################
# variables.tf
#
# Input variables for the Fridgey infrastructure.
# Concrete values live in terraform.tfvars (gitignored). An example with the
# expected shape is provided in terraform.tfvars.example.
###############################################################################

variable "project_id" {
  type        = string
  description = "GCP project ID where Firebase will be initialized. The project must already exist and have a billing account attached."
}

variable "region" {
  type        = string
  description = "Default GCP region for regional resources. europe-west1 (Belgium) is chosen for low latency from Spain."
  default     = "europe-west1"
}

variable "firestore_location" {
  type        = string
  description = "Firestore multi-region location. 'eur3' replicates across europe-west and europe-west2, giving redundancy within the EU."
  default     = "eur3"
}
