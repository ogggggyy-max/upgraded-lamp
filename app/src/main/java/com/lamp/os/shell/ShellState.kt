package com.lamp.os.shell

import kotlinx.coroutines.flow.MutableStateFlow

/** Shared state between the shell overlays and the home screen. */
object ShellState {
    val locked = MutableStateFlow(false)
    val screenOn = MutableStateFlow(true)
}
