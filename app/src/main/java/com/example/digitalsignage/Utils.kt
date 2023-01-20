package com.example.digitalsignage

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log
import java.io.InputStream

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
        Log.d("Barcode","2. Check File Exist Error: ${t.message}")
        false
    } finally {
        try {
            ins?.close()
        } catch (t: Throwable) {
        }
    }
    return isUriExist && isFileExist
}