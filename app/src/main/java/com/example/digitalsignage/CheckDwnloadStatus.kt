package com.example.digitalsignage

import android.app.DownloadManager
import android.content.Context
import android.content.Context.DOWNLOAD_SERVICE
import android.database.Cursor
import android.net.Uri
import android.widget.Toast

fun CheckDwnloadStatus(context: Context, id: Long, currentFileList: (uri:Uri) -> Unit) {
        // TODO Auto-generated method stub
        val downloadManager = context.getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query()
        query.setFilterById(id)
        val cursor: Cursor = downloadManager.query(query)
        if (cursor.moveToFirst()) {
            val columnIndex: Int = cursor
                .getColumnIndex(DownloadManager.COLUMN_STATUS)
            val status: Int = cursor.getInt(columnIndex)
            val columnReason: Int = cursor
                .getColumnIndex(DownloadManager.COLUMN_REASON)
            val reason: Int = cursor.getInt(columnReason)
            when (status) {
                DownloadManager.STATUS_FAILED -> {
                    var failedReason = ""
                    when (reason) {
                        DownloadManager.ERROR_CANNOT_RESUME -> failedReason = "ERROR_CANNOT_RESUME"
                        DownloadManager.ERROR_DEVICE_NOT_FOUND -> failedReason =
                            "ERROR_DEVICE_NOT_FOUND"
                        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> failedReason =
                            "ERROR_FILE_ALREADY_EXISTS"
                        DownloadManager.ERROR_FILE_ERROR -> failedReason = "ERROR_FILE_ERROR"
                        DownloadManager.ERROR_HTTP_DATA_ERROR -> failedReason =
                            "ERROR_HTTP_DATA_ERROR"
                        DownloadManager.ERROR_INSUFFICIENT_SPACE -> failedReason =
                            "ERROR_INSUFFICIENT_SPACE"
                        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> failedReason =
                            "ERROR_TOO_MANY_REDIRECTS"
                        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> failedReason =
                            "ERROR_UNHANDLED_HTTP_CODE"
                        DownloadManager.ERROR_UNKNOWN -> failedReason = "ERROR_UNKNOWN"
                    }
                    Toast.makeText(
                        context, "FAILED: $failedReason",
                        Toast.LENGTH_LONG
                    ).show()
                }
                DownloadManager.STATUS_PAUSED -> {
                    var pausedReason = ""
                    when (reason) {
                        DownloadManager.PAUSED_QUEUED_FOR_WIFI -> pausedReason =
                            "PAUSED_QUEUED_FOR_WIFI"
                        DownloadManager.PAUSED_UNKNOWN -> pausedReason = "PAUSED_UNKNOWN"
                        DownloadManager.PAUSED_WAITING_FOR_NETWORK -> pausedReason =
                            "PAUSED_WAITING_FOR_NETWORK"
                        DownloadManager.PAUSED_WAITING_TO_RETRY -> pausedReason =
                            "PAUSED_WAITING_TO_RETRY"
                    }
                    Toast.makeText(
                        context, "PAUSED: $pausedReason",
                        Toast.LENGTH_LONG
                    ).show()
                }
                DownloadManager.STATUS_PENDING -> Toast.makeText(context, "PENDING", Toast.LENGTH_LONG)
                    .show()
                DownloadManager.STATUS_RUNNING -> Toast.makeText(context, "RUNNING", Toast.LENGTH_LONG)
                    .show()
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val uri = downloadManager.getUriForDownloadedFile(id)
                    uri?.let(currentFileList)
                    /*if (uri != null) {
                        if (uriHashMap.contains(id)) {
                            Log.d("Barcode", "Download Succesful")
                            val uriMapper = uriHashMap[id]
                            val list = timeSlotMap[uriMapper?.starTime]?.uriList
                            val playMapper = timeSlotMap[uriMapper?.starTime]
                            if (list == null) {
                                val mutableList = mutableListOf<Uri>(uri)
                                mutableList.distinct()
                                timeSlotMap[uriMapper!!.starTime] = playMapper!!.copy(
                                    uriList = mutableList
                                )
                            } else {
                                list.let { it1 ->
                                    timeSlotMap[playMapper?.startTime]?.uriList?.add(
                                        uri
                                    )
                                }
                            }
                            Toast.makeText(
                                this,
                                "${totalItems - itemDownload} more item to download",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else if (defaultUriMapper.contains(id)) {
                            Log.d("Barcode", "Download Succesful")
                            Log.d("Barcode", "defaultDownloader")
                            defaultUriMapper.replace(id, uri)
                            Toast.makeText(
                                this,
                                "${totalItems - itemDownload} more item to download",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        itemDownload++
                        bindingActivity.progressCircular.isVisible = false
                        startPlaying()
                        if (itemDownload == totalItems) {
                            isDownloaded = true
                            //startPlaying()
                        }
                    }*/
                }
            }
        }
    }