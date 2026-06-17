package ule.jescuj00.fridgey

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {

    // neveraId pendiente proveniente de un tap en una notificación de caducidad.
    // mutableStateOf para que al llegar por onNewIntent se recomponga la UI.
    private var pendingNeveraId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Arranque en frío desde una notificación: el intent ya trae el extra.
        pendingNeveraId = consumirNeveraId(intent)

        setContent {
            App(
                deepLinkNeveraId = pendingNeveraId,
                onDeepLinkConsumed = { pendingNeveraId = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // App ya viva (launchMode singleTop): el nuevo intent trae el extra.
        // setIntent para que getIntent() devuelva el nuevo de aquí en adelante.
        setIntent(intent)
        pendingNeveraId = consumirNeveraId(intent)
    }

    /**
     * Lee y CONSUME el extra del deep-link: lo elimina del intent para que una
     * recreación por rotación (que reusa el mismo intent) no vuelva a navegar.
     */
    private fun consumirNeveraId(intent: Intent?): String? {
        val neveraId = intent?.getStringExtra(EXTRA_NEVERA_ID) ?: return null
        intent.removeExtra(EXTRA_NEVERA_ID)
        return neveraId
    }

    companion object {
        const val EXTRA_NEVERA_ID = "neveraId"
    }
}
