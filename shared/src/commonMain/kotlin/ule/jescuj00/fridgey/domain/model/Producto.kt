package ule.jescuj00.fridgey.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.daysUntil

data class Producto(
    val id: String,
    val idNevera: String,
    val codigoBarras: String?,
    val nombre: String,
    val categoria: Categoria,
    val fechaCaducidad: LocalDate,
    val fechaRegistro: LocalDate,
    val imagenUrl: String?,
    // Amount of product in this entry. `Double` because the supported
    // units include continuous quantities (g, kg, ml, l) — "0.5 kg of
    // ham" is a perfectly normal entry. For discrete units (UNIDADES)
    // callers store integer values; the field tolerates both.
    val cantidad: Double,
    // Unit of measure pairing with `cantidad`. Persisted as its `valor`
    // string in SQLite and round-tripped via `UnidadMedida.fromString`.
    val unidad: UnidadMedida,
    // Lead-time (in days) for the expiry notification. 0 means "warn me on
    // the day". Stored as a plain Int; the notification scheduler reads it.
    val diasAvisoAntes: Int,
) {
    // Calculated at read time; negative means already expired
    val diasRestantes: Int
        get() {
            val hoy = Clock.System.todayIn(TimeZone.currentSystemDefault())
            return hoy.daysUntil(fechaCaducidad)
        }

    val estaExpirado: Boolean
        get() = diasRestantes < 0

    val expiraPronto: Boolean
        get() = diasRestantes in 0..3
}
