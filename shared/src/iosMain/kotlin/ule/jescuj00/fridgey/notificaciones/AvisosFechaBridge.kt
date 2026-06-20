package ule.jescuj00.fridgey.notificaciones

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.todayIn

/**
 * Pequeños puentes de fecha para el controlador de avisos de iOS (Swift), de
 * modo que la aritmética con [LocalDate] se haga en Kotlin (kotlinx-datetime) y
 * no a mano en Swift, manteniendo la MISMA semántica que Android.
 */

/** Hoy en la zona del sistema (igual que el motor Android). */
fun hoyLocalDate(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

/** Días de [desde] a [hasta]; negativo si [hasta] es anterior a [desde]. */
fun diasEntreFechas(desde: LocalDate, hasta: LocalDate): Int = desde.daysUntil(hasta)
