package io.github.kez.sample.web.message.listener.bridge

interface PermissionRequester {
    suspend fun request(permission: String): Boolean
}
