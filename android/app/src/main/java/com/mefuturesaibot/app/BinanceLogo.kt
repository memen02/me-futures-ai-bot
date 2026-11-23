package com.mefuturesaibot.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import okhttp3.Cache
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException

/** Binance's public assetCode/logoUrl catalog. No credential client or exchange requests are involved. */
internal object BinanceLogo {
    private val bitmaps = LruCache<String, Bitmap>(48)
    private val main = Handler(Looper.getMainLooper())
    private var paths: Map<String, String>? = null
    private var client: OkHttpClient? = null
    private class Binding(val asset: String, val path: String, val icon: ImageView, val initials: TextView,
        val http: OkHttpClient) : View.OnAttachStateChangeListener {
        private var call: Call? = null
        override fun onViewAttachedToWindow(v: View) {
            val cached = bitmaps.get(asset)
            if (cached != null) { show(cached); return }
            if (call != null) return
            val request = Request.Builder().url("https://bin.bnbstatic.com$path").build()
            val current = http.newCall(request)
            call = current
            current.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {}
                override fun onResponse(call: Call, response: Response) {
                    val bitmap = response.use {
                        if (!it.isSuccessful || it.body.contentLength() > 512_000) return
                        val source = it.body.source()
                        if (source.request(512_001)) return
                        val bytes = source.readByteArray()
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                        if (options.outWidth !in 1..4096 || options.outHeight !in 1..4096) return
                        options.inJustDecodeBounds = false
                        while (maxOf(options.outWidth, options.outHeight) / maxOf(1, options.inSampleSize) > 128)
                            options.inSampleSize = maxOf(1, options.inSampleSize) * 2
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    } ?: return
                    main.post {
                        bitmaps.put(asset, bitmap)
                        if (icon.tag === this@Binding && this@Binding.call === current) show(bitmap)
                    }
                }
            })
        }
        private fun show(bitmap: Bitmap) { icon.setImageBitmap(bitmap); initials.visibility = View.INVISIBLE }
        override fun onViewDetachedFromWindow(v: View) { call?.cancel(); call = null }
    }

    fun bind(view: View, base: String, needed: Boolean) {
        val icon = view.findViewById<ImageView>(R.id.asset_logo)
        (icon.tag as? Binding)?.let { it.onViewDetachedFromWindow(icon); icon.removeOnAttachStateChangeListener(it) }
        icon.tag = null
        if (!needed) return
        val context = view.context.applicationContext
        if (paths == null) paths = context.assets.open("binance_logos.tsv").bufferedReader().useLines { lines ->
            lines.associate { it.substringBefore('\t') to it.substringAfter('\t') }
        }
        val path = paths?.get(base) ?: return
        val http = client ?: makeClient(context).also { client = it }
        val binding = Binding(base, path, icon, view.findViewById(R.id.asset_initials), http)
        icon.tag = binding
        icon.addOnAttachStateChangeListener(binding)
        if (icon.isAttachedToWindow) binding.onViewAttachedToWindow(icon)
    }

    private fun makeClient(context: Context) = OkHttpClient.Builder()
        .dispatcher(Dispatcher().apply { maxRequests = 2; maxRequestsPerHost = 2 })
        .cache(Cache(File(context.cacheDir, "asset-logos"), 4L * 1024 * 1024))
        .followRedirects(false).build()
}
