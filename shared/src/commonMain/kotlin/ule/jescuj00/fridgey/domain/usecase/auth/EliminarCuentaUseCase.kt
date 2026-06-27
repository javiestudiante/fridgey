package ule.jescuj00.fridgey.domain.usecase.auth

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.functions.functions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.serialization.Serializable
import ule.jescuj00.fridgey.data.repository.AuthRepository
import ule.jescuj00.fridgey.data.repository.BorradoLocalRepository

/**
 * UC: eliminación de cuenta (RGPD — derecho de supresión). Server-authoritative:
 * la verdad y el borrado los hace la Cloud Function callable `eliminarCuenta`
 * (región europe-west1). El cliente solo orquesta el después.
 *
 * Contrato de la función (payload normal, NO HttpsError — el SDK GitLive 2.1.0 no
 * propaga code/details a iOS): `{ ok: Boolean, neveras: [{ id, nombre }] }`.
 *  - ok=false → hay neveras propias compartidas que bloquean: se devuelven para
 *    listarlas ([ResultadoEliminarCuenta.Bloqueada]). No se ha borrado nada.
 *  - ok=true  → la cuenta ya no existe en el servidor: se cierra sesión local y se
 *    borra TODO el espejo local SQLDelight ([ResultadoEliminarCuenta.Exito]).
 *
 * ANCLAJE A SCOPE DE PROCESO (fix del wipe perdido): toda la operación se ejecuta
 * en [procesoScope] (el scope de sync, de vida igual al proceso), NO en el
 * viewModelScope del llamante. En el éxito la función hace `deleteUser`
 * server-side → el SDK de Auth emite `Unauthenticated` → la navegación raíz
 * desmonta la pantalla de Ajustes y CANCELA su viewModelScope. Si el borrado
 * colgara de ese scope, se abortaría a media transacción y el espejo local NO se
 * limpiaría (bug observado). Al correr en el scope de proceso, `borrarTodo()` +
 * `signOut()` se garantizan completos aunque el llamante (y su `await`) sean
 * cancelados. NOTA: el scope de sync NO se cancela en logout (solo
 * `SyncManager.stop()` para sus jobs), así que sobrevive a este flujo.
 *
 * Cualquier excepción del puente suspend de GitLive (red, función caída, etc.) se
 * captura y se devuelve como [ResultadoEliminarCuenta.Error] legible — nunca se
 * propaga para que la UI no crashee. La [CancellationException] SÍ se relanza (no
 * se enmascara): es estructura de corrutinas, no un error del borrado.
 */
class EliminarCuentaUseCase(
    private val authRepository: AuthRepository,
    private val borradoLocal: BorradoLocalRepository,
    private val procesoScope: CoroutineScope,
) {
    suspend operator fun invoke(): ResultadoEliminarCuenta =
        procesoScope.async {
            try {
                val funciones = Firebase.functions(REGION)
                val resultado = funciones.httpsCallable(NOMBRE_FUNCION)()
                val respuesta = resultado.data<EliminarCuentaResponse>()

                if (!respuesta.ok) {
                    // Bloqueado: el servidor NO borró nada (no hubo deleteUser), la
                    // sesión sigue viva y la UI puede mostrar la lista con calma.
                    return@async ResultadoEliminarCuenta.Bloqueada(
                        respuesta.neveras.map { NeveraBloqueada(id = it.id, nombre = it.nombre) },
                    )
                }

                // Éxito server-authoritative. El servidor YA borró usuarios/{uid} y
                // sus tokens FCM, así que NO se usa SignOutUseCase (su `unregister`
                // sería un write inútil a un doc ya inexistente): signOut directo.
                // Wipe ANTES de signOut.
                borradoLocal.borrarTodo()
                authRepository.signOut()
                ResultadoEliminarCuenta.Exito
            } catch (e: CancellationException) {
                throw e // no enmascarar la cancelación del scope de proceso
            } catch (e: Exception) {
                ResultadoEliminarCuenta.Error(e.message ?: "No se pudo eliminar la cuenta.")
            }
        }.await()

    private companion object {
        const val REGION = "europe-west1"
        const val NOMBRE_FUNCION = "eliminarCuenta"
    }
}

/** Nevera propia compartida que bloquea el borrado (para listarla en la UI). */
data class NeveraBloqueada(val id: String, val nombre: String)

/** Resultado del borrado de cuenta, para que la UI reaccione sin manejar excepciones. */
sealed interface ResultadoEliminarCuenta {
    /** Cuenta eliminada en servidor + sesión cerrada + espejo local borrado. */
    data object Exito : ResultadoEliminarCuenta

    /** Bloqueado: hay neveras propias compartidas que el usuario debe resolver antes. */
    data class Bloqueada(val neveras: List<NeveraBloqueada>) : ResultadoEliminarCuenta

    /** Fallo legible (red, función, etc.). No se ha eliminado la cuenta. */
    data class Error(val mensaje: String) : ResultadoEliminarCuenta
}

/** Espejo del payload de la callable. `neveras` por defecto vacío (ausente cuando ok=true). */
@Serializable
private data class EliminarCuentaResponse(
    val ok: Boolean = false,
    val neveras: List<NeveraBloqueadaDto> = emptyList(),
)

@Serializable
private data class NeveraBloqueadaDto(
    val id: String = "",
    val nombre: String = "",
)
