package com.varsitycollege.birdvue.api

import com.varsitycollege.birdvue.data.BirdInfo
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query
import retrofit2.http.Url

interface BirdInfoAPI {
    //Base URL: https://kpcs4l6aa3.execute-api.eu-west-1.amazonaws.com/BirdRESTApiStage/
    //Get bird info using name of bird asynchronously using coroutines
    @GET("ask")
    suspend fun getBirdInfo(
        @Query("prompt") prompt: String,
        @Header("x-api-key") apiKey: String
    ): Response<BirdInfo>

    @Multipart
    @POST
    suspend fun predictImage(
        @Url url: String, // Pass the full URL: "https://sveiaufbgb.eu-west-1.awsapprunner.com/predict"
        @Part file: MultipartBody.Part, // The image file part
        @Header("x-api-key") apiKey: String
    ): Response<ImagePredictionResponse>

    @Multipart
    @POST
    suspend fun verifyBirdImage(
        @Url url: String, // Full URL: "https://kpcs4l6aa3.execute-api.eu-west-1.amazonaws.com/BirdRESTApiStage/verifybirdimage"
        @Part file: MultipartBody.Part, // The image file part
        @Header("x-api-key") apiKey: String
    ): Response<VerifyBirdImageResponse>
}