package com.veil.app.local

internal class InMemorySecurityMaterialRepository {
    private val rows = linkedMapOf<Pair<String, String>, ByteArray>()
    private var active = false
    fun get(owner: String, slot: String): ByteArray? = rows[owner to slot]?.copyOf()
    fun put(owner: String, slot: String, payload: ByteArray) { require(SecurityMaterialIds.valid(owner) && SecurityMaterialIds.valid(slot)); rows[owner to slot] = payload.copyOf() }
    fun delete(owner: String, slot: String) { rows.remove(owner to slot) }
    fun <T> transaction(block: InMemorySecurityMaterialRepository.() -> T): T {
        check(!active) { "nested security material transactions are not supported" }
        active = true; val snapshot = rows.mapValues { it.value.copyOf() }.toMutableMap()
        return try { block() } catch (error: Throwable) { rows.clear(); rows.putAll(snapshot); throw error } finally { active = false }
    }
}
