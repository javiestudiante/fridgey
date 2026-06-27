package ule.jescuj00.fridgey.domain.model

/**
 * Cómo abre el botón "+" de una nevera el flujo de alta de producto, según la
 * preferencia del usuario en Ajustes. LOCAL ONLY — preferencia de este
 * dispositivo, nunca se sincroniza con Firestore (igual que el toggle de avisos).
 *
 * - [MANUAL]: abre directamente el formulario de alta a mano.
 * - [ESCANEAR]: abre directamente la cámara (barcode → OFF → OCR de fecha).
 *
 * `valor` es la forma estable persistida en la tabla `Preferencia` (TEXT). El
 * `fromString` cae a [MANUAL] ante cualquier valor ausente o desconocido, que
 * es también el default de producto.
 */
enum class ModoAnadirProducto(val valor: String) {
    MANUAL("manual"),
    ESCANEAR("escanear");

    companion object {
        val DEFAULT = MANUAL

        fun fromString(valor: String?): ModoAnadirProducto =
            entries.firstOrNull { it.valor == valor?.lowercase() } ?: DEFAULT
    }
}
