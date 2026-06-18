package ule.jescuj00.fridgey.di

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import ule.jescuj00.fridgey.domain.notification.NotificacionCaducidadPoster
import ule.jescuj00.fridgey.domain.notification.NotificacionCaducidadScheduler
import ule.jescuj00.fridgey.notificaciones.NotificacionCaducidadPosterAndroid
import ule.jescuj00.fridgey.notificaciones.NotificacionCaducidadSchedulerAndroid

/**
 * Implementaciones Android de las interfaces commonMain de avisos de caducidad,
 * enlazadas a su tipo común para que el Worker y FridgeyApplication las
 * resuelvan por interfaz (el NO-OP de iOS vive en su propio módulo, HITO 4).
 */
fun notificationModule(): Module = module {
    single<NotificacionCaducidadPoster> { NotificacionCaducidadPosterAndroid(androidContext()) }
    single<NotificacionCaducidadScheduler> { NotificacionCaducidadSchedulerAndroid(androidContext()) }
}
