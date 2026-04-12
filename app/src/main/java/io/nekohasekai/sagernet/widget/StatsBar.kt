package io.nekohasekai.sagernet.widget

import android.annotation.SuppressLint
import android.content.Context
import android.text.format.Formatter
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.TooltipCompat
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.bottomappbar.BottomAppBar
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.ui.ConnectionUiSnapshot

class StatsBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.bottomAppBarStyle,
) : BottomAppBar(context, attrs, defStyleAttr) {
    private lateinit var statusText: TextView
    private lateinit var txText: TextView
    private lateinit var rxText: TextView
    private lateinit var behavior: YourBehavior

    var allowShow = true

    override fun getBehavior(): YourBehavior {
        if (!this::behavior.isInitialized) behavior = YourBehavior { allowShow }
        return behavior
    }

    class YourBehavior(val getAllowShow: () -> Boolean) : Behavior() {

        override fun onNestedScroll(
            coordinatorLayout: CoordinatorLayout, child: BottomAppBar, target: View,
            dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int,
            type: Int, consumed: IntArray,
        ) {
            super.onNestedScroll(
                coordinatorLayout,
                child,
                target,
                dxConsumed,
                dyConsumed + dyUnconsumed,
                dxUnconsumed,
                0,
                type,
                consumed
            )
        }

        override fun slideUp(child: BottomAppBar) {
            if (!getAllowShow()) return
            super.slideUp(child)
        }

        override fun slideDown(child: BottomAppBar) {
            if (!getAllowShow()) return
            super.slideDown(child)
        }
    }

    private fun ensureViewsInitialized() {
        if (!this::statusText.isInitialized) {
            statusText = findViewById(R.id.status)
            txText = findViewById(R.id.tx)
            rxText = findViewById(R.id.rx)
        }
    }

    override fun setOnClickListener(l: OnClickListener?) {
        ensureViewsInitialized()
        super.setOnClickListener(l)
    }

    fun render(snapshot: ConnectionUiSnapshot) {
        ensureViewsInitialized()
        hideOnScroll = snapshot.state == BaseService.State.Connected
        updateSpeed(snapshot.speed.txRateProxy, snapshot.speed.rxRateProxy)
        setStatus(snapshot.statusText ?: defaultStatus(snapshot.state))
        isEnabled = snapshot.state.connected && !snapshot.isConnectionTestInFlight
        post {
            if (snapshot.statsVisible && allowShow) {
                performShow()
            } else {
                performHide()
            }
        }
    }

    private fun setStatus(text: CharSequence) {
        ensureViewsInitialized()
        statusText.text = text
        TooltipCompat.setTooltipText(this, text)
    }

    private fun defaultStatus(state: BaseService.State): CharSequence {
        return context.getText(
            when (state) {
                BaseService.State.Connected -> R.string.vpn_connected
                BaseService.State.Connecting -> R.string.connecting
                BaseService.State.Stopping -> R.string.stopping
                else -> R.string.not_connected
            }
        )
    }

    @SuppressLint("SetTextI18n")
    fun updateSpeed(txRate: Long, rxRate: Long) {
        ensureViewsInitialized()
        txText.text = "▲  ${
            context.getString(
                R.string.speed, Formatter.formatFileSize(context, txRate)
            )
        }"
        rxText.text = "▼  ${
            context.getString(
                R.string.speed, Formatter.formatFileSize(context, rxRate)
            )
        }"
    }
}
