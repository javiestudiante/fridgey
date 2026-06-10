import SwiftUI
import Shared

struct NeveraListView: View {

    @StateObject private var viewModel: NeveraListViewModel
    let currentUserId: String
    let onSignOut: () -> Void

    @State private var showCreateSheet = false
    @State private var navTarget: Nevera?
    @State private var showUnirse = false

    init(currentUserId: String, onSignOut: @escaping () -> Void) {
        _viewModel = StateObject(wrappedValue: NeveraListViewModel(currentUserId: currentUserId))
        self.currentUserId = currentUserId
        self.onSignOut = onSignOut
    }

    var body: some View {
        NavigationStack {
            ZStack(alignment: .bottomTrailing) {
                Color.fridgeyCream.ignoresSafeArea()

                if viewModel.state.isLoading && viewModel.state.neveras.isEmpty {
                    ProgressView().tint(Color.fridgeyMintDeep)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if viewModel.state.neveras.isEmpty {
                    VStack(spacing: 0) {
                        header
                        emptyState
                    }
                } else {
                    content
                }

                if !(viewModel.state.isLoading && viewModel.state.neveras.isEmpty) {
                    createFab
                        .padding(.trailing, 22)
                        .padding(.bottom, 28)
                }
            }
            .navigationBarHidden(true)
            .navigationDestination(item: $navTarget) { nevera in
                NeveraDetailView(neveraId: nevera.id, currentUserId: currentUserId)
            }
            .navigationDestination(isPresented: $showUnirse) {
                UnirseView(currentUserId: currentUserId)
            }
            .sheet(isPresented: $showCreateSheet) {
                CreateNeveraSheet(isPresented: $showCreateSheet) { name in
                    viewModel.createNevera(name: name)
                }
            }
            .onAppear { viewModel.start() }
            .onDisappear { viewModel.stop() }
        }
    }

    // MARK: - Content

    private var content: some View {
        ScrollView {
            VStack(spacing: 0) {
                header

                if let s = viewModel.state.expiringToday, s.total > 0 {
                    AlertCard(
                        bulletText: String(s.total),
                        title: alertTitle(s),
                        onClick: {
                            if let id = s.neveraId,
                               let nev = viewModel.state.neveras.first(where: { $0.nevera.id == id })?.nevera {
                                navTarget = nev
                            }
                        },
                        subtitle: s.productNames.prefix(2).joined(separator: " · ").isEmpty
                            ? nil : s.productNames.prefix(2).joined(separator: " · ")
                    )
                    .padding(.horizontal, 16)
                }

                SectionHeader(title: "Tus neveras", count: viewModel.state.neveras.count, bottomPadding: 8)

                ForEach(Array(viewModel.state.neveras.enumerated()), id: \.element.nevera.id) { idx, r in
                    NeveraCard(
                        name: r.nevera.nombre,
                        role: r.nevera.esPropietario ? .owner : .guest,
                        featured: idx == 0,
                        memberInitials: r.miembros.map { initial($0.nombre) },
                        memberCount: r.miembros.count,
                        productCount: Int(r.nevera.numeroProductos),
                        expiringCount: Int(r.expiringCount),
                        onClick: { navTarget = r.nevera }
                    )
                    .padding(.horizontal, 16)
                    .padding(.bottom, 12)
                }

                Spacer().frame(height: 112)
            }
        }
    }

    // MARK: - Header

    private var header: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 6) {
                Text(todayEyebrow())
                    .font(.custom("Inter-Regular", size: 11).weight(.medium))
                    .tracking(1.3)
                    .foregroundStyle(Color.fridgeyInkMuted)
                Text("Mis neveras")
                    .font(.custom("InstrumentSerif-Regular", size: 32))
                    .foregroundStyle(Color.fridgeyInk)
            }
            Spacer()
            HStack(spacing: 8) {
                // Sign-out lives in an honest overflow menu; the bell is
                // decorative (notifications aren't a feature yet).
                Menu {
                    // Paridad con el DropdownMenu de Android: unirse a una
                    // nevera colaborativa con un código/QR de invitación.
                    Button(action: { showUnirse = true }) {
                        Label("Unirse con código", systemImage: "person.badge.plus")
                    }
                    Button(role: .destructive, action: onSignOut) {
                        Label("Cerrar sesión", systemImage: "rectangle.portrait.and.arrow.right")
                    }
                } label: {
                    circleIcon("ellipsis")
                }
                circleIcon("bell")
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
        .padding(.bottom, 14)
    }

    private func circleIcon(_ systemName: String) -> some View {
        Image(systemName: systemName)
            .font(.system(size: 17))
            .foregroundStyle(Color.fridgeyInkSoft)
            .frame(width: 36, height: 36)
            .background(Color.fridgeyInk.opacity(0.04), in: Circle())
    }

    // iOS home FAB: 60×60 mint-deep, white "+", soft mint shadow (per the
    // iOS design — distinct from Android's extended mint-soft FAB).
    private var createFab: some View {
        Button(action: { showCreateSheet = true }) {
            Image(systemName: "plus")
                .font(.system(size: 24, weight: .medium))
                .foregroundStyle(Color.fridgeySurfaceWhite)
                .frame(width: 60, height: 60)
                .background(Color.fridgeyMintDeep, in: RoundedRectangle(cornerRadius: FridgeyRadius.lg))
                .shadow(color: Color.fridgeyMintDeep.opacity(0.35), radius: 15, x: 0, y: 14)
        }
        .buttonStyle(.plain)
    }

    private var emptyState: some View {
        VStack(spacing: 8) {
            Spacer()
            Text("Aún no tienes neveras")
                .font(.custom("InstrumentSerif-Regular", size: 30))
                .foregroundStyle(Color.fridgeyInk)
            Text("Crea tu primera nevera para empezar a controlar las fechas de caducidad.")
                .font(.custom("Inter-Regular", size: 14))
                .foregroundStyle(Color.fridgeyInkSoft)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - helpers

    private func initial(_ nombre: String) -> String {
        String(nombre.trimmingCharacters(in: .whitespaces).prefix(1)).uppercased()
    }

    private func alertTitle(_ s: ExpiringTodaySummary) -> String {
        let total = Int(s.total)
        let noun = total == 1 ? "producto" : "productos"
        let verb = total == 1 ? "caduca" : "caducan"
        if let nombre = s.neveraNombre {
            return "\(total) \(noun) \(verb) hoy en \(nombre)"
        }
        return "\(total) \(noun) \(verb) hoy"
    }

    private func todayEyebrow() -> String {
        let meses = ["ENERO", "FEBRERO", "MARZO", "ABRIL", "MAYO", "JUNIO",
                     "JULIO", "AGOSTO", "SEPTIEMBRE", "OCTUBRE", "NOVIEMBRE", "DICIEMBRE"]
        let c = Calendar.current.dateComponents([.day, .month], from: Date())
        let day = c.day ?? 1
        let month = meses[(c.month ?? 1) - 1]
        return "HOY · \(day) \(month)"
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
                    TextField("Nombre", text: $name).autocorrectionDisabled()
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
