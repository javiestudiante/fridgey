package ule.jescuj00.fridgey.ui.screens.add_producto

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime
import org.koin.androidx.compose.koinViewModel
import ule.jescuj00.fridgey.domain.model.Categoria
import ule.jescuj00.fridgey.domain.model.UnidadMedida
import ule.jescuj00.fridgey.ui.components.EyebrowLabel
import ule.jescuj00.fridgey.ui.components.ProductRow
import ule.jescuj00.fridgey.ui.components.ScreenHeader
import ule.jescuj00.fridgey.ui.components.SegmentOption
import ule.jescuj00.fridgey.ui.components.SegmentedToggle
import ule.jescuj00.fridgey.ui.theme.FridgeyShapes
import ule.jescuj00.fridgey.ui.theme.Ink
import ule.jescuj00.fridgey.ui.theme.InkMuted
import ule.jescuj00.fridgey.ui.theme.LocalFridgeySpacing
import ule.jescuj00.fridgey.ui.theme.Mint
import ule.jescuj00.fridgey.ui.theme.SurfaceWhite
import ule.jescuj00.fridgey.ui.util.displayName
import ule.jescuj00.fridgey.ui.util.formatEs

/**
 * AddProducto — editorial-kitchen rewrite (Fase 4 / Prompt B).
 *
 * Public signature is unchanged so callers in the nav graph keep working.
 * The visual language is Material 3 + Fridgey tokens: cream canvas,
 * outlined text fields, serif `ScreenHeader`, a `Vista previa` block
 * powered by the Fase 3 `ProductRow`, and a full-width "Guardar producto"
 * CTA at the bottom (in addition to the trailing "Guardar" link in the
 * header — both wired to the same VM method, as the spec asks).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProductoScreen(
    neveraId: String,
    onNavigateBack: () -> Unit,
    onScanRequested: () -> Unit,
    viewModel: AddProductoViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val spacing = LocalFridgeySpacing.current
    var showAvisarDialog by remember { mutableStateOf(false) }
    var showFechaDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.success) {
        if (state.success) onNavigateBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        // Single-CTA design: header only carries "Cancelar" on the left.
        // The primary "Guardar producto" action is the full-width button
        // at the bottom of the form — the previous trailing "Guardar"
        // link in the header was visually redundant (and on a couple of
        // devices was failing to take taps reliably under the
        // `ScreenHeader` Row layout).
        ScreenHeader(
            title = "Añadir producto",
            leading = {
                TextButton(onClick = onNavigateBack) {
                    Text(
                        "Cancelar",
                        style = MaterialTheme.typography.labelLarge,
                        color = Ink,
                    )
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            // Escanear / A mano — only while the form is pristine.
            if (state.isFormPristine) {
                SegmentedToggle(
                    options = listOf(
                        SegmentOption(label = "Escanear"),
                        SegmentOption(label = "A mano"),
                    ),
                    selectedIndex = if (state.scanMode == ScanMode.Scan) 0 else 1,
                    onSelect = { idx ->
                        when (idx) {
                            0 -> onScanRequested()
                            1 -> viewModel.setScanMode(ScanMode.Manual)
                        }
                    },
                )
            }

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChanged,
                label = { Text("Nombre") },
                singleLine = true,
                shape = FridgeyShapes.small,
                modifier = Modifier.fillMaxWidth(),
            )

            CategoriaDropdown(
                selected = state.categoria,
                onSelected = viewModel::onCategoriaSelected,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                CantidadField(
                    cantidad = state.cantidad,
                    unidad = state.unidad,
                    onCantidadChange = viewModel::onCantidadChanged,
                    onUnidadChange = viewModel::onUnidadChanged,
                    modifier = Modifier.weight(1f),
                )
                AvisarField(
                    dias = state.diasAvisoAntes,
                    onClick = { showAvisarDialog = true },
                    modifier = Modifier.weight(1f),
                )
            }

            FechaCaducidadField(
                fecha = state.fechaCaducidad,
                onClick = { showFechaDialog = true },
            )

            state.error?.let { msg ->
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(modifier = Modifier.height(spacing.md))

            // Vista previa
            EyebrowLabel(text = "VISTA PREVIA")
            Spacer(modifier = Modifier.height(spacing.xs))
            VistaPrevia(
                name = state.name,
                categoria = state.categoria,
                cantidad = state.cantidad,
                unidad = state.unidad,
                fechaCaducidad = state.fechaCaducidad,
            )

            Spacer(modifier = Modifier.height(spacing.xl))

            // Full-width primary CTA — duplicate of the header link to keep
            // the editorial layout balanced. Both call the same VM method.
            Button(
                onClick = { viewModel.onSavePressed(neveraId) },
                enabled = !state.isLoading && state.name.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = FridgeyShapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = Mint),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = SurfaceWhite,
                    )
                } else {
                    Text(
                        text = "Guardar producto",
                        style = MaterialTheme.typography.labelLarge,
                        color = SurfaceWhite,
                    )
                }
            }

            Text(
                text = "Te avisaremos cuando el producto esté próximo a caducar.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.sm, bottom = spacing.xl),
            )
        }
    }

    if (showAvisarDialog) {
        AvisarDialog(
            current = state.diasAvisoAntes,
            fechaCaducidad = state.fechaCaducidad,
            onSelected = {
                viewModel.onDiasAvisoAntesChanged(it)
                showAvisarDialog = false
            },
            onDismiss = { showAvisarDialog = false },
        )
    }

    if (showFechaDialog) {
        FechaPickerDialog(
            current = state.fechaCaducidad,
            onSelected = { date ->
                viewModel.onFechaSelected(date)
                showFechaDialog = false
            },
            onDismiss = { showFechaDialog = false },
        )
    }
}

// MARK: - Sub-composables

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoriaDropdown(
    selected: Categoria,
    onSelected: (Categoria) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = "${selected.emoji}  ${selected.displayName()}",
            onValueChange = {},
            readOnly = true,
            label = { Text("Categoría") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = FridgeyShapes.small,
            modifier = Modifier
                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            Categoria.entries.forEach { cat ->
                DropdownMenuItem(
                    text = { Text("${cat.emoji}  ${cat.displayName()}") },
                    onClick = {
                        onSelected(cat)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CantidadField(
    cantidad: Double,
    unidad: UnidadMedida,
    onCantidadChange: (Double) -> Unit,
    onUnidadChange: (UnidadMedida) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFridgeySpacing.current
    var unitMenuExpanded by remember { mutableStateOf(false) }
    // Track typed text locally so the user can leave the field in an
    // intermediate state like "1." while typing the decimal portion
    // without the VM-driven value snapping it back to "1".
    var textValue by remember(unidad) { mutableStateOf(formatCantidadForInput(cantidad)) }

    OutlinedTextField(
        value = textValue,
        onValueChange = { raw ->
            textValue = raw
            raw.replace(',', '.').toDoubleOrNull()
                ?.takeIf { it > 0.0 }
                ?.let(onCantidadChange)
        },
        label = { Text("Cantidad") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        suffix = {
            Box {
                Row(
                    modifier = Modifier.clickable { unitMenuExpanded = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = unidad.simbolo,
                        style = MaterialTheme.typography.labelLarge,
                        color = Mint,
                    )
                    Spacer(modifier = Modifier.width(spacing.xs))
                    Text(
                        text = "▾",
                        style = MaterialTheme.typography.labelLarge,
                        color = Mint,
                    )
                }
                DropdownMenu(
                    expanded = unitMenuExpanded,
                    onDismissRequest = { unitMenuExpanded = false },
                ) {
                    UnidadMedida.entries.forEach { u ->
                        DropdownMenuItem(
                            text = { Text("${u.simbolo}  ·  ${u.valor.replaceFirstChar { it.uppercase() }}") },
                            onClick = {
                                onUnidadChange(u)
                                unitMenuExpanded = false
                            },
                        )
                    }
                }
            }
        },
        shape = FridgeyShapes.small,
        modifier = modifier,
    )
}

/** "1.0" → "1", "1.5" → "1.5", "0.250" → "0.25" — for the input field. */
private fun formatCantidadForInput(c: Double): String {
    if (c == c.toLong().toDouble()) return c.toLong().toString()
    // Trim trailing zeros so the cursor doesn't land after a noisy ".00".
    return c.toString().trimEnd('0').trimEnd('.')
}

/** "1.0 kg" → "1 kg", "1.5 kg" → "1.5 kg" — for read-only display. */
private fun formatCantidadDisplay(c: Double, u: UnidadMedida): String =
    "${formatCantidadForInput(c)} ${u.simbolo}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AvisarField(
    dias: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // M3 OutlinedTextField with `enabled = false` lets the underlying
    // `clickable` modifier swallow taps (the field itself doesn't react
    // when disabled, so focus never gets stolen). The disabled colour
    // overrides bring the visual weight back up to look enabled.
    Box(modifier = modifier.clickable(onClick = onClick)) {
        OutlinedTextField(
            value = avisarDisplay(dias),
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text("Avisar") },
            trailingIcon = {
                Text(
                    text = "▾",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                )
            },
            shape = FridgeyShapes.small,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = Ink,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = InkMuted,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FechaCaducidadField(
    fecha: LocalDate,
    onClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        OutlinedTextField(
            value = fecha.formatEs(),
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text("Fecha de caducidad") },
            trailingIcon = {
                Text(
                    text = "ELEGIR",
                    style = MaterialTheme.typography.labelMedium,
                    color = Mint,
                )
            },
            shape = FridgeyShapes.small,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = Ink,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = Mint,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun VistaPrevia(
    name: String,
    categoria: Categoria,
    cantidad: Double,
    unidad: UnidadMedida,
    fechaCaducidad: LocalDate,
) {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val daysRemaining = today.daysUntil(fechaCaducidad)
    val displayName = name.trim().ifEmpty { "Producto" }

    // Urgency bucket / colour come from ProductRow via the single
    // expirationStateOf() source of truth — no thresholds duplicated here.
    ProductRow(
        categoria = categoria,
        name = displayName,
        supporting = "${categoria.displayName()} · ${formatCantidadDisplay(cantidad, unidad)}",
        daysRemaining = daysRemaining,
    )
}

// MARK: - Dialogs

/**
 * Predefined set of "warn me X days before" values. The dialog filters
 * this list at runtime against `daysUntil(fechaCaducidad)` so the user
 * never sees an option larger than the available lead-time (a fecha a
 * 2 días vista no permite "5 días antes", etc.).
 */
private val avisarOptions = listOf(0, 1, 2, 3, 5, 7, 10, 14)

private fun avisarDisplay(dias: Int): String = when {
    dias == 0 -> "El mismo día"
    dias == 1 -> "1 día antes"
    else -> "$dias días antes"
}

@Composable
private fun AvisarDialog(
    current: Int,
    fechaCaducidad: LocalDate,
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = LocalFridgeySpacing.current
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val daysUntil = today.daysUntil(fechaCaducidad).coerceAtLeast(0)
    val visibleOptions = avisarOptions.filter { it <= daysUntil }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Avisar antes de caducar") },
        text = {
            Column {
                visibleOptions.forEach { d ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(d) }
                            .padding(vertical = spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = d == current,
                            onClick = { onSelected(d) },
                        )
                        Spacer(modifier = Modifier.size(spacing.sm))
                        Text(
                            text = avisarDisplay(d),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FechaPickerDialog(
    current: LocalDate,
    onSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialMillis = current.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let { millis ->
                    val date = Instant.fromEpochMilliseconds(millis)
                        .toLocalDateTime(TimeZone.UTC)
                        .date
                    onSelected(date)
                }
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    ) {
        DatePicker(state = pickerState)
    }
}
