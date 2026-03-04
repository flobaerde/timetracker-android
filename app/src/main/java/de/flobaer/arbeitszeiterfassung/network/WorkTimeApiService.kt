package de.flobaer.arbeitszeiterfassung.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface WorkTimeApiService {
    @GET("worktimes")
    suspend fun getWorkTimes(@Query("since_hours") sinceHours: Int? = null): Response<List<ApiWorkTime>>

    @POST("worktimes")
    suspend fun startWorkTime(@Body request: StartWorkTimeRequest): Response<StartWorkTimeResponse>

    @PUT("worktimes/{id}")
    suspend fun stopWorkTime(@Path("id") id: Int, @Body request: StopWorkTimeRequest): Response<Unit>

    @PUT("worktimes/{id}")
    suspend fun updateWorkTime(@Path("id") id: Int, @Body request: UpdateWorkTimeRequest): Response<Unit>

    @DELETE("worktimes/{id}")
    suspend fun deleteWorkTime(@Path("id") id: Int): Response<Unit>

    @GET("pauses")
    suspend fun getPauses(@Query("worktime_id") workTimeId: Int): Response<List<ApiPause>>

    @POST("pauses")
    suspend fun startPause(@Body request: PauseCreateRequest): Response<PauseCreateResponse>

    @PUT("pauses/{id}")
    suspend fun updatePause(@Path("id") id: Int, @Body request: PauseUpdateRequest): Response<Unit>

    @DELETE("pauses/{id}")
    suspend fun deletePause(@Path("id") id: Int): Response<Unit>
}