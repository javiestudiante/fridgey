package ule.jescuj00.fridgey.domain.model

/**
 * Everything the "Mis neveras" card needs for one fridge, sourced entirely
 * from existing data:
 *  - [nevera] — id / nombre / esPropietario / numeroProductos (PRODUCTOS stat).
 *  - [expiringCount] — products expiring within the "por caducar" window
 *    (POR CADUCAR stat; amber when > 0).
 *  - [miembros] — owner + collaborators, for the avatar stack and the
 *    MIEMBROS stat ([miembros.size]). No activity tracking is implied.
 */
data class NeveraResumen(
    val nevera: Nevera,
    val expiringCount: Int,
    val miembros: List<Usuario>,
)
