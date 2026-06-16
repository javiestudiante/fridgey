package ule.jescuj00.fridgey.domain.notification

import ule.jescuj00.fridgey.domain.model.AvisoCaducidad

/**
 * Muestra un aviso de caducidad como notificación local del sistema.
 *
 * Interfaz común implementada por plataforma vía Koin (Android:
 * `NotificationManagerCompat`; iOS: no-op en Fase 1).
 */
interface NotificacionCaducidadPoster {
    /**
     * Publica una notificación para [aviso].
     *
     * @return `true` si la notificación se publicó realmente; `false` si no se
     *   pudo (típicamente porque falta el permiso POST_NOTIFICATIONS, o el canal
     *   está silenciado). El llamador NO debe marcar el dedup
     *   (`fecha_caducidad_ultimo_aviso`) salvo que esto devuelva `true`, de modo
     *   que el aviso se reintente cuando el usuario conceda el permiso.
     */
    fun mostrar(aviso: AvisoCaducidad): Boolean
}
