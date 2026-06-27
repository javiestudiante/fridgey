import SwiftUI
import Shared

/// AddProducto — editorial-kitchen rewrite (Fase 4 / Prompt B).
///
/// Visual language: HIG-flavoured InsetGrouped Form over the cream
/// canvas, a 3×3 emoji grid for categoria (replaces the wheel Picker),
/// settings-style rows for cantidad / caduca / avisar (each opens a
/// sheet with predefined options), and a full-width "Guardar producto"
/// CTA at the bottom (mirroring the trailing "Guardar" toolbar item —
/// both call the same VM method).
struct AddProductoView: View {

    let neveraId: String
    let onCompleted: () -> Void
    /// Abre el escáner nada más aparecer (entrada "Escanear" del empty state
    /// del detalle). El flujo normal entra con `false` y usa el toggle.
    let startScanning: Bool
    /// Producto a editar (UC-10), o `nil` en el flujo de alta. iOS navega a
    /// esta vista por `.sheet`, así que el objeto llega entero en memoria — no
    /// hace falta recargarlo por id (a diferencia de Android, que entra por una
    /// ruta de strings).
    private let editingProducto: Producto?

    @StateObject private var viewModel: AddProductoViewModel

    init(
        neveraId: String,
        onCompleted: @escaping () -> Void,
        startScanning: Bool = false,
        editingProducto: Producto? = nil
    ) {
        self.neveraId = neveraId
        self.onCompleted = onCompleted
        self.startScanning = startScanning
        self.editingProducto = editingProducto
        _viewModel = StateObject(
            wrappedValue: AddProductoViewModel(editingProducto: editingProducto)
        )
    }

    @State private var showScanner = false
    @State private var autoScanLaunched = false
    /// Single source of truth for which (if any) bottom sheet is open.
    ///
    /// The previous implementation used three independent `@State Bool`s
    /// driving three stacked `.sheet(isPresented:)` modifiers. SwiftUI's
    /// modifier-chain semantics with multiple sheets on the SAME view
    /// occasionally drop presentations (the "Avisar sheet never opens"
    /// bug the user reported on the previous turn) — the runtime can
    /// only have one pending sheet at a time, and the bindings race.
    /// Routing through `.sheet(item:)` with a single state guarantees
    /// every tap presents the right sheet.
    @State private var activeSheet: ActiveSheet?

    private enum ActiveSheet: Int, Identifiable {
        case cantidad, avisar, fecha
        var id: Int { rawValue }
    }

    var body: some View {
        NavigationStack {
            Form {
                if viewModel.state.isFormPristine {
                    Section {
                        scanModeToggle
                            .listRowInsets(EdgeInsets())
                            .listRowBackground(Color.clear)
                    }
                }

                Section {
                    HStack {
                        Text("Nombre")
                            .font(FridgeyFont.bodyLarge)
                            .foregroundStyle(Color.fridgeyInk)
                        Spacer()
                        TextField(
                            "",
                            text: Binding(
                                get: { viewModel.state.name },
                                set: { viewModel.onNameChanged($0) }
                            )
                        )
                        .multilineTextAlignment(.trailing)
                        .foregroundStyle(Color.fridgeyInk)
                        .autocorrectionDisabled()
                    }
                }
                .listRowBackground(Color.fridgeySurfaceWhite)

                Section {
                    LazyVGrid(
                        columns: [
                            GridItem(.flexible(), spacing: FridgeySpacing.sm),
                            GridItem(.flexible(), spacing: FridgeySpacing.sm),
                            GridItem(.flexible(), spacing: FridgeySpacing.sm),
                        ],
                        spacing: FridgeySpacing.sm
                    ) {
                        ForEach(allCategorias, id: \.self) { cat in
                            categoryCell(cat)
                        }
                    }
                    .listRowInsets(EdgeInsets(
                        top: FridgeySpacing.md,
                        leading: FridgeySpacing.md,
                        bottom: FridgeySpacing.md,
                        trailing: FridgeySpacing.md
                    ))
                } header: {
                    EyebrowLabel(text: "CATEGORÍA")
                }
                .listRowBackground(Color.fridgeySurfaceWhite)

                Section {
                    settingsRow(
                        label: "Cantidad",
                        value: formatCantidadDisplay(
                            viewModel.state.cantidad,
                            viewModel.state.unidad
                        ),
                        valueColor: Color.fridgeyInkSoft,
                        onTap: { activeSheet = .cantidad }
                    )
                    settingsRow(
                        label: "Caduca",
                        value: viewModel.state.fechaCaducidad.formattedEs,
                        valueColor: Color.fridgeyMintDeep,
                        onTap: { activeSheet = .fecha }
                    )
                    settingsRow(
                        label: "Avisar",
                        value: avisarLabel(viewModel.state.diasAvisoAntes),
                        valueColor: Color.fridgeyInkSoft,
                        onTap: { activeSheet = .avisar }
                    )
                }
                .listRowBackground(Color.fridgeySurfaceWhite)

                if let error = viewModel.state.error {
                    Section {
                        Text(error)
                            .font(FridgeyFont.bodySmall)
                            .foregroundStyle(Color.fridgeyRust)
                    }
                    .listRowBackground(Color.fridgeySurfaceWhite)
                }

                Section {
                    Button(action: { viewModel.onSavePressed(neveraId: neveraId) }) {
                        ZStack {
                            if viewModel.state.isLoading {
                                ProgressView().tint(Color.fridgeySurfaceWhite)
                            } else {
                                Text("Guardar producto")
                                    .font(FridgeyFont.labelLarge)
                                    .foregroundStyle(Color.fridgeySurfaceWhite)
                            }
                        }
                        .frame(maxWidth: .infinity, minHeight: 56)
                        .background(
                            Color.fridgeyMintDeep,
                            in: RoundedRectangle(cornerRadius: FridgeyRadius.lg)
                        )
                    }
                    .buttonStyle(.plain)
                    .disabled(saveDisabled)
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())

                    Text("Te avisaremos cuando el producto esté próximo a caducar.")
                        .font(FridgeyFont.bodySmall)
                        .foregroundStyle(Color.fridgeyInkMuted)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                        .listRowBackground(Color.clear)
                }
            }
            .scrollContentBackground(.hidden)
            .background(Color.fridgeyCream)
            .navigationTitle(viewModel.state.isEditing ? "Editar producto" : "Añadir producto")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                // Single CTA design: only "Cancelar" lives in the toolbar.
                // The primary "Guardar producto" action is the full-width
                // button at the bottom of the form — keeps the title bar
                // uncluttered (which was clipping the title on narrower
                // devices) and avoids the double-CTA noise the previous
                // layout had.
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") { onCompleted() }
                        .foregroundStyle(Color.fridgeyInk)
                }
            }
            .fullScreenCover(isPresented: $showScanner) {
                DateScannerView(
                    onCancel: { showScanner = false },
                    onDatePicked: { date, autoFill in
                        // Apply the Open Food Facts autofill (if the barcode
                        // phase resolved anything) before the date.
                        if let autoFill = autoFill {
                            viewModel.onScannedProductReceived(autoFill)
                        }
                        viewModel.onScannedDateReceived(date)
                        showScanner = false
                    },
                    onManualEntry: { autoFill in
                        // No date this route — but apply the CÓDIGO-phase autofill
                        // (if any) so the form lands pre-filled and only the expiry
                        // date is left to set. Nil → nothing to pre-fill (manual
                        // entry from scratch).
                        if let autoFill = autoFill {
                            viewModel.onScannedProductReceived(autoFill)
                        }
                        showScanner = false
                    }
                )
            }
            .sheet(item: $activeSheet) { sheet in
                switch sheet {
                case .cantidad:
                    CantidadSheet(
                        initialCantidad: viewModel.state.cantidad,
                        initialUnidad: viewModel.state.unidad,
                        onApply: { value, unidad in
                            viewModel.onCantidadChanged(value)
                            viewModel.onUnidadChanged(unidad)
                            activeSheet = nil
                        },
                        onCancel: { activeSheet = nil }
                    )
                    .presentationDetents([.medium])
                case .avisar:
                    NumericOptionSheet(
                        title: "Avisar antes de caducar",
                        options: validAvisarOptions(for: viewModel.state.fechaCaducidad),
                        current: viewModel.state.diasAvisoAntes,
                        format: avisarLabel,
                        onSelected: { value in
                            viewModel.onDiasAvisoAntesChanged(value)
                            activeSheet = nil
                        },
                        onCancel: { activeSheet = nil }
                    )
                    .presentationDetents([.medium])
                case .fecha:
                    FechaPickerSheet(
                        currentDate: viewModel.state.fechaCaducidad.asSwiftDate,
                        onSelected: { date in
                            viewModel.onFechaSelected(
                                Kotlinx_datetimeLocalDate.from(date: date)
                            )
                            activeSheet = nil
                        },
                        onCancel: { activeSheet = nil }
                    )
                    .presentationDetents([.medium])
                }
            }
            .onChange(of: viewModel.state.success) { _, succeeded in
                if succeeded { onCompleted() }
            }
            .onAppear {
                guard startScanning, !autoScanLaunched else { return }
                autoScanLaunched = true
                // Pequeño respiro: presentar el fullScreenCover mientras la
                // animación del sheet contenedor sigue en vuelo puede perder
                // la presentación.
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                    showScanner = true
                }
            }
        }
        // Anchor the screen to the cream-on-ink editorial palette. Without
        // this, iOS would honour the system dark mode and the Form cells
        // would render with the dark grey "secondarySystemGroupedBackground"
        // colour — completely off-brand. The design system has no dark
        // variant yet, so we pin to light at the screen level.
        .preferredColorScheme(.light)
    }

    // MARK: - Computed helpers

    private var saveDisabled: Bool {
        viewModel.state.name.trimmingCharacters(in: .whitespaces).isEmpty
            || viewModel.state.isLoading
    }

    /// `Categoria.entries` is exposed by K/N as `NSArray<Categoria *>` —
    /// cast to `[Categoria]` for `ForEach`.
    private var allCategorias: [Categoria] {
        (Categoria.entries as? [Categoria]) ?? []
    }

    private func avisarLabel(_ dias: Int) -> String {
        switch dias {
        case 0:  return "El mismo día"
        case 1:  return "1 día antes"
        default: return "\(dias) días antes"
        }
    }

    /// Filters the canonical "warn me X days before" set against the
    /// number of days remaining until `fecha`, so the sheet never offers
    /// an option that exceeds the available lead-time.
    private func validAvisarOptions(for fecha: Kotlinx_datetimeLocalDate) -> [Int] {
        let todayStart = Calendar.current.startOfDay(for: Date())
        let fechaStart = Calendar.current.startOfDay(for: fecha.asSwiftDate)
        let diff = Calendar.current.dateComponents([.day], from: todayStart, to: fechaStart).day ?? 0
        let cap = max(0, diff)
        return [0, 1, 2, 3, 5, 7, 10, 14].filter { $0 <= cap }
    }

    // MARK: - Sub-views

    /// SwiftUI's `.segmented` style strips icons from `Picker` items, so we
    /// reuse the Fase 3 `SegmentedToggle` instead — gives us the editorial
    /// mint look with crisp text-only segments.
    private var scanModeToggle: some View {
        let selectedIndex = viewModel.state.scanMode == .scan ? 0 : 1
        return SegmentedToggle(
            options: [
                SegmentOption(label: "Escanear"),
                SegmentOption(label: "A mano"),
            ],
            selectedIndex: selectedIndex,
            onSelect: { idx in
                if idx == 0 {
                    showScanner = true
                } else {
                    viewModel.setScanMode(.manual)
                }
            }
        )
    }

    private func categoryCell(_ cat: Categoria) -> some View {
        let isSelected = cat == viewModel.state.categoria
        return Button(action: { viewModel.onCategoriaSelected(cat) }) {
            VStack(spacing: FridgeySpacing.xs) {
                Text(cat.emoji)
                    .font(.system(size: 28))
                Text(cat.displayName)
                    .font(FridgeyFont.labelSmall)
                    .tracking(0.8)
                    .foregroundStyle(isSelected ? Color.fridgeyMintDeep : Color.fridgeyInkSoft)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, FridgeySpacing.md)
            .padding(.horizontal, FridgeySpacing.xs)
            .background(
                isSelected ? Color.fridgeyMintSoft : Color.fridgeySurfaceWhite,
                in: RoundedRectangle(cornerRadius: FridgeyRadius.sm)
            )
            .overlay(
                RoundedRectangle(cornerRadius: FridgeyRadius.sm)
                    .stroke(
                        isSelected ? Color.fridgeyMintDeep : Color.fridgeyInk.opacity(0.08),
                        lineWidth: isSelected ? 2 : 1
                    )
            )
        }
        .buttonStyle(.plain)
    }

    /// Settings-style row: left label, right value (+ chevron), whole row
    /// tappable. Uses a real `Button` so the tap target is the full row,
    /// matching the HIG behaviour for "drill-down" cells.
    private func settingsRow(
        label: String,
        value: String,
        valueColor: Color,
        onTap: @escaping () -> Void
    ) -> some View {
        Button(action: onTap) {
            HStack {
                Text(label)
                    .font(FridgeyFont.bodyLarge)
                    .foregroundStyle(Color.fridgeyInk)
                Spacer()
                Text(value)
                    .font(FridgeyFont.bodyLarge)
                    .foregroundStyle(valueColor)
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Color.fridgeyInkMuted)
            }
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Sheets

/// Generic option picker: shows a list of preset numeric values with a
/// `current` highlighted; tapping a row dismisses with the chosen value.
private struct NumericOptionSheet: View {
    let title: String
    let options: [Int]
    let current: Int
    let format: (Int) -> String
    let onSelected: (Int) -> Void
    let onCancel: () -> Void

    var body: some View {
        NavigationStack {
            List {
                ForEach(options, id: \.self) { value in
                    Button(action: { onSelected(value) }) {
                        HStack {
                            Text(format(value))
                                .font(FridgeyFont.bodyLarge)
                                .foregroundStyle(Color.fridgeyInk)
                            Spacer()
                            if value == current {
                                Image(systemName: "checkmark")
                                    .foregroundStyle(Color.fridgeyMintDeep)
                            }
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .background(Color.fridgeyCream)
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar", action: onCancel)
                        .foregroundStyle(Color.fridgeyInk)
                }
            }
        }
    }
}

/// Cantidad + unit picker. Local-state on the typed text and the
/// selected unit; both get applied via a single `onApply(cantidad, unidad)`
/// callback when the user taps "Aceptar". Cancel discards typing.
private struct CantidadSheet: View {
    let initialCantidad: Double
    let initialUnidad: UnidadMedida
    let onApply: (Double, UnidadMedida) -> Void
    let onCancel: () -> Void

    @State private var cantidadText: String
    @State private var unidad: UnidadMedida

    init(
        initialCantidad: Double,
        initialUnidad: UnidadMedida,
        onApply: @escaping (Double, UnidadMedida) -> Void,
        onCancel: @escaping () -> Void
    ) {
        self.initialCantidad = initialCantidad
        self.initialUnidad = initialUnidad
        self.onApply = onApply
        self.onCancel = onCancel
        self._cantidadText = State(initialValue: formatCantidadForInput(initialCantidad))
        self._unidad = State(initialValue: initialUnidad)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    HStack {
                        Text("Cantidad")
                            .font(FridgeyFont.bodyLarge)
                            .foregroundStyle(Color.fridgeyInk)
                        Spacer()
                        TextField("", text: $cantidadText)
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.trailing)
                            .foregroundStyle(Color.fridgeyInk)
                    }
                }
                .listRowBackground(Color.fridgeySurfaceWhite)

                Section {
                    Picker("Unidad", selection: $unidad) {
                        ForEach(allUnidades, id: \.self) { u in
                            Text("\(u.simbolo)  ·  \(unidadDisplayName(u))").tag(u)
                        }
                    }
                    .pickerStyle(.inline)
                    .labelsHidden()
                } header: {
                    EyebrowLabel(text: "UNIDAD")
                }
                .listRowBackground(Color.fridgeySurfaceWhite)
            }
            .scrollContentBackground(.hidden)
            .background(Color.fridgeyCream)
            .navigationTitle("Cantidad")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar", action: onCancel)
                        .foregroundStyle(Color.fridgeyInk)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Aceptar") {
                        let parsed = Double(
                            cantidadText.replacingOccurrences(of: ",", with: ".")
                        )
                        if let v = parsed, v > 0 {
                            onApply(v, unidad)
                        } else {
                            // Fall back to the previous value if the user
                            // typed something unparseable.
                            onApply(initialCantidad, unidad)
                        }
                    }
                    .foregroundStyle(Color.fridgeyMintDeep)
                }
            }
        }
        .preferredColorScheme(.light)
    }

    private var allUnidades: [UnidadMedida] {
        (UnidadMedida.entries as? [UnidadMedida]) ?? []
    }
}

// MARK: - Formatters

/// "1.0" → "1", "1.5" → "1.5", "0.250" → "0.25" — for the input field.
fileprivate func formatCantidadForInput(_ c: Double) -> String {
    if c == Double(Int64(c)) { return String(Int64(c)) }
    // Trim trailing zeros so the cursor doesn't land after a noisy ".00".
    var s = String(c)
    while s.hasSuffix("0") { s.removeLast() }
    if s.hasSuffix(".") { s.removeLast() }
    return s
}

/// "1.0 kg" → "1 kg", "1.5 kg" → "1.5 kg" — for the read-only row.
fileprivate func formatCantidadDisplay(_ c: Double, _ u: UnidadMedida) -> String {
    "\(formatCantidadForInput(c)) \(u.simbolo)"
}

fileprivate func unidadDisplayName(_ u: UnidadMedida) -> String {
    u.valor.prefix(1).uppercased() + u.valor.dropFirst()
}

/// Native graphical DatePicker inside a sheet. Restricted to today
/// onwards to mirror the VM-level validation.
private struct FechaPickerSheet: View {
    let currentDate: Date
    let onSelected: (Date) -> Void
    let onCancel: () -> Void

    @State private var pickedDate: Date

    init(currentDate: Date, onSelected: @escaping (Date) -> Void, onCancel: @escaping () -> Void) {
        self.currentDate = currentDate
        self.onSelected = onSelected
        self.onCancel = onCancel
        self._pickedDate = State(initialValue: currentDate)
    }

    var body: some View {
        NavigationStack {
            VStack {
                DatePicker(
                    "",
                    selection: $pickedDate,
                    in: Calendar.current.startOfDay(for: Date())...,
                    displayedComponents: .date
                )
                .datePickerStyle(.graphical)
                .tint(Color.fridgeyMintDeep)
                .padding(.horizontal, FridgeySpacing.lg)
                Spacer()
            }
            .background(Color.fridgeyCream)
            .navigationTitle("Fecha de caducidad")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar", action: onCancel)
                        .foregroundStyle(Color.fridgeyInk)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Aceptar") { onSelected(pickedDate) }
                        .foregroundStyle(Color.fridgeyMintDeep)
                }
            }
        }
    }
}
