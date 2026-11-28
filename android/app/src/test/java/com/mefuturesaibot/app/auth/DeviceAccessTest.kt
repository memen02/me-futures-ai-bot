package com.mefuturesaibot.app.auth

import com.mefuturesaibot.app.maintenance.MeProjectApplication
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executor

class ApprovedDeviceApplication : MeProjectApplication() {
    internal override val devicePermission: DevicePermission = object : DevicePermission {
        override fun allowed() = true
    }
}

class DeviceAccessTest {
    private val code = "a".repeat(64)
    private fun release(body: String) = JSONObject().put("id", DeviceRegistry.releaseId).put("draft", false)
        .put("tag_name","izin-perangkat-v1").put("author", JSONObject().put("id",219340650L)).put("body",body)

    @Test fun onlyExactOwnerRegistryAndDeviceMatch() {
        val valid = release("ME-PROJECT-ACCESS-V1\r\n\n$code")
        assertTrue(DeviceRegistry.permits(valid.toString(),code))
        assertFalse(DeviceRegistry.permits(valid.toString(),"b".repeat(64)))
        assertFalse(DeviceRegistry.permits(release("ME-PROJECT-ACCESS-V1").toString(),code))
        for ((field,value) in listOf("draft" to true,"id" to 1,"tag_name" to "fork","author" to JSONObject().put("id",1))) {
            assertFalse(DeviceRegistry.permits(JSONObject(valid.toString()).put(field,value).toString(),code))
        }
        assertThrows(IllegalArgumentException::class.java) { DeviceRegistry.permits(release("ME-PROJECT-ACCESS-V1\n*\n$code").toString(),code) }
    }

    @Test fun expiresDuringPendingLookupAndFailsClosedOnErrorOrRemoval() {
        var now = 1L
        val queue = mutableListOf<Runnable>()
        var grant = true
        var offline = false
        val access = DeviceAccess({code},{ check(!offline); grant },{now},Executor { queue.add(it) })
        assertFalse(access.allowed())
        assertFalse(access.allowed())
        queue.single().run(); queue.clear()
        assertTrue(access.allowed())
        now += 240_000
        assertTrue(access.allowed())
        now += 60_000
        assertFalse(access.allowed())
        offline = true
        queue.single().run(); queue.clear()
        assertFalse(access.allowed())
        offline = false
        now += 60_000
        access.allowed()
        queue.single().run(); queue.clear()
        assertTrue(access.allowed())
        grant = false
        now += 240_000
        access.allowed()
        queue.single().run()
        assertFalse(access.allowed())
    }
}
