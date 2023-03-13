package com.example.digitalsignage

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.util.Log
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.widget.EditText
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.net.toFile
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.time.format.DateTimeParseException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext


var PERMISSION_ALL = 101

@RequiresApi(Build.VERSION_CODES.R)
var PERMISSIONS = arrayOf(
    Manifest.permission.READ_EXTERNAL_STORAGE,
    Manifest.permission.WRITE_EXTERNAL_STORAGE,
    Manifest.permission.MANAGE_EXTERNAL_STORAGE
)

fun checkRight(context: Context, uri: Uri?): Boolean {
    if (uri == null) return false
    val resolver = context.contentResolver
    //1. Check Uri
    var cursor: Cursor? = null
    val isUriExist: Boolean = try {
        cursor = resolver.query(uri, null, null, null, null)
        //cursor null: content Uri was invalid or some other error occurred
        //cursor.moveToFirst() false: Uri was ok but no entry found.
        (cursor != null && cursor.moveToFirst())
    } catch (t: Throwable) {
        Log.d("Barcode", "1.Check Uri Error: ${t.message}")
        false
    } finally {
        try {
            cursor?.close()
        } catch (t: Throwable) {
        }
    }
    //2. Check File Exist
    //如果系统 db 存有 Uri 相关记录, 但是文件失效或者损坏 (If the system db has Uri related records, but the file is invalid or damaged)
    var ins: InputStream? = null
    val isFileExist: Boolean = try {
        ins = resolver.openInputStream(uri)
        // file exists
        true
    } catch (t: Throwable) {
        // File was not found eg: open failed: ENOENT (No such file or directory)
        Log.d("Barcode", "2. Check File Exist Error: ${t.message}")
        false
    } finally {
        try {
            ins?.close()
        } catch (t: Throwable) {
        }
    }
    return isUriExist && isFileExist
}

fun hasPermissions(activity: Activity, permissions: Array<String>, permission_code: Int) {

    permissions.forEach {
        if (ActivityCompat.checkSelfPermission(
                activity,
                it
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(activity, permissions, permission_code)
        }
    }
}


fun showAlertBox(context: Activity, function: (string: String) -> Unit) {
    val alert: AlertDialog.Builder = AlertDialog.Builder(context)
    alert.setTitle("Enter device id")
    alert.setMessage("you can only set device id once")
    alert.setCancelable(false)

// Set an EditText view to get user input

// Set an EditText view to get user input
    val input = EditText(context)
    alert.setView(input)

    alert.setPositiveButton("Ok", DialogInterface.OnClickListener { dialog, whichButton ->
        val value: Editable? = input.text
        function(value.toString())
        // Do something with value!
    })

    alert.setNegativeButton("Cancel",
        DialogInterface.OnClickListener { dialog, whichButton ->
            // Canceled.
            context.finish()
        })
    alert.show()
}

fun download(context: Context, s: String): Long {
    val dmr = DownloadManager.Request(Uri.parse(s))
    //Alternative if you don't know filename
    val fileName: String =
        URLUtil.guessFileName(s, null, MimeTypeMap.getFileExtensionFromUrl(s))
    dmr.setDestinationInExternalFilesDir(context, context.cacheDir.absolutePath, fileName)
    dmr.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
    dmr.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    return manager.enqueue(dmr)
}

fun deleteFile(context: Context, uri: Uri) {
    try {
        //val file = DocumentFile.fromSingleUri(context, uri)?.delete()
        val file = File(uri.path).delete(context = context)
        Log.d("Barcode", file.toString())
    } catch (e: Exception) {
        Log.d("Barcode", e.message.toString())
    }
}

fun File.delete(context: Context): Boolean {
    var selectionArgs = arrayOf(this.absolutePath)
    val contentResolver = context.contentResolver
    var where: String? = null
    var filesUri: Uri? = null
    if (android.os.Build.VERSION.SDK_INT >= 29) {
        filesUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        where = MediaStore.Images.Media._ID + "=?"
        selectionArgs = arrayOf(this.name)
    } else {
        where = MediaStore.MediaColumns.DATA + "=?"
        filesUri = MediaStore.Files.getContentUri("external")
    }

    val int = contentResolver.delete(filesUri!!, where, selectionArgs)

    return !this.exists()
}

fun BroadcastReceiver.goAsync(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> Unit
) {
    val pendingResult = goAsync()
    CoroutineScope(SupervisorJob()).launch(context) {
        try {
            block()
        } finally {
            pendingResult.finish()
        }
    }
}