package ule.jescuj00.fridgey.domain.model

data class Nevera(
    val id: String,
    val nombre: String,
    val idPropietario: String,
    val esPropietario: Boolean,
    val numeroProductos: Int
)
