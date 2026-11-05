package com.ledger.app

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class LedgerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
    }
}
