package ule.jescuj00.fridgey.domain.usecase

import kotlinx.datetime.LocalDate
import ule.jescuj00.fridgey.domain.model.ProductoParaAviso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EvaluarAvisosCaducidadUseCaseTest {

    private val evaluar = EvaluarAvisosCaducidadUseCase()

    // Día de referencia fijo => tests deterministas, sin reloj.
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
    fun bordeDeVentana_hoyIgualFechaDisparo_incluido() {
        // Caduca en 3 días con avisoAntes=3 => fechaDisparo == hoy (límite exacto).
        val p = producto(fechaCaducidad = LocalDate(2026, 6, 19), diasAvisoAntes = 3)

        val avisos = evaluar(listOf(p), hoy)

        assertEquals(1, avisos.size)
        val aviso = avisos.single()
        assertEquals(hoy, aviso.fechaDisparo)
        assertEquals(LocalDate(2026, 6, 19), aviso.fechaCaducidad)
        assertEquals("p1", aviso.productId)
    }

    @Test
    fun antesDeLaVentana_excluido() {
        // Caduca en 4 días con avisoAntes=3 => fechaDisparo = mañana > hoy.
        val p = producto(fechaCaducidad = LocalDate(2026, 6, 20), diasAvisoAntes = 3)

        assertTrue(evaluar(listOf(p), hoy).isEmpty())
    }

    @Test
    fun yaAvisadoMismaFecha_excluido() {
        val caducidad = LocalDate(2026, 6, 18) // dentro de ventana (avisoAntes=3)
        val p = producto(
            fechaCaducidad = caducidad,
            diasAvisoAntes = 3,
            fechaCaducidadUltimoAviso = caducidad, // ya avisado para esta fecha
        )

        assertTrue(evaluar(listOf(p), hoy).isEmpty())
    }

    @Test
    fun fechaEditada_rearmado_incluido() {
        // Se avisó para una fecha anterior; el usuario movió la caducidad a otra
        // fecha que sigue dentro de ventana => debe volver a avisar.
        val p = producto(
            fechaCaducidad = LocalDate(2026, 6, 18),
            diasAvisoAntes = 3,
            fechaCaducidadUltimoAviso = LocalDate(2026, 6, 10),
        )

        assertEquals(1, evaluar(listOf(p), hoy).size)
    }

    @Test
    fun yaCaducadoNuncaAvisado_incluido() {
        // Caducó ayer y nunca se avisó => sin cota superior, se avisa una vez.
        val p = producto(
            fechaCaducidad = LocalDate(2026, 6, 15),
            diasAvisoAntes = 3,
            fechaCaducidadUltimoAviso = null,
        )

        assertEquals(1, evaluar(listOf(p), hoy).size)
    }

    @Test
    fun diasAvisoAntesCero_avisaSoloElDiaDeCaducidad() {
        // Con avisoAntes=0, fechaDisparo == fechaCaducidad.
        val caducaHoy = producto(fechaCaducidad = hoy, diasAvisoAntes = 0)
        assertEquals(1, evaluar(listOf(caducaHoy), hoy).size)

        val caducaManana = producto(fechaCaducidad = LocalDate(2026, 6, 17), diasAvisoAntes = 0)
        assertTrue(evaluar(listOf(caducaManana), hoy).isEmpty())
    }

    @Test
    fun variosProductos_filtraSoloLosQueDebenAvisar() {
        val incluido = producto(id = "incluido", fechaCaducidad = LocalDate(2026, 6, 18))
        val antesVentana = producto(id = "antes", fechaCaducidad = LocalDate(2026, 6, 25))
        val yaAvisado = producto(
            id = "avisado",
            fechaCaducidad = LocalDate(2026, 6, 17),
            fechaCaducidadUltimoAviso = LocalDate(2026, 6, 17),
        )

        val avisos = evaluar(listOf(incluido, antesVentana, yaAvisado), hoy)

        assertEquals(listOf("incluido"), avisos.map { it.productId })
    }
}
