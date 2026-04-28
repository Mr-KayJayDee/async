package com.mythlane.async.exception

/**
 * Sealed root of every exception thrown by the Async library.
 * Consumers can catch this to handle any library-specific failure uniformly.
 */
sealed class AsyncException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** Thrown when dispatching to a world that is no longer alive. */
class WorldClosedException(worldId: String) :
    AsyncException("World $worldId is closed; cannot dispatch")

/** Thrown by `Dispatchers.MainHere` when the current coroutine context has no associated world. */
class NoWorldInContextException :
    AsyncException("No world found in current coroutine context")

/** Thrown when a strict component access cannot find the requested component on an entity ref. */
class ComponentNotFoundException(typeName: String) :
    AsyncException("Component $typeName not present on entity ref")

/** Thrown when [com.mythlane.async.ecs.ComponentRegistry] has no mapping for the requested Kotlin class. */
class ComponentTypeNotRegisteredException(typeName: String) :
    AsyncException(
        "No ComponentType registered for $typeName. " +
            "Call ComponentRegistry.register<$typeName>(componentType) during plugin start()."
    )
