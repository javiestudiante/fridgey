package ule.jescuj00.fridgey.domain.model

import kotlinx.datetime.LocalDate

/**
 * Un aviso de caducidad listo para mostrarse al usuario: el producto ya entró en
 * su ventana de aviso y todavía no se le ha notificado para esta `fechaCaducidad`.
 *
 * Es el resultado de `EvaluarAvisosCaducidadUseCase`. La capa de plataforma lo
 * convierte en una notificación local (Android, Fase 1) o lo ignora (iOS no-op,
 * Fase 1b). `fechaDisparo` se incluye sólo como dato informativo del cálculo:
 * el dedup se hace siempre sobre `fechaCaducidad`.
 */
data class AvisoCaducidad(
    val productId: String,
    val neveraId: String,
    val nombreProducto: String,
    /** Día en que caduca el producto (medianoche UTC en almacenamiento). */
    val fechaCaducidad: LocalDate,
    /** Día en que se abrió la ventana de aviso: `fechaCaducidad - diasAvisoAntes`. */
    val fechaDisparo: LocalDate,
)
