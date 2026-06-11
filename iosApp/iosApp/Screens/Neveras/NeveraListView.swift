import SwiftUI
import Shared

struct NeveraListView: View {

    @StateObject private var viewModel: NeveraListViewModel
    let currentUserId: String
    let onSignOut: () -> Void

    @State private var navTarget: Nevera?
    @State private var showUnirse = false
    // Un ÚNICO `.sheet(item:)` para el selector del FAB y la hoja de crear:
    // dos modificadores `.sheet` en la misma vista entran en conflicto en
    // SwiftUI (solo se honra uno). La acción elegida en el selector se guarda
    // y se dispara en el onDismiss (presentar la siguiente hoja / navegación
    // ANTES de que la primera termine de cerrarse también da conflictos).
    @State private var activeSheet: ActiveSheet?
    @State private var pendingFabAction: FabAction?

    private enum ActiveSheet: String, Identifiable {
        case fabChooser, create
        var id: String { rawValue }
    }
    private enum FabAction { case crear, unirse }

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
            // Hoja única: selector del FAB o crear-nevera (ver nota arriba).
            .sheet(item: $activeSheet, onDismiss: {
                // Al cerrarse el selector con una opción elegida, presenta la
                // siguiente (crear = hoja; unirse = navegación push).
                switch pendingFabAction {
                case .crear: activeSheet = .create
                case .unirse: showUnirse = true
                case nil: break
                }
                pendingFabAction = nil
            }) { which in
                switch which {
                case .fabChooser:
                    FabChooserSheet(
                        onCrear: { pendingFabAction = .crear; activeSheet = nil },
                        onUnirse: { pendingFabAction = .unirse; activeSheet = nil }
                    )
                case .create:
                    CreateNeveraSheet(
                        isPresented: Binding(
                            get: { activeSheet == .create },
                            set: { if !$0 { activeSheet = nil } }
                        )
                    ) { name, guardarEnCuenta in
                        viewModel.createNevera(name: name, guardarEnCuenta: guardarEnCuenta)
                    }
                }
            }
            // Aviso NO bloqueante: la nevera se creó en LOCAL pero no se pudo
            // subir a la cuenta. La creación NO se revierte ni se bloquea.
            .alert("Nevera creada",
                   isPresented: Binding(get: { viewModel.state.uploadWarning != nil },
                                        set: { if !$0 { viewModel.clearUploadWarning() } }),
                   actions: { Button("Entendido") { viewModel.clearUploadWarning() } },
                   message: { Text(viewModel.state.uploadWarning ?? "") })
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
                // "Unirse con código" se movió al bottom sheet del FAB; el
                // menú "⋯" se queda solo con cerrar sesión (punto de entrada
                // único al flujo de unirse).
                Menu {
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
        Button(action: { activeSheet = .fabChooser }) {
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
    let onCreate: (String, Bool) -> Void
    @State private var name: String = ""
    /// Toggle "Guardar en mi cuenta". APAGADO por defecto (privacidad por
    /// defecto: la nevera nace LOCAL salvo activación explícita).
    @State private var guardarEnCuenta: Bool = false

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

                Section {
                    Toggle("Guardar en mi cuenta", isOn: $guardarEnCuenta)
                        .tint(Color.fridgeyMintDeep)
                } footer: {
                    Text("Podrás verla en todos tus dispositivos y, si quieres, invitar a más personas.")
                        .italic()
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
                        onCreate(name, guardarEnCuenta)
                        isPresented = false
                    }
                    .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }
}

// MARK: - FAB chooser sheet (crear / unirse)

/// Bottom sheet del FAB con dos opciones, paridad visual con el ModalBottomSheet
/// de Android: tarjeta custom (no `confirmationDialog` del sistema), cada fila
/// con icono en círculo mint + título + subtítulo atenuado en cursiva.
private struct FabChooserSheet: View {
    let onCrear: () -> Void
    let onUnirse: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            FabOptionRow(
                icon: "plus",
                title: "Crear nevera nueva",
                subtitle: "Empieza una nevera vacía",
                action: onCrear
            )
            FabOptionRow(
                icon: "person.badge.plus",
                title: "Unirse con un código",
                subtitle: "Únete a una nevera compartida",
                action: onUnirse
            )
            Spacer(minLength: 0)
        }
        .padding(.top, 18)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Color.fridgeyCream)
        .presentationDetents([.height(210)])
        .presentationDragIndicator(.visible)
    }
}

private struct FabOptionRow: View {
    let icon: String
    let title: String
    let subtitle: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 16) {
                Image(systemName: icon)
                    .font(.system(size: 18, weight: .medium))
                    .foregroundStyle(Color.fridgeyMintDarker)
                    .frame(width: 40, height: 40)
                    .background(Color.fridgeyMintSoft, in: Circle())
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.custom("Inter-Regular", size: 16).weight(.medium))
                        .foregroundStyle(Color.fridgeyInk)
                    Text(subtitle)
                        .font(.custom("Inter-Regular", size: 13))
                        .italic()
                        .foregroundStyle(Color.fridgeyInkMuted)
                }
                Spacer()
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 14)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
