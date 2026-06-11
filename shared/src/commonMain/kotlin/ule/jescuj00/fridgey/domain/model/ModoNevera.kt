package ule.jescuj00.fridgey.domain.model

/**
 * Eje de PERSISTENCIA de una nevera (uno de los dos ejes del modelo; el otro,
 * "está compartida", es una propiedad DERIVADA de tener `colaboradores`, no un
 * estado).
 *
 * - [LOCAL]: la nevera vive solo en SQLDelight y nunca toca Firestore.
 * - [SYNCED]: existe en Firestore (colección `neveras/{id}`) y se sincroniza
 *   mediante listeners, de modo que sigue al usuario en cualquier dispositivo
 *   donde inicie sesión. Estar SYNCED NO implica estar compartida: una nevera
 *   puede estar en la nube y ser solo del dueño (multi-dispositivo).
 *
 * El default de toda nevera nueva es [LOCAL] (privacidad por defecto: subir a
 * la nube es consentimiento explícito). [fromString] degrada valores
 * desconocidos a [LOCAL] deliberadamente — es el modo seguro: nunca
 * sincronizar por accidente.
 */
enum class ModoNevera(val valor: String) {
    LOCAL("LOCAL"),
    SYNCED("SYNCED");

    companion object {
        fun fromString(valor: String): ModoNevera = when (valor) {
            SYNCED.valor -> SYNCED
            else -> LOCAL
        }
    }
}
