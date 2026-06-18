package ule.jescuj00.fridgey.notificaciones

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import ule.jescuj00.fridgey.data.repository.PreferenciasRepository
import ule.jescuj00.fridgey.data.repository.ProductoRepository
import ule.jescuj00.fridgey.domain.notification.NotificacionCaducidadPoster
import ule.jescuj00.fridgey.domain.usecase.EvaluarAvisosCaducidadUseCase

/**
 * Barrido de avisos de caducidad. Lo lanza WorkManager (periódico diario +
 * one-time de catch-up). Resuelve dependencias por [KoinComponent.inject] desde
 * el contexto global de Koin que arranca FridgeyApplication; así NO hace falta
 * un WorkerFactory ni un Configuration.Provider.
 */
class AvisosCaducidadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val preferenciasRepository: PreferenciasRepository by inject()
    private val productoRepository: ProductoRepository by inject()
    private val evaluarAvisos: EvaluarAvisosCaducidadUseCase by inject()
    private val poster: NotificacionCaducidadPoster by inject()

    override suspend fun doWork(): Result {
        return try {
            // Gate por preferencia: toggle OFF -> no-op (no leemos ni posteamos).
            if (!preferenciasRepository.avisosCaducidadActivados()) {
                return Result.success()
            }

            val productos = productoRepository.getProductosParaAviso()
            val hoy = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val avisos = evaluarAvisos(productos, hoy)

            for (aviso in avisos) {
                // Marcamos el dedup SÓLO si la notificación se publicó de verdad.
                // Si falta el permiso -> mostrar() == false -> NO marcamos, para
                // poder avisar cuando el usuario lo conceda.
                if (poster.mostrar(aviso)) {
                    productoRepository.marcarAvisado(aviso.productId, aviso.fechaCaducidad)
                }
            }
            Result.success()
        } catch (e: CancellationException) {
            // WorkManager paró el worker: propagamos la cancelación; no es un fallo.
            throw e
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
