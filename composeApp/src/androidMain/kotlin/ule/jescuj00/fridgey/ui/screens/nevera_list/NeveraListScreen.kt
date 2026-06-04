package ule.jescuj00.fridgey.ui.screens.nevera_list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.koin.androidx.compose.koinViewModel
import ule.jescuj00.fridgey.domain.model.ExpiringTodaySummary
import ule.jescuj00.fridgey.domain.model.NeveraResumen
import ule.jescuj00.fridgey.ui.components.AlertCard
import ule.jescuj00.fridgey.ui.components.NeveraCard
import ule.jescuj00.fridgey.ui.components.NeveraRole
import ule.jescuj00.fridgey.ui.components.SectionHeader
import ule.jescuj00.fridgey.ui.theme.Cream
import ule.jescuj00.fridgey.ui.theme.Ink
import ule.jescuj00.fridgey.ui.theme.InkMuted
import ule.jescuj00.fridgey.ui.theme.InkSoft
import ule.jescuj00.fridgey.ui.theme.InstrumentSerif
import ule.jescuj00.fridgey.ui.theme.Inter
import ule.jescuj00.fridgey.ui.theme.MintDarker
import ule.jescuj00.fridgey.ui.theme.MintDeep
import ule.jescuj00.fridgey.ui.theme.MintSoft

// --- Local type styles (exact design sizes) ---
private val TitleStyle = TextStyle(
    fontFamily = InstrumentSerif, fontWeight = FontWeight.Normal,
    fontSize = 32.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp,
)
private val EyebrowSansStyle = TextStyle(
    fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.3.sp,
)

private val mesesEs = arrayOf(
    "ENERO", "FEBRERO", "MARZO", "ABRIL", "MAYO", "JUNIO",
    "JULIO", "AGOSTO", "SEPTIEMBRE", "OCTUBRE", "NOVIEMBRE", "DICIEMBRE",
)

@Composable
fun NeveraListScreen(
    currentUserId: String,
    onNavigateToCreate: () -> Unit,
    onNavigateToNevera: (String) -> Unit,
    onSignOut: () -> Unit,
    viewModel: NeveraListViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(currentUserId) {
        viewModel.observeNeveras(currentUserId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Cream),
    ) {
        when {
            state.isLoading && state.neveras.isEmpty() -> LoadingState()

            state.error != null && state.neveras.isEmpty() -> ErrorState(
                message = state.error!!,
                onRetry = { viewModel.observeNeveras(currentUserId) },
            )

            state.neveras.isEmpty() -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
                HomeHeader(onSignOut = onSignOut)
                EmptyState(onCreatePressed = onNavigateToCreate)
            }

            else -> HomeContent(
                state = state,
                onNavigateToNevera = onNavigateToNevera,
                onSignOut = onSignOut,
            )
        }

        // Extended FAB — visible whenever the screen isn't a full-bleed
        // loading/error placeholder.
        if (!(state.isLoading && state.neveras.isEmpty()) && state.error == null) {
            ExtendedFloatingActionButton(
                onClick = onNavigateToCreate,
                containerColor = MintSoft,
                contentColor = MintDarker,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 22.dp, bottom = 28.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Nueva nevera", style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 15.sp))
            }
        }
    }
}

@Composable
private fun HomeContent(
    state: NeveraListUiState,
    onNavigateToNevera: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        item { HomeHeader(onSignOut = onSignOut) }

        // Cross-fridge "caducan hoy" banner — only when there's something today.
        state.expiringToday?.takeIf { it.total > 0 }?.let { summary ->
            item {
                AlertCard(
                    bulletText = summary.total.toString(),
                    title = alertTitle(summary),
                    subtitle = summary.productNames.take(2).joinToString(" · ").ifBlank { null },
                    // Navigate to the fridge when all expiring-today products
                    // share one; otherwise stays informational.
                    onClick = { summary.neveraId?.let(onNavigateToNevera) },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 0.dp),
                )
            }
        }

        item {
            SectionHeader(title = "Tus neveras", count = state.neveras.size, bottomPadding = 8.dp)
        }

        itemsIndexed(state.neveras, key = { _, r -> r.nevera.id }) { index, resumen ->
            NeveraCard(
                name = resumen.nevera.nombre,
                role = if (resumen.nevera.esPropietario) NeveraRole.OWNER else NeveraRole.GUEST,
                featured = index == 0,
                memberInitials = resumen.miembros.map { initialOf(it.nombre) },
                memberCount = resumen.miembros.size,
                productCount = resumen.nevera.numeroProductos,
                expiringCount = resumen.expiringCount,
                onClick = { onNavigateToNevera(resumen.nevera.id) },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
        }

        // NOTE: the "Esta semana — N productos salvados de la basura" block from
        // the design is intentionally omitted: there is no "products saved"
        // metric in the data layer (would require consumed-before-expiry
        // tracking). Flagged for a product decision; re-enable here once a
        // source exists.

        item { Spacer(Modifier.height(112.dp)) }  // breathing room above the FAB
    }
}

@Composable
private fun HomeHeader(onSignOut: () -> Unit) {
    val eyebrow = remember { todayEyebrow() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = eyebrow, style = EyebrowSansStyle, color = InkMuted)
            Spacer(Modifier.height(6.dp))
            Text(text = "Mis neveras", style = TitleStyle, color = Ink)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Sign-out lives in an honest overflow menu (the bell is
            // decorative — notifications are not a feature yet, and the
            // design's account/"Yo" tab is out of scope this session).
            OverflowMenu(onSignOut = onSignOut)
            Spacer(Modifier.width(8.dp))
            CircleHeaderButton(
                icon = Icons.Outlined.Notifications,
                contentDescription = "Notificaciones",
                onClick = { /* decorativa — sin feature de notificaciones */ },
            )
        }
    }
}

@Composable
private fun OverflowMenu(onSignOut: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        CircleHeaderButton(
            icon = Icons.Filled.MoreVert,
            contentDescription = "Más opciones",
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Cerrar sesión") },
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onSignOut()
                },
            )
        }
    }
}

@Composable
private fun CircleHeaderButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(0x0A1A1F1C))  // rgba(26,31,28,0.04)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = InkSoft,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MintDeep)
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Algo no funcionó", style = TitleStyle, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(message, style = TextStyle(fontFamily = Inter, fontSize = 14.sp), color = InkSoft)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = MintDeep),
        ) { Text("Reintentar") }
    }
}

@Composable
private fun EmptyState(onCreatePressed: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Aún no tienes neveras", style = TitleStyle, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Crea tu primera nevera para empezar a controlar las fechas de caducidad.",
            style = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 21.sp),
            color = InkSoft,
        )
    }
}

// --- helpers ---

private fun todayEyebrow(): String {
    val t = Clock.System.todayIn(TimeZone.currentSystemDefault())
    return "HOY · ${t.dayOfMonth} ${mesesEs[t.monthNumber - 1]}"
}

private fun initialOf(nombre: String): String =
    nombre.trim().firstOrNull()?.uppercase() ?: "?"

private fun alertTitle(s: ExpiringTodaySummary): String {
    val noun = if (s.total == 1) "producto" else "productos"
    val verb = if (s.total == 1) "caduca" else "caducan"
    return if (s.neveraNombre != null) {
        "${s.total} $noun $verb hoy en ${s.neveraNombre}"
    } else {
        "${s.total} $noun $verb hoy"
    }
}
