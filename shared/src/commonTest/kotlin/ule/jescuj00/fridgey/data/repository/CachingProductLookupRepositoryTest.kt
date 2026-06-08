package ule.jescuj00.fridgey.data.repository

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import ule.jescuj00.fridgey.domain.model.Categoria
import ule.jescuj00.fridgey.domain.model.ProductAutoFill
import ule.jescuj00.fridgey.domain.model.ProductLookupResult
import ule.jescuj00.fridgey.domain.model.UnidadMedida
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** Counts how many times the delegate (the "network") is actually hit. */
private class FakeLookupSource(var result: ProductLookupResult) : ProductLookupSource {
    var calls = 0
        private set
    val seen = mutableListOf<String>()

    override suspend fun lookup(barcode: String): ProductLookupResult {
        calls++
        seen += barcode
        return result
    }
}

/**
 * Delegate that blocks inside `lookup` until [release] is called, so a test can
 * launch concurrent lookups and inspect state WHILE a call is in flight. `calls`
 * is incremented at entry (before the gate), so it reflects how many lookups
 * actually reached the delegate.
 */
private class GatedLookupSource(var result: ProductLookupResult) : ProductLookupSource {
    var calls = 0
        private set
    private val gate = CompletableDeferred<Unit>()

    fun release() {
        gate.complete(Unit)
    }

    override suspend fun lookup(barcode: String): ProductLookupResult {
        calls++
        gate.await()
        return result
    }
}

private fun found(barcode: String) = ProductLookupResult.Found(
    ProductAutoFill(
        codigoBarras = barcode,
        nombre = "Producto",
        cantidad = 1.0,
        unidad = UnidadMedida.UNIDADES,
        imagenUrl = null,
        categoria = Categoria.OTROS,
    )
)

class CachingProductLookupRepositoryTest {

    @Test
    fun firstLookup_missesCache_hitsDelegateOnce() = runTest {
        val fake = FakeLookupSource(found("111"))
        val caching = CachingProductLookupRepository(fake)

        val r = caching.lookup("111")

        assertEquals(1, fake.calls)
        assertEquals(found("111"), r)
    }

    @Test
    fun secondLookup_sameBarcode_servedFromCache_noExtraDelegateCall() = runTest {
        val fake = FakeLookupSource(found("111"))
        val caching = CachingProductLookupRepository(fake)

        val first = caching.lookup("111")
        val second = caching.lookup("111")

        assertEquals(1, fake.calls, "second lookup must be served from cache")
        assertSame(first, second, "cached instance should be returned verbatim")
    }

    @Test
    fun notFound_isCached_notRequeried() = runTest {
        val fake = FakeLookupSource(ProductLookupResult.NotFound)
        val caching = CachingProductLookupRepository(fake)

        caching.lookup("404")
        caching.lookup("404")

        assertEquals(1, fake.calls, "a known-missing product must not be re-queried")
    }

    @Test
    fun networkError_isNotCached_retriesOnNextLookup() = runTest {
        val fake = FakeLookupSource(ProductLookupResult.NetworkError)
        val caching = CachingProductLookupRepository(fake)

        caching.lookup("111")
        caching.lookup("111")

        assertEquals(2, fake.calls, "a transient NetworkError must NOT be cached")
    }

    @Test
    fun rateLimited_isNotCached_retriesOnNextLookup() = runTest {
        val fake = FakeLookupSource(ProductLookupResult.RateLimited)
        val caching = CachingProductLookupRepository(fake)

        caching.lookup("111")
        caching.lookup("111")

        assertEquals(2, fake.calls, "a transient RateLimited (429) must NOT be cached")
    }

    @Test
    fun differentBarcodes_eachHitDelegateOnce() = runTest {
        val fake = FakeLookupSource(found("x"))
        val caching = CachingProductLookupRepository(fake)

        caching.lookup("111")
        caching.lookup("222")

        assertEquals(2, fake.calls)
        assertEquals(listOf("111", "222"), fake.seen)
    }

    // -- HITO 2: in-flight dedupe -------------------------------------------

    @Test
    fun inFlight_concurrentSameBarcode_singleDelegateCall() = runTest {
        val gated = GatedLookupSource(found("111"))
        val caching = CachingProductLookupRepository(gated)

        val a = async { caching.lookup("111") }
        val b = async { caching.lookup("111") }
        testScheduler.runCurrent()            // both enter lookup; leader blocks on gate

        // Only the leader reached the delegate; the follower joined the Deferred.
        assertEquals(1, gated.calls, "concurrent same-barcode lookups must share one call")

        gated.release()
        val ra = a.await()
        val rb = b.await()

        assertEquals(1, gated.calls)
        assertSame(ra, rb, "both callers receive the same result instance")
    }

    @Test
    fun inFlight_differentBarcodes_notBlocked() = runTest {
        val gated = GatedLookupSource(found("x"))
        val caching = CachingProductLookupRepository(gated)

        val a = async { caching.lookup("111") }
        val b = async { caching.lookup("222") }
        testScheduler.runCurrent()

        // Different barcodes are independent leaders → BOTH reached the delegate
        // while neither has completed (no serialisation behind the lock).
        assertEquals(2, gated.calls, "different barcodes must not serialise")

        gated.release()
        a.await(); b.await()
    }

    @Test
    fun afterInFlightResolves_definitive_servedFromCache() = runTest {
        val gated = GatedLookupSource(found("111"))
        val caching = CachingProductLookupRepository(gated)

        val a = async { caching.lookup("111") }
        testScheduler.runCurrent()
        gated.release()
        a.await()

        // In-flight cleared + result cached → next lookup is a cache hit.
        caching.lookup("111")
        assertEquals(1, gated.calls, "resolved+cached code must not be re-queried")
    }

    @Test
    fun afterInFlightResolves_transient_clearsEntryAndRetries() = runTest {
        val gated = GatedLookupSource(ProductLookupResult.NetworkError)
        val caching = CachingProductLookupRepository(gated)

        val a = async { caching.lookup("111") }
        testScheduler.runCurrent()
        gated.release()
        a.await()

        // NetworkError is NOT cached AND the in-flight entry was cleared, so a
        // fresh lookup hits the delegate again (not stuck following a dead
        // Deferred, not served from cache).
        caching.lookup("111")
        assertEquals(2, gated.calls, "transient outcome must clear in-flight and allow retry")
    }
}
