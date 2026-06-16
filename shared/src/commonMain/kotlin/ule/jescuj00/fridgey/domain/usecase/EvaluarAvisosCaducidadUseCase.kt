package ule.jescuj00.fridgey.domain.usecase

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import ule.jescuj00.fridgey.domain.model.AvisoCaducidad
import ule.jescuj00.fridgey.domain.model.ProductoParaAviso

/**
 * Función pura: dado el conjunto de productos locales y el día de hoy, devuelve
 * los avisos de caducidad que deben dispararse ahora mismo.
 *
 * Un producto entra en la lista cuando se cumplen **ambas** condiciones:
 *  1. La ventana de aviso ya está abierta: `hoy >= fechaCaducidad - diasAvisoAntes`.
 *     **Sin cota superior** — un producto ya caducado y nunca avisado también se
 *     avisa una vez.
 *  2. Aún no se le ha avisado para su `fechaCaducidad` actual
 *     (`fechaCaducidadUltimoAviso != fechaCaducidad`). Si la fecha se edita, el
 *     último aviso deja de coincidir y el producto se re-arma.
 *
 * No tiene dependencias ni efectos colaterales y toda la aritmética es sobre
 * [LocalDate], nunca sobre instantes: `hoy` lo calcula el llamador (en Android,
 * `Clock.System.todayIn(TimeZone.currentSystemDefault())`) y `fechaCaducidad`
 * llega ya convertida desde epoch-segundos en `TimeZone.UTC`.
 *
 * Hallazgo conocido (no se aborda en Fase 1): existe una asimetría UTC/local
 * preexistente — el almacenamiento usa medianoche UTC mientras `Producto
 * .diasRestantes` usa la zona del sistema. Se deja anotado para alinearlo más
 * adelante; aquí no se toca `Producto.diasRestantes`.
 *
 * Se registra como `factory` en Koin (es stateless).
 */
class EvaluarAvisosCaducidadUseCase {

    operator fun invoke(
        productos: List<ProductoParaAviso>,
        hoy: LocalDate,
    ): List<AvisoCaducidad> =
        productos.mapNotNull { producto ->
            val fechaDisparo = producto.fechaCaducidad.minus(producto.diasAvisoAntes, DateTimeUnit.DAY)
            val ventanaAbierta = hoy >= fechaDisparo
            val yaAvisado = producto.fechaCaducidadUltimoAviso == producto.fechaCaducidad

            if (ventanaAbierta && !yaAvisado) {
                AvisoCaducidad(
                    productId = producto.productId,
                    neveraId = producto.neveraId,
                    nombreProducto = producto.nombreProducto,
                    fechaCaducidad = producto.fechaCaducidad,
                    fechaDisparo = fechaDisparo,
                )
            } else {
                null
            }
        }
}
