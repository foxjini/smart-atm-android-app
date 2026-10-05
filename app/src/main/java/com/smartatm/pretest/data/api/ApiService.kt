package com.smartatm.pretest.data.api

import com.smartatm.pretest.data.model.AnalysisRequest
import com.smartatm.pretest.data.model.AnalysisResponse
import com.smartatm.pretest.data.model.DataWrapper
import com.smartatm.pretest.data.model.LoginRequest
import com.smartatm.pretest.data.model.LoginResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface ApiService {

    @GET("health")
    suspend fun checkHealth(): Response<ResponseBody>

    @POST("api/v1/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<DataWrapper<LoginResponse>>

    @POST("api/v1/analysis")
    suspend fun analyzeMessage(
        @Header("Authorization") authHeader: String,
        @Body request: AnalysisRequest
    ): Response<DataWrapper<AnalysisResponse>>
}
