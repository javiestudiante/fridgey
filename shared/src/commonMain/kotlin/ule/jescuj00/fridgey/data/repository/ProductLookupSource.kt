package ule.jescuj00.fridgey.data.repository

import ule.jescuj00.fridgey.domain.model.ProductLookupResult

/**
 * A source of barcode → Open Food Facts lookups.
 *
 * Implemented by the network-backed [ProductLookupRepository] and wrapped by
 * the [CachingProductLookupRepository] decorator (in-memory cache + in-flight
 * dedupe). The use case depends on THIS interface, so the caching layer is
 * transparent to callers and the decorator is unit-testable in commonTest with
 * a fake that counts how many times the delegate is hit.
 */
interface ProductLookupSource {
    suspend fun lookup(barcode: String): ProductLookupResult
}
