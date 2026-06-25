package ule.jescuj00.fridgey.notificaciones

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.qualifier.named
import ule.jescuj00.fridgey.MainActivity
import ule.jescuj00.fridgey.R
import ule.jescuj00.fridgey.data.repository.AuthRepository
import ule.jescuj00.fridgey.di.SYNC_SCOPE_QUALIFIER
import ule.jescuj00.fridgey.domain.notification.RegistroTokenPush

/**
 * Servicio FCM declarado en el manifiesto:
 *  - [onNewToken] (HITO 2): rotación del token.
 *  - [onMessageReceived] (HITO 4): filtro de dispositivo compartido + construcción
 *    de la notificación de colaboración + deep-link a la nevera.
 *
 * IMPORTANTE: el filtro de dispositivo compartido SOLO puede aplicarse aquí, y
 * `onMessageReceived` solo se invoca SIEMPRE (foreground y background) si el push
 * llega DATA-ONLY (sin bloque `notification`). Con un bloque `notification`, en
 * background el sistema muestra la nota por su cuenta sin pasar por aquí y el
 * filtro no correría. Diseñado para data-only (ver propuesta a las Functions);
 * mientras tanto, en foreground sigue funcionando porque ahí siempre se llama.
 */
class FridgeyFirebaseMessagingService : FirebaseMessagingService(), KoinComponent {

    override fun onNewToken(token: String) {
        // FCM rotó el token. Si hay sesión, reescribimos el doc del token (register
        // re-consulta el token actual de FirebaseMessaging, así que el parámetro
        // `token` no se usa directamente). Si NO hay sesión, no hacemos nada: el
        // próximo `register` del ciclo de auth (al iniciar sesión) reconciliará.
        val uid = get<AuthRepository>().getCurrentUser()?.uid ?: return
        val registro = get<RegistroTokenPush>()
        // Scope de proceso (SupervisorJob): fire-and-forget, sobrevive al ciclo
        // corto del servicio. register() es a prueba de fallos internamente.
        get<CoroutineScope>(named(SYNC_SCOPE_QUALIFIER)).launch {
            registro.register(uid)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val destinatarioUid = data["destinatarioUid"]
        val sesionUid = get<AuthRepository>().getCurrentUser()?.uid

        // FILTRO de dispositivo compartido (la pieza crítica): solo mostramos si el
        // push es para la sesión ACTUAL de ESTE dispositivo. Cubre el caso
        // A-cierra-sesión / B-entra en el mismo device y los tokens huérfanos (si el
        // unregister del logout falló). Sin sesión activa → descartar.
        if (sesionUid == null || destinatarioUid == null || destinatarioUid != sesionUid) {
            return
        }

        // Sin permiso no se puede mostrar (el token sí se registró: son cosas
        // independientes). El flujo de petición de POST_NOTIFICATIONS ya existe en
        // la UI (NeveraListScreen / AjustesScreen) y es el mismo permiso.
        if (!tienePermisoNotificaciones(this)) return

        val neveraId = data["neveraId"]
        val tipo = data["tipo"]
        // title/body llegan en `data` cuando el push es data-only; fallback al
        // bloque `notification` para el caso foreground mientras las Functions aún
        // lo envíen.
        val titulo = data["title"] ?: message.notification?.title ?: getString(R.string.app_name)
        val cuerpo = data["body"] ?: message.notification?.body.orEmpty()

        mostrarNotificacion(neveraId, tipo, titulo, cuerpo)
    }

    private fun mostrarNotificacion(neveraId: String?, tipo: String?, titulo: String, cuerpo: String) {
        // Deep-link: reutiliza el MISMO mecanismo que las notis de caducidad
        // (MainActivity + EXTRA_NEVERA_ID → App(deepLinkNeveraId)). SINGLE_TOP reusa
        // la Activity viva (onNewIntent); NEW_TASK permite lanzarla con la app cerrada.
        val intent = Intent(this, MainActivity::class.java).apply {
            if (neveraId != null) putExtra(MainActivity.EXTRA_NEVERA_ID, neveraId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        // requestCode por nevera → PendingIntents independientes (si compartieran
        // code, FLAG_UPDATE_CURRENT pisaría el neveraId de otra nevera).
        val requestCode = (neveraId ?: tipo ?: "colaboracion").hashCode()
        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notificacion = NotificationCompat.Builder(this, CanalColaboracion.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_colaboracion)
            .setColor(MINT_COLOR) // acento de marca #69A481
            .setContentTitle(titulo)
            .setContentText(cuerpo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(cuerpo))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .build()

        // id estable por (nevera, tipo): un mismo tipo de evento en una nevera
        // ACTUALIZA su notificación; tipos/neveras distintas coexisten.
        val notificacionId = listOf(neveraId, tipo).joinToString("|").hashCode()
        NotificationManagerCompat.from(this).notify(notificacionId, notificacion)
    }

    private companion object {
        const val MINT_COLOR = 0xFF69A481.toInt()
    }
}
