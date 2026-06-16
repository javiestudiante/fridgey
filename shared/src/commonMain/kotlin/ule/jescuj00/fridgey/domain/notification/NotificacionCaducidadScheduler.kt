package ule.jescuj00.fridgey.domain.notification

/**
 * Programa y cancela la comprobación periódica de avisos de caducidad.
 *
 * Interfaz común implementada por plataforma vía Koin (Android: WorkManager;
 * iOS: no-op en Fase 1). Deliberadamente NO usa `expect/actual`: es una interfaz
 * de dominio con bindings de plataforma registrados en cada módulo Koin.
 */
interface NotificacionCaducidadScheduler {
    /**
     * Asegura que existe una comprobación diaria recurrente. Es idempotente:
     * llamarla repetidamente no crea trabajos duplicados (en Android se apoya en
     * una política KEEP sobre un work único).
     */
    fun programarComprobacionDiaria()

    /**
     * Lanza una comprobación puntual inmediata (one-time), p. ej. al arrancar la
     * app o tras editar la fecha de un producto, para no esperar al ciclo diario.
     */
    fun comprobarAhora()

    /** Cancela toda la comprobación de avisos (p. ej. al desactivar el toggle). */
    fun cancelar()
}
