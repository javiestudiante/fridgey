package ule.jescuj00.fridgey.di

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.dsl.module
import ule.jescuj00.fridgey.data.remote.OpenFoodFactsApi
import ule.jescuj00.fridgey.data.repository.ProductLookupRepository

/**
 * Wires the Ktor [HttpClient], the Open Food Facts API client and the lookup
 * repository. The HttpClient is engine-agnostic here — Ktor resolves the
 * platform engine (OkHttp on Android, Darwin on iOS) from the classpath.
 */
val networkModule: Module = module {
    single {
        HttpClient {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true   // OFF returns far more than we model
                        isLenient = true
                    }
                )
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 10_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 10_000
            }
        }
    }
    single { OpenFoodFactsApi(get()) }
    single { ProductLookupRepository(get(), get()) }
}
