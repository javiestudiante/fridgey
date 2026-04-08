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
    val imagenUrl: String?
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
