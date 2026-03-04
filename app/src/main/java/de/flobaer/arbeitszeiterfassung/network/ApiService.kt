package de.flobaer.arbeitszeiterfassung.network

import de.flobaer.arbeitszeiterfassung.network.model.ApiLocation
import de.flobaer.arbeitszeiterfassung.network.model.AuthResponse
import de.flobaer.arbeitszeiterfassung.network.model.HealthStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class RefreshRequest(
    @SerialName("refresh_token")
    val refreshToken: String
)

interface ApiService {

    @POST("login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("refresh")
    suspend fun refresh(@Body request: RefreshRequest): Response<AuthResponse>

    @POST("logout")
    suspend fun logout(@Body request: RefreshRequest): Response<Unit>

    @GET("locations")
    suspend fun getLocations(): List<ApiLocation>

    @GET("health")
    suspend fun getHealth(): Response<HealthStatus>
}