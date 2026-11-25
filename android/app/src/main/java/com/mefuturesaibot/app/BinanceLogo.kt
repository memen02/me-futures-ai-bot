package com.mefuturesaibot.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import java.util.WeakHashMap
import java.util.concurrent.TimeUnit

internal object BinanceLogo {

    private const val RETRY_MS = 300_000L
    private const val MAX_BYTES = 512_000L
    private const val MAX_PENDING = 32

    private val main = Handler(Looper.getMainLooper())
    private val bitmaps = LruCache<String, Bitmap>(48)
    private val failures = LruCache<String, Long>(256)

    private val bindings = WeakHashMap<View, String>()
    private val pending = mutableSetOf<String>()

    private var paths: Map<String, String>? = null
    private var client: OkHttpClient? = null

    fun catalogUpdated() {
        val visible = bindings.entries.map {
            it.key to it.value
        }

        visible.forEach { (view, asset) ->
            if (view.isAttachedToWindow) {
                bind(view, asset, true)
            }
        }
    }

    private class Binding(
        val path: String,
        val icon: ImageView,
        val initials: TextView,
        val http: OkHttpClient
    ) : View.OnAttachStateChangeListener {

        override fun onViewAttachedToWindow(v: View) {
            val cached = bitmaps.get(path)

            if (cached != null) {
                show(cached)
                return
            }

            fetch(path, http)
        }

        override fun onViewDetachedFromWindow(v: View) {
            // Shared downloads continue for other visible rows.
        }

        fun show(bitmap: Bitmap) {
            if (icon.tag !== this ||
                !icon.isAttachedToWindow
            ) return

            icon.setImageBitmap(bitmap)
            initials.visibility = View.INVISIBLE
        }
    }

    private fun fetch(
        path: String,
        http: OkHttpClient
    ) {
        if (path in pending) return
        if (pending.size >= MAX_PENDING) return

        val retryAt = failures.get(path) ?: 0L
        if (SystemClock.elapsedRealtime() < retryAt) return

        val request = try {
            Request.Builder()
                .url("https://bin.bnbstatic.com$path")
                .build()
        } catch (_: IllegalArgumentException) {
            failures.put(
                path,
                SystemClock.elapsedRealtime() + RETRY_MS
            )
            return
        }

        pending.add(path)

        http.newCall(request).enqueue(object : Callback {

            override fun onFailure(
                call: Call,
                e: IOException
            ) {
                finish(path, null)
            }

            override fun onResponse(
                call: Call,
                response: Response
            ) {
                val bitmap = try {
                    response.use { decode(it) }
                } catch (_: Exception) {
                    null
                }

                finish(path, bitmap)
            }
        })
    }

    private fun decode(response: Response): Bitmap? {
        if (!response.isSuccessful) return null

        val body = response.body
        if (body.contentLength() > MAX_BYTES) return null

        val source = body.source()
        if (source.request(MAX_BYTES + 1)) return null

        val bytes = source.readByteArray()

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size, options
        )

        if (options.outWidth !in 1..4096 ||
            options.outHeight !in 1..4096
        ) return null

        options.inJustDecodeBounds = false
        options.inSampleSize = 1

        while (
            maxOf(options.outWidth, options.outHeight) /
            options.inSampleSize > 128
        ) {
            options.inSampleSize *= 2
        }

        return BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size, options
        )
    }

    private fun finish(path: String, bitmap: Bitmap?) {
        main.post {
            pending.remove(path)

            if (bitmap == null) {
                failures.put(
                    path,
                    SystemClock.elapsedRealtime() + RETRY_MS
                )
            } else {
                failures.remove(path)
                bitmaps.put(path, bitmap)

                val visible = bindings.keys.toList()

                visible.forEach { view ->
                    val icon = view.findViewById<ImageView>(
                        R.id.asset_logo
                    )

                    val binding = icon.tag as? Binding

                    if (binding?.path == path) {
                        binding.show(bitmap)
                    }
                }
            }
        }
    }

    fun bind(view: View, base: String, needed: Boolean) {
        val icon = view.findViewById<ImageView>(
            R.id.asset_logo
        )

        (icon.tag as? Binding)?.let {
            icon.removeOnAttachStateChangeListener(it)
        }

        icon.tag = null
        bindings.remove(view)

        if (!needed) return

        bindings[view] = base

        val context = view.context.applicationContext

        if (paths == null) {
            paths = context.assets
                .open("binance_logos.tsv")
                .bufferedReader()
                .useLines { lines ->
                    lines.associate {
                        it.substringBefore('\t') to
                            it.substringAfter('\t')
                    }
                }
        }

        DynamicLogoCatalog.refreshIfNeeded()

        val path = DynamicLogoCatalog.path(base)
            ?: paths?.get(base)
            ?: return

        if (!path.startsWith("/image/") &&
            !path.startsWith("/images/")
        ) return

        val http = client ?: makeClient(context).also {
            client = it
        }

        val binding = Binding(
            path,
            icon,
            view.findViewById(R.id.asset_initials),
            http
        )

        icon.tag = binding
        icon.addOnAttachStateChangeListener(binding)

        if (icon.isAttachedToWindow) {
            binding.onViewAttachedToWindow(icon)
        }
    }

    private fun makeClient(context: Context) =
        OkHttpClient.Builder()
            .dispatcher(Dispatcher().apply {
                maxRequests = 2
                maxRequestsPerHost = 2
            })
            .cache(
                Cache(
                    File(context.cacheDir, "asset-logos"),
                    16L * 1024 * 1024
                )
            )
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
}
