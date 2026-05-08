import SwiftUI
import Shared

struct NeveraDetailView: View {

    @StateObject private var viewModel: NeveraDetailViewModel

    let neveraId: String
    @State private var showAddSheet = false
    @State private var pendingDelete: Producto?

    init(neveraId: String, currentUserId: String) {
        self.neveraId = neveraId
        _viewModel = StateObject(
            wrappedValue: NeveraDetailViewModel(
                neveraId: neveraId,
                currentUserId: currentUserId
            )
        )
    }

    var body: some View {
        Group {
            if viewModel.state.isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if viewModel.state.productos.isEmpty {
                EmptyProductosView()
            } else {
                List {
                    ForEach(viewModel.state.productos, id: \.id) { producto in
                        ProductoRow(producto: producto)
                            .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                                Button(role: .destructive) {
                                    pendingDelete = producto
                                } label: {
                                    Label("Eliminar", systemImage: "trash")
                                }
                            }
                    }
                }
                .listStyle(.plain)
            }
        }
        .navigationTitle(viewModel.state.neveraNombre.isEmpty ? "Nevera" : viewModel.state.neveraNombre)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button(action: { showAddSheet = true }) {
                    Image(systemName: "plus")
                }
                .accessibilityLabel("Añadir producto")
            }
        }
        .sheet(isPresented: $showAddSheet) {
            AddProductoView(
                neveraId: neveraId,
                onCompleted: { showAddSheet = false }
            )
        }
        .alert(
            "Eliminar producto",
            isPresented: Binding(
                get: { pendingDelete != nil },
                set: { if !$0 { pendingDelete = nil } }
            ),
            presenting: pendingDelete
        ) { producto in
            Button("Eliminar", role: .destructive) {
                viewModel.deleteProducto(producto)
                pendingDelete = nil
            }
            Button("Cancelar", role: .cancel) { pendingDelete = nil }
        } message: { producto in
            Text("¿Seguro que quieres eliminar “\(producto.nombre)”?")
        }
        .alert(
            "Error",
            isPresented: Binding(
                get: { viewModel.state.error != nil },
                set: { if !$0 { viewModel.clearError() } }
            ),
            actions: { Button("OK") { viewModel.clearError() } },
            message: { Text(viewModel.state.error ?? "") }
        )
        .onAppear { viewModel.start() }
        .onDisappear { viewModel.stop() }
    }
}

// MARK: - Row

private struct ProductoRow: View {
    let producto: Producto

    var body: some View {
        HStack(spacing: 12) {
            Circle()
                .fill(indicatorColor)
                .frame(width: 14, height: 14)

            VStack(alignment: .leading, spacing: 2) {
                Text(producto.nombre)
                    .font(.headline)
                Text(producto.categoria.displayName)
                    .font(.caption)
                    .foregroundColor(.secondary)
                Text("Caduca: \(producto.fechaCaducidad.formattedEs)")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            Spacer()
            Text(daysLabel)
                .font(.subheadline.weight(.semibold))
                .foregroundColor(indicatorColor)
        }
        .padding(.vertical, 6)
    }

    /// Same thresholds as Android (`<3` red, `<=7` yellow, else green).
    private var indicatorColor: Color {
        let d = Int(producto.diasRestantes)
        switch d {
        case ..<3:   return .red
        case ...7:   return .orange
        default:     return .green
        }
    }

    private var daysLabel: String {
        let d = Int(producto.diasRestantes)
        switch d {
        case ..<0:  return "Caducado"
        case 0:     return "Hoy"
        case 1:     return "1 día"
        default:    return "\(d) días"
        }
    }
}

// MARK: - Empty state

private struct EmptyProductosView: View {
    var body: some View {
        VStack(spacing: 16) {
            Spacer()
            Image(systemName: "tray")
                .font(.system(size: 56))
                .foregroundColor(.secondary)
            Text("Esta nevera está vacía")
                .font(.title2)
                .fontWeight(.semibold)
            Text("¡Empieza añadiendo productos!")
                .font(.body)
                .foregroundColor(.secondary)
            Spacer()
        }
    }
}

// `AddProductoSheet` (the inline form previously here) was replaced by
// `AddProductoView` in `Screens/Productos/`, which adds the
// Escanear / A mano toggle, the `isFormPristine` one-way gate, and the
// scanner integration — full functional parity with Android's
// `AddProductoScreen`. See `AddProductoView.swift`.
