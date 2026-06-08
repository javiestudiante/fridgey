package ule.jescuj00.fridgey.data.repository

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ule.jescuj00.fridgey.domain.model.ProductLookupResult

/**
 * Caching + in-flight-deduping decorator over a [ProductLookupSource].
 *
 * Two layers of redundant-call protection:
 *  1. **Cache** — repeated lookups of the same barcode are served from an
 *     in-memory, session-scoped map so the same product is never queried twice.
 *     Only DEFINITIVE outcomes are cached ([ProductLookupResult.Found] and
 *     [ProductLookupResult.NotFound]); transitory ones (NetworkError) are not,
 *     so a retry can recover.
 *  2. **In-flight guard** — if a lookup for a barcode is already running,
 *     concurrent callers for the SAME barcode await that one call instead of
 *     firing a second. (In practice the ViewModel calls sequentially, so this
 *     is structural safety against concurrency rather than something hit today.)
 *
 * No TTL, no persistence: the maps live for the process lifetime (this is a
 * Koin `single`). Its only job is to avoid re-querying within a session.
 *
 * Locking discipline: the [mutex] guards the (non-thread-safe) [cache] and
 * [inFlight] maps ONLY. The slow work — the delegate network call and awaiting
 * an in-flight [Deferred] — happens OUTSIDE the lock, so lookups of different
 * barcodes never serialise against each other.
 */
class CachingProductLookupRepository(
    private val delegate: ProductLookupSource,
) : ProductLookupSource {

    private val cache = mutableMapOf<String, ProductLookupResult>()
    private val inFlight = mutableMapOf<String, CompletableDeferred<ProductLookupResult>>()
    private val mutex = Mutex()

    override suspend fun lookup(barcode: String): ProductLookupResult {
        // Decide under the lock: serve from cache, follow an in-flight call, or
        // lead a new one. For the "lead" case the Deferred is created and
        // registered here so the decision + registration are atomic.
        val plan = mutex.withLock {
            cache[barcode]?.let { return it }
            val existing = inFlight[barcode]
            if (existing != null) {
                Plan.Follow(existing)
            } else {
                val d = CompletableDeferred<ProductLookupResult>()
                inFlight[barcode] = d
                Plan.Lead(d)
            }
        }

        return when (plan) {
            is Plan.Follow -> plan.deferred.await()             // outside the lock
            is Plan.Lead -> runLeader(barcode, plan.deferred)
        }
    }

    /** Performs the real call, publishes the result to cache + any followers,
     *  and clears the in-flight entry no matter what. */
    private suspend fun runLeader(
        barcode: String,
        deferred: CompletableDeferred<ProductLookupResult>,
    ): ProductLookupResult {
        try {
            val result = delegate.lookup(barcode)              // outside the lock
            mutex.withLock {
                if (result.isCacheable) cache[barcode] = result
                inFlight.remove(barcode)
            }
            deferred.complete(result)                          // wake followers
            return result
        } catch (t: Throwable) {
            // Failure (incl. cancellation): never leave the code stuck as
            // "in-flight" — a future lookup must be able to retry.
            mutex.withLock { inFlight.remove(barcode) }
            deferred.completeExceptionally(t)
            throw t
        }
    }

    /**
     * Only definitive results are worth remembering. Transitory ones
     * (NetworkError — and, later, RateLimited) are deliberately excluded so a
     * retry can recover. Expressed as a `when` (not `is Found || is NotFound`)
     * so a future variant added to the sealed type forces a conscious decision
     * here rather than being silently cached.
     */
    private val ProductLookupResult.isCacheable: Boolean
        get() = when (this) {
            is ProductLookupResult.Found -> true
            ProductLookupResult.NotFound -> true
            ProductLookupResult.NetworkError -> false
            ProductLookupResult.RateLimited -> false   // transitory — retry must be allowed
        }

    private sealed interface Plan {
        data class Follow(val deferred: Deferred<ProductLookupResult>) : Plan
        data class Lead(val deferred: CompletableDeferred<ProductLookupResult>) : Plan
    }
}
