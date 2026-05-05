package ule.jescuj00.fridgey.ui.navigation

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object NeveraList : Screen("nevera_list")
    data object CreateNevera : Screen("create_nevera")

    data object NeveraDetail : Screen("nevera_detail/{neveraId}") {
        const val ARG_NEVERA_ID = "neveraId"
        fun createRoute(neveraId: String) = "nevera_detail/$neveraId"
    }

    data object AddProducto : Screen("add_producto/{neveraId}") {
        const val ARG_NEVERA_ID = "neveraId"
        fun createRoute(neveraId: String) = "add_producto/$neveraId"
    }
}
