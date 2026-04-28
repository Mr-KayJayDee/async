package com.mythlane.async.binding

import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import com.mythlane.async.Async

/**
 * Standalone library-plugin entry point. Drop the shaded jar in `mods/` and
 * other plugins can depend on Async without bundling it themselves.
 *
 * Installs the disconnect hook on start and drains every tracked scope on
 * shutdown. Plugins that bundle Async directly should keep calling
 * [installAsync] and [Async.shutdown] themselves and not rely on this class.
 */
class AsyncPlugin(init: JavaPluginInit) : JavaPlugin(init) {
    override fun start() {
        installAsync()
    }

    override fun shutdown() {
        Async.shutdown()
    }
}
