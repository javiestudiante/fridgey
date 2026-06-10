package ule.jescuj00.fridgey.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import ule.jescuj00.fridgey.database.FoodSaverDatabase
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Verifies the explicit cascade-delete fix in [NeveraRepository]: the Android
 * driver never enables PRAGMA foreign_keys, so the schema's ON DELETE CASCADE
 * clauses do not fire at runtime — deletes must remove dependent rows
 * explicitly. The JDBC driver used here has foreign_keys OFF by default too,
 * faithfully reproducing the on-device behaviour.
 *
 * The lazy remote repository that throws doubles as an assertion: LOCAL-only
 * code paths must never touch Firestore.
 */
class NeveraRepositoryLocalDeleteTest {

    private lateinit var db: FoodSaverDatabase
    private lateinit var repository: NeveraRepository

    @BeforeTest
    fun setUp() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        FoodSaverDatabase.Schema.create(driver)
        db = FoodSaverDatabase(driver)
        repository = NeveraRepository(
            neveraQueries = db.neveraQueries,
            colaboradorQueries = db.neveraColaboradorQueries,
            productoQueries = db.productoQueries,
            usuarioQueries = db.usuarioQueries,
            remoteRepository = lazy { error("Una nevera LOCAL nunca debe tocar Firestore") },
            syncScope = CoroutineScope(Dispatchers.Unconfined),
        )
        seed()
    }

    private fun seed() {
        db.usuarioQueries.insert("owner", "o@o.com", "Owner", "google", null, 0)
        db.usuarioQueries.insert("colab", "c@c.com", "Colab", "google", null, 0)
        db.neveraQueries.insert("nev-1", "Principal", "owner", 0)
        db.neveraQueries.insert("nev-2", "Otra", "owner", 0)
        insertProducto("p1", "nev-1")
        insertProducto("p2", "nev-1")
        insertProducto("p3", "nev-2")
        db.neveraColaboradorQueries.insert("nev-1", "colab", 0)
    }

    private fun insertProducto(id: String, neveraId: String) {
        db.productoQueries.insert(
            id = id,
            id_nevera = neveraId,
            codigo_barras = null,
            nombre = "Leche",
            categoria = "lacteos",
            fecha_caducidad = 0,
            fecha_registro = 0,
            imagen_url = null,
            cantidad = 1.0,
            dias_aviso_antes = 3,
            unidad = "unidades",
        )
    }

    @Test
    fun `deleteNevera LOCAL borra productos y colaboradores sin tocar Firestore`() = runTest {
        repository.deleteNevera("nev-1")

        assertNull(db.neveraQueries.selectById("nev-1").executeAsOneOrNull())
        assertEquals(0, db.productoQueries.countByNevera("nev-1").executeAsOne())
        assertEquals(0, db.neveraColaboradorQueries.selectCountByNevera("nev-1").executeAsOne())
        // Unrelated rows are untouched.
        assertNotNull(db.neveraQueries.selectById("nev-2").executeAsOneOrNull())
        assertEquals(1, db.productoQueries.countByNevera("nev-2").executeAsOne())
        assertNotNull(db.usuarioQueries.selectById("colab").executeAsOneOrNull())
    }

    @Test
    fun `eliminarNeveraLocal borra la copia local entera`() = runTest {
        repository.eliminarNeveraLocal("nev-1")

        assertNull(db.neveraQueries.selectById("nev-1").executeAsOneOrNull())
        assertEquals(0, db.productoQueries.countByNevera("nev-1").executeAsOne())
        assertEquals(0, db.neveraColaboradorQueries.selectCountByNevera("nev-1").executeAsOne())
        assertNotNull(db.neveraQueries.selectById("nev-2").executeAsOneOrNull())
    }

    @Test
    fun `revertirANoCompartida conserva productos y limpia colaboradores y watermark`() = runTest {
        // Simulate a fridge that was SHARED and had synced at least once.
        db.neveraQueries.updateModo(modo = "SHARED", id = "nev-1")
        db.neveraQueries.updateFromRemote(
            nombre = "Principal",
            id_propietario = "owner",
            updated_at = 123L,
            id = "nev-1",
        )

        repository.revertirANoCompartida("nev-1")

        val row = db.neveraQueries.selectById("nev-1").executeAsOne()
        assertEquals("LOCAL", row.modo)
        assertNull(row.updated_at)
        assertEquals(0, db.neveraColaboradorQueries.selectCountByNevera("nev-1").executeAsOne())
        // The owner KEEPS their products when the fridge goes back to LOCAL.
        assertEquals(2, db.productoQueries.countByNevera("nev-1").executeAsOne())
    }
}
