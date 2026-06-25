package ule.jescuj00.fridgey.debug

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import ule.jescuj00.fridgey.data.repository.NeveraRepository
import ule.jescuj00.fridgey.data.repository.ProductoRepository
import ule.jescuj00.fridgey.domain.model.Categoria
import ule.jescuj00.fridgey.domain.model.Producto
import ule.jescuj00.fridgey.domain.model.UnidadMedida
import java.util.UUID

/**
 * Siembra dos neveras de demostración con productos variados. Pensado SOLO para
 * builds debug y para ejecutarse UNA vez (lo protege un flag persistente en
 * [ule.jescuj00.fridgey.data.repository.PreferenciasRepository.datosDemoSembrados]).
 *
 * Las neveras nacen LOCAL (default de [NeveraRepository.createNevera]): viven
 * solo en el SQLite del dispositivo y nunca tocan Firestore. Pertenecen al
 * usuario logueado ([uid]) para que aparezcan en "Mis neveras".
 */
suspend fun sembrarNeverasMock(
    uid: String,
    neveraRepository: NeveraRepository,
    productoRepository: ProductoRepository,
) {
    val hoy = Clock.System.todayIn(TimeZone.currentSystemDefault())

    val hogarId = neveraRepository.createNevera("Hogar", uid)
    productosHogar(hogarId, uid, hoy).forEach { productoRepository.insertProducto(it) }

    val playaId = neveraRepository.createNevera("Casa de la playa", uid)
    productosPlaya(playaId, uid, hoy).forEach { productoRepository.insertProducto(it) }
}

/** 15 productos variados para la nevera "Hogar". */
private fun productosHogar(neveraId: String, uid: String, hoy: LocalDate): List<Producto> = listOf(
    prod(neveraId, uid, hoy, "Leche entera", Categoria.LACTEOS, 1.0, UnidadMedida.LITROS, diasCad = 5),
    prod(neveraId, uid, hoy, "Yogur natural", Categoria.LACTEOS, 4.0, UnidadMedida.UNIDADES, diasCad = 10),
    prod(neveraId, uid, hoy, "Queso curado", Categoria.LACTEOS, 250.0, UnidadMedida.GRAMOS, diasCad = 30),
    prod(neveraId, uid, hoy, "Mantequilla", Categoria.LACTEOS, 250.0, UnidadMedida.GRAMOS, diasCad = -2),
    prod(neveraId, uid, hoy, "Pechuga de pollo", Categoria.CARNES, 0.5, UnidadMedida.KILOGRAMOS, diasCad = 2),
    prod(neveraId, uid, hoy, "Filetes de ternera", Categoria.CARNES, 0.4, UnidadMedida.KILOGRAMOS, diasCad = 1),
    prod(neveraId, uid, hoy, "Salmón fresco", Categoria.PESCADOS, 300.0, UnidadMedida.GRAMOS, diasCad = 1),
    prod(neveraId, uid, hoy, "Manzanas", Categoria.FRUTAS, 6.0, UnidadMedida.UNIDADES, diasCad = 12),
    prod(neveraId, uid, hoy, "Plátanos", Categoria.FRUTAS, 5.0, UnidadMedida.UNIDADES, diasCad = 4),
    prod(neveraId, uid, hoy, "Espinacas baby", Categoria.VERDURAS, 200.0, UnidadMedida.GRAMOS, diasCad = 3),
    prod(neveraId, uid, hoy, "Tomates", Categoria.VERDURAS, 8.0, UnidadMedida.UNIDADES, diasCad = 7),
    prod(neveraId, uid, hoy, "Zumo de naranja", Categoria.BEBIDAS, 1.0, UnidadMedida.LITROS, diasCad = 15),
    prod(neveraId, uid, hoy, "Guisantes congelados", Categoria.CONGELADOS, 0.75, UnidadMedida.KILOGRAMOS, diasCad = 120),
    prod(neveraId, uid, hoy, "Pan de molde", Categoria.PANADERIA, 1.0, UnidadMedida.UNIDADES, diasCad = 6),
    prod(neveraId, uid, hoy, "Huevos", Categoria.OTROS, 12.0, UnidadMedida.UNIDADES, diasCad = 20),
)

/** Productos distintos para la nevera "Casa de la playa". */
private fun productosPlaya(neveraId: String, uid: String, hoy: LocalDate): List<Producto> = listOf(
    prod(neveraId, uid, hoy, "Agua mineral", Categoria.BEBIDAS, 6.0, UnidadMedida.UNIDADES, diasCad = 180),
    prod(neveraId, uid, hoy, "Cerveza", Categoria.BEBIDAS, 12.0, UnidadMedida.UNIDADES, diasCad = 90),
    prod(neveraId, uid, hoy, "Refresco de cola", Categoria.BEBIDAS, 2.0, UnidadMedida.LITROS, diasCad = 60),
    prod(neveraId, uid, hoy, "Sandía", Categoria.FRUTAS, 1.0, UnidadMedida.UNIDADES, diasCad = 5),
    prod(neveraId, uid, hoy, "Melón", Categoria.FRUTAS, 1.0, UnidadMedida.UNIDADES, diasCad = 6),
    prod(neveraId, uid, hoy, "Gambas congeladas", Categoria.CONGELADOS, 0.5, UnidadMedida.KILOGRAMOS, diasCad = 200),
    prod(neveraId, uid, hoy, "Helado de vainilla", Categoria.CONGELADOS, 1.0, UnidadMedida.LITROS, diasCad = 150),
    prod(neveraId, uid, hoy, "Chorizo", Categoria.CARNES, 300.0, UnidadMedida.GRAMOS, diasCad = 25),
    prod(neveraId, uid, hoy, "Sardinas", Categoria.PESCADOS, 0.4, UnidadMedida.KILOGRAMOS, diasCad = 1),
    prod(neveraId, uid, hoy, "Lechuga", Categoria.VERDURAS, 1.0, UnidadMedida.UNIDADES, diasCad = 3),
    prod(neveraId, uid, hoy, "Pimientos", Categoria.VERDURAS, 4.0, UnidadMedida.UNIDADES, diasCad = 8),
    prod(neveraId, uid, hoy, "Pan de hamburguesa", Categoria.PANADERIA, 8.0, UnidadMedida.UNIDADES, diasCad = 4),
)

/**
 * Construye un [Producto] mock. [diasCad] son los días desde hoy hasta la
 * caducidad (negativo = ya caducado, útil para probar los estados de la UI).
 */
private fun prod(
    neveraId: String,
    uid: String,
    hoy: LocalDate,
    nombre: String,
    categoria: Categoria,
    cantidad: Double,
    unidad: UnidadMedida,
    diasCad: Int,
): Producto = Producto(
    id = UUID.randomUUID().toString(),
    idNevera = neveraId,
    codigoBarras = null,
    nombre = nombre,
    categoria = categoria,
    fechaCaducidad = hoy.plus(diasCad, DateTimeUnit.DAY),
    fechaRegistro = hoy,
    imagenUrl = null,
    cantidad = cantidad,
    unidad = unidad,
    diasAvisoAntes = 3,
    creadoPor = uid,
)
