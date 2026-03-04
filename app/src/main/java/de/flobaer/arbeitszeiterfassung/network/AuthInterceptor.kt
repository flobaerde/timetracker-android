package de.flobaer.arbeitszeiterfassung.network

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import de.flobaer.arbeitszeiterfassung.BuildConfig
import de.flobaer.arbeitszeiterfassung.PrefKeys
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class UrlInterceptor(
    private val dataStore: DataStore<Preferences>
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        
        val baseUrlString = runBlocking {
            dataStore.data.first()[PrefKeys.API_URL]
        }

        if (!baseUrlString.isNullOrBlank()) {
            val newUrl = baseUrlString.toHttpUrlOrNull()
            if (newUrl != null) {
                val oldUrl = request.url
                val newFullUrlBuilder = oldUrl.newBuilder()
                    .scheme(newUrl.scheme)
                    .host(newUrl.host)
                    .port(newUrl.port)
                
                // Wir löschen den alten Pfad und bauen ihn neu, basierend auf der neuen Basis-URL + dem Pfad-Rest des Requests
                val newBasePathSegments = newUrl.encodedPathSegments.filter { it.isNotEmpty() }
                
                // Die Pfadsegmente des aktuellen Requests (oldUrl), die NACH der ursprünglichen Basis-URL kommen.
                // Da Retrofit die Request-URLs relativ zur ursprünglichen Basis-URL baut,
                // müssen wir herausfinden, welcher Teil von oldUrl zum ursprünglichen Pfad gehört.
                val originalBaseUrl = BuildConfig.BASE_URL.toHttpUrlOrNull()
                val originalBasePathSegments = originalBaseUrl?.encodedPathSegments?.filter { it.isNotEmpty() } ?: emptyList()
                
                val oldPathSegments = oldUrl.encodedPathSegments.filter { it.isNotEmpty() }
                
                // Wir nehmen an, dass oldPathSegments mit originalBasePathSegments beginnt.
                // Der eigentliche Request-Pfad (z.B. "login") ist das, was danach kommt.
                val relativePathSegments = if (oldPathSegments.size >= originalBasePathSegments.size) {
                    oldPathSegments.subList(originalBasePathSegments.size, oldPathSegments.size)
                } else {
                    oldPathSegments
                }
                
                newFullUrlBuilder.encodedPath("/")
                for (segment in newBasePathSegments) {
                    newFullUrlBuilder.addEncodedPathSegment(segment)
                }
                for (segment in relativePathSegments) {
                    newFullUrlBuilder.addEncodedPathSegment(segment)
                }

                val newFullUrl = newFullUrlBuilder.build()
                
                request = request.newBuilder()
                    .url(newFullUrl)
                    .build()
            }
        }
        
        return chain.proceed(request)
    }
}

class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val accessToken = tokenManager.getAccessToken()

        val requestBuilder = request.newBuilder()
        if (accessToken != null) {
            requestBuilder.header("Authorization", "Bearer $accessToken")
        }

        return chain.proceed(requestBuilder.build())
    }
}

class AuthAuthenticator(
    private val tokenManager: TokenManager,
    private val apiService: ApiService
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Stop retrying after 2 attempts to avoid infinite loops
        if (responseCount(response) >= 2) {
            Log.e("AuthAuthenticator", "Stopping after 2 retry attempts")
            return null
        }

        synchronized(this) {
            val currentToken = tokenManager.getAccessToken()
            val requestToken = response.request.header("Authorization")?.removePrefix("Bearer ")

            // If the token was already refreshed by another thread, retry with the new token
            if (currentToken != null && currentToken != requestToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
            }

            val refreshToken = tokenManager.getRefreshToken() ?: run {
                Log.e("AuthAuthenticator", "No refresh token available")
                return null
            }

            Log.d("AuthAuthenticator", "Attempting to refresh token...")
            val tokenResponse = runBlocking {
                try {
                    apiService.refresh(RefreshRequest(refreshToken))
                } catch (e: Exception) {
                    Log.e("AuthAuthenticator", "Refresh call failed", e)
                    null
                }
            }

            if (tokenResponse?.isSuccessful == true && tokenResponse.body() != null) {
                val newTokens = tokenResponse.body()!!
                Log.d("AuthAuthenticator", "Token refresh successful")
                tokenManager.saveTokens(newTokens.accessToken, newTokens.refreshToken)

                return response.request.newBuilder()
                    .header("Authorization", "Bearer ${newTokens.accessToken}")
                    .build()
            } else {
                Log.e("AuthAuthenticator", "Token refresh failed: ${tokenResponse?.code()} ${tokenResponse?.message()}")
                
                // If the refresh token is invalid (401 or 403), we should clear the tokens
                // to force the user to log in again.
                val code = tokenResponse?.code()
                if (code == 401 || code == 400 || code == 403) {
                    Log.w("AuthAuthenticator", "Refresh token invalid, clearing tokens")
                    tokenManager.clearTokens()
                }
                return null
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var r: Response? = response.priorResponse
        while (r != null) {
            result++
            r = r.priorResponse
        }
        return result
    }
}