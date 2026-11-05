package com.ledger.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object IconStore {
    private const val SIZE = 192
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun dir(context: Context): File = File(context.filesDir, "icons").apply { mkdirs() }

    fun file(context: Context, name: String): File = File(dir(context), name)

    /** 保存为 PNG，返回文件名 */
    fun save(context: Context, bitmap: Bitmap): String {
        val name = "icon_" + UUID.randomUUID().toString().replace("-", "") + ".png"
        FileOutputStream(file(context, name)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return name
    }

    fun delete(context: Context, name: String?) {
        if (name.isNullOrBlank()) return
        cache.remove(name)
        runCatching { file(context, name).delete() }
    }

    fun load(context: Context, name: String): Bitmap? {
        cache.get(name)?.let { return it }
        val f = file(context, name)
        if (!f.exists()) return null
        val bmp = BitmapFactory.decodeFile(f.absolutePath) ?: return null
        cache.put(name, bmp)
        return bmp
    }

    fun drawableToBitmap(d: Drawable): Bitmap {
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        d.setBounds(0, 0, SIZE, SIZE)
        d.draw(c)
        return bmp
    }

    /** 从图片 Uri 读取，中心裁剪为正方形并缩放 */
    fun fromUri(context: Context, uri: Uri): Bitmap? {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= SIZE * 2 && bounds.outHeight / (sample * 2) >= SIZE * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val src = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val side = minOf(src.width, src.height)
        val left = (src.width - side) / 2
        val top = (src.height - side) / 2
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
        Canvas(out).drawBitmap(src, Rect(left, top, left + side, top + side), Rect(0, 0, SIZE, SIZE), paint)
        if (src != out) src.recycle()
        return out
    }
}
