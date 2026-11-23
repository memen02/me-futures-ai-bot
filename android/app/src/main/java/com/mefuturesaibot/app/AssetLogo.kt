package com.mefuturesaibot.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import android.widget.TextView

/** CC0 asset artwork from spothq/cryptocurrency-icons 0.18.1. Only decode visible, explicitly mapped assets. */
internal object AssetLogo {
    private val cache = LruCache<String, Bitmap>(24)
    private var atlas: Bitmap? = null
    private var index: List<String> = emptyList()
    fun bind(view: View, base: String) {
        val assets = view.context.assets
        if (index.isEmpty()) {
            index = assets.open("token_logos.txt").bufferedReader().use { it.readText().trim().split(" ") }
            atlas = assets.open("token_logos.png").use { BitmapFactory.decodeStream(it) }
        }
        val i = index.indexOf(base)
        val bitmap = if (i < 0) null else cache.get(base) ?: run {
            val x = i % 8 * 64
            val y = i / 8 * 64
            atlas?.takeIf { x + 64 <= it.width && y + 64 <= it.height }?.let {
                Bitmap.createBitmap(it, x, y, 64, 64).also { logo -> cache.put(base, logo) }
            }
        }
        view.findViewById<ImageView>(R.id.asset_logo).setImageBitmap(bitmap)
        view.findViewById<TextView>(R.id.asset_initials).apply {
            text = base.take(2)
            visibility = if (bitmap == null) View.VISIBLE else View.INVISIBLE
        }
        BinanceLogo.bind(view, base, bitmap == null)
    }
}
