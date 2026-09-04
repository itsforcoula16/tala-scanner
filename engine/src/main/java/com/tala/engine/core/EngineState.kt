package com.tala.engine.core

enum class EngineState {
    IDLE,
    INITIALIZING,
    READY,
    SCANNING,
    PAUSED,
    ERROR;

    fun canStart(): Boolean = this == READY || this == PAUSED
    fun isRunning(): Boolean = this == SCANNING
}
