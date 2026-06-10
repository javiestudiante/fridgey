package ule.jescuj00.fridgey.data.sync

import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import dev.gitlive.firebase.firestore.FirestoreExceptionCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ule.jescuj00.fridgey.data.remote.firestore.NeveraRemoteRepository
import ule.jescuj00.fridgey.data.remote.firestore.RemoteNeveraEvent
import ule.jescuj00.fridgey.data.remote.firestore.RemoteProductoChange
import ule.jescuj00.fridgey.data.repository.NeveraRepository
import ule.jescuj00.fridgey.data.repository.ProductoRepository

/**
 * Orquestador de la sincronización Firestore ↔ SQLDelight para neveras
 * colaborativas.
 *
 * Mantiene un listener remoto (doc de nevera + colección de productos) por
 * cada nevera local en modo [ule.jescuj00.fridgey.domain.model.ModoNevera.SHARED],
 * reconciliando el conjunto de listeners cada vez que cambia el conjunto de
 * neveras compartidas observado en la base local.
 *
 * Vive en commonMain, pero en este sprint SOLO Android lo arranca
 * (FridgeyApplication lo engancha al ciclo de autenticación: login →
 * [start], logout → [stop]); iOS se enganchará en una sesión posterior.
 *
 * Solo se escuchan neveras SHARED; la resolución de conflictos
 * (last-write-wins por serverTimestamp) se aplica en los repositorios,
 * no aquí.
 */
class SyncManager(
    private val remoteRepository: NeveraRemoteRepository,
    private val neveraRepository: NeveraRepository,
    private val productoRepository: ProductoRepository,
) {

    private companion object {
        const val BACKOFF_BASE_MS = 1_000L
        const val BACKOFF_MAX_MS = 60_000L
    }

    private var scope: CoroutineScope? = null
    private var uid: String? = null
    private var reconcileJob: Job? = null

    /** Listener activo (doc + productos) por id de nevera. */
    private val listeners = mutableMapOf<String, Job>()

    /** Neveras con el sync suspendido temporalmente vía [pauseSync]. */
    private val pausadas = mutableSetOf<String>()

    /** Último conjunto de ids SHARED emitido por la base local. */
    private var ultimasIds: Set<String> = emptySet()

    /**
     * Protege [listeners], [pausadas] y [ultimasIds]: el scope de sync corre
     * en Dispatchers.Default (multihilo) y reconciliaciones, pausas y
     * reanudaciones pueden solaparse.
     */
    private val mutex = Mutex()

    /**
     * Arranca el orquestador para el usuario [uid].
     *
     * Llama a [stop] primero para que un re-login deje el estado limpio.
     * A partir de aquí observa el conjunto de neveras SHARED locales y
     * reconcilia los listeners remotos contra él.
     */
    fun start(scope: CoroutineScope, uid: String) {
        stop()
        this.scope = scope
        this.uid = uid
        reconcileJob = scope.launch {
            neveraRepository.observeSharedNeveraIds().collect { ids ->
                mutex.withLock {
                    ultimasIds = ids
                    reconciliar()
                }
            }
        }
    }

    /**
     * Para el orquestador: cancela la reconciliación y todos los listeners
     * y limpia el estado interno. Se invoca en logout y al re-arrancar.
     */
    fun stop() {
        reconcileJob?.cancel()
        reconcileJob = null
        listeners.values.forEach { it.cancel() }
        listeners.clear()
        pausadas.clear()
        uid = null
        scope = null
    }

    /**
     * Suspende temporalmente el sync de [neveraId] (cancela su listener y la
     * marca como pausada para que la reconciliación no lo relance).
     *
     * Lo usa UnshareNeveraUseCase antes de borrar la nevera de Firestore:
     * sin esta pausa, los ecos REMOVED del borrado masivo de productos
     * vaciarían la copia local del dueño, que debe conservar sus datos al
     * volver a modo LOCAL.
     */
    suspend fun pauseSync(neveraId: String) {
        mutex.withLock {
            pausadas += neveraId
            listeners.remove(neveraId)?.cancel()
        }
    }

    /**
     * Levanta la pausa de [neveraId] y reconcilia.
     *
     * Si el unshare falló (el modo local sigue SHARED) esto reengancha el
     * listener; si se completó (modo LOCAL) la nevera ya no está en el
     * conjunto objetivo y no hay nada que reenganchar.
     */
    suspend fun resumeSync(neveraId: String) {
        mutex.withLock {
            pausadas -= neveraId
            reconciliar()
        }
    }

    /**
     * Reconcilia los listeners activos contra el conjunto objetivo
     * (neveras SHARED menos pausadas). Debe llamarse con [mutex] cogido.
     */
    private fun reconciliar() {
        val scope = this.scope ?: return
        val uid = this.uid ?: return
        val objetivo = ultimasIds - pausadas

        // Cancela los listeners de neveras que ya no son objetivo.
        val sobrantes = listeners.keys - objetivo
        for (id in sobrantes) {
            listeners.remove(id)?.cancel()
        }

        // Lanza listener para las que faltan.
        for (id in objetivo) {
            if (id !in listeners) {
                listeners[id] = scope.launch { escuchar(id, uid) }
            }
        }
    }

    /**
     * Escucha en paralelo el doc de la nevera y su colección de productos
     * hasta que el Job del listener se cancele.
     */
    private suspend fun escuchar(neveraId: String, uid: String) {
        coroutineScope {
            launch { colectarNevera(neveraId, uid) }
            launch { colectarProductos(neveraId, uid) }
        }
    }

    /**
     * Colector del documento de la nevera. Errores transitorios se
     * reintentan con backoff exponencial capado; PERMISSION_DENIED no se
     * reintenta y se trata como pérdida de acceso.
     */
    private suspend fun colectarNevera(neveraId: String, uid: String) {
        remoteRepository.observeNevera(neveraId)
            .retryWhen { cause, attempt ->
                if (cause.esPerdidaDeAcceso()) {
                    false
                } else {
                    delay(backoff(attempt))
                    true
                }
            }
            .catch { cause ->
                if (cause.esPerdidaDeAcceso()) {
                    manejarAccesoPerdido(neveraId, uid)
                } else {
                    throw cause
                }
            }
            .collect { event ->
                when (event) {
                    is RemoteNeveraEvent.Actualizada ->
                        neveraRepository.aplicarNeveraRemota(
                            neveraId,
                            event.doc,
                            event.updatedAtSeconds,
                        )

                    RemoteNeveraEvent.Eliminada ->
                        manejarNeveraDesaparecida(neveraId, uid)
                }
            }
    }

    /**
     * Colector de la colección de productos de la nevera. Misma política de
     * reintentos que [colectarNevera]; en pérdida de acceso también delega
     * en [manejarAccesoPerdido] (es idempotente: el primero que llegue
     * resuelve y el segundo no encuentra snapshot).
     */
    private suspend fun colectarProductos(neveraId: String, uid: String) {
        remoteRepository.observeProductos(neveraId)
            .retryWhen { cause, attempt ->
                if (cause.esPerdidaDeAcceso()) {
                    false
                } else {
                    delay(backoff(attempt))
                    true
                }
            }
            .catch { cause ->
                if (cause.esPerdidaDeAcceso()) {
                    manejarAccesoPerdido(neveraId, uid)
                } else {
                    throw cause
                }
            }
            .collect { cambios ->
                for (cambio in cambios) {
                    when (cambio) {
                        is RemoteProductoChange.Upsert ->
                            productoRepository.aplicarProductoRemoto(
                                cambio.productoId,
                                neveraId,
                                cambio.doc,
                                cambio.updatedAtSeconds,
                            )

                        is RemoteProductoChange.Eliminado ->
                            productoRepository.eliminarProductoLocal(cambio.productoId)
                    }
                }
            }
    }

    /**
     * El documento remoto de la nevera ha desaparecido (borrado confirmado
     * en el servidor).
     *
     * - Si [uid] es el propietario → [NeveraRepository.revertirANoCompartida]:
     *   el dueño conserva sus datos en local (caso típico: eco de su propio
     *   unshare ejecutado desde otro dispositivo).
     * - Si no → [NeveraRepository.eliminarNeveraLocal]: el colaborador pierde
     *   la nevera por diseño — la copia compartida pertenece al dueño.
     */
    private suspend fun manejarNeveraDesaparecida(neveraId: String, uid: String) {
        val snapshot = neveraRepository.getSyncSnapshot(neveraId) ?: return
        if (snapshot.idPropietario == uid) {
            neveraRepository.revertirANoCompartida(neveraId)
        } else {
            neveraRepository.eliminarNeveraLocal(neveraId)
        }
    }

    /**
     * Se ha perdido el acceso a la nevera (PERMISSION_DENIED en el listener,
     * típicamente porque el dueño revocó al colaborador o dejó de compartir).
     *
     * Misma resolución que [manejarNeveraDesaparecida]:
     * - Propietario → [NeveraRepository.revertirANoCompartida] (conserva datos).
     * - Colaborador → [NeveraRepository.eliminarNeveraLocal] (pierde la
     *   nevera por diseño: la revocación de acceso implica perder la copia).
     */
    private suspend fun manejarAccesoPerdido(neveraId: String, uid: String) {
        val snapshot = neveraRepository.getSyncSnapshot(neveraId) ?: return
        if (snapshot.idPropietario == uid) {
            neveraRepository.revertirANoCompartida(neveraId)
        } else {
            neveraRepository.eliminarNeveraLocal(neveraId)
        }
    }

    /** Backoff exponencial capado: 1s, 2s, 4s, ... hasta 60s. */
    private fun backoff(attempt: Long): Long {
        val shift = attempt.coerceIn(0L, 6L).toInt()
        return (BACKOFF_BASE_MS shl shift).coerceAtMost(BACKOFF_MAX_MS)
    }

    /**
     * `true` si el fallo es un PERMISSION_DENIED de Firestore — pérdida de
     * acceso definitiva que no debe reintentarse.
     */
    private fun Throwable.esPerdidaDeAcceso(): Boolean =
        this is FirebaseFirestoreException && code == FirestoreExceptionCode.PERMISSION_DENIED
}
