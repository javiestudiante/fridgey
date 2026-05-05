import SwiftUI
import Shared

struct NeveraListView: View {

    @StateObject private var viewModel: NeveraListViewModel
    let currentUserId: String
    let onSignOut: () -> Void

    @State private var showCreateSheet = false
    @State private var pendingDelete: Nevera?

    init(currentUserId: String, onSignOut: @escaping () -> Void) {
        _viewModel = StateObject(wrappedValue: NeveraListViewModel(currentUserId: currentUserId))
        self.currentUserId = currentUserId
        self.onSignOut = onSignOut
    }

    var body: some View {
        Group {
            if viewModel.state.isLoading && viewModel.state.neveras.isEmpty {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if viewModel.state.neveras.isEmpty {
                EmptyState(onCreateTap: { showCreateSheet = true })
            } else {
                List {
                    ForEach(viewModel.state.neveras, id: \.id) { nevera in
                        NavigationLink(value: nevera) {
                            NeveraRow(nevera: nevera)
                        }
                        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                            Button(role: .destructive) {
                                pendingDelete = nevera
                            } label: {
                                Label("Eliminar", systemImage: "trash")
                            }
                        }
                    }
                }
                .listStyle(.plain)
            }
        }
        .navigationTitle("Mis Neveras")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button(action: { showCreateSheet = true }) {
                    Image(systemName: "plus")
                }
                .accessibilityLabel("Crear nevera")
            }
            ToolbarItem(placement: .topBarLeading) {
                Button(action: onSignOut) {
                    Image(systemName: "rectangle.portrait.and.arrow.right")
                }
                .accessibilityLabel("Cerrar sesión")
            }
        }
        .navigationDestination(for: Nevera.self) { nevera in
            NeveraDetailView(
                neveraId: nevera.id,
                currentUserId: currentUserId
            )
        }
        .sheet(isPresented: $showCreateSheet) {
            CreateNeveraSheet(
                isPresented: $showCreateSheet,
                onCreate: { name in viewModel.createNevera(name: name) }
            )
        }
        .alert(
            "Eliminar nevera",
            isPresented: Binding(
                get: { pendingDelete != nil },
                set: { if !$0 { pendingDelete = nil } }
            ),
            presenting: pendingDelete
        ) { nevera in
            Button("Eliminar", role: .destructive) {
                viewModel.deleteNevera(nevera)
                pendingDelete = nil
            }
            Button("Cancelar", role: .cancel) { pendingDelete = nil }
        } message: { nevera in
            Text("Se eliminarán también los productos de “\(nevera.nombre)”. Esta acción no se puede deshacer.")
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

private struct NeveraRow: View {
    let nevera: Nevera

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(nevera.nombre)
                    .font(.headline)
                Spacer()
                Text(nevera.esPropietario ? "Propietario" : "Colaborador")
                    .font(.caption)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(nevera.esPropietario ? Color.blue.opacity(0.15) : Color.green.opacity(0.15))
                    .foregroundColor(nevera.esPropietario ? .blue : .green)
                    .clipShape(Capsule())
            }
            Text(productCountLabel(nevera.numeroProductos))
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .padding(.vertical, 4)
    }

    private func productCountLabel(_ count: Int32) -> String {
        count == 1 ? "1 producto" : "\(count) productos"
    }
}

// MARK: - Empty state

private struct EmptyState: View {
    let onCreateTap: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Spacer()
            Image(systemName: "refrigerator")
                .font(.system(size: 56))
                .foregroundColor(.secondary)
            Text("Aún no tienes neveras")
                .font(.title2)
                .fontWeight(.semibold)
            Text("Crea tu primera nevera para empezar a controlar las fechas de caducidad.")
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
            Button(action: onCreateTap) {
                Label("Crear nevera", systemImage: "plus")
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color.accentColor)
                    .foregroundColor(.white)
                    .cornerRadius(10)
            }
            .padding(.horizontal, 32)
            Spacer()
        }
    }
}

// MARK: - Create sheet

private struct CreateNeveraSheet: View {
    @Binding var isPresented: Bool
    let onCreate: (String) -> Void

    @State private var name: String = ""

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Nombre", text: $name)
                        .autocorrectionDisabled()
                } header: {
                    Text("Nueva nevera")
                } footer: {
                    Text("Ejemplos: Hogar, Casa de la playa, Despensa")
                }
            }
            .navigationTitle("Nueva nevera")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") { isPresented = false }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Crear") {
                        onCreate(name)
                        isPresented = false
                    }
                    .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }
}
