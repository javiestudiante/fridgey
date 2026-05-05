package ule.jescuj00.fridgey.di

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import ule.jescuj00.fridgey.R

/**
 * Bridges values that live in the application's resources (composeApp) into
 * the shared Koin graph.
 *
 * `default_web_client_id` is generated at build time by the google-services
 * plugin from `composeApp/google-services.json` (the Web OAuth client of
 * type 3). It cannot be referenced from the shared module's R class
 * because the json lives here, in composeApp.
 */
fun authBridgeModule(): Module = module {
    single(qualifier = named(GOOGLE_WEB_CLIENT_ID_QUALIFIER)) {
        androidContext().getString(R.string.default_web_client_id)
    }
}
