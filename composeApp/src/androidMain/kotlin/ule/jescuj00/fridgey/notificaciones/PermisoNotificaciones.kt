package ule.jescuj00.fridgey.notificaciones

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * `true` si se pueden publicar notificaciones: POST_NOTIFICATIONS concedido
 * (API 33+) o no aplica (en <33 se concede al instalar).
 */
fun tienePermisoNotificaciones(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

/**
 * Abre los ajustes de notificaciones de la app (caso de denegación permanente):
 * `ACTION_APP_NOTIFICATION_SETTINGS` y, si no está disponible, los detalles de
 * la aplicación como fallback.
 */
fun abrirAjustesNotificaciones(context: Context) {
    val intentCanal = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val abierto = runCatching { context.startActivity(intentCanal) }.isSuccess
    if (!abierto) {
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(fallback) }
    }
}
