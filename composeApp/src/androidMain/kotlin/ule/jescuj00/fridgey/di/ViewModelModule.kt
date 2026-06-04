package ule.jescuj00.fridgey.di

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import ule.jescuj00.fridgey.ui.scanner.DateScannerViewModel
import ule.jescuj00.fridgey.ui.screens.add_producto.AddProductoViewModel
import ule.jescuj00.fridgey.ui.screens.create_nevera.CreateNeveraViewModel
import ule.jescuj00.fridgey.ui.screens.login.LoginViewModel
import ule.jescuj00.fridgey.ui.screens.nevera_detail.NeveraDetailViewModel
import ule.jescuj00.fridgey.ui.screens.nevera_list.NeveraListViewModel

/**
 * Compose ViewModels — registered with Koin so screens can resolve them
 * via [org.koin.androidx.compose.koinViewModel].
 */
fun viewModelModule(): Module = module {
    viewModel { NeveraListViewModel(get()) }
    viewModel { CreateNeveraViewModel(get()) }
    viewModel { NeveraDetailViewModel(get(), get()) }
    viewModel { AddProductoViewModel(get()) }
    viewModel { LoginViewModel(get()) }
    // get() resolves ScanExpirationDateUseCase, BarcodeScanner, LookupProductByBarcodeUseCase
    viewModel { DateScannerViewModel(get(), get(), get()) }
}
