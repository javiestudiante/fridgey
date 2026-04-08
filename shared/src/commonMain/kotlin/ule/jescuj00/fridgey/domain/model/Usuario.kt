package ule.jescuj00.fridgey.domain.model

data class Usuario(
    val id: String,
    val email: String,
    val nombre: String,
    val proveedor: Proveedor,
    val fotoUrl: String?
)

enum class Proveedor(val valor: String) {
    GOOGLE("google"),
    APPLE("apple");

    companion object {
        fun fromString(valor: String): Proveedor =
            entries.firstOrNull { it.valor == valor.lowercase() } ?: GOOGLE
    }
}
