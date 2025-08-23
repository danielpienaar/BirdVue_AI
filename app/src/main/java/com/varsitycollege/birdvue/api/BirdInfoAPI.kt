package com.varsitycollege.birdvue.api

import com.varsitycollege.birdvue.data.BirdInfo
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface BirdInfoAPI {
    //Base URL: https://kpcs4l6aa3.execute-api.eu-west-1.amazonaws.com/BirdRESTApiStage/
    //Get bird info using name of bird asynchronously using coroutines
    @GET("ask")
    suspend fun getBirdInfo(@Query("prompt") prompt: String,
                            @Header("x-api-key") apiKey: String): Response<BirdInfo>
}