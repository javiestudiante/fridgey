package ule.jescuj00.fridgey.domain.usecase.auth

import ule.jescuj00.fridgey.data.repository.AuthRepository
import ule.jescuj00.fridgey.domain.notification.RegistroTokenPush

class SignOutUseCase(
    private val authRepository: AuthRepository,
    private val registroTokenPush: RegistroTokenPush,
) {
    /**
     * Borra el token push de ESTE dispositivo ANTES de cerrar sesión y luego
     * cierra sesión. El orden es deliberado: la regla de `usuarios/{uid}/tokens`
     * exige `request.auth.uid == uid`, así que el borrado debe ocurrir mientras
     * la sesión sigue viva; reaccionar al estado `Unauthenticated` (post-signOut)
     * no funcionaría — ya no habría auth ni uid. `unregister` es a prueba de
     * fallos (try/catch interno), de modo que un fallo de red NO impide el
     * signOut.
     */
    suspend operator fun invoke() {
        authRepository.getCurrentUser()?.uid?.let { uid ->
            registroTokenPush.unregister(uid)
        }
        authRepository.signOut()
    }
}
