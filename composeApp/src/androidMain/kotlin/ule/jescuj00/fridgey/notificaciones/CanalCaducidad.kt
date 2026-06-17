package ule.jescuj00.fridgey.notificaciones

import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Canal único de notificación de los avisos de caducidad (IMPORTANCE_DEFAULT).
 *
 * Crear un canal es idempotente, así que [crear] se invoca sin más al arranque
 * de la app (FridgeyApplication). Usamos `NotificationChannelCompat` para no
 * tener que guardar por API: en < 26 es un no-op.
 */
object CanalCaducidad {
    const val CHANNEL_ID = "caducidad"

    fun crear(context: Context) {
        val canal = NotificationChannelCompat.Builder(
            CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_DEFAULT,
        )
            .setName("Avisos de caducidad")
            .setDescription("Te avisamos cuando un producto está a punto de caducar.")
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(canal)
    }
}
