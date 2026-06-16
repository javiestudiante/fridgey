package ule.jescuj00.fridgey.domain.model

import kotlinx.datetime.LocalDate

/**
 * Entrada (DTO) de `EvaluarAvisosCaducidadUseCase`: la proyección mínima de un
 * producto local con lo justo para decidir si debe dispararse un aviso.
 *
 * No es el modelo de dominio [Producto] (que no lleva estado de dedup). La capa
 * Android lo construye desde una query SQLDelight que incluye, además de estos
 * campos, la columna de dedup `fecha_caducidad_ultimo_aviso` (Fase 1, HITO 2/3).
 */
data class ProductoParaAviso(
    val productId: String,
    val neveraId: String,
    val nombreProducto: String,
    /**
     * Día de caducidad. Siempre presente: el modelo [Producto] no admite null,
     * así que aquí tampoco. Se lee como `LocalDate` en `TimeZone.UTC` (reusando
     * la conversión epoch-segundos → LocalDate del repositorio).
     */
    val fechaCaducidad: LocalDate,
    /** Lead-time en días; `0` = avisar el mismo día de caducidad. */
    val diasAvisoAntes: Int,
    /**
     * `fechaCaducidad` para la que ya se mostró un aviso. `null` = nunca avisado.
     * El aviso se re-arma en cuanto este valor deja de coincidir con
     * [fechaCaducidad] (p. ej. tras editar la fecha de caducidad del producto).
     */
    val fechaCaducidadUltimoAviso: LocalDate?,
)
