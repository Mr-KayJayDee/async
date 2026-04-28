package com.mythlane.example.bounty

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Stub component shapes — replace with real `Component<EntityStore>` subclasses in your plugin. */
class Wallet { var gold: Int = 0 }
class BountyState { var bountiesOnMe: List<Bounty> = emptyList() }

data class Bounty(val payerUuid: UUID, val amount: Int)

/** Stubs that return the registered `ComponentType<EntityStore, T>` from your setup. */
object WalletBinding {
    fun componentType(): Any = TODO("Return EntityStore.REGISTRY.register(Wallet::class.java, ...) result.")
}
object BountyStateBinding {
    fun componentType(): Any = TODO("Return EntityStore.REGISTRY.register(BountyState::class.java, ...) result.")
}

/**
 * Tiny in-memory ledger so the example has something to persist on shutdown
 * besides the live ECS components. Replace with your own persistence layer.
 */
object BountyRepo {
    private val placements = ConcurrentHashMap<UUID, MutableList<Pair<UUID, Int>>>()

    fun recordPlacement(payer: UUID, target: UUID, amount: Int) {
        placements.computeIfAbsent(target) { mutableListOf() }.add(payer to amount)
    }

    fun recordPayout(victim: UUID, killer: UUID, amount: Int) {
        placements.remove(victim) // bounty resolved; clear ledger row
        // killer credit is stored on the live Wallet component, not here.
        @Suppress("UNUSED_PARAMETER") killer
        @Suppress("UNUSED_PARAMETER") amount
    }

    fun serialize(): String = placements.entries.joinToString(prefix = "{", postfix = "}") { (target, list) ->
        """"$target":[${list.joinToString { "[\"${it.first}\",${it.second}]" }}]"""
    }
}
