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
    private val pages = LruCache<Int, Bitmap>(2)
    private var local: Map<String, Int>? = null
    private fun bundled(view: View, base: String): Bitmap? {
        val assets = view.context.assets
        if (local == null) local = assets.open("market_icons.txt").bufferedReader().use {
            it.readText().trim().split(" ").withIndex().associate { entry -> entry.value to entry.index }
        }
        val i = local?.get(base) ?: return null
        val page = i / 64
        val sheet = pages.get(page) ?: assets.open("market_icons_$page.webp").use { BitmapFactory.decodeStream(it) }
            ?.also { pages.put(page,it) } ?: return null
        val x = i % 8 * 48
        val y = (i % 64) / 8 * 48
        if (x + 48 > sheet.width || y + 48 > sheet.height) return null
        return Bitmap.createBitmap(sheet,x,y,48,48)
    }
    fun bind(view: View, base: String) {
        val assets = view.context.assets
        if (index.isEmpty()) {
            index = assets.open("token_logos.txt").bufferedReader().use { it.readText().trim().split(" ") }
            atlas = assets.open("token_logos.png").use { BitmapFactory.decodeStream(it) }
        }
        val i = index.indexOf(base)
        val bitmap = cache.get(base) ?: bundled(view,base)?.also { cache.put(base,it) } ?: if (i < 0) null else run {
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
