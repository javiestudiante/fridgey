package ule.jescuj00.fridgey.domain.usecase

import ule.jescuj00.fridgey.domain.model.UnidadMedida

data class ParsedQuantity(val cantidad: Double, val unidad: UnidadMedida)

/**
 * Best-effort parser for Open Food Facts' free-text `quantity` field, which is
 * notoriously unstructured ("500 g", "1 L", "33 cl", "6 x 125 g", "", ...).
 *
 * Strategy: scan for the FIRST `<number><unit>` pair whose unit token we
 * recognise. This naturally skips multiplier prefixes — in "6 x 125 g" the
 * "6" has no unit attached, so we land on "125 g". `cl` is normalised to ml
 * (×10) since the domain has no centilitre unit. When nothing usable is found
 * we fall back to `(1.0, fallbackUnit)` — the caller passes the category's
 * default unit.
 */
class ParseQuantityUseCase {

    operator fun invoke(raw: String?, fallbackUnit: UnidadMedida): ParsedQuantity {
        val text = raw?.trim()?.lowercase().orEmpty()
        val parsed = NUMBER_UNIT.findAll(text).firstNotNullOfOrNull { match ->
            val number = match.groupValues[1].replace(',', '.').toDoubleOrNull()
                ?: return@firstNotNullOfOrNull null
            mapUnit(number, match.groupValues[2])
        }
        return parsed ?: ParsedQuantity(1.0, fallbackUnit)
    }

    /** Returns null when [token] is not a unit we understand. */
    private fun mapUnit(number: Double, token: String): ParsedQuantity? = when (token) {
        "kg", "kgs", "kilo", "kilos", "kilogramo", "kilogramos" ->
            ParsedQuantity(number, UnidadMedida.KILOGRAMOS)
        "g", "gr", "grs", "gramo", "gramos" ->
            ParsedQuantity(number, UnidadMedida.GRAMOS)
        "mg", "miligramo", "miligramos" ->
            ParsedQuantity(number / 1000.0, UnidadMedida.GRAMOS)
        "l", "lt", "ltr", "litro", "litros" ->
            ParsedQuantity(number, UnidadMedida.LITROS)
        "cl", "centilitro", "centilitros" ->
            ParsedQuantity(number * 10.0, UnidadMedida.MILILITROS)
        "ml", "mililitro", "mililitros" ->
            ParsedQuantity(number, UnidadMedida.MILILITROS)
        else -> null
    }

    private companion object {
        // <number with . or , decimals> <optional space> <letter token>
        val NUMBER_UNIT = Regex("""(\d+(?:[.,]\d+)?)\s*([a-z]+)""")
    }
}
