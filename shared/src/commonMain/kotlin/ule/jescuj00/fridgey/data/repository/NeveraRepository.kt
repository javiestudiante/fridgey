package ule.jescuj00.fridgey.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import ule.jescuj00.fridgey.database.NeveraColaboradorQueries
import ule.jescuj00.fridgey.database.NeveraQueries
import ule.jescuj00.fridgey.database.ProductoQueries
import ule.jescuj00.fridgey.database.UsuarioQueries
import ule.jescuj00.fridgey.domain.model.ExpiringTodaySummary
import ule.jescuj00.fridgey.domain.model.Nevera
import ule.jescuj00.fridgey.domain.model.NeveraResumen
import ule.jescuj00.fridgey.domain.model.Proveedor
import ule.jescuj00.fridgey.domain.model.Usuario
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class NeveraRepository(
    private val neveraQueries: NeveraQueries,
    private val colaboradorQueries: NeveraColaboradorQueries,
    private val productoQueries: ProductoQueries,
    private val usuarioQueries: UsuarioQueries
) {

    private companion object {
        /** Days-ahead window for the "por caducar" stat. Matches the design's
         *  section cut for "caduca ya" + "esta semana" (diasRestantes <= 7). */
        const val EXPIRING_SOON_DAYS = 7
    }

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

    /**
     * Reactive "Mis neveras" home feed: each fridge with its product count,
     * its "por caducar" count, and its members (owner + collaborators). Built
     * from existing data — no activity tracking. Re-emits on any Producto
     * change (via [ProductoQueries.countAll]) and on Nevera/colaborador
     * changes (the underlying `selectByUsuario` LEFT-JOINs NeveraColaborador).
     */
    fun observeNeverasResumen(usuarioId: String): Flow<List<NeveraResumen>> {
        val neverasFlow = neveraQueries.selectByUsuario(usuarioId)
            .asFlow()
            .mapToList(Dispatchers.Default)

        val productosTrigger = productoQueries.countAll()
            .asFlow()
            .mapToOne(Dispatchers.Default)

        return combine(neverasFlow, productosTrigger) { rows, _ ->
            val threshold = expiringSoonThresholdEpoch()
            rows.map { row ->
                val nevera = row.toDomain(
                    esPropietario = row.id_propietario == usuarioId,
                    numeroProductos = productoQueries.countByNevera(row.id).executeAsOne().toInt()
                )
                val expiringCount = productoQueries
                    .countExpiringByNevera(neveraId = row.id, threshold = threshold)
                    .executeAsOne().toInt()
                NeveraResumen(
                    nevera = nevera,
                    expiringCount = expiringCount,
                    miembros = membersOf(neveraId = row.id, ownerId = row.id_propietario),
                )
            }
        }.flowOn(Dispatchers.Default)
    }

    /**
     * Reactive cross-fridge "caducan hoy" summary for the home banner. Emits
     * total + product names + (single) fridge name. Re-emits on any change to
     * the joined tables (Producto / Nevera / NeveraColaborador).
     */
    fun observeExpiringTodaySummary(usuarioId: String): Flow<ExpiringTodaySummary> {
        val hoy = todayEpoch()
        return productoQueries.selectExpiringTodayForUsuario(hoy = hoy, usuarioId = usuarioId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
                ExpiringTodaySummary(
                    total = rows.size,
                    productNames = rows.map { it.producto_nombre },
                    // Only name / link the fridge when every product shares one.
                    neveraNombre = rows.map { it.nevera_nombre }.distinct().singleOrNull(),
                    neveraId = rows.map { it.nevera_id }.distinct().singleOrNull(),
                )
            }
            .flowOn(Dispatchers.Default)
    }

    /** Owner + collaborators of a fridge — one-shot (detail header avatars). */
    suspend fun getMiembros(neveraId: String): List<Usuario> = withContext(Dispatchers.Default) {
        val ownerId = neveraQueries.selectById(neveraId).executeAsOneOrNull()?.id_propietario
            ?: return@withContext emptyList()
        membersOf(neveraId, ownerId)
    }

    /** Owner + collaborators of a fridge (for the avatar stack / MIEMBROS). */
    private fun membersOf(neveraId: String, ownerId: String): List<Usuario> {
        val owner = usuarioQueries.selectById(ownerId).executeAsOneOrNull()?.toDomainUsuario()
        val colaboradores = colaboradorQueries.selectUsuariosByNevera(neveraId)
            .executeAsList().map { it.toDomainUsuario() }
        return listOfNotNull(owner) + colaboradores
    }

    /** Epoch-seconds of midnight-UTC of today (matches fecha_caducidad storage). */
    private fun todayEpoch(): Long =
        Clock.System.todayIn(TimeZone.currentSystemDefault())
            .atStartOfDayIn(TimeZone.UTC).epochSeconds

    /** Epoch-seconds threshold for "expires within EXPIRING_SOON_DAYS days". */
    private fun expiringSoonThresholdEpoch(): Long =
        Clock.System.todayIn(TimeZone.currentSystemDefault())
            .plus(EXPIRING_SOON_DAYS, DateTimeUnit.DAY)
            .atStartOfDayIn(TimeZone.UTC).epochSeconds

    private fun ule.jescuj00.fridgey.database.Usuario.toDomainUsuario(): Usuario =
        Usuario(
            id = id,
            email = email,
            nombre = nombre,
            proveedor = Proveedor.fromString(proveedor),
            fotoUrl = foto_url,
        )

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
