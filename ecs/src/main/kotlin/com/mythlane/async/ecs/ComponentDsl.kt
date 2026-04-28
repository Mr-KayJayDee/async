package com.mythlane.async.ecs

import com.mythlane.async.dispatchers.AsyncDispatchers
import com.mythlane.async.exception.ComponentNotFoundException
import kotlinx.coroutines.withContext

/**
 * Suspending component DSL.
 *
 * Every entry point switches to the entity's world dispatcher, runs the user
 * block on the world thread, and suspends until done. The block receives the
 * **live** component; any mutation is persisted in place — there is no
 * write-back step and no copy-on-read.
 *
 * **No rollback.** If a [modify] block throws after a partial mutation, the
 * mutation stays. This mirrors Hytale ECS semantics; treat blocks as best-effort
 * atomic chunks of work and prefer pure validation before mutation.
 *
 * **Cancellation.** If the coroutine is cancelled before the world thread picks
 * up the task, the block never runs. Once running, blocks complete — they are
 * not interrupted mid-mutation.
 */

/**
 * Read a projection from a component without (intentionally) mutating it.
 *
 * @throws ComponentNotFoundException if the component is missing on [entity].
 * @throws com.mythlane.async.exception.ComponentTypeNotRegisteredException
 *         if [T] was never passed to [ComponentRegistry.register].
 */
suspend inline fun <reified T : Any, R> read(
    entity: EntityHandle,
    crossinline block: T.() -> R,
): R {
    val key = ComponentRegistry.keyOf(T::class)
    val name = T::class.simpleName ?: T::class.toString()
    return withContext(AsyncDispatchers.World(entity.world)) {
        val comp = entity.get<T>(key) ?: throw ComponentNotFoundException(name)
        comp.block()
    }
}

/**
 * Like [read], but returns null if the component is absent or the type is unregistered.
 */
suspend inline fun <reified T : Any, R : Any> readOrNull(
    entity: EntityHandle,
    crossinline block: T.() -> R,
): R? {
    val key = ComponentRegistry.keyOrNull(T::class) ?: return null
    return withContext(AsyncDispatchers.World(entity.world)) {
        entity.get<T>(key)?.block()
    }
}

/**
 * Mutate the live component on the world thread. The block receives the
 * in-store instance — mutations persist without an explicit commit.
 *
 * Returning a value from the block is supported: typically `Unit` for plain
 * mutations, or a `Boolean`/data type when you need to signal an outcome
 * to the caller (e.g. `if (xp >= 100) { xp = 0; level += 1; true } else false`).
 *
 * @throws ComponentNotFoundException if the component is missing on [entity].
 */
suspend inline fun <reified T : Any, R> modify(
    entity: EntityHandle,
    crossinline block: T.() -> R,
): R {
    val key = ComponentRegistry.keyOf(T::class)
    val name = T::class.simpleName ?: T::class.toString()
    return withContext(AsyncDispatchers.World(entity.world)) {
        val comp = entity.get<T>(key) ?: throw ComponentNotFoundException(name)
        comp.block()
    }
}

/**
 * `Unit`-returning convenience overload so callers can write
 * `modify<PlayerStats>(handle) { level += 1 }` without specifying the return type.
 * Kotlin can't infer one of two `reified` type args, hence the dedicated overload.
 */
@JvmName("modifyUnit")
suspend inline fun <reified T : Any> modify(
    entity: EntityHandle,
    crossinline block: T.() -> Unit,
) {
    modify<T, Unit>(entity, block)
}
