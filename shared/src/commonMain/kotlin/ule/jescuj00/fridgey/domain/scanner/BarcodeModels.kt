package ule.jescuj00.fridgey.domain.scanner

/**
 * A single decoded barcode. [rawValue] is the digits (EAN/UPC). [format] is a
 * human-readable symbology label (e.g. "EAN_13"), informational only.
 */
data class BarcodeResult(
    val rawValue: String,
    val format: String,
)
