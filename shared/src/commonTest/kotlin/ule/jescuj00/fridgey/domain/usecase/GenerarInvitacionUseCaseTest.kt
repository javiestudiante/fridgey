package ule.jescuj00.fridgey.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenerarInvitacionUseCaseTest {

    private val alfabeto = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toSet()

    @Test
    fun el_codigo_tiene_la_longitud_esperada_y_solo_usa_el_alfabeto_seguro() {
        repeat(100) {
            val codigo = GenerarInvitacionUseCase.generarCodigo()
            assertEquals(GenerarInvitacionUseCase.LONGITUD_CODIGO, codigo.length)
            assertTrue(codigo.all { it in alfabeto }, "Carácter fuera del alfabeto en: $codigo")
        }
    }

    @Test
    fun dos_codigos_consecutivos_no_coinciden() {
        // Probabilísticamente imposible con 32^8 combinaciones; si esto falla
        // es que el generador no se está alimentando de Uuid.random().
        val codigos = List(50) { GenerarInvitacionUseCase.generarCodigo() }
        assertEquals(codigos.size, codigos.toSet().size)
    }
}
