package com.example.digitalsignage.model

import androidx.room.ColumnInfo
import com.google.gson.annotations.SerializedName

data class Campaigns(
    val startTime: String = "",
    val endTime: String = "",
    val assets: ArrayList<FileUrls> = arrayListOf(),
    var campaignId: String = ""
)

data class FileUrls(
    val fileUrl: String = "",
    val order: Int = 0
)

data class CurrentCampaign(
    val campaignId: String = "",
)


data class ErrorBody(
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("campaignId") val campaignId: String,
    @SerializedName("errorCode") val errorCode: String,
    @SerializedName("message") val message: String,
    @SerializedName("stacktrace") val stacktrace: String,
)