package ule.jescuj00.fridgey.domain.model.auth

import ule.jescuj00.fridgey.domain.model.Proveedor

/**
 * Domain representation of an authenticated user.
 *
 * Built from the Firebase user object inside [AuthRepository] — the rest
 * of the app should never see Firebase types.
 */
data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val provider: Proveedor
)
