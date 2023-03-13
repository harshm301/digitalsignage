package com.example.digitalsignage.repository

import com.example.digitalsignage.db.AppDatabase
import com.example.digitalsignage.db.SignageDoa
import com.example.digitalsignage.model.CampaignFile
import com.example.digitalsignage.model.CurrentCampaign
import com.example.digitalsignage.model.DefaultList

class SignageRepository(appDatabase: AppDatabase) {

    private var signageDoa: SignageDoa = appDatabase.signageDao()

    suspend fun getAllCampaign() = signageDoa.getAll()

    suspend fun updateCampaignFile(campaignFile: CampaignFile) = signageDoa.insertAll(campaignFile)

    suspend fun fetchCurrentCampaign(currentCampaign: String) =
        signageDoa.getCurrentCampaign(currentCampaign)

    suspend fun deleteCampaigns() = signageDoa.deleteCampaigns()

    suspend fun getAlldefaultFiles() = signageDoa.fetchDefaultFiles()

    suspend fun updateDefaultFile(defaultList: DefaultList) =
        signageDoa.insertAllDownloadedFile(defaultList)

    suspend fun getAllDownloadedDefaultFiles() = signageDoa.fetchDownloadedDefaultFiles()

    suspend fun deleteAllDownloadFiles() = signageDoa.deleteDefaults()
}