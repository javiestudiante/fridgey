package ule.jescuj00.fridgey.ui.screens.invitar

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import ule.jescuj00.fridgey.ui.components.EyebrowLabel
import ule.jescuj00.fridgey.ui.theme.Cream
import ule.jescuj00.fridgey.ui.theme.Hairline
import ule.jescuj00.fridgey.ui.theme.Ink
import ule.jescuj00.fridgey.ui.theme.InkMuted
import ule.jescuj00.fridgey.ui.theme.InkSoft
import ule.jescuj00.fridgey.ui.theme.InstrumentSerif
import ule.jescuj00.fridgey.ui.theme.Inter
import ule.jescuj00.fridgey.ui.theme.MintDeep
import ule.jescuj00.fridgey.ui.theme.Paper
import ule.jescuj00.fridgey.ui.theme.Rust
import ule.jescuj00.fridgey.ui.theme.SurfaceWhite
import ule.jescuj00.fridgey.ui.util.generateQrBitmap

private val TitleStyle = TextStyle(
    fontFamily = InstrumentSerif, fontWeight = FontWeight.Normal,
    fontSize = 30.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp,
)
private val CodigoStyle = TextStyle(
    fontFamily = Inter, fontWeight = FontWeight.SemiBold,
    fontSize = 28.sp, letterSpacing = 4.sp,
)

/**
 * Pantalla de invitación (UC-03a): muestra el código generado y su QR para
 * que otro usuario se una a la nevera. El código se genera al entrar y puede
 * regenerarse (cada código es de un solo uso y caduca a las 24h).
 */
@Composable
fun InvitarScreen(
    neveraId: String,
    currentUserId: String,
    onNavigateBack: () -> Unit,
    viewModel: InvitarViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(neveraId, currentUserId) {
        viewModel.generar(neveraId, currentUserId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cream)
            .statusBarsPadding(),
    ) {
        // Header: volver + título.
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(androidx.compose.ui.graphics.Color(0x0A1A1F1C))
                    .clickable(onClick = onNavigateBack),
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
                Text("Invitar", style = TitleStyle, color = Ink)
            }
        }

        // El `when` cubre TODOS los estados sellados — sin else.
        when (val s = state) {
            InvitarUiState.Generando -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = MintDeep)
            }

            is InvitarUiState.Generada -> CodigoGenerado(
                codigo = s.codigo,
                onCompartir = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Únete a mi nevera en Fridgey con el código ${formatearCodigo(s.codigo)} " +
                                "(válido 24 horas)",
                        )
                    }
                    context.startActivity(Intent.createChooser(intent, "Compartir código"))
                },
                onRegenerar = { viewModel.generar(neveraId, currentUserId) },
            )

            is InvitarUiState.Error -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    s.mensaje,
                    color = Rust,
                    textAlign = TextAlign.Center,
                    style = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 21.sp),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.generar(neveraId, currentUserId) },
                    colors = ButtonDefaults.buttonColors(containerColor = MintDeep, contentColor = SurfaceWhite),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("Reintentar") }
            }
        }
    }
}

@Composable
private fun CodigoGenerado(
    codigo: String,
    onCompartir: () -> Unit,
    onRegenerar: () -> Unit,
) {
    // El QR codifica el código TAL CUAL (sin guion): es lo que el flujo de
    // escaneo entrega a AceptarInvitacionUseCase. Render fuera del hilo
    // principal (zxing es CPU-bound).
    val qrSizePx = with(LocalDensity.current) { 240.dp.roundToPx() }
    val qrBitmap by produceState<Bitmap?>(initialValue = null, codigo) {
        value = withContext(Dispatchers.Default) { generateQrBitmap(codigo, qrSizePx) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .size(264.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Paper)
                .border(1.dp, Hairline, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = qrBitmap
            if (bitmap == null) {
                CircularProgressIndicator(color = MintDeep)
            } else {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Código QR de invitación",
                    modifier = Modifier.size(240.dp),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(formatearCodigo(codigo), style = CodigoStyle, color = Ink)
        Spacer(Modifier.height(6.dp))
        Text(
            "Comparte el QR o el código. Caduca en 24 horas y es de un solo uso.",
            style = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 19.sp),
            color = InkMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onCompartir,
            colors = ButtonDefaults.buttonColors(containerColor = MintDeep, contentColor = SurfaceWhite),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Compartir código", style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp))
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onRegenerar,
            colors = ButtonDefaults.buttonColors(containerColor = Paper, contentColor = Ink),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Hairline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Generar otro código", style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp))
        }
    }
}

/** "ABCDEFGH" → "ABCD-EFGH" solo para mostrar; al aceptar se normaliza. */
private fun formatearCodigo(codigo: String): String =
    if (codigo.length == 8) "${codigo.take(4)}-${codigo.drop(4)}" else codigo
