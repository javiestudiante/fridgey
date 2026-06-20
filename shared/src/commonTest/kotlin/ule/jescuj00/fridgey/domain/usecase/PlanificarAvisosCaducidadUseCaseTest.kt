package ule.jescuj00.fridgey.domain.usecase

import kotlinx.datetime.LocalDate
import ule.jescuj00.fridgey.domain.model.ProductoParaAviso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlanificarAvisosCaducidadUseCaseTest {

    private val planificar = PlanificarAvisosCaducidadUseCase()
    private val hoy = LocalDate(2026, 6, 16)

    private fun producto(
        id: String = "p1",
        fechaCaducidad: LocalDate,
        diasAvisoAntes: Int = 3,
        fechaCaducidadUltimoAviso: LocalDate? = null,
    ) = ProductoParaAviso(
        productId = id,
        neveraId = "n1",
        nombreProducto = "Leche",
        fechaCaducidad = fechaCaducidad,
        diasAvisoAntes = diasAvisoAntes,
        fechaCaducidadUltimoAviso = fechaCaducidadUltimoAviso,
    )

    @Test
    fun futuro_fechaDisparoMayorQueHoy_vaAFuturos() {
        // caduca el 26, aviso 3 => fechaDisparo = 23 (futuro)
        val p = producto(fechaCaducidad = LocalDate(2026, 6, 26), diasAvisoAntes = 3)

        val plan = planificar(listOf(p), hoy)

        assertEquals(1, plan.futuros.size)
        assertTrue(plan.inmediatos.isEmpty())
        assertEquals(LocalDate(2026, 6, 23), plan.futuros.single().fechaDisparo)
    }

    @Test
    fun bordeVentana_fechaDisparoIgualHoy_esInmediatoNoFuturo() {
        // caduca el 19, aviso 3 => fechaDisparo == hoy (límite: no es futuro)
        val p = producto(fechaCaducidad = LocalDate(2026, 6, 19), diasAvisoAntes = 3)

        val plan = planificar(listOf(p), hoy)

        assertTrue(plan.futuros.isEmpty())
        assertEquals(1, plan.inmediatos.size)
    }

    @Test
    fun inmediato_ventanaAbiertaYNoAvisado_vaAInmediatos() {
        // caduca el 18, aviso 3 => fechaDisparo = 15 <= hoy
        val p = producto(fechaCaducidad = LocalDate(2026, 6, 18), diasAvisoAntes = 3)

        val plan = planificar(listOf(p), hoy)

        assertEquals(1, plan.inmediatos.size)
        assertTrue(plan.futuros.isEmpty())
    }

    @Test
    fun inmediato_yaAvisadoMismaFecha_seExcluyeDeAmbosCubos() {
        val caducidad = LocalDate(2026, 6, 18)
        val p = producto(
            fechaCaducidad = caducidad,
            diasAvisoAntes = 3,
            fechaCaducidadUltimoAviso = caducidad,
        )

        val plan = planificar(listOf(p), hoy)

        assertTrue(plan.inmediatos.isEmpty())
        assertTrue(plan.futuros.isEmpty())
    }

    @Test
    fun yaCaducadoNuncaAvisado_esInmediato() {
        // caducó ayer, nunca avisado => fechaDisparo < hoy => inmediato (catch-up)
        val p = producto(
            fechaCaducidad = LocalDate(2026, 6, 15),
            diasAvisoAntes = 3,
            fechaCaducidadUltimoAviso = null,
        )

        assertEquals(1, planificar(listOf(p), hoy).inmediatos.size)
    }

    @Test
    fun futuro_ignoraColumnaDedup() {
        // fechaDisparo futura PERO ya marcado: decisión A -> futuros NO miran el
        // dedup, así que se sigue programando.
        val caducidad = LocalDate(2026, 6, 26) // fechaDisparo = 23 (futuro)
        val p = producto(
            fechaCaducidad = caducidad,
            diasAvisoAntes = 3,
            fechaCaducidadUltimoAviso = caducidad,
        )

        assertEquals(1, planificar(listOf(p), hoy).futuros.size)
    }

    @Test
    fun futuros_ordenadosAscendentePorFechaDisparo() {
        val lejano = producto(id = "lejano", fechaCaducidad = LocalDate(2026, 7, 20), diasAvisoAntes = 0)
        val cercano = producto(id = "cercano", fechaCaducidad = LocalDate(2026, 6, 25), diasAvisoAntes = 0)
        val medio = producto(id = "medio", fechaCaducidad = LocalDate(2026, 7, 1), diasAvisoAntes = 0)

        val plan = planificar(listOf(lejano, cercano, medio), hoy)

        assertEquals(listOf("cercano", "medio", "lejano"), plan.futuros.map { it.productId })
    }

    @Test
    fun futuros_truncadosAlLimite_conservaLosMasProximos() {
        val p1 = producto(id = "p1", fechaCaducidad = LocalDate(2026, 7, 1), diasAvisoAntes = 0)
        val p2 = producto(id = "p2", fechaCaducidad = LocalDate(2026, 7, 2), diasAvisoAntes = 0)
        val p3 = producto(id = "p3", fechaCaducidad = LocalDate(2026, 7, 3), diasAvisoAntes = 0)
        val p4 = producto(id = "p4", fechaCaducidad = LocalDate(2026, 7, 4), diasAvisoAntes = 0)
        val p5 = producto(id = "p5", fechaCaducidad = LocalDate(2026, 7, 5), diasAvisoAntes = 0)

        // entrada deliberadamente desordenada
        val plan = planificar(listOf(p3, p5, p1, p4, p2), hoy, limite = 3)

        assertEquals(3, plan.futuros.size)
        assertEquals(listOf("p1", "p2", "p3"), plan.futuros.map { it.productId })
    }

    @Test
    fun diasAvisoAntesCero_caducaHoyEsInmediato_caducaFuturoEsFuturo() {
        val caducaHoy = producto(id = "hoy", fechaCaducidad = hoy, diasAvisoAntes = 0)
        val caducaFuturo = producto(id = "fut", fechaCaducidad = LocalDate(2026, 6, 20), diasAvisoAntes = 0)

        val plan = planificar(listOf(caducaHoy, caducaFuturo), hoy)

        assertEquals(listOf("hoy"), plan.inmediatos.map { it.productId })
        assertEquals(listOf("fut"), plan.futuros.map { it.productId })
    }

    @Test
    fun listaVacia_planVacio() {
        val plan = planificar(emptyList(), hoy)

        assertTrue(plan.futuros.isEmpty())
        assertTrue(plan.inmediatos.isEmpty())
    }
}
