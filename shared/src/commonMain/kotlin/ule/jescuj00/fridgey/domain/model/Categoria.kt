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

    companion object {
        fun fromString(valor: String): Categoria =
            entries.firstOrNull { it.valor == valor.lowercase() } ?: OTROS
    }
}
