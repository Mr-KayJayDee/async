package com.mythlane.async

import com.mythlane.async.dispatchers.AsyncDispatchers
import com.mythlane.async.dispatchers.WorldExecutor
import com.mythlane.async.exception.WorldClosedException
import com.mythlane.async.scope.PlayerScopes
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Smoke test: `Dispatchers.World` + `PlayerScopes` end-to-end against a stub world.
 * Proves that work submitted from an arbitrary thread lands on the world's main thread,
 * and that disconnecting a player cancels in-flight work.
 */
class SmokeTest {

    private class StubWorld(override val worldId: UUID = UUID.randomUUID()) : WorldExecutor {
        private val exec = Executors.newSingleThreadExecutor { r ->
            Thread(r, "Stub-World-$worldId").also { mainThread.set(it) }
        }
        val mainThread = AtomicReference<Thread>()
        private val alive = AtomicBoolean(true)
        override fun isAlive() = alive.get()
        override fun execute(task: Runnable) { exec.submit(task) }
        fun shutdown() { alive.set(false); exec.shutdownNow(); exec.awaitTermination(2, TimeUnit.SECONDS) }
    }

    private val world = StubWorld()

    @AfterEach fun tearDown() {
        AsyncDispatchers.evictWorld(world.worldId)
        PlayerScopes.cancelAll()
        world.shutdown()
    }

    @Test fun `World dispatcher confines work to world main thread`() = runTest {
        val landed = AtomicReference<Thread>()
        withContext(AsyncDispatchers.World(world)) {
            landed.set(Thread.currentThread())
        }
        landed.get() shouldBe world.mainThread.get()
    }

    @Test fun `dispatching to a dead world throws WorldClosedException`() = runTest {
        world.shutdown()
        shouldThrow<WorldClosedException> {
            withContext(AsyncDispatchers.World(world)) { /* never runs */ }
        }
    }

    @Test fun `cancelling player scope stops in-flight work`() = runTest {
        val playerId = UUID.randomUUID()
        val scope = PlayerScopes.of(playerId)
        val ran = AtomicBoolean(false)

        val job = scope.launch {
            delay(10_000)
            ran.set(true)
        }
        PlayerScopes.cancel(playerId)
        job.join()

        ran.get() shouldBe false
        PlayerScopes.activeCount() shouldBe 0
    }
}
