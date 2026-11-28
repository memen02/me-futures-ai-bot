package com.mefuturesaibot.app.auth

internal class OwnerPanelCoordinator(
    private val view: OwnerDirectoryView,
    private val controller: OwnerAccountController,
    private val sessions: SecureSessionManager,
    private val onError: (String) -> Unit,
    private val manageLicense: (OwnerDirectoryRow) -> Unit,
    private val manageDevice: (OwnerDirectoryRow) -> Unit,
    private val canAct: () -> Boolean
) : AutoCloseable {
    private val worker = OwnerRequestWorker()

    fun refresh(after: String? = null) {
        if (!canAct()) return
        if (sessions.ownerToken() == null) {
            view.removeAllViews()
            onError("Sesi OWNER berakhir")
            return
        }
        if (!worker.submit({
            OwnerDirectoryPage.parse(controller.listUsers(after))
        }) { result ->
            if (!canAct()) return@submit
            if (sessions.ownerToken() == null) {
                view.removeAllViews()
                onError("Sesi OWNER berakhir")
            } else result.fold(
                onSuccess = { page ->
                    view.render(page, ::review, { if (canAct()) refresh(it) },
                        manageLicense, manageDevice)
                },
                onFailure = { onError("Gagal memuat pengguna") }
            )
        }) onError("Permintaan sedang diproses")
    }

    private fun review(row: OwnerDirectoryRow, desired: AccountStatus) {
        if (!canAct()) return
        if (!worker.submit({
            controller.review(row.accountId, row.status, desired)
        }) { result ->
            if (!canAct()) return@submit
            if (result.getOrNull() != true)
                onError("Perubahan belum terkonfirmasi")
            refresh()
        }) onError("Permintaan sedang diproses")
    }

    fun grantLicense(row: OwnerDirectoryRow, kind: String, expires: Long?) {
        mutate { controller.grantLicense(row.accountId, kind, expires) }
    }

    fun revokeLicense(row: OwnerDirectoryRow) {
        mutate { controller.revokeLicense(row.accountId) }
    }

    fun replaceDevice(row: OwnerDirectoryRow, digest: String) {
        mutate { controller.bindDevice(row.accountId, digest, row.deviceDigest) }
    }

    private fun mutate(action: () -> Boolean) {
        if (!canAct()) return
        if (!worker.submit(action) { result ->
            if (!canAct()) return@submit
            if (result.getOrNull() != true)
                onError("Perubahan belum terkonfirmasi; periksa status terbaru")
            refresh()
        }) onError("Permintaan sedang diproses")
    }

    override fun close() {
        worker.close()
        view.removeAllViews()
    }
}
