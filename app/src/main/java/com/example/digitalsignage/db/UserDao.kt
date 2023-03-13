package com.example.digitalsignage.db

import androidx.room.*
import com.example.digitalsignage.model.CampaignFile
import com.example.digitalsignage.model.DefaultList

@Dao
interface SignageDoa {
    @Query("SELECT * FROM campaigns")
    suspend fun getAll(): List<CampaignFile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vararg campaignFile: CampaignFile)

    @Query("DELETE from campaigns")
    suspend fun deleteCampaigns()

    @Query("DELETE FROM defaultList")
    suspend fun deleteDefaults()

    @Query("Select * from campaigns where campaign_id=:campaignId and is_downloaded=1 ORDER BY :order ASC")
    suspend fun getCurrentCampaign(
        campaignId: String,
        order: String = "order"
    ): List<CampaignFile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllDownloadedFile(vararg defaultList: DefaultList)

    @Query("Select * from defaultList")
    suspend fun fetchDefaultFiles(): List<DefaultList>

    @Query("Select * from defaultList where is_downloaded=:isDowloaded")
    suspend fun fetchDownloadedDefaultFiles(isDowloaded: Boolean = true): List<DefaultList>

    // suspend fun updateCampaignFile(campaignFile: CampaignFile)

    /* @Query("Select * from campaigns where start_time:=startTime and campaign_id:=campaignId")
     fun getCurrentCampaign(startTime: Long, campaignId: String): List<Campaign_File>*/
}