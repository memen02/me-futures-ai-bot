package com.mefuturesaibot.app.auth

import android.app.Activity
import android.widget.ScrollView

// Host must close this component on navigation away or Activity destruction.
internal class OwnerPanelHost(
    activity: Activity,
    sessions: SecureSessionManager,
    api: HttpsAuthenticationApi,
    onError: (String) -> Unit
) : AutoCloseable {
    private val directory = OwnerDirectoryView(activity)
    val view = ScrollView(activity).apply {
        isFillViewport = true
        addView(directory)
    }
    private lateinit var dialogs: OwnerManagementDialogs
    private val coordinator = OwnerPanelCoordinator(
        directory, OwnerAccountController(sessions, api), sessions, onError,
        { row -> dialogs.license(row) },
        { row -> dialogs.device(row) }
    )
    private var closed = false

    init {
        dialogs = OwnerManagementDialogs(activity, coordinator)
    }

    fun refresh() {
        if (!closed) coordinator.refresh()
    }

    override fun close() {
        if (closed) return
        closed = true
        coordinator.close()
        view.removeAllViews()
    }
}
