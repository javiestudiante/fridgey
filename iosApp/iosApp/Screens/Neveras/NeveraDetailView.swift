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
    /// Preferencia "modo de añadido por defecto" del botón "+". Se lee al entrar;
    /// cambiarla requiere ir a Ajustes (fuera de esta nevera), así que al reabrir
    /// la nevera el `.task` la refresca solo.
    @State private var modoAnadir: ModoAnadirProducto = .manual
    @State private var pendingDelete: Producto?
    @State private var selectedCategory: Categoria?
    @State private var showCompartir = false
    @State private var navInvitar = false
    @State private var showMiembros = false
    @State private var showConfirmarBorrado = false

    init(neveraId: String, currentUserId: String) {
        self.neveraId = neveraId
        self.currentUserId = currentUserId
        _viewModel = StateObject(
            wrappedValue: NeveraDetailViewModel(neveraId: neveraId, currentUserId: currentUserId)
        )
    }

    /// Onboarding de "nevera vacía" SOLO con la búsqueda en blanco: con query
    /// vacía, productos == la nevera completa, así que isEmpty es "vacía" de
    /// verdad. Buscando, una lista vacía es "sin resultados" → va a `content`
    /// (que mantiene el campo de búsqueda visible para poder borrar la consulta).
    private var isEmpty: Bool {
        !viewModel.state.isLoading && viewModel.state.error == nil
            && viewModel.state.productos.isEmpty && viewModel.state.query.isEmpty
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
        .task {
            modoAnadir = (try? await KoinIosKt.getPreferenciasRepository().modoAnadirProducto()) ?? .manual
        }
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
        .overlay { confirmarBorradoOverlay }
        .sheet(isPresented: $showMiembros, onDismiss: { viewModel.limpiarErrorMiembros() }) {
            MiembrosSheet(
                viewModel: viewModel,
                currentUserId: currentUserId,
                onSalir: {
                    showMiembros = false
                    showConfirmarBorrado = true
                }
            )
            .presentationDetents([.medium, .large])
        }
        // Borrado o salida completados: la nevera ya no existe en este
        // dispositivo → volver a "Mis neveras".
        .onChange(of: viewModel.state.neveraCerrada) { _, cerrada in
            if cerrada {
                showConfirmarBorrado = false
                showMiembros = false
                dismiss()
            }
        }
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

    /// Diálogo de opciones de la nevera.
    ///
    /// GATING ESTRICTO por rol DENTRO del diálogo (no solo en si se muestra):
    /// el COLABORADOR tiene su rama propia ([dialogColaborador]) con SOLO info
    /// + "Ver miembros" + "Salir de la nevera" — nunca acciones del dueño. El
    /// PROPIETARIO mantiene el `switch` EXHAUSTIVO sobre [ModoNeveraUI] y,
    /// dentro de SYNCED, según el derivado `tieneColaboradores`. Permanece
    /// abierto durante las transiciones (spinner en línea) y se re-renderiza
    /// al cambiar el estado — igual que el AlertDialog de Android.
    @ViewBuilder
    private var compartirDialogOverlay: some View {
        if showCompartir {
            ZStack {
                Color.black.opacity(0.25).ignoresSafeArea()
                    .onTapGesture { dismissCompartir() }

                VStack(alignment: .leading, spacing: 0) {
                    if !viewModel.state.esPropietario {
                        dialogColaborador
                    } else {
                        switch viewModel.state.modo {
                        case .local:
                            dialogLocal
                        case .synced:
                            dialogSynced
                        }
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

    /// COLABORADOR → SOLO info + "Ver miembros" + "Salir de la nevera".
    /// Rama separada a propósito (no un `if` dentro del diálogo del dueño):
    /// estructuralmente no puede mostrar invitar / dejar de compartir /
    /// quitar de cuenta / borrar.
    private var dialogColaborador: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Nevera compartida")
                .font(.custom("Inter-Regular", size: 18).weight(.semibold))
                .foregroundStyle(Color.fridgeyInk)
            Spacer().frame(height: 12)
            Text("Esta nevera es de otra persona y se sincroniza contigo en la nube.")
                .font(.custom("Inter-Regular", size: 14))
                .foregroundStyle(Color.fridgeyInkSoft)

            errorCompartirText

            Spacer().frame(height: 14)
            dialogActionRow("Ver miembros", color: .fridgeyInk) {
                showCompartir = false
                showMiembros = true
            }
            dialogActionRow("Salir de la nevera", color: .fridgeyRust) {
                showCompartir = false
                showConfirmarBorrado = true
            }
            HStack {
                Spacer()
                dialogTextButton("Cancelar", color: .fridgeyInkSoft) { dismissCompartir() }
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

            Spacer().frame(height: 10)
            dialogActionRow("Borrar nevera", color: .fridgeyRust) {
                showCompartir = false
                showConfirmarBorrado = true
            }
            .disabled(viewModel.state.guardando)

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
            // "Ver miembros" solo con colaboradores: es la entrada a la hoja
            // cuando los avatares no se ven (nevera vacía).
            if viewModel.state.tieneColaboradores {
                dialogActionRow("Ver miembros", color: .fridgeyInk) {
                    showCompartir = false
                    showMiembros = true
                }
                .disabled(viewModel.state.dejandoDeCompartir || viewModel.state.quitando)
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
            dialogActionRow("Borrar nevera", color: .fridgeyRust) {
                showCompartir = false
                showConfirmarBorrado = true
            }
            .disabled(viewModel.state.dejandoDeCompartir || viewModel.state.quitando)
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

    // MARK: - Confirmación de borrar / salir (aviso dinámico, 4 casos)

    private func dismissConfirmarBorrado() {
        showConfirmarBorrado = false
        viewModel.limpiarErrorBorrado()
    }

    private var confirmacionTitulo: String {
        viewModel.state.esPropietario ? "Borrar nevera" : "Salir de la nevera"
    }

    private var confirmacionAccion: String {
        viewModel.state.esPropietario ? "Borrar" : "Salir"
    }

    /// Texto del aviso, armado con un `switch` EXHAUSTIVO sobre [ModoNeveraUI]
    /// + `tieneColaboradores` (mismos literales que Android):
    ///  - C1 dueño LOCAL → aviso mínimo.
    ///  - C2 dueño SYNCED sin colaboradores → + nube/otros dispositivos.
    ///  - C3 dueño SYNCED con colaboradores → + las N personas afectadas
    ///    (plural dinámico).
    ///  - C4 colaborador → salir, no borra nada para los demás.
    private var confirmacionMensaje: String {
        let nombre = viewModel.state.neveraNombre.isEmpty
            ? "esta nevera" : viewModel.state.neveraNombre
        guard viewModel.state.esPropietario else {
            return "Dejarás de ver \"\(nombre)\" y sus productos en este "
                + "dispositivo. La nevera seguirá disponible para las demás "
                + "personas; solo dejarás de verla tú."
        }
        let base = "¿Seguro que quieres borrar \"\(nombre)\"? Se eliminarán "
            + "todos sus productos. Esta acción no se puede deshacer."
        switch viewModel.state.modo {
        case .local:
            return base
        case .synced:
            if viewModel.state.tieneColaboradores {
                let n = viewModel.state.miembros
                    .filter { $0.id != viewModel.state.idPropietario }.count
                return base + (n == 1
                    ? " También se borrará para la persona que la comparte "
                        + "contigo: dejará de verla en sus dispositivos."
                    : " También se borrará para las \(n) personas que la "
                        + "comparten contigo: dejarán de verla en sus dispositivos.")
            } else {
                return base + " También se quitará de tu cuenta y dejará de "
                    + "verse en tus otros dispositivos."
            }
        }
    }

    /// Tarjeta custom (mismo lenguaje visual que el diálogo de compartir):
    /// permanece abierta durante la operación con spinner en línea y muestra
    /// el error recuperable dentro — igual que el AlertDialog de Android.
    @ViewBuilder
    private var confirmarBorradoOverlay: some View {
        if showConfirmarBorrado {
            ZStack {
                Color.black.opacity(0.25).ignoresSafeArea()
                    .onTapGesture { dismissConfirmarBorrado() }

                VStack(alignment: .leading, spacing: 0) {
                    Text(confirmacionTitulo)
                        .font(.custom("Inter-Regular", size: 18).weight(.semibold))
                        .foregroundStyle(Color.fridgeyInk)
                    Spacer().frame(height: 12)
                    Text(confirmacionMensaje)
                        .font(.custom("Inter-Regular", size: 14))
                        .foregroundStyle(Color.fridgeyInkSoft)
                    if let error = viewModel.state.errorBorrado {
                        Spacer().frame(height: 10)
                        Text(error)
                            .font(.custom("Inter-Regular", size: 13))
                            .foregroundStyle(Color.fridgeyRust)
                    }
                    Spacer().frame(height: 18)
                    HStack(spacing: 8) {
                        Spacer()
                        dialogTextButton("Cancelar", color: .fridgeyInkSoft) {
                            dismissConfirmarBorrado()
                        }
                        dialogTextButton(confirmacionAccion, color: .fridgeyRust,
                                         loading: viewModel.state.borrandoOSaliendo) {
                            viewModel.borrarOSalir()
                        }
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
                searchField
                filterRail
                urgencySection("Caduca ya", .fridgeyRust, bad)
                urgencySection("Esta semana", .fridgeyAmber, warn)
                urgencySection("Más adelante", .fridgeyInk, fresh)
                // Sin coincidencias (búsqueda y/o filtro de categoría): mensaje en
                // vez de baldas vacías. NO es el onboarding de nevera vacía (ése se
                // filtra antes en `isEmpty`).
                if bad.isEmpty && warn.isEmpty && fresh.isEmpty {
                    Text("No hay productos que coincidan")
                        .font(.custom("Inter-Regular", size: 15))
                        .foregroundStyle(Color.fridgeyInkMuted)
                        .frame(maxWidth: .infinity)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 32)
                }
                Spacer().frame(height: 112)
            }
        }
    }

    // MARK: - Search field (siempre visible — paridad con Android)

    private var searchField: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 16))
                .foregroundStyle(Color.fridgeyInkMuted)
            TextField(
                "Buscar producto",
                text: Binding(
                    get: { viewModel.state.query },
                    set: { viewModel.onQueryChange($0) }
                ),
                prompt: Text("Buscar producto").foregroundColor(Color.fridgeyInkMuted)
            )
            .font(.custom("Inter-Regular", size: 16))
            .foregroundStyle(Color.fridgeyInk)
            .autocorrectionDisabled()
            .textInputAutocapitalization(.never)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(Color.fridgeySurfaceWhite, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.fridgeyHairline, lineWidth: 1))
        .padding(.horizontal, 16)
        .padding(.top, 4)
        .padding(.bottom, 4)
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
                // Tocable → hoja "Miembros" (con la nevera vacía, la entrada
                // es la acción "Ver miembros" del diálogo de opciones).
                Button(action: { showMiembros = true }) {
                    detailAvatars.padding(2)
                }
                .buttonStyle(.plain)
            }
            // Opciones de la nevera: el dueño gestiona compartición/borrado;
            // el colaborador puede ver miembros y salir.
            squareButton(systemName: "square.and.arrow.up") { showCompartir = true }
                .padding(.leading, 8)
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
        Button(action: {
            // Enruta según la preferencia: ESCANEAR abre AddProducto con el
            // escáner ya lanzado (mismo flujo que el "Escanear" del empty state);
            // MANUAL abre el formulario. Reutiliza el flujo existente, no lo duplica.
            if modoAnadir == .escanear {
                addSheetStartsScanning = true
            }
            showAddSheet = true
        }) {
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

// MARK: - Hoja "Miembros" (gestión de miembros)

/// Acción disponible en una fila de la hoja de miembros (espejo del enum
/// `AccionMiembro` de Android).
private enum AccionMiembro {
    case expulsar, salir, ninguna
}

/// Hoja "Miembros": lista de perfiles denormalizados (avatar + nombre) con
/// chips "Propietario" / "Tú".
///
/// GATING ESTRICTO por rol DENTRO de la lista: el dueño ve "Expulsar" en
/// cada colaborador (nunca en sí mismo); el colaborador ve "Salir" SOLO en
/// su propia fila y NUNCA acciones sobre otros. La expulsión pide una
/// confirmación ligera (destructiva y afecta a otra persona).
private struct MiembrosSheet: View {

    @ObservedObject var viewModel: NeveraDetailViewModel
    let currentUserId: String
    /// "Salir" desde la propia fila → cierra la hoja y abre la MISMA
    /// confirmación C4 del diálogo de opciones.
    let onSalir: () -> Void

    @State private var pendingExpulsar: Usuario?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text("Miembros")
                    .font(.custom("InstrumentSerif-Regular", size: 26))
                    .foregroundStyle(Color.fridgeyInk)
                Spacer().frame(height: 2)
                Text("Las personas con acceso a esta nevera.")
                    .font(.custom("Inter-Regular", size: 13))
                    .foregroundStyle(Color.fridgeyInkMuted)
                Spacer().frame(height: 12)
                ForEach(Array(viewModel.state.miembros.enumerated()), id: \.element.id) { idx, miembro in
                    miembroRow(miembro, index: idx)
                }
                if let error = viewModel.state.errorMiembros {
                    Spacer().frame(height: 6)
                    Text(error)
                        .font(.custom("Inter-Regular", size: 13))
                        .foregroundStyle(Color.fridgeyRust)
                }
                Spacer().frame(height: 24)
            }
            .padding(.horizontal, 22)
            .padding(.top, 24)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(Color.fridgeyCream)
        // Confirmación de expulsión (dueño): destructiva y afecta a otra persona.
        .alert(tituloExpulsar,
               isPresented: Binding(get: { pendingExpulsar != nil },
                                    set: { if !$0 { pendingExpulsar = nil } }),
               presenting: pendingExpulsar) { usuario in
            Button("Expulsar", role: .destructive) {
                viewModel.expulsarColaborador(usuario.id)
                pendingExpulsar = nil
            }
            Button("Cancelar", role: .cancel) { pendingExpulsar = nil }
        } message: { _ in
            Text("Dejará de ver esta nevera y sus productos en sus dispositivos. "
                 + "Podrá volver si la invitas de nuevo.")
        }
    }

    private var tituloExpulsar: String {
        let nombre = pendingExpulsar?.nombre ?? ""
        return "Expulsar a \(nombre.isEmpty ? "esta persona" : nombre)"
    }

    /// Mismo `when` de gating que Android, como función pura sobre el estado.
    private func accion(para miembro: Usuario) -> AccionMiembro {
        if viewModel.state.esPropietario && miembro.id != viewModel.state.idPropietario {
            // Dueño → expulsa a cualquier colaborador, no a sí mismo.
            return .expulsar
        }
        if !viewModel.state.esPropietario && miembro.id == currentUserId {
            // Colaborador → sale él mismo; nada sobre los demás.
            return .salir
        }
        return .ninguna
    }

    private func miembroRow(_ miembro: Usuario, index: Int) -> some View {
        let palette: [Color] = [.fridgeyMint, .fridgeyMintDeep, .fridgeyAmber]
        let esDueno = miembro.id == viewModel.state.idPropietario
        let esYo = miembro.id == currentUserId
        let expulsando = viewModel.state.expulsandoUid == miembro.id

        return HStack(spacing: 12) {
            Text(inicial(miembro.nombre))
                .font(.custom("Inter-Regular", size: 14).weight(.semibold))
                .foregroundStyle(Color.fridgeySurfaceWhite)
                .frame(width: 36, height: 36)
                .background(palette[index % palette.count], in: Circle())
            VStack(alignment: .leading, spacing: 3) {
                Text(miembro.nombre.isEmpty ? "Sin nombre" : miembro.nombre)
                    .font(.custom("Inter-Regular", size: 15).weight(.medium))
                    .foregroundStyle(Color.fridgeyInk)
                if esDueno || esYo {
                    HStack(spacing: 6) {
                        if esDueno { chip("Propietario") }
                        if esYo { chip("Tú") }
                    }
                }
            }
            Spacer()
            switch accion(para: miembro) {
            case .expulsar:
                Button(action: { pendingExpulsar = miembro }) {
                    HStack(spacing: 6) {
                        if expulsando {
                            ProgressView().tint(Color.fridgeyRust).controlSize(.small)
                        }
                        Text("Expulsar")
                            .font(.custom("Inter-Regular", size: 13).weight(.semibold))
                            .foregroundStyle(Color.fridgeyRust)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .disabled(expulsando)

            case .salir:
                Button(action: onSalir) {
                    Text("Salir")
                        .font(.custom("Inter-Regular", size: 13).weight(.semibold))
                        .foregroundStyle(Color.fridgeyRust)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)

            case .ninguna:
                EmptyView()
            }
        }
        .padding(.vertical, 8)
    }

    private func chip(_ label: String) -> some View {
        Text(label)
            .font(.custom("Inter-Regular", size: 11).weight(.medium))
            .foregroundStyle(Color.fridgeyInkSoft)
            .padding(.horizontal, 7)
            .padding(.vertical, 2)
            .background(Color.fridgeyMintTint, in: RoundedRectangle(cornerRadius: 8))
            .overlay(RoundedRectangle(cornerRadius: 8)
                .stroke(Color.fridgeyHairline, lineWidth: 1))
    }

    private func inicial(_ nombre: String) -> String {
        String(nombre.trimmingCharacters(in: .whitespaces).prefix(1)).uppercased()
    }
}
