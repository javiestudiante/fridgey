package ule.jescuj00.fridgey.domain.usecase

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import ule.jescuj00.fridgey.domain.model.AvisoCaducidad
import ule.jescuj00.fridgey.domain.model.PlanAvisos
import ule.jescuj00.fridgey.domain.model.ProductoParaAviso

/**
 * Función pura que decide QUÉ avisos de caducidad programar en iOS, donde las
 * notificaciones se programan POR ADELANTADO (`UNCalendarNotificationTrigger` en
 * `fechaDisparo`) para dispararse con la app cerrada.
 *
 * Reparte los productos en dos cubos (ver [PlanAvisos]):
 *  - **futuros** (`fechaDisparo > hoy`): se programan por adelantado. Ordenados
 *    ascendente por `fechaDisparo` y **truncados a [limite]** (iOS admite máx. 64
 *    notificaciones locales pendientes; la estrategia iOS es cancel-all +
 *    reschedule en cada apertura para rellenar conforme se van disparando). NO
 *    consultan el dedup — su idempotencia es el identifier estable + reschedule.
 *  - **inmediatos** (`fechaDisparo <= hoy` Y `fechaCaducidadUltimoAviso !=
 *    fechaCaducidad`): ventana ya abierta y aún no avisados. Se entregan ya y el
 *    llamador marca el dedup, para no re-dispararlos en cada reschedule.
 *
 * No reemplaza a [EvaluarAvisosCaducidadUseCase] (motor Android "avisar ahora");
 * este planner es el modelo iOS "programar por adelantado". Sin dependencias ni
 * efectos; toda la aritmética es sobre [LocalDate]. Se registra como `factory`.
 */
class PlanificarAvisosCaducidadUseCase {

    operator fun invoke(
        productos: List<ProductoParaAviso>,
        hoy: LocalDate,
        limite: Int = LIMITE_NOTIFICACIONES_IOS,
    ): PlanAvisos {
        val futuros = mutableListOf<AvisoCaducidad>()
        val inmediatos = mutableListOf<AvisoCaducidad>()

        for (producto in productos) {
            val fechaDisparo = producto.fechaCaducidad.minus(producto.diasAvisoAntes, DateTimeUnit.DAY)
            val aviso = AvisoCaducidad(
                productId = producto.productId,
                neveraId = producto.neveraId,
                nombreProducto = producto.nombreProducto,
                fechaCaducidad = producto.fechaCaducidad,
                fechaDisparo = fechaDisparo,
            )
            when {
                // Ventana futura: se programa por adelantado (sin mirar el dedup).
                fechaDisparo > hoy -> futuros += aviso
                // Ventana abierta y no avisado todavía para esta fechaCaducidad.
                producto.fechaCaducidadUltimoAviso != producto.fechaCaducidad -> inmediatos += aviso
                // Resto (ventana abierta pero ya avisado) -> se ignora.
            }
        }

        return PlanAvisos(
            futuros = futuros.sortedBy { it.fechaDisparo }.take(limite.coerceAtLeast(0)),
            inmediatos = inmediatos,
        )
    }

    companion object {
        /** Máximo de notificaciones locales pendientes que iOS permite por app. */
        const val LIMITE_NOTIFICACIONES_IOS = 64
    }
}
