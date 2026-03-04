package de.flobaer.arbeitszeiterfassung.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.flobaer.arbeitszeiterfassung.BuildConfig
import de.flobaer.arbeitszeiterfassung.data.WorkTimeRepository
import de.flobaer.arbeitszeiterfassung.data.local.AppDatabase
import de.flobaer.arbeitszeiterfassung.data.local.LocationDao
import de.flobaer.arbeitszeiterfassung.data.local.PauseDao
import de.flobaer.arbeitszeiterfassung.data.local.WorkTimeDao
import de.flobaer.arbeitszeiterfassung.dataStore
import de.flobaer.arbeitszeiterfassung.network.ApiService
import de.flobaer.arbeitszeiterfassung.network.AuthAuthenticator
import de.flobaer.arbeitszeiterfassung.network.AuthInterceptor
import de.flobaer.arbeitszeiterfassung.network.LocationsRepository
import de.flobaer.arbeitszeiterfassung.network.TokenManager
import de.flobaer.arbeitszeiterfassung.network.UrlInterceptor
import de.flobaer.arbeitszeiterfassung.network.WorkTimeApiService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.net.InetAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.dataStore

    @Provides
    @Singleton
    fun provideTokenManager(@ApplicationContext context: Context): TokenManager = TokenManager(context)

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
    }

    @Provides
    @Singleton
    fun provideDns(dataStore: DataStore<Preferences>): Dns = if (BuildConfig.DEBUG) {
        object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val baseUrlString = runBlocking {
                    dataStore.data.first()[de.flobaer.arbeitszeiterfassung.PrefKeys.API_URL]
                }
                val currentHost = if (!baseUrlString.isNullOrBlank()) {
                    baseUrlString.toHttpUrlOrNull()?.host
                } else {
                    BuildConfig.BASE_URL.toHttpUrlOrNull()?.host
                }

                return if (hostname == currentHost && hostname == "timer.api") {
                    // Falls im Emulator/lokal DNS Probleme bestehen, hier eine bekannte IP nutzen
                    // Der User nutzt MAMP lokal auf dem Mac, im Android Emulator ist der Host via 10.0.2.2 erreichbar
                    listOf(InetAddress.getByName("10.0.2.2"))
                } else {
                    Dns.SYSTEM.lookup(hostname)
                }
            }
        }
    } else {
        Dns.SYSTEM
    }

    @Provides
    @Singleton
    fun provideBaseOkHttpClient(
        logging: HttpLoggingInterceptor,
        dns: Dns,
        dataStore: DataStore<Preferences>
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(UrlInterceptor(dataStore))
            .dns(dns)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            try {
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })

                val sslContext = SSLContext.getInstance("SSL")
                sslContext.init(null, trustAllCerts, SecureRandom())
                builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                builder.hostnameVerifier { _, _ -> true }
            } catch (e: Exception) {
                // Ignore
            }
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(tokenManager: TokenManager): AuthInterceptor = AuthInterceptor(tokenManager)

    @Provides
    @Singleton
    fun provideApiService(baseClient: OkHttpClient, tokenManager: TokenManager): ApiService {
        val client = baseClient.newBuilder()
            .addInterceptor(AuthInterceptor(tokenManager))
            .authenticator(AuthAuthenticator(tokenManager, createRetrofit(baseClient).create(ApiService::class.java)))
            .build()
        return createRetrofit(client).create(ApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideWorkTimeApiService(baseClient: OkHttpClient, tokenManager: TokenManager): WorkTimeApiService {
        val client = baseClient.newBuilder()
            .addInterceptor(AuthInterceptor(tokenManager))
            .authenticator(AuthAuthenticator(tokenManager, createRetrofit(baseClient).create(ApiService::class.java)))
            .build()
        return createRetrofit(client).create(WorkTimeApiService::class.java)
    }

    private fun createRetrofit(client: OkHttpClient): Retrofit {
        val json = Json { ignoreUnknownKeys = true }
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase = AppDatabase.getDatabase(context)

    @Provides
    fun provideLocationDao(db: AppDatabase): LocationDao = db.locationDao()

    @Provides
    fun provideWorkTimeDao(db: AppDatabase): WorkTimeDao = db.workTimeDao()

    @Provides
    fun providePauseDao(db: AppDatabase): PauseDao = db.pauseDao()

    @Provides
    @Singleton
    fun provideLocationsRepository(
        locationDao: LocationDao,
        apiService: ApiService,
        dataStore: DataStore<Preferences>
    ): LocationsRepository = LocationsRepository(locationDao, apiService, dataStore)

    @Provides
    @Singleton
    fun provideWorkTimeRepository(
        @ApplicationContext context: Context,
        workTimeDao: WorkTimeDao,
        pauseDao: PauseDao,
        workTimeApiService: WorkTimeApiService
    ): WorkTimeRepository = WorkTimeRepository(context, workTimeDao, pauseDao, workTimeApiService)
}