package ule.jescuj00.fridgey.domain.model

/**
 * Resultado de `PlanificarAvisosCaducidadUseCase` (modelo iOS "programar por
 * adelantado"): los avisos repartidos en dos cubos con tratamiento distinto.
 *
 * No aplica a Android (allí el motor "avisa ahora" con
 * `EvaluarAvisosCaducidadUseCase`).
 */
data class PlanAvisos(
    /**
     * Ventana aún NO abierta (`fechaDisparo > hoy`). Se programan por adelantado
     * (en iOS, un `UNCalendarNotificationTrigger` en `fechaDisparo`). Vienen ya
     * ordenados ascendente por `fechaDisparo` y **truncados al límite** (iOS
     * admite máx. 64 notificaciones locales pendientes). NO consultan el dedup:
     * su idempotencia es el identifier estable + cancel-all/reschedule.
     */
    val futuros: List<AvisoCaducidad>,
    /**
     * Ventana ya abierta (`fechaDisparo <= hoy`) y aún no avisados para su
     * `fechaCaducidad` actual. Se entregan de inmediato; el llamador marca el
     * dedup (`marcarAvisado`) tras entregarlos, para no re-dispararlos en cada
     * reschedule. Sin truncar (no son notificaciones pendientes en iOS).
     */
    val inmediatos: List<AvisoCaducidad>,
)
