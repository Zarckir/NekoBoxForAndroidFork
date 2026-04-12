package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.RuleEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.databinding.LayoutEmptyRouteBinding
import io.nekohasekai.sagernet.databinding.LayoutRouteItemBinding
import io.nekohasekai.sagernet.ktx.FixedLinearLayoutManager
import io.nekohasekai.sagernet.ktx.launchCustomTab
import io.nekohasekai.sagernet.ktx.needReload
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.route.RouteRulePolicy
import io.nekohasekai.sagernet.route.SystemRouteRuleItem
import io.nekohasekai.sagernet.widget.ListListener
import io.nekohasekai.sagernet.widget.UndoSnackbarManager

class RouteFragment : ToolbarFragment(R.layout.layout_route), Toolbar.OnMenuItemClickListener {

    sealed interface RouteRow {
        val stableId: Long

        data object Document : RouteRow {
            override val stableId = 0L
        }

        data class System(val item: SystemRouteRuleItem) : RouteRow {
            override val stableId = item.stableId
        }

        data class User(val rule: RuleEntity) : RouteRow {
            override val stableId = rule.id
        }
    }

    lateinit var activity: MainActivity
    lateinit var ruleListView: RecyclerView
    lateinit var ruleAdapter: RuleAdapter
    lateinit var undoManager: UndoSnackbarManager<RuleEntity>

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activity = requireActivity() as MainActivity

        ViewCompat.setOnApplyWindowInsetsListener(view, ListListener)
        toolbar.setTitle(R.string.menu_route)
        toolbar.inflateMenu(R.menu.add_route_menu)
        toolbar.setOnMenuItemClickListener(this)

        ruleListView = view.findViewById(R.id.route_list)
        ruleListView.layoutManager = FixedLinearLayoutManager(ruleListView)
        ruleAdapter = RuleAdapter()
        ProfileManager.addListener(ruleAdapter)
        ruleListView.adapter = ruleAdapter
        undoManager = UndoSnackbarManager(activity, ruleAdapter)

        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.START
        ) {

            override fun getSwipeDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
            ): Int {
                return if (ruleAdapter.isMovableUserPosition(viewHolder.bindingAdapterPosition)) {
                    super.getSwipeDirs(recyclerView, viewHolder)
                } else {
                    0
                }
            }

            override fun getDragDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
            ): Int {
                return if (ruleAdapter.isMovableUserPosition(viewHolder.bindingAdapterPosition)) {
                    super.getDragDirs(recyclerView, viewHolder)
                } else {
                    0
                }
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val holder = viewHolder as? RuleAdapter.RuleHolder ?: return
                val rule = holder.userRule ?: return
                val index = viewHolder.bindingAdapterPosition
                ruleAdapter.remove(index)
                undoManager.remove(index to rule)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                return if (ruleAdapter.isMovableUserPosition(from) && ruleAdapter.isDropTargetPosition(to)) {
                    ruleAdapter.move(from, to)
                    true
                } else {
                    false
                }
            }

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
            ) {
                super.clearView(recyclerView, viewHolder)
                ruleAdapter.commitMove()
            }
        }).attachToRecyclerView(ruleListView)
    }

    override fun onDestroy() {
        if (::ruleAdapter.isInitialized) {
            ProfileManager.removeListener(ruleAdapter)
        }
        super.onDestroy()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_new_route -> {
                startActivity(Intent(context, RouteSettingsActivity::class.java))
            }

            R.id.action_reset_route -> {
                MaterialAlertDialogBuilder(activity).setTitle(R.string.confirm)
                    .setMessage(R.string.clear_profiles_message)
                    .setPositiveButton(R.string.yes) { _, _ ->
                        runOnDefaultDispatcher {
                            ProfileManager.resetRulesToDefaults()
                            ruleAdapter.reload()
                        }
                    }
                    .setNegativeButton(R.string.no, null)
                    .show()
            }

            R.id.action_manage_assets -> {
                startActivity(Intent(requireContext(), AssetsActivity::class.java))
            }
        }
        return true
    }

    inner class RuleAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>(),
        ProfileManager.RuleListener,
        UndoSnackbarManager.Interface<RuleEntity> {

        private val systemRules = RouteRulePolicy.systemRouteItems()
        private val userRules = ArrayList<RuleEntity>()
        private val updated = LinkedHashMap<Long, RuleEntity>()

        init {
            setHasStableIds(true)
            runOnDefaultDispatcher {
                reload()
            }
        }

        suspend fun reload() {
            val rules = ProfileManager.getRules()
            ruleListView.post {
                userRules.clear()
                userRules.addAll(rules)
                notifyDataSetChanged()
            }
        }

        fun isMovableUserPosition(position: Int): Boolean {
            val row = rowAt(position) as? RouteRow.User ?: return false
            return !RouteRulePolicy.isPinnedUserRule(row.rule)
        }

        fun isDropTargetPosition(position: Int): Boolean {
            return rowAt(position) != RouteRow.Document
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): RecyclerView.ViewHolder {
            return if (viewType == 0) {
                DocumentHolder(LayoutEmptyRouteBinding.inflate(layoutInflater, parent, false))
            } else {
                RuleHolder(LayoutRouteItemBinding.inflate(layoutInflater, parent, false))
            }
        }

        override fun getItemViewType(position: Int): Int {
            return if (position == 0) 0 else 1
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rowAt(position)) {
                RouteRow.Document -> (holder as DocumentHolder).bind()
                is RouteRow.System -> (holder as RuleHolder).bind(row)
                is RouteRow.User -> (holder as RuleHolder).bind(row)
            }
        }

        override fun getItemCount(): Int {
            return displayRows().size
        }

        override fun getItemId(position: Int): Long {
            return rowAt(position).stableId
        }

        fun move(from: Int, to: Int) {
            val movingRule = (rowAt(from) as? RouteRow.User)?.rule ?: return
            if (RouteRulePolicy.isPinnedUserRule(movingRule)) {
                return
            }
            val ordered = RouteRulePolicy.orderedUserRules(userRules.filter { it.id != movingRule.id })
            val insertion = RouteRulePolicy.resolveCustomInsertion(
                ordered = ordered,
                insertionPosition = resolveInsertionPosition(from, to),
                systemRuleCount = systemRules.size,
            )
            applyUserRules(RouteRulePolicy.insertCustomRule(ordered, movingRule, insertion))
        }

        fun commitMove() = runOnDefaultDispatcher {
            if (updated.isNotEmpty()) {
                SagerDatabase.rulesDao.updateRules(updated.values.toList())
                updated.clear()
                needReload()
            }
        }

        fun remove(adapterPosition: Int) {
            val rule = (rowAt(adapterPosition) as? RouteRow.User)?.rule ?: return
            val index = userRules.indexOfFirst { it.id == rule.id }
            if (index == -1) return
            userRules.removeAt(index)
            notifyDataSetChanged()
        }

        override fun undo(actions: List<Pair<Int, RuleEntity>>) {
            actions.sortedBy { it.first }.forEach { (adapterPosition, item) ->
                restoreCustomRule(adapterPosition, item)
            }
        }

        override fun commit(actions: List<Pair<Int, RuleEntity>>) {
            val rules = actions.map { it.second }
            runOnDefaultDispatcher {
                ProfileManager.deleteRules(rules)
            }
        }

        override suspend fun onAdd(rule: RuleEntity) {
            reload()
        }

        override suspend fun onUpdated(rule: RuleEntity) {
            reload()
        }

        override suspend fun onRemoved(ruleId: Long) {
            reload()
        }

        override suspend fun onCleared() {
            reload()
        }

        private fun rowAt(position: Int): RouteRow {
            return displayRows()[position]
        }

        private fun displayRows(): List<RouteRow> {
            val ordered = RouteRulePolicy.orderedUserRules(userRules)
            return buildList {
                add(RouteRow.Document)
                addAll(ordered.customBeforeAds.map { RouteRow.User(it) })
                addAll(ordered.adsRules.map { RouteRow.User(it) })
                addAll(ordered.customBetweenAdsAndRu.map { RouteRow.User(it) })
                addAll(systemRules.map { RouteRow.System(it) })
                addAll(ordered.customBetweenRuAndQuic.map { RouteRow.User(it) })
                addAll(ordered.quicRules.map { RouteRow.User(it) })
                addAll(ordered.customAfterQuic.map { RouteRow.User(it) })
            }
        }

        private fun restoreCustomRule(adapterPosition: Int, item: RuleEntity) {
            val ordered = RouteRulePolicy.orderedUserRules(userRules)
            val insertion = RouteRulePolicy.resolveCustomInsertion(
                ordered = ordered,
                insertionPosition = adapterPosition.coerceIn(1, displayRows().size),
                systemRuleCount = systemRules.size,
            )
            applyUserRules(RouteRulePolicy.insertCustomRule(ordered, item, insertion))
        }

        private fun applyUserRules(orderedRules: List<RuleEntity>) {
            orderedRules.forEach { rule ->
                updated[rule.id] = rule
            }
            userRules.clear()
            userRules.addAll(orderedRules)
            notifyDataSetChanged()
        }

        private fun resolveInsertionPosition(from: Int, to: Int): Int {
            var insertionPosition = if (to > from) to + 1 else to
            if (from < insertionPosition) {
                insertionPosition -= 1
            }
            return insertionPosition.coerceIn(1, displayRows().size)
        }

        inner class DocumentHolder(binding: LayoutEmptyRouteBinding) :
            RecyclerView.ViewHolder(binding.root) {
            fun bind() {
                itemView.setOnClickListener {
                    it.context.launchCustomTab("https://matsuridayo.github.io/nb4a-route/")
                }
            }
        }

        inner class RuleHolder(binding: LayoutRouteItemBinding) :
            RecyclerView.ViewHolder(binding.root) {

            var userRule: RuleEntity? = null
            private val profileName = binding.profileName
            private val profileType = binding.profileType
            private val routeOutbound = binding.routeOutbound
            private val editButton = binding.edit
            private val shareLayout = binding.share
            private val enableSwitch = binding.enable

            fun bind(row: RouteRow) {
                when (row) {
                    is RouteRow.System -> bindSystem(row.item)
                    is RouteRow.User -> bindUser(row.rule)
                    RouteRow.Document -> error("Document row cannot be bound by RuleHolder")
                }
            }

            private fun bindSystem(item: SystemRouteRuleItem) {
                userRule = null
                profileName.text = item.name
                profileType.text = item.summary
                routeOutbound.text = item.outboundLabel
                itemView.setOnClickListener(null)
                editButton.visibility = View.INVISIBLE
                shareLayout.visibility = View.GONE
                enableSwitch.setOnCheckedChangeListener(null)
                enableSwitch.isChecked = true
                enableSwitch.isEnabled = false
            }

            private fun bindUser(rule: RuleEntity) {
                userRule = rule
                profileName.text = rule.displayName()
                profileType.text = rule.mkSummary()
                routeOutbound.text = rule.displayOutbound()
                editButton.visibility = View.VISIBLE
                shareLayout.visibility = View.GONE
                enableSwitch.setOnCheckedChangeListener(null)
                enableSwitch.isEnabled = true
                enableSwitch.isChecked = rule.enabled
                itemView.setOnClickListener {
                    enableSwitch.performClick()
                }
                enableSwitch.setOnCheckedChangeListener { _, isChecked ->
                    runOnDefaultDispatcher {
                        rule.enabled = isChecked
                        ProfileManager.updateRule(rule)
                        needReload()
                    }
                }
                editButton.setOnClickListener {
                    startActivity(Intent(it.context, RouteSettingsActivity::class.java).apply {
                        putExtra(RouteSettingsActivity.EXTRA_ROUTE_ID, rule.id)
                    })
                }
            }
        }
    }
}
