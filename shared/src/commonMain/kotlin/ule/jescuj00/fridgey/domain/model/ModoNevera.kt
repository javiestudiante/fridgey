package ule.jescuj00.fridgey.domain.model

/**
 * Modo de sincronización de una nevera.
 *
 * - [LOCAL]: la nevera vive solo en SQLDelight y nunca toca Firestore.
 * - [SHARED]: existe en Firestore (colección `neveras/{id}`) y se
 *   sincroniza mediante listeners.
 *
 * El default de toda nevera nueva es [LOCAL]. [fromString] degrada
 * valores desconocidos a [LOCAL] deliberadamente — es el modo seguro:
 * nunca sincronizar por accidente.
 */
enum class ModoNevera(val valor: String) {
    LOCAL("LOCAL"),
    SHARED("SHARED");

    companion object {
        fun fromString(valor: String): ModoNevera = when (valor) {
            SHARED.valor -> SHARED
            else -> LOCAL
        }
    }
}
