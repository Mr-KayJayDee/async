package com.mythlane.example.moderation

import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import com.mythlane.async.Async
import com.mythlane.async.dispatchers.AsyncDispatchers
import com.mythlane.async.ecs.ComponentRegistry
import com.mythlane.async.ecs.modify
import com.mythlane.async.binding.installAsync
import com.mythlane.async.binding.toEntityHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.future
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

private val FLAG_WORDS = setOf("badword", "spam", "scam")

/**
 * Demonstrates the async-event flow:
 *  - `registerAsyncGlobal(PlayerChatEvent)` — `registerAsync` only matches
 *    `IAsyncEvent<Void>`, and `PlayerChatEvent` is `IAsyncEvent<String>`, so
 *    the global overload is required.
 *  - `scope.future { … }` — bridges Hytale's `CompletableFuture` contract to
 *    coroutines via `kotlinx-coroutines-jdk8`.
 *  - `withContext(AsyncDispatchers.HytaleIO)` for the simulated HTTP call.
 *  - Conditional `modify<T>` + `event.isCancelled = true`.
 *
 * Component-side wiring is left as `TODO` — orthogonal to the demo.
 */
class ModerationPlugin(init: JavaPluginInit) : JavaPlugin(init) {

    // Plugin-scoped supervisor for the chat-handling coroutines bridged from
    // CompletableFuture. Cancelled in shutdown via Async.shutdown.
    private val scope = CoroutineScope(SupervisorJob() + AsyncDispatchers.HytaleIO)

    override fun start() {
        installAsync()
        ComponentRegistry.register<ModerationStats>(ModerationStatsBinding.componentType())

        eventRegistry.registerAsyncGlobal(PlayerChatEvent::class.java) { future ->
            future.thenCompose { event ->
                scope.future {
                    val flagged = withContext(AsyncDispatchers.HytaleIO) {
                        delay(200.milliseconds) // simulated HTTP call
                        FLAG_WORDS.any { it in event.content.lowercase() }
                    }
                    if (flagged) {
                        modify<ModerationStats, Unit>(event.sender.toEntityHandle()) {
                            warnings += 1
                        }
                        event.isCancelled = true
                    }
                    event
                }
            }
        }
    }

    override fun shutdown() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        Async.shutdown()
    }
}

/** Stub component shape — replace with your real `Component<EntityStore>` subclass. */
class ModerationStats { var warnings: Int = 0 }

object ModerationStatsBinding {
    fun componentType(): Any = TODO("Return EntityStore.REGISTRY.register(ModerationStats::class.java, ...) result here.")
}
