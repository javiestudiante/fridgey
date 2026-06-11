import SwiftUI
import Shared

struct NeveraDetailView: View {

    @StateObject private var viewModel: NeveraDetailViewModel
    let neveraId: String
    let currentUserId: String
    @Environment(\.dismiss) private var dismiss

    @State private var showAddSheet = false
    /// El "Escanear" del empty state abre AddProducto con el escáner ya
    /// lanzado; "Añadir a mano" y el FAB abren el formulario normal.
    @State private var addSheetStartsScanning = false
    @State private var pendingDelete: Producto?
    @State private var selectedCategory: Categoria?
    @State private var showCompartir = false
    @State private var navInvitar = false

    init(neveraId: String, currentUserId: String) {
        self.neveraId = neveraId
        self.currentUserId = currentUserId
        _viewModel = StateObject(
            wrappedValue: NeveraDetailViewModel(neveraId: neveraId, currentUserId: currentUserId)
        )
    }

    private var isEmpty: Bool {
        !viewModel.state.isLoading && viewModel.state.error == nil && viewModel.state.productos.isEmpty
    }

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            (isEmpty ? Color.fridgeyCream : Color.fridgeySmoke).ignoresSafeArea()

            if viewModel.state.isLoading {
                ProgressView().tint(Color.fridgeyMintDeep)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if isEmpty {
                VStack(spacing: 0) {
                    header(emptyVariant: true)
                    emptyDetail
                }
            } else {
                content
            }

            if !viewModel.state.isLoading {
                fab.padding(.trailing, 22).padding(.bottom, 28)
            }
        }
        .navigationBarHidden(true)
        // La barra oculta desactiva el swipe-back nativo; lo reactivamos sin
        // quitar la flecha custom (ambas vías de volver conviven).
        .enableSwipeBack()
        .sheet(isPresented: $showAddSheet, onDismiss: { addSheetStartsScanning = false }) {
            AddProductoView(
                neveraId: neveraId,
                onCompleted: { showAddSheet = false },
                startScanning: addSheetStartsScanning
            )
        }
        .alert("Eliminar producto",
               isPresented: Binding(get: { pendingDelete != nil },
                                    set: { if !$0 { pendingDelete = nil } }),
               presenting: pendingDelete) { producto in
            Button("Eliminar", role: .destructive) {
                viewModel.deleteProducto(producto)
                pendingDelete = nil
            }
            Button("Cancelar", role: .cancel) { pendingDelete = nil }
        } message: { producto in
            Text("¿Seguro que quieres eliminar “\(producto.nombre)”?")
        }
        .alert("Error",
               isPresented: Binding(get: { viewModel.state.error != nil },
                                    set: { if !$0 { viewModel.clearError() } }),
               actions: { Button("OK") { viewModel.clearError() } },
               message: { Text(viewModel.state.error ?? "") })
        // --- Nube + colaboración (paridad con el diálogo Android) ---
        .overlay { compartirDialogOverlay }
        .navigationDestination(isPresented: $navInvitar) {
            InvitarView(neveraId: neveraId, currentUserId: currentUserId)
        }
        .onAppear { viewModel.start() }
        .onDisappear { viewModel.stop() }
    }

    // MARK: - Diálogo de compartición (tarjeta custom, paridad con Android)

    private func dismissCompartir() {
        showCompartir = false
        viewModel.limpiarErrorCompartir()
    }

    /// Diálogo del propietario para gestionar los dos ejes. El contenido se
    /// decide con un `switch` EXHAUSTIVO sobre [ModoNeveraUI] y, dentro de
    /// SYNCED, según el derivado `tieneColaboradores`. Permanece abierto
    /// durante las transiciones (spinner en línea) y se re-renderiza al
    /// cambiar el estado — igual que el AlertDialog de Android.
    @ViewBuilder
    private var compartirDialogOverlay: some View {
        if showCompartir {
            ZStack {
                Color.black.opacity(0.25).ignoresSafeArea()
                    .onTapGesture { dismissCompartir() }

                VStack(alignment: .leading, spacing: 0) {
                    switch viewModel.state.modo {
                    case .local:
                        dialogLocal
                    case .synced:
                        dialogSynced
                    }
                }
                .padding(22)
                .background(Color.fridgeySurfaceWhite,
                            in: RoundedRectangle(cornerRadius: 20))
                .overlay(RoundedRectangle(cornerRadius: 20)
                    .stroke(Color.fridgeyHairline, lineWidth: 1))
                .padding(.horizontal, 36)
            }
        }
    }

    /// LOCAL → confirmación de "Guardar en mi cuenta" con el cuerpo exacto y el
    /// apunte de invitar ATENUADO (cursiva + color suave), igual de secundario
    /// que en Android.
    private var dialogLocal: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Guardar en mi cuenta")
                .font(.custom("Inter-Regular", size: 18).weight(.semibold))
                .foregroundStyle(Color.fridgeyInk)
            Spacer().frame(height: 12)
            Text("Tu nevera quedará guardada de forma segura y podrás verla "
                 + "desde cualquier móvil o tablet donde inicies sesión.")
                .font(.custom("Inter-Regular", size: 14))
                .foregroundStyle(Color.fridgeyInkSoft)
            Spacer().frame(height: 10)
            Text("Y si quieres, después podrás invitar a más personas a esta nevera.")
                .font(.custom("Inter-Regular", size: 13))
                .italic()
                .foregroundStyle(Color.fridgeyInkMuted)

            errorCompartirText

            Spacer().frame(height: 18)
            HStack(spacing: 8) {
                Spacer()
                dialogTextButton("Cancelar", color: .fridgeyInkSoft) { dismissCompartir() }
                dialogTextButton("Guardar en mi cuenta", color: .fridgeyMintDeep,
                                 loading: viewModel.state.guardando) {
                    viewModel.guardarEnMiCuenta()
                }
            }
        }
    }

    /// SYNCED → menú de acciones: invitar + (dejar de compartir si hay
    /// colaboradores) + quitar de mi cuenta.
    private var dialogSynced: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Compartir nevera")
                .font(.custom("Inter-Regular", size: 18).weight(.semibold))
                .foregroundStyle(Color.fridgeyInk)
            Spacer().frame(height: 12)
            Text(viewModel.state.tieneColaboradores
                 ? "Esta nevera está en tu cuenta y compartida con otras personas. "
                   + "Se sincroniza en la nube entre todos sus miembros."
                 : "Esta nevera está guardada en tu cuenta: la verás en cualquier "
                   + "dispositivo donde inicies sesión. Invita a otras personas "
                   + "para compartirla.")
                .font(.custom("Inter-Regular", size: 14))
                .foregroundStyle(Color.fridgeyInkSoft)

            errorCompartirText

            Spacer().frame(height: 14)
            dialogActionRow("Invitar con código", color: .fridgeyMintDeep) {
                showCompartir = false
                navInvitar = true
            }
            // "Dejar de compartir" solo con colaboradores (eje derivado).
            if viewModel.state.tieneColaboradores {
                dialogActionRow("Dejar de compartir", color: .fridgeyInk,
                                loading: viewModel.state.dejandoDeCompartir) {
                    viewModel.dejarDeCompartir()
                }
            }
            dialogActionRow("Quitar de mi cuenta", color: .fridgeyRust,
                            loading: viewModel.state.quitando) {
                viewModel.quitarDeMiCuenta()
            }
            HStack {
                Spacer()
                dialogTextButton("Cancelar", color: .fridgeyInkSoft) { dismissCompartir() }
            }
        }
    }

    @ViewBuilder
    private var errorCompartirText: some View {
        if let error = viewModel.state.errorCompartir {
            Spacer().frame(height: 10)
            Text(error)
                .font(.custom("Inter-Regular", size: 13))
                .foregroundStyle(Color.fridgeyRust)
        }
    }

    /// Acción a fila completa (variante SYNCED), con spinner en línea.
    private func dialogActionRow(
        _ label: String,
        color: Color,
        loading: Bool = false,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if loading {
                    ProgressView().tint(color).controlSize(.small)
                }
                Text(label)
                    .font(.custom("Inter-Regular", size: 15).weight(.medium))
                    .foregroundStyle(color)
                Spacer()
            }
            .padding(.vertical, 10)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(loading)
    }

    /// Botón de texto compacto (Cancelar / confirmación), con spinner en línea.
    private func dialogTextButton(
        _ label: String,
        color: Color,
        loading: Bool = false,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if loading {
                    ProgressView().tint(color).controlSize(.small)
                }
                Text(label)
                    .font(.custom("Inter-Regular", size: 14).weight(.semibold))
                    .foregroundStyle(color)
            }
            .padding(.vertical, 6)
            .padding(.horizontal, 8)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(loading)
    }

    // MARK: - Content (3 urgency shelves)

    private var content: some View {
        let filtered: [Producto] = selectedCategory == nil
            ? viewModel.state.productos
            : viewModel.state.productos.filter { $0.categoria == selectedCategory }
        let bad = filtered.filter { expirationState(Int($0.diasRestantes)) == .bad }
            .sorted { $0.diasRestantes < $1.diasRestantes }
        let warn = filtered.filter { expirationState(Int($0.diasRestantes)) == .warn }
            .sorted { $0.diasRestantes < $1.diasRestantes }
        let fresh = filtered.filter { expirationState(Int($0.diasRestantes)) == .fresh }
            .sorted { $0.diasRestantes < $1.diasRestantes }

        return ScrollView {
            VStack(spacing: 0) {
                header(emptyVariant: false)
                filterRail
                urgencySection("Caduca ya", .fridgeyRust, bad)
                urgencySection("Esta semana", .fridgeyAmber, warn)
                urgencySection("Más adelante", .fridgeyInk, fresh)
                Spacer().frame(height: 112)
            }
        }
    }

    @ViewBuilder
    private func urgencySection(_ title: String, _ accent: Color, _ products: [Producto]) -> some View {
        if !products.isEmpty {
            SectionHeader(title: title, count: products.count, accentColor: accent)
            shelf(products)
        }
    }

    private func shelf(_ products: [Producto]) -> some View {
        VStack(spacing: 0) {
            // fr-shelf-top decorative strip.
            LinearGradient(
                colors: [Color(red: 0.412, green: 0.643, blue: 0.506).opacity(0.18), .clear],
                startPoint: .top, endPoint: .bottom
            )
            .frame(height: 6)

            ForEach(Array(products.enumerated()), id: \.element.id) { idx, producto in
                if idx > 0 {
                    Rectangle().fill(Color.fridgeyHairline).frame(height: 1)
                }
                ProductRow(
                    categoria: producto.categoria,
                    name: producto.nombre,
                    supporting: "\(producto.categoria.displayName) · \(cantidadLabel(producto.cantidad, producto.unidad))",
                    daysRemaining: Int(producto.diasRestantes)
                )
                .contentShape(Rectangle())
                .onLongPressGesture { pendingDelete = producto }
            }
        }
        .background(Color.fridgeySurfaceWhite)
        .clipShape(RoundedRectangle(cornerRadius: FridgeyRadius.shelf))
        .overlay(
            RoundedRectangle(cornerRadius: FridgeyRadius.shelf)
                .stroke(Color.fridgeyHairline, lineWidth: 1)
        )
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
    }

    // MARK: - Header

    private func header(emptyVariant: Bool) -> some View {
        HStack(alignment: .center, spacing: 12) {
            squareButton(systemName: "arrow.left") { dismiss() }
            VStack(alignment: .leading, spacing: 2) {
                EyebrowLabel(text: "NEVERA · \(viewModel.state.miembros.count) MIEMBROS")
                Text(viewModel.state.neveraNombre.isEmpty ? "Nevera" : viewModel.state.neveraNombre)
                    .font(.custom("InstrumentSerif-Regular", size: 30))
                    .foregroundStyle(Color.fridgeyInk)
            }
            Spacer()
            if !emptyVariant {
                detailAvatars
            }
            // Compartir (UC-03): solo el propietario gestiona la compartición.
            if viewModel.state.esPropietario {
                squareButton(systemName: "square.and.arrow.up") { showCompartir = true }
                    .padding(.leading, 8)
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
        .padding(.bottom, 12)
    }

    private func squareButton(systemName: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: 18))
                .foregroundStyle(Color.fridgeyInkSoft)
                .frame(width: 36, height: 36)
                .background(Color.fridgeyInk.opacity(0.04),
                            in: RoundedRectangle(cornerRadius: FridgeyRadius.backButton))
        }
        .buttonStyle(.plain)
    }

    private var detailAvatars: some View {
        let palette: [Color] = [.fridgeyMint, .fridgeyMintDeep, .fridgeyAmber]
        let shown = Array(viewModel.state.miembros.prefix(3).enumerated())
        return HStack(spacing: -6) {
            ForEach(shown, id: \.offset) { idx, m in
                avatar(text: initial(m.nombre), bg: palette[idx % palette.count])
            }
            if viewModel.state.miembros.count > 3 {
                avatar(text: "+\(viewModel.state.miembros.count - 3)", bg: .fridgeyAmber)
            }
        }
    }

    private func avatar(text: String, bg: Color) -> some View {
        Text(text)
            .font(.custom("Inter-Regular", size: 10).weight(.semibold))
            .foregroundStyle(Color.fridgeySurfaceWhite)
            .frame(width: 22, height: 22)
            .background(bg, in: Circle())
            .overlay(Circle().stroke(Color.fridgeySurfaceWhite, lineWidth: 2))
    }

    // MARK: - Filter rail

    private var filterRail: some View {
        let present = NeveraDetailView.allCategorias.filter { cat in
            viewModel.state.productos.contains { $0.categoria == cat }
        }
        return ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                FilterPill(label: "Todo", selected: selectedCategory == nil,
                           onClick: { selectedCategory = nil }, count: viewModel.state.productos.count)
                ForEach(present, id: \.self) { cat in
                    FilterPill(label: cat.displayName, selected: selectedCategory == cat,
                               onClick: { selectedCategory = cat })
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 4)
            .padding(.bottom, 8)
        }
    }

    private static let allCategorias: [Categoria] = (Categoria.entries as? [Categoria]) ?? []

    // MARK: - Empty state (Pantalla 3)

    private var emptyDetail: some View {
        VStack(spacing: 0) {
            Spacer()
            fridgeIllustration
            Spacer().frame(height: 24)
            Text("Tu nevera")
                .font(.custom("InstrumentSerif-Regular", size: 30))
                .foregroundStyle(Color.fridgeyInk)
            Text("está vacía")
                .font(.custom("InstrumentSerif-Italic", size: 30))
                .foregroundStyle(Color.fridgeyInk)
            Spacer().frame(height: 12)
            Text("Escanea el código de barras y la fecha de caducidad de tu primer producto para empezar.")
                .font(.custom("Inter-Regular", size: 14))
                .foregroundStyle(Color.fridgeyInkSoft)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 240)
            Spacer().frame(height: 24)
            HStack(spacing: 10) {
                Button(action: {
                    addSheetStartsScanning = true
                    showAddSheet = true
                }) {
                    Text("Escanear")
                        .font(.custom("Inter-Regular", size: 14).weight(.semibold))
                        .foregroundStyle(Color.fridgeySurfaceWhite)
                        .padding(.horizontal, 20).padding(.vertical, 14)
                        .background(Color.fridgeyMintDeep, in: RoundedRectangle(cornerRadius: 16))
                }.buttonStyle(.plain)
                Button(action: { showAddSheet = true }) {
                    Text("Añadir a mano")
                        .font(.custom("Inter-Regular", size: 14).weight(.semibold))
                        .foregroundStyle(Color.fridgeyInk)
                        .padding(.horizontal, 20).padding(.vertical, 14)
                        .background(Color.fridgeySurfaceWhite, in: RoundedRectangle(cornerRadius: 16))
                        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.fridgeyHairline, lineWidth: 1))
                }.buttonStyle(.plain)
            }
            Spacer().frame(height: 24)
            Text("💡 Tip: añade varios a la vez encadenando escaneos")
                .font(.custom("Inter-Regular", size: 12))
                .foregroundStyle(Color.fridgeyInkMuted)
            Spacer()
        }
        .padding(.horizontal, 32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    /// 132×168 empty-fridge illustration drawn with native shapes (no raster).
    private var fridgeIllustration: some View {
        ZStack(alignment: .top) {
            Canvas { ctx, size in
                let body = RoundedRectangle(cornerRadius: 16).path(in: CGRect(origin: .zero, size: size))
                ctx.fill(body, with: .linearGradient(
                    Gradient(colors: [Color.fridgeyMintTint, Color.fridgeySurfaceWhite]),
                    startPoint: CGPoint(x: size.width / 2, y: 0),
                    endPoint: CGPoint(x: size.width / 2, y: size.height)
                ))
                ctx.stroke(body, with: .color(Color.fridgeyMintSoft), lineWidth: 2)
                for f in [0.38, 0.70] {
                    var line = Path()
                    line.move(to: CGPoint(x: 8, y: size.height * f))
                    line.addLine(to: CGPoint(x: size.width - 8, y: size.height * f))
                    ctx.stroke(line, with: .color(Color.fridgeyMintSoft), lineWidth: 1.5)
                }
                let handle = RoundedRectangle(cornerRadius: 4).path(
                    in: CGRect(x: size.width - 14, y: size.height * 0.12, width: 4, height: 36)
                )
                ctx.fill(handle, with: .color(Color.fridgeyMintSoft))
            }
            Text("🌿").font(.system(size: 32)).opacity(0.45).padding(.top, 16)
        }
        .frame(width: 132, height: 168)
    }

    // MARK: - FAB

    // iOS detail FAB: extended mint-deep "Añadir" (radius 20), per the iOS design.
    private var fab: some View {
        Button(action: { showAddSheet = true }) {
            HStack(spacing: 8) {
                Image(systemName: "plus")
                Text("Añadir").font(.custom("Inter-Regular", size: 15).weight(.medium))
            }
            .foregroundStyle(Color.fridgeySurfaceWhite)
            .padding(.horizontal, 20).padding(.vertical, 16)
            .background(Color.fridgeyMintDeep, in: RoundedRectangle(cornerRadius: FridgeyRadius.lg))
            .shadow(color: Color.fridgeyMintDeep.opacity(0.35), radius: 15, x: 0, y: 14)
        }
        .buttonStyle(.plain)
    }

    // MARK: - helpers

    private func initial(_ nombre: String) -> String {
        String(nombre.trimmingCharacters(in: .whitespaces).prefix(1)).uppercased()
    }

    private func cantidadLabel(_ c: Double, _ u: UnidadMedida) -> String {
        "\(String(format: "%g", c)) \(u.simbolo)"
    }
}
