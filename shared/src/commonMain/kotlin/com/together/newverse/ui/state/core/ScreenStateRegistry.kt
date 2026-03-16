package com.together.newverse.ui.state.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Type-safe identifier for a screen state.
 * Each screen should have a unique ScreenId object that specifies its state type.
 *
 * @param S The type of state this screen uses
 */
interface ScreenId<S : Any> {
    /**
     * Unique string key for this screen.
     */
    val key: String
}

/**
 * Registry for managing screen states in a type-safe manner.
 * Provides a centralized place to store and observe screen states.
 */
class ScreenStateRegistry {
    private val _states = MutableStateFlow<Map<String, Any>>(emptyMap())

    /**
     * Observable map of all screen states.
     */
    val states: StateFlow<Map<String, Any>> = _states.asStateFlow()

    /**
     * Gets the current state for a screen, or null if not set.
     */
    @Suppress("UNCHECKED_CAST")
    fun <S : Any> getState(id: ScreenId<S>): S? = _states.value[id.key] as? S

    /**
     * Gets the current state for a screen, or a default value if not set.
     */
    fun <S : Any> getStateOrDefault(id: ScreenId<S>, default: S): S = getState(id) ?: default

    /**
     * Sets the state for a screen.
     */
    fun <S : Any> setState(id: ScreenId<S>, state: S) {
        _states.value += (id.key to state)
    }

    /**
     * Updates the state for a screen using an update function.
     * If the state doesn't exist, the update function receives null.
     */
    fun <S : Any> updateState(id: ScreenId<S>, update: (S?) -> S) {
        val currentState = getState(id)
        val newState = update(currentState)
        setState(id, newState)
    }

    /**
     * Updates the state for a screen only if it exists.
     * If the state doesn't exist, nothing happens.
     */
    fun <S : Any> updateStateIfPresent(id: ScreenId<S>, update: (S) -> S) {
        val currentState = getState(id) ?: return
        setState(id, update(currentState))
    }

    /**
     * Removes the state for a screen.
     */
    fun <S : Any> removeState(id: ScreenId<S>) {
        _states.value -= id.key
    }

    /**
     * Clears all screen states.
     */
    fun clear() {
        _states.value = emptyMap()
    }

    /**
     * Observes the state for a specific screen.
     * Emits null if the state is not set.
     */
    @Suppress("UNCHECKED_CAST")
    fun <S : Any> observeState(id: ScreenId<S>): Flow<S?> = _states
        .map { it[id.key] as? S }
        .distinctUntilChanged()

    /**
     * Observes the state for a specific screen, using a default value if not set.
     */
    fun <S : Any> observeStateOrDefault(id: ScreenId<S>, default: S): Flow<S> = _states
        .map { getState(id) ?: default }
        .distinctUntilChanged()

    /**
     * Returns true if a state exists for the given screen.
     */
    fun <S : Any> hasState(id: ScreenId<S>): Boolean = _states.value.containsKey(id.key)

    /**
     * Returns all registered screen keys.
     */
    fun registeredScreens(): Set<String> = _states.value.keys
}

/**
 * Convenience extension to get state with a non-null assertion.
 * Use only when you're certain the state has been set.
 */
fun <S : Any> ScreenStateRegistry.requireState(id: ScreenId<S>): S =
    getState(id) ?: throw IllegalStateException("State not found for screen: ${id.key}")
