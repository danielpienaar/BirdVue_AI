package com.varsitycollege.birdvue.api

import com.varsitycollege.birdvue.data.BirdInfo
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface BirdInfoAPI {
    //Base URL: https://3hkbc9xpzr.eu-west-1.awsapprunner.com/
    //Get bird info using name of bird asynchronously using coroutines
    @GET("ask")
    suspend fun getBirdInfo(@Query("prompt") prompt: String): Response<BirdInfo>
}