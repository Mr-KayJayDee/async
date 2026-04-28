package com.mythlane.async.ecs

import com.mythlane.async.dispatchers.WorldExecutor
import com.mythlane.async.exception.ComponentNotFoundException
import com.mythlane.async.exception.ComponentTypeNotRegisteredException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class ComponentDslTest {

    data class PlayerStats(var level: Int = 1, var xp: Int = 0)

    private val statsKey: Any = "stats-token" // opaque, mimics Hytale ComponentType<PlayerStats>

    private class StubWorld : WorldExecutor {
        override val worldId: UUID = UUID.randomUUID()
        val mainThread = AtomicReference<Thread>()
        private val exec = Executors.newSingleThreadExecutor { r ->
            Thread(r, "Stub-World").also { mainThread.set(it) }
        }
        private val alive = AtomicBoolean(true)
        override fun isAlive() = alive.get()
        override fun execute(task: Runnable) { exec.submit(task) }
        fun shutdown() { alive.set(false); exec.shutdownNow(); exec.awaitTermination(2, TimeUnit.SECONDS) }
    }

    private class MapEntity(override val world: WorldExecutor) : EntityHandle {
        val store = ConcurrentHashMap<Any, Any>()
        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> get(typeKey: Any): T? = store[typeKey] as T?
    }

    private val world = StubWorld()
    private lateinit var entity: MapEntity

    @BeforeEach fun setUp() {
        ComponentRegistry.register<PlayerStats>(statsKey)
        entity = MapEntity(world)
    }

    @AfterEach fun tearDown() {
        ComponentRegistry.clear()
        world.shutdown()
    }

    @Test fun `read runs on the world thread and returns projection`() = runTest {
        entity.store[statsKey] = PlayerStats(level = 7)
        val landed = AtomicReference<Thread>()
        val level = read<PlayerStats, Int>(entity) {
            landed.set(Thread.currentThread())
            level
        }
        level shouldBe 7
        landed.get() shouldBe world.mainThread.get()
    }

    @Test fun `modify Unit overload mutates without explicit return type`() = runTest {
        entity.store[statsKey] = PlayerStats(level = 5, xp = 0)
        modify<PlayerStats>(entity) { level = 7 }
        (entity.store[statsKey] as PlayerStats).level shouldBe 7
    }

    @Test fun `modify mutates and writes back`() = runTest {
        entity.store[statsKey] = PlayerStats(level = 1, xp = 50)
        modify<PlayerStats, Unit>(entity) { level += 1; xp = 0 }
        val after = entity.store[statsKey] as PlayerStats
        after.level shouldBe 2
        after.xp shouldBe 0
    }

    @Test fun `read throws when component absent`() = runTest {
        shouldThrow<ComponentNotFoundException> {
            read<PlayerStats, Int>(entity) { level }
        }
    }

    @Test fun `readOrNull returns null when component absent`() = runTest {
        readOrNull<PlayerStats, Int>(entity) { level } shouldBe null
    }

    @Test fun `readOrNull returns null when type unregistered`() = runTest {
        ComponentRegistry.clear()
        readOrNull<PlayerStats, Int>(entity) { level } shouldBe null
    }

    @Test fun `read throws when type unregistered`() = runTest {
        ComponentRegistry.clear()
        shouldThrow<ComponentTypeNotRegisteredException> {
            read<PlayerStats, Int>(entity) { level }
        }
    }

    @Test fun `modify with Boolean return signals success and persists in place`() = runTest {
        entity.store[statsKey] = PlayerStats(level = 1, xp = 100)
        val leveled = modify<PlayerStats, Boolean>(entity) {
            if (xp >= 100) { xp = 0; level += 1; true } else false
        }
        leveled shouldBe true
        (entity.store[statsKey] as PlayerStats).level shouldBe 2

        val again = modify<PlayerStats, Boolean>(entity) {
            if (xp >= 100) { xp = 0; level += 1; true } else false
        }
        again shouldBe false
        (entity.store[statsKey] as PlayerStats).level shouldBe 2
    }
}
