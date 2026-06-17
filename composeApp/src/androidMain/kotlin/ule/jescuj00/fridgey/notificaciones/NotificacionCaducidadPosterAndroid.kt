package ule.jescuj00.fridgey.notificaciones

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.todayIn
import ule.jescuj00.fridgey.MainActivity
import ule.jescuj00.fridgey.R
import ule.jescuj00.fridgey.domain.model.AvisoCaducidad
import ule.jescuj00.fridgey.domain.notification.NotificacionCaducidadPoster

/**
 * Implementación Android de [NotificacionCaducidadPoster]: publica un aviso de
 * caducidad como notificación local en el canal [CanalCaducidad.CHANNEL_ID].
 *
 * Se registra en Koin (HITO 3B) recibiendo el `androidContext()`.
 */
class NotificacionCaducidadPosterAndroid(
    private val context: Context,
) : NotificacionCaducidadPoster {

    override fun mostrar(aviso: AvisoCaducidad): Boolean {
        // Sin permiso no posteamos NI marcamos el dedup: así el aviso se
        // reintenta cuando el usuario lo conceda.
        if (!permisoConcedido()) return false

        val notificacion = NotificationCompat.Builder(context, CanalCaducidad.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_aviso_caducidad)
            .setColor(MINT_COLOR) // acento de marca #69A481
            .setContentTitle(aviso.nombreProducto)
            .setContentText(textoRelativo(aviso.fechaCaducidad))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(construirPendingIntent(aviso))
            .build()

        // id estable por producto: re-postear ACTUALIZA en lugar de apilar.
        NotificationManagerCompat.from(context)
            .notify(aviso.productId.hashCode(), notificacion)
        return true
    }

    private fun permisoConcedido(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // < API 33: el permiso se concede al instalar
        }

    private fun construirPendingIntent(aviso: AvisoCaducidad): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_NEVERA_ID, aviso.neveraId)
            // SINGLE_TOP: si MainActivity ya está arriba reusa la instancia y
            // entra por onNewIntent (no apila otra). NEW_TASK: necesario para
            // poder lanzar la Activity desde el contexto (system UI) de la
            // notificación cuando la app está cerrada.
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return PendingIntent.getActivity(
            context,
            // requestCode distinto por producto -> PendingIntents independientes
            // (si compartieran code, FLAG_UPDATE_CURRENT pisaría el neveraId).
            aviso.productId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /**
     * Texto relativo calculado en [LocalDate] (NO usa `Producto.diasRestantes`,
     * que arrastra el bug UTC/local). `hoy` en la zona del sistema; la
     * `fechaCaducidad` del aviso ya llega como LocalDate.
     */
    private fun textoRelativo(fechaCaducidad: LocalDate): String {
        val hoy = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val dias = hoy.daysUntil(fechaCaducidad)
        return when {
            dias < 0 -> if (dias == -1) "Caducó hace 1 día" else "Caducó hace ${-dias} días"
            dias == 0 -> "Caduca hoy"
            dias == 1 -> "Caduca mañana"
            else -> "Caduca en $dias días"
        }
    }

    private companion object {
        const val MINT_COLOR = 0xFF69A481.toInt()
    }
}
