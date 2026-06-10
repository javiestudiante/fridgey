package ule.jescuj00.fridgey.ui.screens.unirse

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import ule.jescuj00.fridgey.domain.model.ResultadoInvitacion
import ule.jescuj00.fridgey.domain.scanner.BarcodeScanner
import ule.jescuj00.fridgey.ui.components.EyebrowLabel
import ule.jescuj00.fridgey.ui.scanner.CameraPreview
import ule.jescuj00.fridgey.ui.scanner.QrCodeAnalyzer
import ule.jescuj00.fridgey.ui.theme.Cream
import ule.jescuj00.fridgey.ui.theme.Hairline
import ule.jescuj00.fridgey.ui.theme.Ink
import ule.jescuj00.fridgey.ui.theme.InkMuted
import ule.jescuj00.fridgey.ui.theme.InkSoft
import ule.jescuj00.fridgey.ui.theme.InstrumentSerif
import ule.jescuj00.fridgey.ui.theme.Inter
import ule.jescuj00.fridgey.ui.theme.MintDeep
import ule.jescuj00.fridgey.ui.theme.MintSoft
import ule.jescuj00.fridgey.ui.theme.MintTint
import ule.jescuj00.fridgey.ui.theme.Paper
import ule.jescuj00.fridgey.ui.theme.Rust
import ule.jescuj00.fridgey.ui.theme.SurfaceWhite

private val TitleStyle = TextStyle(
    fontFamily = InstrumentSerif, fontWeight = FontWeight.Normal,
    fontSize = 30.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp,
)
private val BodyStyle = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 21.sp)

/**
 * Flujo "Unirse con código" (UC-03b): entrada manual del código o escaneo
 * del QR de invitación. Todos los [ResultadoInvitacion] sellados se cubren
 * con mensajes claros; los dos finales felices ofrecen abrir la nevera.
 */
@Composable
fun UnirseScreen(
    currentUserId: String,
    onNavigateBack: () -> Unit,
    onNavigateToNevera: (String) -> Unit,
    viewModel: UnirseViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var permisoDenegado by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permisoDenegado = !granted
        if (granted) viewModel.empezarEscaneo()
    }

    val onEscanearClick = {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            permisoDenegado = false
            viewModel.empezarEscaneo()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cream)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x0A1A1F1C))
                    .clickable(onClick = {
                        if (state.escaneando) viewModel.cancelarEscaneo() else onNavigateBack()
                    }),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = InkSoft,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                EyebrowLabel(text = "NEVERA COLABORATIVA")
                Spacer(Modifier.height(2.dp))
                Text("Unirse con código", style = TitleStyle, color = Ink)
            }
        }

        if (state.escaneando) {
            EscanerQr(
                onQrDetectado = { raw -> viewModel.onQrDetectado(raw, currentUserId) },
                onCancelar = { viewModel.cancelarEscaneo() },
            )
        } else {
            EntradaCodigo(
                state = state,
                permisoDenegado = permisoDenegado,
                onCodigoChange = viewModel::onCodigoChange,
                onUnirse = { viewModel.unirse(currentUserId) },
                onEscanear = onEscanearClick,
                onAbrirNevera = onNavigateToNevera,
            )
        }
    }
}

@Composable
private fun EntradaCodigo(
    state: UnirseUiState,
    permisoDenegado: Boolean,
    onCodigoChange: (String) -> Unit,
    onUnirse: () -> Unit,
    onEscanear: () -> Unit,
    onAbrirNevera: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            "Introduce el código que te han compartido o escanea el QR de la invitación.",
            style = BodyStyle,
            color = InkMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = state.codigo,
            onValueChange = onCodigoChange,
            placeholder = { Text("p. ej. ABCD-EFGH", color = InkMuted) },
            singleLine = true,
            enabled = !state.validando,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MintDeep,
                unfocusedBorderColor = Hairline,
                focusedContainerColor = Paper,
                unfocusedContainerColor = Paper,
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onUnirse,
            enabled = state.codigo.isNotBlank() && !state.validando,
            colors = ButtonDefaults.buttonColors(containerColor = MintDeep, contentColor = SurfaceWhite),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.validando) {
                CircularProgressIndicator(
                    color = SurfaceWhite,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text("Uniéndote…", style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp))
            } else {
                Text("Unirse", style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onEscanear,
            enabled = !state.validando,
            colors = ButtonDefaults.buttonColors(containerColor = Paper, contentColor = Ink),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Hairline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Escanear QR", style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp))
        }

        if (permisoDenegado) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Sin permiso de cámara no se puede escanear. Concédelo o teclea el código a mano.",
                style = TextStyle(fontFamily = Inter, fontSize = 12.sp, lineHeight = 18.sp),
                color = Rust,
                textAlign = TextAlign.Center,
            )
        }

        state.resultado?.let { resultado ->
            Spacer(Modifier.height(24.dp))
            ResultadoCard(resultado = resultado, onAbrirNevera = onAbrirNevera)
        }
    }
}

/**
 * Render del resultado: `when` EXHAUSTIVO sobre el tipo sellado — añadir un
 * estado nuevo a [ResultadoInvitacion] obliga a decidir aquí su mensaje.
 */
@Composable
private fun ResultadoCard(
    resultado: ResultadoInvitacion,
    onAbrirNevera: (String) -> Unit,
) {
    when (resultado) {
        is ResultadoInvitacion.Aceptada -> ExitoCard(
            titulo = "¡Te has unido a «${resultado.nombreNevera}»!",
            detalle = "Sus productos se están sincronizando en tu dispositivo.",
            neveraId = resultado.neveraId,
            onAbrirNevera = onAbrirNevera,
        )

        is ResultadoInvitacion.YaEresMiembro -> ExitoCard(
            titulo = "Ya formas parte de «${resultado.nombreNevera}»",
            detalle = "No hace falta volver a unirse — la nevera está enganchada a este dispositivo.",
            neveraId = resultado.neveraId,
            onAbrirNevera = onAbrirNevera,
        )

        ResultadoInvitacion.NoEncontrada -> AvisoCard(
            "El código no es válido. Revísalo e inténtalo de nuevo."
        )

        ResultadoInvitacion.Expirada -> AvisoCard(
            "La invitación ha caducado (las invitaciones duran 24 horas). Pide al propietario que genere otra."
        )

        ResultadoInvitacion.YaUsada -> AvisoCard(
            "Esta invitación ya se ha utilizado: cada código vale para una sola persona. Pide otro al propietario."
        )

        ResultadoInvitacion.NeveraLlena -> AvisoCard(
            "La nevera ya tiene 4 miembros, el máximo. No se pueden añadir más."
        )

        is ResultadoInvitacion.Error -> AvisoCard(
            "No se pudo completar la unión: ${resultado.mensaje}"
        )
    }
}

@Composable
private fun ExitoCard(
    titulo: String,
    detalle: String,
    neveraId: String,
    onAbrirNevera: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MintTint)
            .border(1.dp, MintSoft, RoundedCornerShape(18.dp))
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            titulo,
            style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
            color = Ink,
            textAlign = TextAlign.Center,
        )
        Text(detalle, style = BodyStyle.copy(fontSize = 13.sp, lineHeight = 19.sp), color = InkSoft, textAlign = TextAlign.Center)
        Button(
            onClick = { onAbrirNevera(neveraId) },
            colors = ButtonDefaults.buttonColors(containerColor = MintDeep, contentColor = SurfaceWhite),
            shape = RoundedCornerShape(14.dp),
        ) { Text("Abrir nevera", style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)) }
    }
}

@Composable
private fun AvisoCard(mensaje: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Paper)
            .border(1.dp, Hairline, RoundedCornerShape(18.dp))
            .padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(mensaje, style = BodyStyle.copy(fontSize = 13.sp, lineHeight = 19.sp), color = Rust, textAlign = TextAlign.Center)
    }
}

/**
 * Visor de cámara reutilizando [CameraPreview] con el [QrCodeAnalyzer]
 * (hermano del analyzer de productos, restringido a QR). El primer QR no
 * vacío dispara la aceptación; el analyzer emite `null` en frames sin QR y
 * el filtro lo descarta.
 */
@Composable
private fun EscanerQr(
    onQrDetectado: (String) -> Unit,
    onCancelar: () -> Unit,
) {
    val barcodeScanner: BarcodeScanner = koinInject()
    val scope = rememberCoroutineScope()
    val analyzer = remember { QrCodeAnalyzer(barcodeScanner, scope) }

    LaunchedEffect(analyzer) {
        analyzer.results.collect { result ->
            val raw = result?.rawValue
            if (!raw.isNullOrBlank()) onQrDetectado(raw)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp)
                .clip(RoundedCornerShape(20.dp)),
        ) {
            CameraPreview(analyzer = analyzer, modifier = Modifier.fillMaxSize())
        }
        Text(
            "Apunta al QR de la invitación",
            style = BodyStyle,
            color = InkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onCancelar,
            colors = ButtonDefaults.buttonColors(containerColor = Paper, contentColor = Ink),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Hairline),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) { Text("Cancelar", style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)) }
        Spacer(Modifier.height(20.dp))
    }
}
