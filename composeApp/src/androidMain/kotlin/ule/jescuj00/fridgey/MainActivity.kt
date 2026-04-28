package ule.jescuj00.fridgey

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import ule.jescuj00.fridgey.data.repository.UsuarioRepository
import ule.jescuj00.fridgey.domain.model.Proveedor
import ule.jescuj00.fridgey.domain.model.Usuario

class MainActivity : ComponentActivity() {

    private val usuarioRepository: UsuarioRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Dev-only: ensure the hardcoded user exists so fridge creation works.
        // INSERT OR REPLACE in the schema makes this idempotent.
        lifecycleScope.launch {
            usuarioRepository.insertUsuario(
                Usuario(
                    id = DEV_USER_ID,
                    email = "test@fridgey.dev",
                    nombre = "Usuario de pruebas",
                    proveedor = Proveedor.GOOGLE,
                    fotoUrl = null
                )
            )
        }

        setContent { App() }
    }
}
