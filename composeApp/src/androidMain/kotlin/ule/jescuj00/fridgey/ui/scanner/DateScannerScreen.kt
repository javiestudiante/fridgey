// NOTE: This screen requires portrait orientation lock. The hosting activity
// should set android:screenOrientation="portrait" on the manifest, OR the
// navigation host should call activity.requestedOrientation = SCREEN_ORIENTATION_PORTRAIT
// when entering this destination. Step 6 (navigation wiring) handles this.
package ule.jescuj00.fridgey.ui.scanner

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.format.DateTimeFormatter
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import org.koin.androidx.compose.koinViewModel

private const val TAG = "DateScannerScreen"

private val chipDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy")

// =============================================================================
// Public entry
// =============================================================================

@Composable
fun DateScannerScreen(
    onDatePicked: (LocalDate) -> Unit,
    onManualEntry: () -> Unit,
    onCancel: () -> Unit,
    viewModel: DateScannerViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val activity = remember(context) { context as? Activity }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // After a denial, shouldShowRequestPermissionRationale tells us whether
        // we may ask again. If false AND the user just denied, the system has
        // recorded "don't ask again" — the only path forward is the settings
        // intent. If activity is null we can't query, fall back to "ask again".
        val canAskAgain = if (granted) {
            true
        } else if (activity != null) {
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.CAMERA,
            )
        } else {
            Log.w(TAG, "LocalContext is not an Activity; defaulting canAskAgain=true")
            true
        }
        viewModel.onPermissionResult(granted, canAskAgain)
    }

    // First-composition permission probe. Runs once per screen instance — if
    // the user goes to Settings and returns, the "Conceder permiso" / "Abrir
    // ajustes" buttons handle re-checking; we don't auto-rerun on resume.
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.onPermissionResult(granted = true, canAskAgain = true)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // One-shot navigation events from the VM.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ScannerEvent.DatePicked -> onDatePicked(event.date)
                ScannerEvent.ManualEntryRequested -> onManualEntry()
            }
        }
    }

    when (val s = state) {
        ScannerUiState.RequestingPermission -> RequestingPermissionUi()

        is ScannerUiState.PermissionDenied -> PermissionDeniedUi(
            canAskAgain = s.canAskAgain,
            onRetry = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = { openAppSettings(context) },
            onCancel = onCancel,
        )

        ScannerUiState.Scanning -> CameraUi(
            analyzer = viewModel.analyzer,
            state = s,
            onCancel = onCancel,
            onManualTap = { viewModel.onManualEntry() },
        )

        is ScannerUiState.DatesDetected -> CameraUi(
            analyzer = viewModel.analyzer,
            state = s,
            onCancel = onCancel,
            onManualTap = { viewModel.onManualEntry() },
        )

        is ScannerUiState.Error -> ErrorUi(
            message = s.message,
            onRetry = {
                viewModel.onPermissionResult(granted = true, canAskAgain = true)
            },
            onManualTap = { viewModel.onManualEntry() },
        )
    }
}

// =============================================================================
// State-specific UIs
// =============================================================================

@Composable
private fun RequestingPermissionUi() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun PermissionDeniedUi(
    canAskAgain: Boolean,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onCancel: () -> Unit,
) {
    val message = if (canAskAgain) {
        "Necesitamos acceso a la cámara para escanear fechas de caducidad"
    } else {
        "El permiso de cámara está desactivado. Actívalo en los ajustes para escanear fechas."
    }
    val primaryLabel = if (canAskAgain) "Conceder permiso" else "Abrir ajustes"
    val primaryAction = if (canAskAgain) onRetry else onOpenSettings

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.PhotoCamera,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 280.dp),
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = primaryAction) { Text(primaryLabel) }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel) { Text("Cancelar") }
    }
}

@Composable
private fun ErrorUi(
    message: String,
    onRetry: () -> Unit,
    onManualTap: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 280.dp),
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) { Text("Reintentar") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onManualTap) { Text("Introducir manualmente") }
    }
}

@Composable
private fun CameraUi(
    analyzer: FrameAnalyzer?,
    state: ScannerUiState,
    onCancel: () -> Unit,
    onManualTap: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Analyzer should be non-null whenever we're in Scanning/DatesDetected,
        // but defend against the brief gap between state transition and the
        // VM's `_analyzer` field being set on the same frame.
        if (analyzer != null) {
            CameraPreview(
                analyzer = analyzer,
                modifier = Modifier.fillMaxSize(),
            )
        }
        ViewfinderOverlay()
        ScannerTopBar(
            onCancel = onCancel,
            modifier = Modifier.align(Alignment.TopStart),
        )
        ScannerBottomBar(
            state = state,
            onManualTap = onManualTap,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// =============================================================================
// Top / bottom chrome
// =============================================================================

@Composable
private fun ScannerTopBar(
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .statusBarsPadding()
            .padding(8.dp),
    ) {
        IconButton(onClick = onCancel) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Cancelar",
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun ScannerBottomBar(
    state: ScannerUiState,
    onManualTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .navigationBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state is ScannerUiState.DatesDetected) {
            DetectionChip(
                date = state.date,
                stabilityProgress = state.stabilityProgress,
            )
        }
        TextButton(onClick = onManualTap) {
            Text(
                text = "Introducir manualmente",
                color = Color.White,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun DetectionChip(
    date: LocalDate,
    stabilityProgress: Float,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = stabilityProgress,
        animationSpec = tween(durationMillis = 200),
        label = "stabilityProgress",
    )

    Box(
        modifier = Modifier.size(96.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.size(96.dp),
            strokeWidth = 3.dp,
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.White.copy(alpha = 0.3f),
        )
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = date.toJavaLocalDate().format(chipDateFormatter),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// =============================================================================
// Helpers
// =============================================================================

private fun openAppSettings(context: android.content.Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

// =============================================================================
// Previews — only for non-camera states (PreviewView can't render in @Preview)
// =============================================================================

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PermissionDeniedCanAskAgainPreview() {
    PermissionDeniedUi(
        canAskAgain = true,
        onRetry = {},
        onOpenSettings = {},
        onCancel = {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PermissionDeniedNoAskAgainPreview() {
    PermissionDeniedUi(
        canAskAgain = false,
        onRetry = {},
        onOpenSettings = {},
        onCancel = {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ErrorPreview() {
    ErrorUi(
        message = "No se pudo iniciar la cámara",
        onRetry = {},
        onManualTap = {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF222222, widthDp = 360, heightDp = 200)
@Composable
private fun DetectionChipPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.DarkGray),
        contentAlignment = Alignment.Center,
    ) {
        DetectionChip(
            date = LocalDate(2026, 5, 15),
            stabilityProgress = 0.66f,
        )
    }
}
