package io.github.kez.sample.web.message.listener.bridge

object WebBridgeConfig {
    const val BRIDGE_NAME = "NativeBridge"

    const val LOCAL_DEMO_URL = "file:///android_asset/demo.html"
    val LOCAL_ALLOWED_ORIGINS: Set<String> = setOf("null")

    const val PROD_WEB_URL = "https://samplewebmessagelistener-web.pages.dev"
    val PROD_ALLOWED_ORIGINS: Set<String> = setOf(
        "https://samplewebmessagelistener-web.pages.dev"
    )
}
