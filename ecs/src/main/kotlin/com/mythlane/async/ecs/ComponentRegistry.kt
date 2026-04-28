package com.mythlane.async.ecs

import com.mythlane.async.exception.ComponentTypeNotRegisteredException
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Maps a Kotlin component class to its Hytale `ComponentType` token (opaque [Any]).
 *
 * Lookup is a single [ConcurrentHashMap] read on the hot path — no reflection.
 * Register every component type used with the DSL once during plugin start():
 *
 * ```
 * override fun start() {
 *     ComponentRegistry.register<PlayerStats>(PlayerStats.getComponentType())
 * }
 * ```
 *
 * @ThreadSafe
 */
object ComponentRegistry {
    private val map = ConcurrentHashMap<KClass<*>, Any>()

    /** Register [componentType] (a Hytale `ComponentType<T>` instance) for [T]. */
    inline fun <reified T : Any> register(componentType: Any) {
        registerClass(T::class, componentType)
    }

    /** Non-inline overload usable from Java. */
    @JvmStatic
    fun registerClass(klass: KClass<*>, componentType: Any) {
        map[klass] = componentType
    }

    /** Throws [ComponentTypeNotRegisteredException] if [klass] has no mapping. */
    @JvmStatic
    fun keyOf(klass: KClass<*>): Any =
        map[klass] ?: throw ComponentTypeNotRegisteredException(klass.qualifiedName ?: klass.toString())

    /** Returns the mapping for [klass] or null. Mirrors the `OrNull` DSL flavor. */
    @JvmStatic
    fun keyOrNull(klass: KClass<*>): Any? = map[klass]

    /** Test/reset hook. */
    fun clear() { map.clear() }
}
