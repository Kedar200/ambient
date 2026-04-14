package com.ambient.os

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Represents the live pairing state between this Android device and the
 * Ambient OS desktop agent. Consumed by UI for cinematic state transitions.
 */
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    data class Searching(val attempt: Int) : ConnectionState()
    data class Connecting(val name: String) : ConnectionState()
    data class Connected(
        val name: String,
        val host: String,
        val port: Int,
        val battery: Int? = null,
        val version: String? = null,
    ) : ConnectionState()
}

/**
 * Process-wide holder for the current ConnectionState. UI subscribes via
 * [state]; the discovery controller publishes updates via [update].
 */
object ConnectionStateHolder {
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    fun update(newState: ConnectionState) {
        _state.value = newState
    }

    fun current(): ConnectionState = _state.value
}
