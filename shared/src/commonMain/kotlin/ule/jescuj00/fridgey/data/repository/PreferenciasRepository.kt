package ule.jescuj00.fridgey.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ule.jescuj00.fridgey.database.PreferenciaQueries

/**
 * Acceso tipado al almacén de preferencias LOCAL (tabla `Preferencia`).
 * LOCAL ONLY — nunca se sincroniza con Firestore. Hoy respalda el toggle
 * "Avisos de caducidad" (Fase 1).
 */
class PreferenciasRepository(
    private val queries: PreferenciaQueries,
) {

    /**
     * Si los avisos de caducidad están activados. **Default ON explícito**: si la
     * clave NO existe devuelve `true` — nunca `null` ni `false`.
     *
     * Es crítico: si ante clave ausente devolviera `false`, en cada instalación
     * limpia el toggle quedaría apagado de facto y los avisos no se programarían
     * jamás, sin que ningún test lo detectara. El valor se persiste como TEXT
     * "true"/"false".
     */
    suspend fun avisosCaducidadActivados(): Boolean = withContext(Dispatchers.Default) {
        val valor = queries.selectByClave(CLAVE_AVISOS_CADUCIDAD).executeAsOneOrNull()
            ?: return@withContext true // clave ausente -> default ON
        valor == "true"
    }

    /** Persiste el estado del toggle como TEXT "true"/"false" (upsert). */
    suspend fun setAvisosCaducidadActivados(activado: Boolean): Unit =
        withContext(Dispatchers.Default) {
            queries.upsert(
                clave = CLAVE_AVISOS_CADUCIDAD,
                valor = if (activado) "true" else "false",
            )
        }

    companion object {
        const val CLAVE_AVISOS_CADUCIDAD = "avisos_caducidad"
    }
}
