package ule.jescuj00.fridgey.notificaciones

import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Canal de notificación de eventos COLABORATIVOS push (alguien se une/sale, te
 * expulsan, borran la nevera, añaden producto). Separado del canal de avisos de
 * caducidad ([CanalCaducidad]) para que el usuario pueda silenciar uno sin el
 * otro desde los ajustes del sistema.
 *
 * Crear un canal es idempotente; [crear] se invoca al arranque (FridgeyApplication),
 * junto al de caducidad. `NotificationChannelCompat` es no-op en < API 26.
 */
object CanalColaboracion {
    const val CHANNEL_ID = "colaboracion"

    fun crear(context: Context) {
        val canal = NotificationChannelCompat.Builder(
            CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_DEFAULT,
        )
            .setName("Actividad de neveras compartidas")
            .setDescription("Cambios de miembros y productos en tus neveras compartidas.")
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(canal)
    }
}
