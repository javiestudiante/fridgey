package ule.jescuj00.fridgey.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ule.jescuj00.fridgey.database.UsuarioQueries
import ule.jescuj00.fridgey.domain.model.Proveedor
import ule.jescuj00.fridgey.domain.model.Usuario

class UsuarioRepository(private val queries: UsuarioQueries) {

    suspend fun getUsuarioById(id: String): Usuario? = withContext(Dispatchers.Default) {
        queries.selectById(id).executeAsOneOrNull()?.toDomain()
    }

    suspend fun insertUsuario(usuario: Usuario): Unit = withContext(Dispatchers.Default) {
        queries.insert(
            id = usuario.id,
            email = usuario.email,
            nombre = usuario.nombre,
            proveedor = usuario.proveedor.valor,
            foto_url = usuario.fotoUrl,
            fecha_creacion = kotlinx.datetime.Clock.System.now().epochSeconds
        )
    }

    suspend fun updateUsuario(usuario: Usuario): Unit = withContext(Dispatchers.Default) {
        // INSERT OR REPLACE handles upsert in the schema
        queries.insert(
            id = usuario.id,
            email = usuario.email,
            nombre = usuario.nombre,
            proveedor = usuario.proveedor.valor,
            foto_url = usuario.fotoUrl,
            fecha_creacion = kotlinx.datetime.Clock.System.now().epochSeconds
        )
    }

    private fun ule.jescuj00.fridgey.database.Usuario.toDomain(): Usuario =
        Usuario(
            id = id,
            email = email,
            nombre = nombre,
            proveedor = Proveedor.fromString(proveedor),
            fotoUrl = foto_url
        )
}
