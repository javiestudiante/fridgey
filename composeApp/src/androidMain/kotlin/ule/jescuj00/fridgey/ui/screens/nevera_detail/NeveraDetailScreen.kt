package ule.jescuj00.fridgey.ui.screens.nevera_detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel
import ule.jescuj00.fridgey.domain.model.Categoria
import ule.jescuj00.fridgey.domain.model.ModoNevera
import ule.jescuj00.fridgey.domain.model.Producto
import ule.jescuj00.fridgey.domain.model.UnidadMedida
import ule.jescuj00.fridgey.domain.model.Usuario
import ule.jescuj00.fridgey.ui.components.EyebrowLabel
import ule.jescuj00.fridgey.ui.components.ExpirationState
import ule.jescuj00.fridgey.ui.components.FilterPill
import ule.jescuj00.fridgey.ui.components.ProductRow
import ule.jescuj00.fridgey.ui.components.SectionHeader
import ule.jescuj00.fridgey.ui.components.expirationStateOf
import ule.jescuj00.fridgey.ui.theme.Amber
import ule.jescuj00.fridgey.ui.theme.BackButtonShape
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
import ule.jescuj00.fridgey.ui.theme.ShelfShape
import ule.jescuj00.fridgey.ui.theme.Smoke
import ule.jescuj00.fridgey.ui.theme.SurfaceWhite
import ule.jescuj00.fridgey.ui.util.displayName

private val NameStyle = TextStyle(
    fontFamily = InstrumentSerif, fontWeight = FontWeight.Normal,
    fontSize = 30.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp,
)
private val AvatarStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
private val avatarColors = listOf(Color(0xFF69A481), MintDeep, Amber)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NeveraDetailScreen(
    neveraId: String,
    currentUserId: String,
    onNavigateBack: () -> Unit,
    onNavigateToAddProducto: () -> Unit,
    onNavigateToInvitar: () -> Unit,
    viewModel: NeveraDetailViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var pendingDelete by remember { mutableStateOf<Producto?>(null) }
    var selectedCategory by remember { mutableStateOf<Categoria?>(null) }
    var showCompartir by remember { mutableStateOf(false) }

    LaunchedEffect(neveraId, currentUserId) {
        viewModel.loadProducts(neveraId, currentUserId)
    }

    val showEmpty = !state.isLoading && state.error == null && state.productos.isEmpty()
    val background = if (showEmpty) Cream else Smoke

    Box(modifier = Modifier.fillMaxSize().background(background)) {
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = MintDeep)
            }

            state.error != null -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
                DetailHeader(
                    nombre = state.neveraNombre.ifEmpty { "Nevera" },
                    miembros = state.miembros,
                    emptyVariant = false,
                    onBack = onNavigateBack,
                    mostrarCompartir = state.esPropietario,
                    onCompartir = { showCompartir = true },
                )
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(state.error!!, color = Rust)
                }
            }

            showEmpty -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
                DetailHeader(
                    nombre = state.neveraNombre.ifEmpty { "Nevera" },
                    miembros = state.miembros,
                    emptyVariant = true,
                    onBack = onNavigateBack,
                    mostrarCompartir = state.esPropietario,
                    onCompartir = { showCompartir = true },
                )
                EmptyDetail(onScan = onNavigateToAddProducto, onManual = onNavigateToAddProducto)
            }

            else -> DetailContent(
                state = state,
                selectedCategory = selectedCategory,
                onSelectCategory = { selectedCategory = it },
                onBack = onNavigateBack,
                onLongPressDelete = { pendingDelete = it },
                mostrarCompartir = state.esPropietario,
                onCompartir = { showCompartir = true },
            )
        }

        // FAB — hidden while loading / error so it doesn't float over placeholders.
        if (!state.isLoading && state.error == null) {
            ExtendedFloatingActionButton(
                onClick = onNavigateToAddProducto,
                containerColor = MintSoft,
                contentColor = ule.jescuj00.fridgey.ui.theme.MintDarker,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 22.dp, bottom = 28.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Añadir", style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 15.sp))
            }
        }
    }

    pendingDelete?.let { producto ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Eliminar producto") },
            text = { Text("¿Seguro que quieres eliminar \"${producto.nombre}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProducto(producto.id)
                    pendingDelete = null
                }) { Text("Eliminar", color = Rust) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") }
            },
        )
    }

    if (showCompartir) {
        CompartirDialog(
            state = state,
            onDismiss = {
                showCompartir = false
                viewModel.limpiarErrorCompartir()
            },
            onGuardar = viewModel::guardarEnMiCuenta,
            onInvitar = {
                showCompartir = false
                onNavigateToInvitar()
            },
            onDejarDeCompartir = viewModel::dejarDeCompartir,
            onQuitar = viewModel::quitarDeMiCuenta,
        )
    }
}

/**
 * Diálogo del propietario para gestionar los dos ejes (nube + colaboración).
 * El contenido se decide con un `when` exhaustivo sobre [ModoNevera] y, dentro
 * de SYNCED, según el derivado `tieneColaboradores`:
 *  - LOCAL → confirmación de "Guardar en mi cuenta" (SubirANube; síncrona →
 *    spinner; el timeout vive en el use case).
 *  - SYNCED sin colaboradores → "Invitar con código" + "Quitar de mi cuenta".
 *  - SYNCED con colaboradores → "Invitar con código" + "Dejar de compartir"
 *    + "Quitar de mi cuenta".
 *
 * El diálogo permanece abierto durante las transiciones (spinner en línea) y
 * se re-renderiza al cambiar el estado: tras "Guardar" pasa a las opciones de
 * nube; tras "Quitar" vuelve a "Guardar"; tras "Dejar de compartir"
 * desaparece esa acción.
 */
@Composable
private fun CompartirDialog(
    state: NeveraDetailUiState,
    onDismiss: () -> Unit,
    onGuardar: () -> Unit,
    onInvitar: () -> Unit,
    onDejarDeCompartir: () -> Unit,
    onQuitar: () -> Unit,
) {
    when (state.modo) {
        // --- LOCAL: confirmación de "Guardar en mi cuenta" ---
        ModoNevera.LOCAL -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Guardar en mi cuenta") },
            text = {
                Column {
                    Text(
                        "Tu nevera quedará guardada de forma segura y podrás verla " +
                            "desde cualquier móvil o tablet donde inicies sesión."
                    )
                    Spacer(Modifier.height(10.dp))
                    // Apunte secundario atenuado: cursiva + color más suave,
                    // claramente menos prominente que el cuerpo.
                    Text(
                        "Y si quieres, después podrás invitar a más personas a esta nevera.",
                        style = TextStyle(
                            fontFamily = Inter,
                            fontStyle = FontStyle.Italic,
                            fontSize = 13.sp,
                        ),
                        color = InkMuted,
                    )
                    state.errorCompartir?.let { error ->
                        Spacer(Modifier.height(10.dp))
                        Text(error, color = Rust)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onGuardar, enabled = !state.guardando) {
                    if (state.guardando) {
                        CircularProgressIndicator(
                            color = MintDeep, strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Guardar en mi cuenta", color = MintDeep)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            },
        )

        // --- SYNCED: menú de acciones de nube/colaboración ---
        ModoNevera.SYNCED -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Compartir nevera") },
            text = {
                Column {
                    Text(
                        if (state.tieneColaboradores) {
                            "Esta nevera está en tu cuenta y compartida con otras personas. " +
                                "Se sincroniza en la nube entre todos sus miembros."
                        } else {
                            "Esta nevera está guardada en tu cuenta: la verás en cualquier " +
                                "dispositivo donde inicies sesión. Invita a otras personas " +
                                "para compartirla."
                        }
                    )
                    Spacer(Modifier.height(8.dp))

                    DialogAction(
                        label = "Invitar con código",
                        color = MintDeep,
                        enabled = !state.dejandoDeCompartir && !state.quitando,
                        onClick = onInvitar,
                    )
                    // "Dejar de compartir" solo cuando hay colaboradores (eje
                    // derivado): vacía colaboradores pero sigue en la nube.
                    if (state.tieneColaboradores) {
                        DialogAction(
                            label = "Dejar de compartir",
                            color = Ink,
                            loading = state.dejandoDeCompartir,
                            enabled = !state.quitando,
                            onClick = onDejarDeCompartir,
                        )
                    }
                    DialogAction(
                        label = "Quitar de mi cuenta",
                        color = Rust,
                        loading = state.quitando,
                        enabled = !state.dejandoDeCompartir,
                        onClick = onQuitar,
                    )

                    state.errorCompartir?.let { error ->
                        Spacer(Modifier.height(10.dp))
                        Text(error, color = Rust)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            },
        )
    }
}

/**
 * Acción a fila completa dentro del menú de compartición (variante SYNCED),
 * con spinner en línea durante las transiciones síncronas.
 */
@Composable
private fun DialogAction(
    label: String,
    color: Color,
    loading: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = color, strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(label, color = color, modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailContent(
    state: NeveraDetailUiState,
    selectedCategory: Categoria?,
    onSelectCategory: (Categoria?) -> Unit,
    onBack: () -> Unit,
    onLongPressDelete: (Producto) -> Unit,
    mostrarCompartir: Boolean,
    onCompartir: () -> Unit,
) {
    val filtered = remember(state.productos, selectedCategory) {
        if (selectedCategory == null) state.productos
        else state.productos.filter { it.categoria == selectedCategory }
    }
    val bad = filtered.filter { expirationStateOf(it.diasRestantes) == ExpirationState.BAD }
        .sortedBy { it.diasRestantes }
    val warn = filtered.filter { expirationStateOf(it.diasRestantes) == ExpirationState.WARN }
        .sortedBy { it.diasRestantes }
    val fresh = filtered.filter { expirationStateOf(it.diasRestantes) == ExpirationState.FRESH }
        .sortedBy { it.diasRestantes }

    val categoriesPresent = remember(state.productos) {
        Categoria.entries.filter { cat -> state.productos.any { it.categoria == cat } }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        item {
            DetailHeader(
                nombre = state.neveraNombre.ifEmpty { "Nevera" },
                miembros = state.miembros,
                emptyVariant = false,
                onBack = onBack,
                mostrarCompartir = mostrarCompartir,
                onCompartir = onCompartir,
            )
        }
        item {
            FilterRail(
                total = state.productos.size,
                categories = categoriesPresent,
                selected = selectedCategory,
                onSelect = onSelectCategory,
            )
        }

        urgencySection(this, "Caduca ya", Rust, bad, onLongPressDelete)
        urgencySection(this, "Esta semana", Amber, warn, onLongPressDelete)
        urgencySection(this, "Más adelante", Ink, fresh, onLongPressDelete)

        item { Spacer(Modifier.height(112.dp)) }
    }
}

/** Emits a SectionHeader + a Shelf of ProductRows, only when [products] is non-empty. */
@OptIn(ExperimentalFoundationApi::class)
private fun urgencySection(
    scope: androidx.compose.foundation.lazy.LazyListScope,
    title: String,
    accent: Color,
    products: List<Producto>,
    onLongPressDelete: (Producto) -> Unit,
) {
    if (products.isEmpty()) return
    scope.item(key = "head-$title") {
        SectionHeader(title = title, count = products.size, accentColor = accent)
    }
    scope.item(key = "shelf-$title") {
        Shelf {
            products.forEachIndexed { index, producto ->
                if (index > 0) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
                }
                Box(
                    modifier = Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = { onLongPressDelete(producto) },
                    )
                ) {
                    ProductRow(
                        categoria = producto.categoria,
                        name = producto.nombre,
                        supporting = "${producto.categoria.displayName()} · ${cantidadLabel(producto.cantidad, producto.unidad)}",
                        daysRemaining = producto.diasRestantes,
                    )
                }
            }
        }
    }
}

@Composable
private fun Shelf(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp)
            .clip(ShelfShape)
            .background(Paper)
            .border(1.dp, Hairline, ShelfShape),
    ) {
        // fr-shelf-top decorative strip.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(
                    Brush.verticalGradient(listOf(Color(0x2E69A481), Color.Transparent))
                )
        )
        content()
    }
}

// MARK: - Header

@Composable
private fun DetailHeader(
    nombre: String,
    miembros: List<Usuario>,
    emptyVariant: Boolean,
    onBack: () -> Unit,
    mostrarCompartir: Boolean = false,
    onCompartir: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SquareIconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = InkSoft, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            EyebrowLabel(text = "NEVERA · ${miembros.size} MIEMBROS")
            Spacer(Modifier.height(2.dp))
            Text(text = nombre, style = NameStyle, color = Ink)
        }
        if (!emptyVariant) {
            DetailAvatars(miembros)
        }
        // Compartir (UC-03): solo el propietario gestiona la compartición.
        if (mostrarCompartir) {
            Spacer(Modifier.width(8.dp))
            SquareIconButton(onClick = onCompartir) {
                Icon(Icons.Filled.Share, contentDescription = "Compartir", tint = InkSoft, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SquareIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(BackButtonShape)
            .background(Color(0x0A1A1F1C))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun DetailAvatars(miembros: List<Usuario>) {
    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
        miembros.take(3).forEachIndexed { index, m ->
            DetailAvatar(text = initialOf(m.nombre), bg = avatarColors[index % avatarColors.size])
        }
        if (miembros.size > 3) {
            DetailAvatar(text = "+${miembros.size - 3}", bg = Amber)
        }
    }
}

@Composable
private fun DetailAvatar(text: String, bg: Color) {
    Box(
        modifier = Modifier.size(22.dp).clip(CircleShape).background(bg).border(2.dp, Paper, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = AvatarStyle, color = Paper)
    }
}

// MARK: - Filter rail

@Composable
private fun FilterRail(
    total: Int,
    categories: List<Categoria>,
    selected: Categoria?,
    onSelect: (Categoria?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterPill(label = "Todo", count = total, selected = selected == null, onClick = { onSelect(null) })
        categories.forEach { cat ->
            FilterPill(label = cat.displayName(), selected = selected == cat, onClick = { onSelect(cat) })
        }
    }
}

// MARK: - Empty state (Pantalla 3)

@Composable
private fun EmptyDetail(onScan: () -> Unit, onManual: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        FridgeIllustration()
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Tu nevera",
            style = TextStyle(fontFamily = InstrumentSerif, fontSize = 30.sp, letterSpacing = (-0.4).sp, lineHeight = 33.sp),
            color = Ink,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "está vacía",
            style = TextStyle(fontFamily = InstrumentSerif, fontStyle = FontStyle.Italic, fontSize = 30.sp, letterSpacing = (-0.4).sp, lineHeight = 33.sp),
            color = Ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Escanea el código de barras y la fecha de caducidad de tu primer producto para empezar.",
            style = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 21.sp),
            color = InkSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(240.dp),
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onScan,
                colors = ButtonDefaults.buttonColors(containerColor = MintDeep, contentColor = SurfaceWhite),
                shape = RoundedCornerShape(16.dp),
            ) { Text("Escanear", style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)) }
            Button(
                onClick = onManual,
                colors = ButtonDefaults.buttonColors(containerColor = Paper, contentColor = Ink),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Hairline),
            ) { Text("Añadir a mano", style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)) }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = "💡 Tip: añade varios a la vez encadenando escaneos",
            style = TextStyle(fontFamily = Inter, fontSize = 12.sp),
            color = ule.jescuj00.fridgey.ui.theme.InkMuted,
            textAlign = TextAlign.Center,
        )
    }
}

/** 132×168 empty-fridge illustration drawn with native shapes (no raster). */
@Composable
private fun FridgeIllustration() {
    Box(modifier = Modifier.size(width = 132.dp, height = 168.dp), contentAlignment = Alignment.TopCenter) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val corner = 16.dp.toPx()
            val cr = androidx.compose.ui.geometry.CornerRadius(corner, corner)
            // Body fill (mint-tint → paper) + 2-px mint-soft border.
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(MintTint, Paper)),
                cornerRadius = cr,
            )
            drawRoundRect(
                color = MintSoft,
                cornerRadius = cr,
                style = Stroke(width = 2.dp.toPx()),
            )
            // Two shelf lines at 38% and 70% height.
            listOf(0.38f, 0.70f).forEach { f ->
                val y = size.height * f
                drawLine(
                    color = MintSoft,
                    start = Offset(8.dp.toPx(), y),
                    end = Offset(size.width - 8.dp.toPx(), y),
                    strokeWidth = 1.5.dp.toPx(),
                )
            }
            // Vertical handle (4×36, radius 4) on the right.
            val handleW = 4.dp.toPx()
            val handleH = 36.dp.toPx()
            drawRoundRect(
                color = MintSoft,
                topLeft = Offset(size.width - 14.dp.toPx(), size.height * 0.12f),
                size = Size(handleW, handleH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            )
        }
        // Centred herb emoji near the top, faded.
        Text(
            text = "🌿",
            style = TextStyle(fontSize = 32.sp),
            modifier = Modifier.alpha(0.45f).padding(top = 16.dp),
        )
    }
}

// MARK: - helpers

private fun initialOf(nombre: String): String = nombre.trim().firstOrNull()?.uppercase() ?: "?"

private fun cantidadLabel(cantidad: Double, unidad: UnidadMedida): String {
    val n = if (cantidad == cantidad.toLong().toDouble()) cantidad.toLong().toString()
    else cantidad.toString().trimEnd('0').trimEnd('.')
    return "$n ${unidad.simbolo}"
}
