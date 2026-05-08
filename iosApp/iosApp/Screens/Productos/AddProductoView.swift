import SwiftUI
import Shared

/// Add-producto form, mirroring `AddProductoScreen.kt` (Android).
///
/// Layout from top to bottom:
///   1. Scan/Manual toggle — visible only while `isFormPristine == true`
///      (one-way gate; once any field changes, the toggle hides for good).
///   2. Nombre (TextField).
///   3. Categoría (Picker with all `Categoria.entries`).
///   4. Fecha de caducidad (DatePicker, restricted to today or later).
///   5. Inline error (only when `state.error != nil`).
///
/// Saving routes through the VM (`onSavePressed(neveraId:)`) which calls
/// `KoinIosKt.getProductoRepository().insertProducto(producto:)` directly,
/// matching how Android's `AddProductoViewModel` works.
struct AddProductoView: View {

    let neveraId: String
    let onCompleted: () -> Void

    @StateObject private var viewModel = AddProductoViewModel()
    @State private var showScanner = false

    var body: some View {
        NavigationStack {
            Form {
                // Toggle Escanear / A mano. Only while the form is pristine.
                if viewModel.state.isFormPristine {
                    Section {
                        scanModeToggle
                    }
                    .listRowBackground(Color.clear)
                }

                Section("Datos") {
                    TextField(
                        "Nombre",
                        text: Binding(
                            get: { viewModel.state.name },
                            set: { viewModel.onNameChanged($0) }
                        )
                    )
                    .autocorrectionDisabled()

                    Picker(
                        "Categoría",
                        selection: Binding(
                            get: { viewModel.state.categoria },
                            set: { viewModel.onCategoriaSelected($0) }
                        )
                    ) {
                        ForEach(allCategorias, id: \.self) { cat in
                            Text(cat.displayName).tag(cat)
                        }
                    }

                    DatePicker(
                        "Fecha de caducidad",
                        selection: Binding(
                            get: { viewModel.state.fechaCaducidad.asSwiftDate },
                            set: { newDate in
                                viewModel.onFechaSelected(
                                    Kotlinx_datetimeLocalDate.from(date: newDate)
                                )
                            }
                        ),
                        in: Calendar.current.startOfDay(for: Date())...,
                        displayedComponents: .date
                    )
                }

                if let error = viewModel.state.error {
                    Section {
                        Text(error)
                            .foregroundColor(.red)
                            .font(.caption)
                    }
                }
            }
            .navigationTitle("Añadir producto")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") { onCompleted() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if viewModel.state.isLoading {
                        ProgressView()
                    } else {
                        Button("Guardar") {
                            viewModel.onSavePressed(neveraId: neveraId)
                        }
                        .disabled(
                            viewModel.state.name
                                .trimmingCharacters(in: .whitespaces)
                                .isEmpty
                        )
                    }
                }
            }
            .fullScreenCover(isPresented: $showScanner) {
                DateScannerView(
                    onCancel: { showScanner = false },
                    onDatePicked: { date in
                        viewModel.onScannedDateReceived(date)
                        showScanner = false
                    },
                    onManualEntry: { showScanner = false }
                )
            }
            .onChange(of: viewModel.state.success) { _, succeeded in
                if succeeded { onCompleted() }
            }
        }
    }

    /// Two-segment Picker. SwiftUI's `.segmented` style renders text labels
    /// from each `tag()`'s associated `View` — segmented controls don't
    /// natively combine icons with text the way Android's `SegmentedButton`
    /// does, so we go text-only here. Behaviourally identical: tap "Escanear"
    /// to open the scanner, "A mano" is a no-op when already manual (the
    /// toggle simply stays visible because the form is still pristine).
    private var scanModeToggle: some View {
        Picker(
            "",
            selection: Binding<ScanMode>(
                get: { viewModel.state.scanMode },
                set: { mode in
                    switch mode {
                    case .scan:
                        showScanner = true
                    case .manual:
                        viewModel.setScanMode(.manual)
                    }
                }
            )
        ) {
            Text("Escanear").tag(ScanMode.scan)
            Text("A mano").tag(ScanMode.manual)
        }
        .pickerStyle(.segmented)
    }

    /// `Categoria.entries` is exposed by K/N as an `NSArray<Categoria *>`;
    /// cast to a Swift array for `ForEach`.
    private var allCategorias: [Categoria] {
        (Categoria.entries as? [Categoria]) ?? []
    }
}
