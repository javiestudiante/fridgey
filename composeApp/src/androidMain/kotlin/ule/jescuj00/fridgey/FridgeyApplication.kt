package ule.jescuj00.fridgey

import android.app.Application
import android.util.Log
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import ule.jescuj00.fridgey.data.repository.NeveraRepository
import ule.jescuj00.fridgey.di.androidModule
import ule.jescuj00.fridgey.di.sharedModules
import ule.jescuj00.fridgey.di.viewModelModule

class FridgeyApplication : Application(), KoinComponent {

    private val neveraRepository: NeveraRepository by inject()

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@FridgeyApplication)
            modules(sharedModules() + androidModule() + viewModelModule())
        }

        // --- Temporary verification (remove once UI work begins) ---
        Log.d("Fridgey", "Koin wired: ${neveraRepository::class.simpleName}")
    }
}
