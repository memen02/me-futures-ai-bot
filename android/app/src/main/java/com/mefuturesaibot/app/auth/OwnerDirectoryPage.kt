package com.mefuturesaibot.app.auth

import org.json.JSONObject

internal data class OwnerDirectoryRow(
    val accountId: String,
    val username: String,
    val status: AccountStatus,
    val licenseKind: String?,
    val licenseExpires: Long?,
    val deviceDigest: String?
)

internal data class OwnerDirectoryPage(
    val users: List<OwnerDirectoryRow>,
    val nextCursor: String?
) {
    companion object {
        fun parse(value: JSONObject): OwnerDirectoryPage {
            val array = value.getJSONArray("users")
            require(array.length() <= 20)
            val idPattern = Regex("[0-9a-f]{32}")
            val rows = (0 until array.length()).map { index ->
                val row = array.getJSONObject(index)
                val id = row.getString("accountId")
                val name = row.getString("username")
                require(id.matches(idPattern) && name.matches(Regex("[a-z0-9_]{4,32}")))
                val kind = if (row.isNull("licenseKind")) null else row.getString("licenseKind")
                val expiry = if (row.isNull("licenseExpires")) null else row.getLong("licenseExpires")
                require((kind == null && expiry == null) ||
                    (kind == "FAMILY" && expiry == null) ||
                    (kind == "MONTHLY" && expiry != null && expiry > 0))
                val digest = if (row.isNull("deviceDigest")) null else row.getString("deviceDigest")
                require(digest == null || digest.matches(Regex("[0-9a-f]{64}")))
                OwnerDirectoryRow(id, name, AccountStatus.valueOf(row.getString("status")), kind, expiry, digest)
            }
            require(rows.zipWithNext().all { (a,b) -> a.accountId < b.accountId })
            val cursor = if (value.isNull("nextCursor")) null else value.getString("nextCursor")
            require(cursor == null || (rows.size == 20 && cursor == rows.last().accountId))
            return OwnerDirectoryPage(rows, cursor)
        }
    }
}
