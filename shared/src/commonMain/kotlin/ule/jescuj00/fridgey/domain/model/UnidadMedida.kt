package ule.jescuj00.fridgey.domain.model

/**
 * Unidades de medida soportadas por el modelo de productos.
 *
 *  - `valor`   string canónico para persistencia (SQLDelight serializes the
 *              enum as TEXT using this).
 *  - `simbolo` abreviatura corta mostrada al usuario en la UI.
 */
enum class UnidadMedida(val valor: String, val simbolo: String) {
    UNIDADES("unidades", "ud"),
    GRAMOS("gramos", "g"),
    KILOGRAMOS("kilogramos", "kg"),
    MILILITROS("mililitros", "ml"),
    LITROS("litros", "l");

    companion object {
        fun fromString(valor: String): UnidadMedida =
            entries.firstOrNull { it.valor == valor.lowercase() } ?: UNIDADES
    }
}
