package ule.jescuj00.fridgey.notificaciones

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import ule.jescuj00.fridgey.domain.notification.NotificacionCaducidadScheduler
import java.util.concurrent.TimeUnit

/**
 * Implementación Android de [NotificacionCaducidadScheduler] sobre WorkManager.
 * Sin constraints (tampoco de red): el barrido es 100% local/offline.
 */
class NotificacionCaducidadSchedulerAndroid(
    private val context: Context,
) : NotificacionCaducidadScheduler {

    private val workManager get() = WorkManager.getInstance(context)

    override fun programarComprobacionDiaria() {
        val diario = PeriodicWorkRequestBuilder<AvisosCaducidadWorker>(1, TimeUnit.DAYS)
            .build()
        // KEEP: si ya hay un periódico programado, no lo recreamos (no reinicia
        // el ciclo en cada arranque de la app).
        workManager.enqueueUniquePeriodicWork(
            WORK_DIARIO,
            ExistingPeriodicWorkPolicy.KEEP,
            diario,
        )
    }

    override fun comprobarAhora() {
        val ahora = OneTimeWorkRequestBuilder<AvisosCaducidadWorker>().build()
        // REPLACE: el catch-up reemplaza al anterior pendiente (no acumula). Se
        // invoca al abrir la app y tras conceder el permiso (HITO 4).
        workManager.enqueueUniqueWork(
            WORK_AHORA,
            ExistingWorkPolicy.REPLACE,
            ahora,
        )
    }

    override fun cancelar() {
        // Lo usará el toggle (HITO 4) al desactivar los avisos.
        workManager.cancelUniqueWork(WORK_DIARIO)
    }

    private companion object {
        const val WORK_DIARIO = "avisos_caducidad_diario"
        const val WORK_AHORA = "avisos_caducidad_ahora"
    }
}
