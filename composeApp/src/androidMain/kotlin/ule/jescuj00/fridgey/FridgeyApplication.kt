package ule.jescuj00.fridgey

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import ule.jescuj00.fridgey.di.androidModule
import ule.jescuj00.fridgey.di.authBridgeModule
import ule.jescuj00.fridgey.di.sharedModules
import ule.jescuj00.fridgey.di.viewModelModule

class FridgeyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // The google-services Gradle plugin adds a ContentProvider that
        // already initializes Firebase before onCreate runs; this call is
        // idempotent and gives us a deterministic log when something is
        // misconfigured.
        FirebaseApp.initializeApp(this)
        Log.d("Fridgey", "Firebase initialized: ${FirebaseApp.getInstance().name}")

        startKoin {
            androidLogger()
            androidContext(this@FridgeyApplication)
            modules(sharedModules() + androidModule() + authBridgeModule() + viewModelModule())
        }
    }
}
