package ule.jescuj00.fridgey.domain.usecase

import ule.jescuj00.fridgey.domain.model.UnidadMedida
import kotlin.test.Test
import kotlin.test.assertEquals

class ParseQuantityUseCaseTest {

    private val parse = ParseQuantityUseCase()
    private val fallback = UnidadMedida.UNIDADES

    private fun assertParsed(raw: String?, cantidad: Double, unidad: UnidadMedida) {
        val r = parse(raw, fallback)
        assertEquals(cantidad, r.cantidad, "cantidad for \"$raw\"")
        assertEquals(unidad, r.unidad, "unidad for \"$raw\"")
    }

    @Test fun grams() = assertParsed("500 g", 500.0, UnidadMedida.GRAMOS)
    @Test fun kilograms() = assertParsed("2 kg", 2.0, UnidadMedida.KILOGRAMOS)
    @Test fun litres() = assertParsed("1 L", 1.0, UnidadMedida.LITROS)
    @Test fun millilitres() = assertParsed("330 ml", 330.0, UnidadMedida.MILILITROS)
    @Test fun centilitres_normalised_to_ml() = assertParsed("33 cl", 330.0, UnidadMedida.MILILITROS)
    @Test fun decimal_comma() = assertParsed("1,5 l", 1.5, UnidadMedida.LITROS)
    @Test fun no_space() = assertParsed("750g", 750.0, UnidadMedida.GRAMOS)
    @Test fun multipack_takes_the_unit_bearing_number() = assertParsed("6 x 125 g", 125.0, UnidadMedida.GRAMOS)

    @Test fun empty_falls_back() = assertParsed("", 1.0, fallback)
    @Test fun null_falls_back() = assertParsed(null, 1.0, fallback)
    @Test fun unparseable_falls_back() = assertParsed("tamaño familiar", 1.0, fallback)
}
