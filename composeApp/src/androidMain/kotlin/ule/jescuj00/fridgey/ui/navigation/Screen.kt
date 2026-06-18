package ule.jescuj00.fridgey.ui.navigation

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object NeveraList : Screen("nevera_list")
    data object CreateNevera : Screen("create_nevera")

    data object NeveraDetail : Screen("nevera_detail/{neveraId}") {
        const val ARG_NEVERA_ID = "neveraId"
        fun createRoute(neveraId: String) = "nevera_detail/$neveraId"
    }

    data object AddProducto : Screen("add_producto/{neveraId}") {
        const val ARG_NEVERA_ID = "neveraId"
        fun createRoute(neveraId: String) = "add_producto/$neveraId"
    }

    data object DateScanner : Screen("date_scanner")

    /** Pantalla de invitación (código + QR) de una nevera colaborativa. */
    data object Invitar : Screen("invitar/{neveraId}") {
        const val ARG_NEVERA_ID = "neveraId"
        fun createRoute(neveraId: String) = "invitar/$neveraId"
    }

    /** Flujo "Unirse con código" (entrada manual + escaneo QR). */
    data object Unirse : Screen("unirse")

    /** Ajustes de la app (hoy: toggle de avisos de caducidad). */
    data object Ajustes : Screen("ajustes")
}

/**
 * Saved-state key the scanner writes to and AddProducto reads from when
 * a date has been picked. Value type: `String` (ISO `yyyy-MM-dd`).
 */
const val SCANNED_DATE_KEY = "scanned_date"

/**
 * Saved-state key for the Open Food Facts autofill resolved during the
 * scanner's CÓDIGO phase. Value type: `String` — a JSON-encoded
 * `ProductAutoFill` (see `ProductAutoFill.toJson` / `fromJsonOrNull`).
 * Absent when the barcode phase was skipped.
 */
const val SCANNED_AUTOFILL_KEY = "scanned_autofill"
