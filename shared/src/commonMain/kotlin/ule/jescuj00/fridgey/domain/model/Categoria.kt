package ule.jescuj00.fridgey.domain.model

enum class Categoria(val valor: String) {
    LACTEOS("lacteos"),
    CARNES("carnes"),
    PESCADOS("pescados"),
    FRUTAS("frutas"),
    VERDURAS("verduras"),
    BEBIDAS("bebidas"),
    CONGELADOS("congelados"),
    PANADERIA("panaderia"),
    OTROS("otros");

    /**
     * Editorial emoji used by the UI for category chips, list rows, and the
     * Vista previa block in AddProducto. Implemented as a member property
     * (not a top-level extension) so the Kotlin/Native exporter surfaces it
     * to Swift as `cat.emoji`, matching the Kotlin access pattern on
     * Android. A top-level extension on an enum would otherwise be exposed
     * to Swift as a static function (`CategoriaKt.emojiOf(cat)`), breaking
     * the symmetry the design system relies on.
     */
    val emoji: String
        get() = when (this) {
            LACTEOS -> "🥛"
            CARNES -> "🍗"
            PESCADOS -> "🐟"
            FRUTAS -> "🍎"
            VERDURAS -> "🥬"
            BEBIDAS -> "🧉"
            CONGELADOS -> "🧊"
            PANADERIA -> "🥖"
            OTROS -> "📦"
        }

    /**
     * Unidad de medida sugerida por defecto cuando el usuario elige esta
     * categoría. Es solo una sugerencia: la UI permite cambiarla a mano
     * después. Cada vez que el usuario cambia de categoría, este default
     * vuelve a aplicarse — no recordamos overrides manuales previos.
     *
     * Implementada como propiedad miembro (mismo patrón que `emoji`) para
     * que el K/N exporter la exponga a Swift como `cat.unidadDefault`.
     */
    val unidadDefault: UnidadMedida
        get() = when (this) {
            LACTEOS -> UnidadMedida.LITROS
            CARNES -> UnidadMedida.KILOGRAMOS
            PESCADOS -> UnidadMedida.KILOGRAMOS
            FRUTAS -> UnidadMedida.KILOGRAMOS
            VERDURAS -> UnidadMedida.KILOGRAMOS
            BEBIDAS -> UnidadMedida.LITROS
            CONGELADOS -> UnidadMedida.KILOGRAMOS
            PANADERIA -> UnidadMedida.UNIDADES
            OTROS -> UnidadMedida.UNIDADES
        }

    companion object {
        fun fromString(valor: String): Categoria =
            entries.firstOrNull { it.valor == valor.lowercase() } ?: OTROS
    }
}
