package io.nekohasekai.sagernet.ui

import io.nekohasekai.sagernet.aidl.SpeedDisplayData
import io.nekohasekai.sagernet.bg.BaseService

data class ConnectionUiSnapshot(
    val state: BaseService.State = BaseService.State.Idle,
    val speed: SpeedDisplayData = SpeedDisplayData(),
    val statusText: String? = null,
    val statsVisible: Boolean = false,
    val isConnectionTestInFlight: Boolean = false,
)
