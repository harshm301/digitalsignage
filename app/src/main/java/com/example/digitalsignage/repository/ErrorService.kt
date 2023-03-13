package com.example.digitalsignage.repository

import com.example.digitalsignage.model.ErrorBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.create
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST


interface ErrorService {
    @POST("v1/device/error")
    suspend fun reportError(@Body user: ErrorBody): Response<Unit>

    companion object {
        fun create(): ErrorService {
            val retrofit = Retrofit.Builder()
                .baseUrl("https://adbeets.com/adbeets/api/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return retrofit.create(ErrorService::class.java)
        }
    }
}





