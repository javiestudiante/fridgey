package ule.jescuj00.fridgey.domain.model

data class NeveraWithDetails(
    val nevera: Nevera,
    val colaboradores: List<Usuario>,
    val numeroProductos: Int
)
