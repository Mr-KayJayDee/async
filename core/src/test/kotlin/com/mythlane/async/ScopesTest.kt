package com.mythlane.async

import com.mythlane.async.scope.PlayerScopes
import com.mythlane.async.scope.PluginScopes
import com.mythlane.async.scope.WorldScopes
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class ScopesTest {

    @AfterEach fun tearDown() = Async.shutdown()

    @Test fun `WorldScopes - cancel stops in-flight work and removes scope`() = runTest {
        val id = UUID.randomUUID()
        val ran = AtomicBoolean(false)
        val job = WorldScopes.of(id).launch { delay(10_000); ran.set(true) }
        WorldScopes.cancel(id)
        job.join()
        ran.get() shouldBe false
        WorldScopes.activeCount() shouldBe 0
    }

    @Test fun `WorldScopes - same UUID returns same scope`() {
        val id = UUID.randomUUID()
        val a = WorldScopes.of(id)
        val b = WorldScopes.of(id)
        (a === b) shouldBe true
    }

    @Test fun `WorldScopes - distinct UUIDs are isolated on cancel`() = runTest {
        val a = UUID.randomUUID(); val b = UUID.randomUUID()
        val ranA = AtomicBoolean(false); val ranB = AtomicBoolean(false)
        val jobA = WorldScopes.of(a).launch { delay(10_000); ranA.set(true) }
        val jobB = WorldScopes.of(b).launch { delay(50); ranB.set(true) }
        WorldScopes.cancel(a)
        jobA.join(); jobB.join()
        ranA.get() shouldBe false
        ranB.get() shouldBe true
    }

    @Test fun `PluginScopes - identity-keyed and cancel works`() = runTest {
        val plugin = Any()
        val ran = AtomicBoolean(false)
        val job = PluginScopes.of(plugin).launch { delay(10_000); ran.set(true) }
        PluginScopes.cancel(plugin)
        job.join()
        ran.get() shouldBe false
        PluginScopes.activeCount() shouldBe 0
    }

    @Test fun `PluginScopes - two equal-but-distinct plugin instances get separate scopes`() {
        // Identity, not equality. Use a value-equal data class instance pair.
        data class P(val name: String)
        val p1 = P("x"); val p2 = P("x")
        (p1 == p2) shouldBe true
        val s1 = PluginScopes.of(p1)
        val s2 = PluginScopes.of(p2)
        (s1 === s2) shouldBe false
    }

    @Test fun `Async shutdown cancels all registries`() = runTest {
        PlayerScopes.of(UUID.randomUUID()).launch { delay(10_000) }
        WorldScopes.of(UUID.randomUUID()).launch { delay(10_000) }
        PluginScopes.of(Any()).launch { delay(10_000) }
        Async.shutdown()
        PlayerScopes.activeCount() shouldBe 0
        WorldScopes.activeCount() shouldBe 0
        PluginScopes.activeCount() shouldBe 0
    }

    @Test fun `cancelAll is idempotent`() {
        WorldScopes.cancelAll()
        WorldScopes.cancelAll()
        PluginScopes.cancelAll()
        PluginScopes.cancelAll()
    }
}
