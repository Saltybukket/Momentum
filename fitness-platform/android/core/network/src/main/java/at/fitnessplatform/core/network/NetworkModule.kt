package at.fitnessplatform.core.network

import android.content.Context
import at.fitnessplatform.core.model.JavaUuidProvider
import at.fitnessplatform.core.model.SystemClock
import at.fitnessplatform.core.model.UuidProvider
import at.fitnessplatform.core.model.Clock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Provides @Singleton
    fun provideOkHttp(@ApplicationContext context: Context): OkHttpClient = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "http"), 10L * 1024L * 1024L))
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    @Provides @Singleton
    fun provideApi(client: OkHttpClient, json: Json): FitnessApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(FitnessApi::class.java)

    @Provides @Singleton fun provideClock(): Clock = SystemClock()
    @Provides @Singleton fun provideUuidProvider(): UuidProvider = JavaUuidProvider()
}
