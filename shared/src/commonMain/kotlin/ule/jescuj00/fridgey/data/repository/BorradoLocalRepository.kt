package ule.jescuj00.fridgey.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ule.jescuj00.fridgey.database.FoodSaverDatabase

/**
 * Borra POR COMPLETO el espejo local SQLDelight (todas las tablas). Lo usa el
 * borrado de cuenta (RGPD): tras la baja server-authoritative no debe quedar
 * ningún dato del usuario en el dispositivo.
 *
 * Se hace con `DELETE FROM` por tabla dentro de UNA transacción (no se borra el
 * fichero de la base de datos): así no hay que recrear el driver ni tocar
 * expect/actual, y queda atómico y testeable. El orden es hijo→padre, seguro con
 * o sin enforcement de claves foráneas. `Producto` va primero porque su trigger
 * `Producto_fts_ad` (AFTER DELETE) mantiene el índice FTS5 externo `ProductoFts`
 * consistente fila a fila.
 */
class BorradoLocalRepository(
    private val database: FoodSaverDatabase,
) {
    suspend fun borrarTodo(): Unit = withContext(Dispatchers.Default) {
        database.transaction {
            database.productoQueries.deleteAll()
            database.neveraColaboradorQueries.deleteAll()
            database.productoCacheQueries.deleteAll()
            database.preferenciaQueries.deleteAll()
            database.neveraQueries.deleteAll()
            database.usuarioQueries.deleteAll()
        }
    }
}
