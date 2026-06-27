package ule.jescuj00.fridgey.ui.screens.ajustes

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import ule.jescuj00.fridgey.data.repository.PreferenciasRepository
import ule.jescuj00.fridgey.domain.model.ModoAnadirProducto
import ule.jescuj00.fridgey.notificaciones.abrirAjustesNotificaciones
import ule.jescuj00.fridgey.notificaciones.tienePermisoNotificaciones
import ule.jescuj00.fridgey.ui.components.EyebrowLabel
import ule.jescuj00.fridgey.ui.components.SegmentOption
import ule.jescuj00.fridgey.ui.components.SegmentedToggle
import ule.jescuj00.fridgey.ui.theme.Cream
import ule.jescuj00.fridgey.ui.theme.Ink
import ule.jescuj00.fridgey.ui.theme.InkSoft
import ule.jescuj00.fridgey.ui.theme.Rust
import ule.jescuj00.fridgey.ui.theme.RustSoft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjustesScreen(
    onNavigateBack: () -> Unit,
    viewModel: AjustesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = remember(context) { context as? Activity }
    val preferencias: PreferenciasRepository = koinInject()
    val esApi33 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    var permisoConcedido by remember { mutableStateOf(tienePermisoNotificaciones(context)) }
    var solicitado by remember { mutableStateOf(false) }
    var denegadoPermanente by remember { mutableStateOf(false) }
    var showConfirmEliminar by remember { mutableStateOf(false) }

    fun recomputarEstadoPermiso() {
        permisoConcedido = tienePermisoNotificaciones(context)
        // Denegado permanente = ya se pidió, sigue sin concederse y el sistema
        // ya NO permite volver a mostrar el diálogo (shouldShowRationale=false).
        denegadoPermanente = esApi33 && solicitado && !permisoConcedido && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.POST_NOTIFICATIONS,
            )
    }

    val permisoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { concedido ->
        solicitado = true
        viewModel.onPermisoResultado(concedido) // persiste solicitado + comprobarAhora si concedido
        recomputarEstadoPermiso()
    }

    LaunchedEffect(Unit) {
        solicitado = preferencias.permisoNotifSolicitado()
        recomputarEstadoPermiso()
    }

    // Al volver de los ajustes del sistema, refrescar el estado del permiso.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        recomputarEstadoPermiso()
    }

    Scaffold(
        containerColor = Cream,
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            AjusteFilaSwitch(
                titulo = "Avisos de caducidad",
                subtitulo = "Te avisamos en este dispositivo cuando un producto " +
                    "está a punto de caducar.",
                checked = state.avisosCaducidad,
                onCheckedChange = { activado ->
                    // El toggle representa la intención: persiste y (des)programa.
                    viewModel.onToggle(activado)
                    // Al activar sin permiso (API 33+), pide el permiso DESDE el
                    // Composable. Si está denegado permanente, el sistema no
                    // mostrará diálogo y el aviso de abajo guía a ajustes.
                    if (activado && esApi33 && !permisoConcedido) {
                        permisoLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )

            // Compuerta del SO: el toggle puede seguir en ON, pero sin permiso
            // (denegado permanente) los avisos no salen → guiamos a ajustes.
            if (state.avisosCaducidad && denegadoPermanente) {
                Spacer(Modifier.size(8.dp))
                AvisoPermisoCard(onAbrirAjustes = { abrirAjustesNotificaciones(context) })
            }

            // Sección "Preferencias": cómo abre el botón "+" de una nevera el
            // alta de producto. Reutiliza el SegmentedToggle (mismo control que
            // el Escanear/A mano de AddProducto).
            Spacer(Modifier.height(24.dp))
            EyebrowLabel(text = "PREFERENCIAS")
            Spacer(Modifier.height(12.dp))
            ModoAnadirPreferencia(
                modo = state.modoAnadir,
                onSeleccionar = viewModel::onModoAnadirSeleccionado,
            )

            // Sección "Cuenta": acción destructiva de borrado de cuenta (RGPD).
            Spacer(Modifier.height(28.dp))
            EyebrowLabel(text = "CUENTA")
            Spacer(Modifier.height(12.dp))
            EliminarCuentaFila(
                eliminando = state.eliminandoCuenta,
                onClick = { showConfirmEliminar = true },
            )
        }
    }

    // --- Diálogo de confirmación fuerte (irreversibilidad) ---
    if (showConfirmEliminar) {
        AlertDialog(
            onDismissRequest = { showConfirmEliminar = false },
            title = { Text("¿Eliminar tu cuenta?") },
            text = {
                Text(
                    "Esta acción es permanente y no se puede deshacer. Se eliminarán tu " +
                        "cuenta, tus neveras en solitario y sus productos. Saldrás de las " +
                        "neveras compartidas de otras personas.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmEliminar = false
                    viewModel.onEliminarCuentaConfirmado()
                }) {
                    Text("Eliminar", color = Rust)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmEliminar = false }) { Text("Cancelar") }
            },
        )
    }

    // --- Bloqueo: neveras compartidas a resolver primero ---
    state.neverasBloqueadas?.let { neveras ->
        AlertDialog(
            onDismissRequest = viewModel::onDismissBloqueo,
            title = { Text("Tienes neveras compartidas") },
            text = {
                Column {
                    Text(
                        "Para eliminar tu cuenta, primero elimina estas neveras compartidas:",
                    )
                    Spacer(Modifier.height(8.dp))
                    neveras.forEach { nevera ->
                        Text(
                            text = "• ${nevera.nombre.ifBlank { "Nevera" }}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::onDismissBloqueo) { Text("Entendido") }
            },
        )
    }

    // --- Error legible del borrado ---
    state.errorEliminarCuenta?.let { mensaje ->
        AlertDialog(
            onDismissRequest = viewModel::onDismissErrorEliminar,
            title = { Text("No se pudo eliminar la cuenta") },
            text = { Text(mensaje) },
            confirmButton = {
                TextButton(onClick = viewModel::onDismissErrorEliminar) { Text("OK") }
            },
        )
    }
}

/** Fila destructiva "Eliminar cuenta" (rojo). Muestra progreso mientras está en curso. */
@Composable
private fun EliminarCuentaFila(
    eliminando: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !eliminando, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Eliminar cuenta",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Rust,
            )
            Text(
                text = "Borra tu cuenta y tus datos de forma permanente.",
                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                color = InkSoft,
            )
        }
        if (eliminando) {
            Spacer(Modifier.size(12.dp))
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Rust, strokeWidth = 2.dp)
        }
    }
}

/**
 * Selector de modo de añadido por defecto para el botón "+" de una nevera.
 * Índice 0 = MANUAL, índice 1 = ESCANEAR (orden Manual / Escanear).
 */
@Composable
private fun ModoAnadirPreferencia(
    modo: ModoAnadirProducto,
    onSeleccionar: (ModoAnadirProducto) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Al añadir productos",
            style = MaterialTheme.typography.bodyLarge,
            color = Ink,
        )
        Text(
            text = "Qué abre el botón + de una nevera por defecto.",
            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
            color = InkSoft,
        )
        Spacer(Modifier.height(10.dp))
        SegmentedToggle(
            options = listOf(
                SegmentOption(label = "Manual"),
                SegmentOption(label = "Escanear"),
            ),
            selectedIndex = if (modo == ModoAnadirProducto.MANUAL) 0 else 1,
            onSelect = { idx ->
                onSeleccionar(
                    if (idx == 0) ModoAnadirProducto.MANUAL else ModoAnadirProducto.ESCANEAR,
                )
            },
        )
    }
}

/** Fila de ajuste con título + subtítulo no técnico y un Switch a la derecha. */
@Composable
private fun AjusteFilaSwitch(
    titulo: String,
    subtitulo: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.bodyLarge,
                color = Ink,
            )
            Text(
                text = subtitulo,
                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                color = InkSoft,
            )
        }
        Spacer(Modifier.size(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Aviso de denegación permanente del permiso + acceso a ajustes del sistema. */
@Composable
private fun AvisoPermisoCard(onAbrirAjustes: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RustSoft, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = "Activa las notificaciones en los ajustes del sistema para recibir avisos.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink,
        )
        TextButton(onClick = onAbrirAjustes) {
            Text("Abrir ajustes del sistema", color = Rust)
        }
    }
}
