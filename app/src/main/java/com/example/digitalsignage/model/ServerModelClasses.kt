package com.example.digitalsignage.model

import androidx.room.ColumnInfo

data class Campaigns(
    @ColumnInfo(name = "start_time") val startTime: String = "",
    @ColumnInfo(name = "end_time") val endTime: String ="",
    @ColumnInfo(name = "file_Url") var fileUrl: String = "",
    @ColumnInfo(name = "campaign_id") val campaignId: String ="",
    @ColumnInfo(name = "order") val order: Int = 0
)

data class CurrentCampaign(
    @ColumnInfo(name = "start_time") val startTime: String = "",
    @ColumnInfo(name = "end_time") val endTime: String ="",
    @ColumnInfo(name = "campaign_id") val campaignId: String ="",
)