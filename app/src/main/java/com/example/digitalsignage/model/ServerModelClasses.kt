package com.example.digitalsignage.model

import androidx.room.ColumnInfo

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