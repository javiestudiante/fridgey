package ule.jescuj00.fridgey.domain.model

/**
 * Aggregate for the cross-fridge "caducan hoy" home banner.
 *
 *  - [total] — products expiring today across all the user's fridges. The
 *    banner is hidden when this is 0.
 *  - [productNames] — names (ordered) so the subtitle can show the first few.
 *  - [neveraNombre] — the fridge name IFF every expiring-today product is in
 *    the SAME fridge (so the title can read "… en {fridge}"); null when they
 *    span multiple fridges (title omits the "en …" suffix).
 */
data class ExpiringTodaySummary(
    val total: Int,
    val productNames: List<String>,
    val neveraNombre: String?,
    /** Fridge id IFF all expiring-today products share one fridge (so the
     *  banner can navigate to its detail); null when they span several. */
    val neveraId: String?,
)
