package io.github.kez.sample.web.message.listener.bridge

import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class ActivityPermissionRequester(
    activity: ComponentActivity
) : PermissionRequester {

    private val requestMutex = Mutex()
    private var pendingContinuation: Continuation<Boolean>? = null

    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        pendingContinuation?.resume(granted)
        pendingContinuation = null
    }

    override suspend fun request(permission: String): Boolean {
        return requestMutex.withLock {
            suspendCoroutine { continuation ->
                pendingContinuation = continuation
                permissionLauncher.launch(permission)
            }
        }
    }
}
