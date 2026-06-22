package ule.jescuj00.fridgey.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import ule.jescuj00.fridgey.database.FoodSaverDatabase
import ule.jescuj00.fridgey.domain.model.Producto
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the FTS5 product search end-to-end through [ProductoRepository] (so the
 * MATCH-string builder, the blank-query routing, and the sync triggers are all in the
 * path), plus a few direct unit tests of [buildFtsMatchQuery].
 *
 * IMPORTANT: these run on the JVM Xerial SQLite (JdbcSqliteDriver), which ships a modern
 * SQLite with FTS5 + `remove_diacritics 2`. So this validates the SEARCH LOGIC — the
 * query, the builder, the triggers, the routing — NOT the Android bundled (osmerion)
 * driver or the iOS native driver. Those are covered by the manual device checklist.
 */
class ProductoFtsSearchTest {

    private lateinit var db: FoodSaverDatabase
    private lateinit var repository: ProductoRepository

    @BeforeTest
    fun setUp() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        FoodSaverDatabase.Schema.create(driver)
        db = FoodSaverDatabase(driver)
        repository = ProductoRepository(
            queries = db.productoQueries,
            neveraQueries = db.neveraQueries,
            remoteRepository = lazy { error("La búsqueda LOCAL nunca debe tocar Firestore") },
            syncScope = CoroutineScope(Dispatchers.Unconfined),
        )
        db.usuarioQueries.insert("owner", "o@o.com", "Owner", "google", null, 0)
        db.neveraQueries.insert("nev-1", "Principal", "owner", 0)
        db.neveraQueries.insert("nev-2", "Otra", "owner", 0)
    }

    private fun insert(
        id: String,
        nombre: String,
        categoria: String,
        neveraId: String = "nev-1",
    ) {
        db.productoQueries.insert(
            id = id,
            id_nevera = neveraId,
            codigo_barras = null,
            nombre = nombre,
            categoria = categoria,
            fecha_caducidad = 0,
            fecha_registro = 0,
            imagen_url = null,
            cantidad = 1.0,
            dias_aviso_antes = 3,
            unidad = "unidades",
        )
    }

    private suspend fun search(query: String, neveraId: String = "nev-1"): List<Producto> =
        repository.searchProductos(neveraId, query).first()

    private suspend fun nombres(query: String): Set<String> =
        search(query).map { it.nombre }.toSet()

    // (a) accent-insensitive, both directions ------------------------------------------

    @Test
    fun `acento - platano encuentra Platano con tilde y viceversa`() = runTest {
        insert("p1", "Plátano", "frutas")   // stored WITH accent
        insert("p2", "Cafe", "bebidas")     // stored WITHOUT accent

        assertTrue("Plátano" in nombres("platano"), "consulta sin tilde debe encontrar 'Plátano'")
        assertTrue("Plátano" in nombres("plátano"), "consulta con tilde debe encontrar 'Plátano'")
        assertTrue("Cafe" in nombres("café"), "consulta con tilde debe encontrar 'Cafe'")
    }

    // (b) prefix / as-you-type ----------------------------------------------------------

    @Test
    fun `prefijo - lech encuentra Leche entera`() = runTest {
        insert("p1", "Leche entera", "lacteos")

        assertTrue("Leche entera" in nombres("lech"))
        assertTrue("Leche entera" in nombres("leche"))
        assertTrue("Leche entera" in nombres("ent"), "prefijo sobre el último token coincide con 'entera'")
        assertFalse("Leche entera" in nombres("xyz"), "un término inexistente no devuelve nada")
    }

    // (c) the categoria column is indexed ----------------------------------------------

    @Test
    fun `busca tambien en categoria`() = runTest {
        insert("p1", "Yogur Griego", "lacteos") // "lacteos" no está en el nombre

        assertTrue("Yogur Griego" in nombres("lacteos"), "un término que solo está en categoría devuelve el producto")
    }

    // (d) GTIN / exact code lookups are unchanged --------------------------------------

    @Test
    fun `gtin exacto en ProductoCache sigue siendo igualdad exacta`() = runTest {
        db.productoCacheQueries.insert("8410000000001", "Leche", "MarcaX", "lacteos", null, 0)

        val hit = db.productoCacheQueries.selectByCodigoBarras("8410000000001").executeAsOneOrNull()
        assertNotNull(hit)
        assertEquals("Leche", hit.nombre)
        // Un código PARCIAL no coincide: la búsqueda por código sigue siendo exacta, no FTS/LIKE.
        assertNull(db.productoCacheQueries.selectByCodigoBarras("8410").executeAsOneOrNull())
    }

    // (e) triggers keep FTS in sync on insert / update / delete ------------------------

    @Test
    fun `triggers mantienen FTS sincronizado`() = runTest {
        // INSERT → searchable
        insert("p1", "Manzana", "frutas")
        assertTrue("Manzana" in nombres("manzana"))

        // UPDATE → findable by the new term, not the old one
        db.productoQueries.update(
            nombre = "Naranja",
            categoria = "frutas",
            fecha_caducidad = 0,
            imagen_url = null,
            cantidad = 1.0,
            dias_aviso_antes = 3,
            unidad = "unidades",
            id = "p1",
        )
        assertTrue("Naranja" in nombres("naranja"), "tras UPDATE se encuentra por el término nuevo")
        assertFalse("Manzana" in nombres("manzana"), "tras UPDATE ya no se encuentra por el término viejo")

        // DELETE → no longer searchable
        db.productoQueries.deleteById("p1")
        assertTrue(nombres("naranja").isEmpty(), "tras DELETE ya no aparece")
    }

    // (f) blank query → all products in the nevera (routing from §A) -------------------

    @Test
    fun `consulta vacia o en blanco devuelve todos los productos de la nevera`() = runTest {
        insert("p1", "Manzana", "frutas")
        insert("p2", "Leche", "lacteos")
        insert("p3", "Pan", "panaderia")
        insert("p4", "Atún", "pescados", neveraId = "nev-2") // otra nevera, no debe aparecer

        assertEquals(3, search("").size, "cadena vacía → todos los de la nevera")
        assertEquals(3, search("   ").size, "cadena en blanco → todos los de la nevera")
        // Scoping intacto: no se filtran productos de otras neveras.
        assertFalse("Atún" in nombres(""))
    }

    // direct builder unit tests --------------------------------------------------------

    @Test
    fun `buildFtsMatchQuery escapa tokens y aplica prefijo al ultimo`() {
        assertNull(buildFtsMatchQuery(""))
        assertNull(buildFtsMatchQuery("   "))
        assertEquals("\"lech\"*", buildFtsMatchQuery("lech"))
        assertEquals("\"lech\" \"ent\"*", buildFtsMatchQuery("  lech   ent  "))
        // Las comillas internas se duplican (se neutraliza la sintaxis de MATCH).
        assertEquals("\"a\"\"b\"*", buildFtsMatchQuery("a\"b"))
    }
}
