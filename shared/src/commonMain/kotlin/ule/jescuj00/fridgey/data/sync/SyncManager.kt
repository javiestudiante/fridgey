package ule.jescuj00.fridgey.data.sync

import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import dev.gitlive.firebase.firestore.FirestoreExceptionCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ule.jescuj00.fridgey.data.remote.firestore.NeveraDescubierta
import ule.jescuj00.fridgey.data.remote.firestore.NeveraRemoteRepository
import ule.jescuj00.fridgey.data.remote.firestore.RemoteNeveraEvent
import ule.jescuj00.fridgey.data.remote.firestore.RemoteProductoChange
import ule.jescuj00.fridgey.data.repository.NeveraRepository
import ule.jescuj00.fridgey.data.repository.ProductoRepository

/**
 * Orquestador de la sincronización Firestore ↔ SQLDelight para neveras en la
 * nube.
 *
 * Tiene dos motores: (1) un descubrimiento por colección que descarga las
 * neveras del usuario en la nube (propias + colaborando) y las engancha en
 * local, y (2) un listener remoto (doc de nevera + colección de productos) por
 * cada nevera local en modo [ule.jescuj00.fridgey.domain.model.ModoNevera.SYNCED],
 * reconciliando el conjunto de listeners cada vez que cambia el conjunto de
 * neveras SYNCED en la base local.
 *
 * Vive en commonMain y lo arrancan ambas plataformas al autenticarse
 * (Android desde FridgeyApplication, iOS desde bindSyncManagerToAuth):
 * login → [start], logout → [stop].
 *
 * Solo se escuchan neveras SYNCED; la resolución de conflictos
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

    /**
     * Colectores de descubrimiento por colección (propias + colaborando):
     * descargan y enganchan las neveras del usuario en la nube aunque la BD
     * local esté vacía (dispositivo nuevo). Es el corazón multi-dispositivo.
     */
    private val discoveryJobs = mutableListOf<Job>()

    /** Listener activo (doc + productos) por id de nevera. */
    private val listeners = mutableMapOf<String, Job>()

    /** Neveras con el sync suspendido temporalmente vía [pauseSync]. */
    private val pausadas = mutableSetOf<String>()

    /** Último conjunto de ids SYNCED emitido por la base local. */
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
     * Llama a [stop] primero para que un re-login deje el estado limpio. Hace
     * DOS cosas en paralelo:
     *  1. Descubrimiento remoto por colección ([descubrirNeverasDelUsuario]):
     *     descarga las neveras del usuario en la nube (propias + colaborando)
     *     y las engancha en local — así la nube le sigue a un dispositivo nuevo.
     *  2. Reconciliación local-driven: observa el conjunto de neveras SYNCED
     *     locales (alimentado por (1) y por el flujo de aceptar invitación) y
     *     mantiene un listener por-nevera (doc + productos) contra él.
     */
    fun start(scope: CoroutineScope, uid: String) {
        stop()
        this.scope = scope
        this.uid = uid
        reconcileJob = scope.launch {
            neveraRepository.observeSyncedNeveraIds().collect { ids ->
                mutex.withLock {
                    ultimasIds = ids
                    reconciliar()
                }
            }
        }
        descubrirNeverasDelUsuario(scope, uid)
    }

    /**
     * Para el orquestador: cancela el descubrimiento, la reconciliación y
     * todos los listeners, y limpia el estado interno. Se invoca en logout y
     * al re-arrancar.
     */
    fun stop() {
        reconcileJob?.cancel()
        reconcileJob = null
        discoveryJobs.forEach { it.cancel() }
        discoveryJobs.clear()
        listeners.values.forEach { it.cancel() }
        listeners.clear()
        pausadas.clear()
        uid = null
        scope = null
    }

    /**
     * Lanza los dos colectores de descubrimiento por colección. Cada nevera
     * descubierta se engancha vía el idempotente
     * [NeveraRepository.engancharNeveraRemota], que la inserta en modo SYNCED
     * → la reconciliación local-driven le ata su listener por-nevera. Que el
     * colector de descubrimiento y el listener por-doc apliquen el mismo doc a
     * la vez es seguro: ambos pasan por `aplicarNeveraRemota` (last-write-wins,
     * idempotente).
     */
    private fun descubrirNeverasDelUsuario(scope: CoroutineScope, uid: String) {
        discoveryJobs += scope.launch {
            colectarDescubrimiento(remoteRepository.observeNeverasPropias(uid))
        }
        discoveryJobs += scope.launch {
            colectarDescubrimiento(remoteRepository.observeNeverasColaborando(uid))
        }
    }

    /**
     * Colecta un flujo de descubrimiento y engancha cada nevera. Reintenta
     * errores transitorios con el mismo backoff exponencial que los listeners
     * por-doc. No hay caso PERMISSION_DENIED a tratar: una query de colección
     * sobre `idPropietario==uid` / `colaboradores arrayContains uid` solo
     * devuelve docs que las reglas permiten leer, así que nunca se deniega.
     */
    private suspend fun colectarDescubrimiento(flujo: Flow<List<NeveraDescubierta>>) {
        flujo
            .retryWhen { _, attempt ->
                delay(backoff(attempt))
                true
            }
            .collect { descubiertas ->
                for (d in descubiertas) {
                    neveraRepository.engancharNeveraRemota(d.neveraId, d.doc, d.updatedAtSeconds)
                }
            }
    }

    /**
     * Suspende temporalmente el sync de [neveraId] (cancela su listener y la
     * marca como pausada para que la reconciliación no lo relance).
     *
     * Lo usa QuitarDeNubeUseCase antes de borrar la nevera de Firestore:
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
     * Si el quitar-de-nube falló (el modo local sigue SYNCED) esto reengancha
     * el listener; si se completó (modo LOCAL) la nevera ya no está en el
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
     * (neveras SYNCED menos pausadas). Debe llamarse con [mutex] cogido.
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
