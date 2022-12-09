package com.example.digitalsignage

import android.app.Application
import io.paperdb.Paper

class ApplicationClass:Application() {

    override fun onCreate() {
        super.onCreate()
        Paper.init(this);
    }
}