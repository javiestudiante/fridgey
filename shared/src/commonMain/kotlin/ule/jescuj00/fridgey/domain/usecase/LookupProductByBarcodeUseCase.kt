package ule.jescuj00.fridgey.domain.usecase

import ule.jescuj00.fridgey.data.repository.ProductLookupRepository
import ule.jescuj00.fridgey.domain.model.ProductLookupResult

/**
 * Scanner-facing entry point for the barcode → Open Food Facts lookup.
 * Thin delegate over [ProductLookupRepository] so the scanner ViewModel
 * depends on a domain use case rather than a data repository directly.
 */
class LookupProductByBarcodeUseCase(
    private val repository: ProductLookupRepository,
) {
    suspend operator fun invoke(barcode: String): ProductLookupResult =
        repository.lookup(barcode)
}
