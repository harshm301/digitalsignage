package com.example.digitalsignage.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "campaigns")
data class CampaignFile(
    @ColumnInfo(name = "start_time") var startTime: String,
    @ColumnInfo(name = "end_time") var endTime: String,
    @ColumnInfo(name = "fileName") val fileName: String,
    @PrimaryKey var fileUrl: String,
    @ColumnInfo(name = "file_uri") var fileUri: String? = null,
    @ColumnInfo(name = "campaign_id") var campaignId: String,
    @ColumnInfo(name = "is_downloaded") var isDownloaded: Boolean = false,
    @ColumnInfo(name = "order") var order: Int,
    @ColumnInfo(name = "download_reference_id") var downloadRefrenceId: Long? = null
)

@Entity(tableName = "defaultList")
data class DefaultList(
    @PrimaryKey var fileUrl: String,
    @ColumnInfo(name = "file_uri") var fileUri: String? = null,
    @ColumnInfo(name = "is_downloaded") var isDownloaded: Boolean = false,
    @ColumnInfo(name = "download_reference_id") var downloadRefrenceId: Long? = null
)