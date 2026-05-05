package ule.jescuj00.fridgey.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import ule.jescuj00.fridgey.database.NeveraColaboradorQueries
import ule.jescuj00.fridgey.database.NeveraQueries
import ule.jescuj00.fridgey.database.ProductoQueries
import ule.jescuj00.fridgey.domain.model.Nevera
import ule.jescuj00.fridgey.domain.model.Proveedor
import ule.jescuj00.fridgey.domain.model.Usuario
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class NeveraRepository(
    private val neveraQueries: NeveraQueries,
    private val colaboradorQueries: NeveraColaboradorQueries,
    private val productoQueries: ProductoQueries
) {

    /**
     * Returns all fridges the user owns or collaborates in.
     */
    suspend fun getNeverasByUsuario(usuarioId: String): List<Nevera> =
        withContext(Dispatchers.Default) {
            neveraQueries.selectByUsuario(usuarioId).executeAsList().map { row ->
                row.toDomain(
                    esPropietario = row.id_propietario == usuarioId,
                    numeroProductos = productoQueries.countByNevera(row.id)
                        .executeAsOne().toInt()
                )
            }
        }

    /**
     * Reactive variant of [getNeverasByUsuario]: emits a fresh list whenever
     * either the Nevera table OR the Producto table changes — the latter
     * matters because each fridge carries its own product count.
     *
     * The Producto-side trigger is `countAll()`, an unfiltered query whose
     * sole purpose is to invalidate on any insert/update/delete. SQLDelight
     * re-emits the Flow on table change regardless of whether the value
     * itself changed.
     */
    fun observeNeverasByUsuario(usuarioId: String): Flow<List<Nevera>> {
        val neverasFlow = neveraQueries.selectByUsuario(usuarioId)
            .asFlow()
            .mapToList(Dispatchers.Default)

        val productosTrigger = productoQueries.countAll()
            .asFlow()
            .mapToOne(Dispatchers.Default)

        return combine(neverasFlow, productosTrigger) { rows, _ ->
            rows.map { row ->
                row.toDomain(
                    esPropietario = row.id_propietario == usuarioId,
                    numeroProductos = productoQueries.countByNevera(row.id)
                        .executeAsOne().toInt()
                )
            }
        }.flowOn(Dispatchers.Default)
    }

    suspend fun getNeveraById(neveraId: String, currentUserId: String): Nevera? =
        withContext(Dispatchers.Default) {
            neveraQueries.selectById(neveraId).executeAsOneOrNull()?.let { row ->
                row.toDomain(
                    esPropietario = row.id_propietario == currentUserId,
                    numeroProductos = productoQueries.countByNevera(row.id)
                        .executeAsOne().toInt()
                )
            }
        }

    /**
     * Creates a new fridge and returns its ID.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun createNevera(nombre: String, idPropietario: String): String =
        withContext(Dispatchers.Default) {
            val id = Uuid.random().toString()
            neveraQueries.insert(
                id = id,
                nombre = nombre,
                id_propietario = idPropietario,
                fecha_creacion = Clock.System.now().epochSeconds
            )
            id
        }

    suspend fun updateNevera(neveraId: String, nuevoNombre: String): Unit =
        withContext(Dispatchers.Default) {
            neveraQueries.updateNombre(nombre = nuevoNombre, id = neveraId)
        }

    suspend fun deleteNevera(neveraId: String): Unit = withContext(Dispatchers.Default) {
        neveraQueries.deleteById(neveraId)
    }

    /**
     * Adds a collaborator to a fridge. Does NOT enforce limits — use [AddColaboradorUseCase].
     */
    suspend fun addColaborador(neveraId: String, usuarioId: String): Unit =
        withContext(Dispatchers.Default) {
            colaboradorQueries.insert(
                id_nevera = neveraId,
                id_usuario = usuarioId,
                fecha_union = Clock.System.now().epochSeconds
            )
        }

    suspend fun removeColaborador(neveraId: String, usuarioId: String): Unit =
        withContext(Dispatchers.Default) {
            colaboradorQueries.delete(id_nevera = neveraId, id_usuario = usuarioId)
        }

    /**
     * Returns collaborator users (excludes owner) for a fridge via JOIN.
     */
    suspend fun getColaboradores(neveraId: String): List<Usuario> =
        withContext(Dispatchers.Default) {
            colaboradorQueries.selectUsuariosByNevera(neveraId).executeAsList().map { row ->
                Usuario(
                    id = row.id,
                    email = row.email,
                    nombre = row.nombre,
                    proveedor = Proveedor.fromString(row.proveedor),
                    fotoUrl = row.foto_url
                )
            }
        }

    /**
     * Returns number of collaborators (excludes owner).
     */
    suspend fun getColaboradorCount(neveraId: String): Int =
        withContext(Dispatchers.Default) {
            colaboradorQueries.selectCountByNevera(neveraId).executeAsOne().toInt()
        }

    /**
     * Checks whether [usuarioId] is the owner of [neveraId].
     */
    suspend fun isOwner(neveraId: String, usuarioId: String): Boolean =
        withContext(Dispatchers.Default) {
            neveraQueries.selectById(neveraId).executeAsOneOrNull()
                ?.id_propietario == usuarioId
        }

    /**
     * Counts how many fridges a user owns.
     */
    suspend fun countNeverasByPropietario(usuarioId: String): Int =
        withContext(Dispatchers.Default) {
            neveraQueries.selectByUsuario(usuarioId).executeAsList()
                .count { it.id_propietario == usuarioId }
        }

    // --- Row mapper ---

    private fun ule.jescuj00.fridgey.database.Nevera.toDomain(
        esPropietario: Boolean,
        numeroProductos: Int
    ): Nevera = Nevera(
        id = id,
        nombre = nombre,
        idPropietario = id_propietario,
        esPropietario = esPropietario,
        numeroProductos = numeroProductos
    )
}
