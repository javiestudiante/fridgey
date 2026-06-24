package ule.jescuj00.fridgey.di

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import ule.jescuj00.fridgey.domain.notification.NotificacionCaducidadPoster
import ule.jescuj00.fridgey.domain.notification.NotificacionCaducidadScheduler
import ule.jescuj00.fridgey.domain.notification.RegistroTokenPush
import ule.jescuj00.fridgey.notificaciones.NotificacionCaducidadPosterAndroid
import ule.jescuj00.fridgey.notificaciones.NotificacionCaducidadSchedulerAndroid
import ule.jescuj00.fridgey.notificaciones.RegistroTokenPushAndroid

/**
 * Implementaciones Android de las interfaces commonMain de avisos de caducidad
 * y del registro de token push, enlazadas a su tipo común para que el Worker,
 * FridgeyApplication, el servicio FCM y SignOutUseCase las resuelvan por interfaz
 * (el NO-OP de iOS vive en su propio módulo, HITO 4 / HITO 5).
 */
fun notificationModule(): Module = module {
    single<NotificacionCaducidadPoster> { NotificacionCaducidadPosterAndroid(androidContext()) }
    single<NotificacionCaducidadScheduler> { NotificacionCaducidadSchedulerAndroid(androidContext()) }
    single<RegistroTokenPush> { RegistroTokenPushAndroid(get(), get()) }
}
