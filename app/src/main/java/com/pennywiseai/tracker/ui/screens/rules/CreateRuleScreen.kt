package com.pennywiseai.tracker.ui.screens.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.model.rule.*
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.FinancialAccountIdentity
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.viewmodel.RulesViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.pennywiseai.tracker.ui.theme.Spacing
import java.util.UUID

/**
 * Transaction-type options for rule condition/action pickers: stored enum name → display label resource.
 * Labels mirror the [com.pennywiseai.tracker.data.database.entity.TransactionType] names (e.g.
 * EXPENSE → "Expense") so what the user picks matches what the rule stores and applies.
 */
internal val RULE_TRANSACTION_TYPE_OPTIONS: List<Pair<String, Int>> = listOf(
    "INCOME" to R.string.filter_type_income,
    "EXPENSE" to R.string.filter_type_expense,
    "CREDIT" to R.string.filter_type_credit,
    "TRANSFER" to R.string.filter_type_transfer,
    "INVESTMENT" to R.string.filter_type_investment
)

/** User-facing label for a stored transaction-type value, falling back to the raw value. */
internal fun ruleTransactionTypeLabel(value: String, context: android.content.Context): String {
    val resId = RULE_TRANSACTION_TYPE_OPTIONS.firstOrNull { it.first.equals(value, ignoreCase = true) }?.second
    return if (resId != null) context.getString(resId) else value
}

/** The operator a field should reset to when it becomes the condition's field. */
private fun TransactionField.defaultConditionOperator(): ConditionOperator = when (this) {
    TransactionField.AMOUNT -> ConditionOperator.LESS_THAN
    TransactionField.TRANSACTION_TIME -> ConditionOperator.LESS_THAN
    TransactionField.TRANSACTION_HOUR -> ConditionOperator.EQUALS
    TransactionField.TRANSACTION_DAY_OF_WEEK -> ConditionOperator.EQUALS
    TransactionField.TRANSACTION_DAY_OF_MONTH -> ConditionOperator.EQUALS
    TransactionField.TRANSACTION_DATE -> ConditionOperator.EQUALS
    TransactionField.ACCOUNT -> ConditionOperator.EQUALS
    TransactionField.TYPE -> ConditionOperator.EQUALS
    else -> ConditionOperator.CONTAINS
}

/**
 * Operators offered for a condition field, paired with their display label resource. The first entry is
 * the field's default (see [defaultConditionOperator]); every default must appear in its list.
 */
private fun conditionOperatorsForField(
    field: TransactionField
): List<Pair<ConditionOperator, Int>> = when (field) {
    TransactionField.AMOUNT -> listOf(
        ConditionOperator.LESS_THAN to R.string.rule_op_less_symbol,
        ConditionOperator.GREATER_THAN to R.string.rule_op_greater_symbol,
        ConditionOperator.EQUALS to R.string.rule_op_equal_symbol
    )
    TransactionField.TYPE -> listOf(
        ConditionOperator.EQUALS to R.string.rule_op_is,
        ConditionOperator.NOT_EQUALS to R.string.rule_op_is_not
    )
    TransactionField.TRANSACTION_TIME -> listOf(
        ConditionOperator.LESS_THAN to R.string.rule_op_before,
        ConditionOperator.GREATER_THAN to R.string.rule_op_after,
        ConditionOperator.GREATER_THAN_OR_EQUAL to R.string.rule_op_at_after,
        ConditionOperator.LESS_THAN_OR_EQUAL to R.string.rule_op_at_before,
        ConditionOperator.EQUALS to R.string.rule_op_exactly
    )
    TransactionField.TRANSACTION_HOUR -> listOf(
        ConditionOperator.EQUALS to R.string.rule_op_is,
        ConditionOperator.LESS_THAN to R.string.rule_op_before,
        ConditionOperator.GREATER_THAN to R.string.rule_op_after
    )
    TransactionField.TRANSACTION_DAY_OF_WEEK -> listOf(
        ConditionOperator.EQUALS to R.string.rule_op_is,
        ConditionOperator.IN to R.string.rule_op_any_of,
        ConditionOperator.NOT_EQUALS to R.string.rule_op_is_not
    )
    TransactionField.TRANSACTION_DAY_OF_MONTH -> listOf(
        ConditionOperator.EQUALS to R.string.rule_op_is,
        ConditionOperator.IN to R.string.rule_op_any_of,
        ConditionOperator.LESS_THAN to R.string.rule_op_before,
        ConditionOperator.GREATER_THAN to R.string.rule_op_after
    )
    TransactionField.TRANSACTION_DATE -> listOf(
        ConditionOperator.EQUALS to R.string.rule_op_is,
        ConditionOperator.LESS_THAN to R.string.rule_op_before,
        ConditionOperator.GREATER_THAN to R.string.rule_op_after
    )
    TransactionField.ACCOUNT -> listOf(
        ConditionOperator.EQUALS to R.string.rule_op_is,
        ConditionOperator.NOT_EQUALS to R.string.rule_op_is_not
    )
    else -> listOf(
        ConditionOperator.CONTAINS to R.string.rule_op_contains,
        ConditionOperator.EQUALS to R.string.rule_op_equals,
        ConditionOperator.STARTS_WITH to R.string.rule_op_starts_with
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRuleScreen(
    onNavigateBack: () -> Unit,
    onSaveRule: (TransactionRule) -> Unit,
    existingRule: TransactionRule? = null,
    // True only when editing a saved rule. A duplicate passes a prefilled [existingRule]
    // (carrying a fresh id) but isEditing = false, so it saves as a brand-new rule.
    // Defaults to false: a non-null prefill must NOT imply edit, or a duplicate that
    // omits this flag would overwrite its source. Callers state edit intent explicitly.
    isEditing: Boolean = false,
    allAccounts: List<RulesViewModel.AccountInfo> = emptyList()
) {
    val context = LocalContext.current
    var ruleName by remember(existingRule) { mutableStateOf(existingRule?.name ?: "") }
    var description by remember(existingRule) { mutableStateOf(existingRule?.description ?: "") }

    // Initialize conditions list from existing rule or use single default condition
    var conditions by remember(existingRule) {
        mutableStateOf(existingRule?.conditions?.toMutableList() ?: mutableListOf(
            RuleCondition(
                field = TransactionField.AMOUNT,
                operator = ConditionOperator.LESS_THAN,
                value = ""
            )
        ))
    }

    // Initialize actions list from existing rule or use a single default action
    var actions by remember(existingRule) {
        mutableStateOf(
            existingRule?.actions?.takeIf { it.isNotEmpty() }
                ?: listOf(
                    RuleAction(
                        field = TransactionField.CATEGORY,
                        actionType = ActionType.SET,
                        value = ""
                    )
                )
        )
    }

    // Holds a pending switch-to-BLOCK while we confirm discarding the other actions.
    var pendingBlockAction by remember { mutableStateOf<RuleAction?>(null) }

    // Common presets for quick setup
    val commonPresets = listOf(
        "Block OTPs" to {
            ruleName = "Block OTP Messages"
            conditions = mutableListOf(
                RuleCondition(
                    field = TransactionField.SMS_TEXT,
                    operator = ConditionOperator.CONTAINS,
                    value = "OTP"
                )
            )
            actions = listOf(
                RuleAction(
                    field = TransactionField.CATEGORY,
                    actionType = ActionType.BLOCK,
                    value = ""
                )
            )
        },
        "Block Small Amounts" to {
            ruleName = "Block Small Transactions"
            conditions = mutableListOf(
                RuleCondition(
                    field = TransactionField.AMOUNT,
                    operator = ConditionOperator.LESS_THAN,
                    value = "10"
                )
            )
            actions = listOf(
                RuleAction(
                    field = TransactionField.CATEGORY,
                    actionType = ActionType.BLOCK,
                    value = ""
                )
            )
        },
        "Small amounts → Food" to {
            ruleName = "Small Food Payments"
            conditions = mutableListOf(
                RuleCondition(
                    field = TransactionField.AMOUNT,
                    operator = ConditionOperator.LESS_THAN,
                    value = "200"
                )
            )
            actions = listOf(
                RuleAction(
                    field = TransactionField.CATEGORY,
                    actionType = ActionType.SET,
                    value = "Food & Dining"
                )
            )
        },
        "Standardize Merchant" to {
            ruleName = "Standardize Merchant Name"
            conditions = mutableListOf(
                RuleCondition(
                    field = TransactionField.MERCHANT,
                    operator = ConditionOperator.CONTAINS,
                    value = "AMZN"
                )
            )
            actions = listOf(
                RuleAction(
                    field = TransactionField.MERCHANT,
                    actionType = ActionType.SET,
                    value = "Amazon"
                )
            )
        },
        "Mark as Income" to {
            ruleName = "Mark Credits as Income"
            conditions = mutableListOf(
                RuleCondition(
                    field = TransactionField.SMS_TEXT,
                    operator = ConditionOperator.CONTAINS,
                    value = "credited"
                )
            )
            actions = listOf(
                RuleAction(
                    field = TransactionField.TYPE,
                    actionType = ActionType.SET,
                    value = "INCOME"
                )
            )
        },
        "Daily Investment" to {
            ruleName = "Daily Investment"
            conditions = mutableListOf(
                RuleCondition(
                    field = TransactionField.TRANSACTION_TIME,
                    operator = ConditionOperator.GREATER_THAN_OR_EQUAL,
                    value = "09:00"
                ),
                RuleCondition(
                    field = TransactionField.TRANSACTION_TIME,
                    operator = ConditionOperator.LESS_THAN,
                    value = "09:30"
                )
            )
            actions = listOf(
                RuleAction(
                    field = TransactionField.CATEGORY,
                    actionType = ActionType.SET,
                    value = "Investments"
                )
            )
        }
    )

    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = remember { HazeState() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = if (isEditing) "Edit Rule" else "Create Rule",
                hasBackButton = true,
                hasActionButton = true,
                navigationContent = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actionContent = {
                    TextButton(
                        onClick = {
                            // Validate: rule name + all conditions have values + all actions are valid
                            val areConditionsValid = conditions.isNotEmpty() &&
                                conditions.all { it.validate() }
                            val isActionValid = actions.isNotEmpty() && actions.all { it.validate() }
                            val isValid = ruleName.isNotBlank() && areConditionsValid && isActionValid

                            if (isValid) {
                                val rule = TransactionRule(
                                    id = existingRule?.id ?: UUID.randomUUID().toString(),
                                    name = ruleName,
                                    description = description.takeIf { it.isNotBlank() },
                                    priority = existingRule?.priority ?: 100,
                                    conditions = conditions.toList(),
                                    actions = actions,
                                    isActive = existingRule?.isActive ?: true,
                                    isSystemTemplate = existingRule?.isSystemTemplate ?: false,
                                    createdAt = existingRule?.createdAt ?: System.currentTimeMillis(),
                                    updatedAt = System.currentTimeMillis()
                                )
                                onSaveRule(rule)
                            }
                        },
                        enabled = ruleName.isNotBlank() &&
                                 conditions.isNotEmpty() &&
                                 conditions.all { it.validate() } &&
                                 actions.isNotEmpty() &&
                                 actions.all { it.validate() }
                    ) {
                        Text("Save")
                    }
                },
                hazeState = hazeState
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(Dimensions.Padding.content)
                .imePadding()
                .overScrollVertical()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            // Quick presets
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(Dimensions.Padding.content),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = stringResource(R.string.rule_quick_templates),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        commonPresets.forEach { (label, action) ->
                            ElevatedAssistChip(
                                onClick = action,
                                label = { Text(label, style = MaterialTheme.typography.bodySmall) }
                            )
                        }
                    }
                }
            }

            // Rule name and description
            TextField(
                value = ruleName,
                onValueChange = { ruleName = it },
                label = { Text(stringResource(R.string.rule_name_label)) },
                placeholder = { Text(stringResource(R.string.rule_name_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            TextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.rule_desc_label)) },
                placeholder = { Text(stringResource(R.string.rule_desc_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 3
            )

            // Conditions section (supports multiple)
            Card {
                Column(
                    modifier = Modifier.padding(Dimensions.Padding.content),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.rule_when_header),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        TextButton(
                            onClick = {
                                conditions = (conditions + RuleCondition(
                                    field = TransactionField.AMOUNT,
                                    operator = ConditionOperator.LESS_THAN,
                                    value = ""
                                )).toMutableList()
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small))
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text(stringResource(R.string.rule_add_condition))
                        }
                    }

                    // Display all conditions
                    conditions.forEachIndexed { index, condition ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(Spacing.md),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                // Header with logical-operator toggle and delete button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (index == 0) {
                                        Text(
                                            text = "Condition",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                    } else {
                                        LogicalOperatorToggle(
                                            selected = condition.logicalOperator,
                                            onSelect = { newOp ->
                                                conditions = conditions.toMutableList().apply {
                                                    set(index, condition.copy(logicalOperator = newOp))
                                                }
                                            }
                                        )
                                    }
                                    if (conditions.size > 1) {
                                        IconButton(
                                            onClick = {
                                                conditions = conditions.toMutableList().apply { removeAt(index) }
                                            },
                                            modifier = Modifier.size(Dimensions.Component.minTouchTarget)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = stringResource(R.string.rule_remove_condition),
                                                modifier = Modifier.size(Dimensions.Icon.small),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }

                                // Field selector
                                ConditionFieldSelector(
                                    condition = condition,
                                    onConditionChange = { newCondition ->
                                        conditions = conditions.toMutableList().apply {
                                            set(index, newCondition)
                                        }
                                    },
                                    allAccounts = allAccounts
                                )
                            }
                        }
                    }
                }
            }

            // Action section (supports multiple)
            Card {
                Column(
                    modifier = Modifier.padding(Dimensions.Padding.content),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.rule_then_header),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        // BLOCK drops the transaction, so it's terminal — no further
                        // actions can run alongside it. Hide "Add Action" while one exists.
                        if (actions.none { it.actionType == ActionType.BLOCK }) {
                            TextButton(
                                onClick = {
                                    actions = actions + RuleAction(
                                        field = TransactionField.CATEGORY,
                                        actionType = ActionType.SET,
                                        value = ""
                                    )
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small))
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                Text(stringResource(R.string.rule_add_action))
                            }
                        }
                    }

                    // Display all actions
                    actions.forEachIndexed { index, action ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(Spacing.md),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                // Header with delete button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (actions.size > 1) stringResource(R.string.rule_action_n_label, index + 1) else stringResource(R.string.rule_action_label),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (actions.size > 1) {
                                        IconButton(
                                            onClick = {
                                                actions = actions.toMutableList().apply { removeAt(index) }
                                            },
                                            modifier = Modifier.size(Dimensions.Component.minTouchTarget)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = stringResource(R.string.rule_remove_action),
                                                modifier = Modifier.size(Dimensions.Icon.small),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }

                                // Per-action editor
                                ActionEditor(
                                    action = action,
                                    onActionChange = { updated ->
                                        // BLOCK is terminal — it drops the transaction, so the other
                                        // actions can't run. If the user switches to BLOCK while other
                                        // actions exist, confirm before discarding them (no silent
                                        // data loss); otherwise apply the change directly.
                                        if (updated.actionType == ActionType.BLOCK && actions.size > 1) {
                                            pendingBlockAction = updated
                                        } else {
                                            actions = actions.toMutableList().apply { set(index, updated) }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Preview
            val showPreview = ruleName.isNotBlank() &&
                             conditions.isNotEmpty() &&
                             conditions.all { it.validate() } &&
                             actions.isNotEmpty() &&
                             actions.all { it.validate() }
            if (showPreview) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(Dimensions.Padding.content),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Text(
                            text = stringResource(R.string.rule_preview_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = buildString {
                                append(stringResource(R.string.rule_when_header))
                                append(" ")
                                conditions.forEachIndexed { index, condition ->
                                    if (index > 0) append(" AND ")
                                    append(when(condition.field) {
                                        TransactionField.AMOUNT -> stringResource(R.string.add_amount_required).replace("*", "").trim().lowercase()
                                        TransactionField.TYPE -> stringResource(R.string.rule_field_type).lowercase()
                                        TransactionField.CATEGORY -> stringResource(R.string.txn_category_label).lowercase()
                                        TransactionField.MERCHANT -> stringResource(R.string.txn_merchant_label).lowercase()
                                        TransactionField.NARRATION -> stringResource(R.string.txn_desc_label).replace("(Optional)", "").trim().lowercase()
                                        TransactionField.SMS_TEXT -> stringResource(R.string.rule_field_sms).lowercase()
                                        TransactionField.BANK_NAME -> stringResource(R.string.rule_field_bank).lowercase()
                                        TransactionField.TRANSACTION_TIME -> stringResource(R.string.rule_field_time_day).lowercase()
                                        TransactionField.TRANSACTION_HOUR -> stringResource(R.string.rule_field_hour).lowercase()
                                        TransactionField.TRANSACTION_DAY_OF_WEEK -> stringResource(R.string.rule_field_dow).lowercase()
                                        TransactionField.TRANSACTION_DAY_OF_MONTH -> stringResource(R.string.rule_field_dom).lowercase()
                                        TransactionField.TRANSACTION_DATE -> stringResource(R.string.rule_field_date).lowercase()
                                        TransactionField.ACCOUNT -> stringResource(R.string.filter_account_label).lowercase()
                                    })
                                    append(" ")
                                    append(when(condition.operator) {
                                        ConditionOperator.LESS_THAN -> stringResource(R.string.rule_op_before)
                                        ConditionOperator.GREATER_THAN -> stringResource(R.string.rule_op_after)
                                        ConditionOperator.LESS_THAN_OR_EQUAL -> stringResource(R.string.rule_op_at_before)
                                        ConditionOperator.GREATER_THAN_OR_EQUAL -> stringResource(R.string.rule_op_at_after)
                                        ConditionOperator.EQUALS -> stringResource(R.string.rule_op_is)
                                        ConditionOperator.CONTAINS -> stringResource(R.string.rule_op_contains)
                                        ConditionOperator.STARTS_WITH -> stringResource(R.string.rule_op_starts_with)
                                        ConditionOperator.IN -> stringResource(R.string.rule_op_any_of)
                                        ConditionOperator.NOT_EQUALS -> stringResource(R.string.rule_op_is_not)
                                        else -> "matches"
                                    })
                                    append(" ")
                                    val dayNames = mapOf(
                                        "1" to "Mon", "2" to "Tue", "3" to "Wed", "4" to "Thu",
                                        "5" to "Fri", "6" to "Sat", "7" to "Sun"
                                    )
                                    when {
                                        condition.field == TransactionField.TYPE -> {
                                            append(ruleTransactionTypeLabel(condition.value, context))
                                        }
                                        condition.field == TransactionField.TRANSACTION_DAY_OF_WEEK -> {
                                            append(condition.value.split(",").joinToString(", ") { dayNames[it.trim()] ?: it })
                                        }
                                        condition.field == TransactionField.ACCOUNT -> {
                                            val parts = condition.value.split("||")
                                            if (parts.size == 2) {
                                                append(AccountBalanceEntity.accountLabel(parts[0], parts[1]))
                                            } else {
                                                append(condition.value)
                                            }
                                        }
                                        else -> append(condition.value)
                                    }
                                }
                                append(", ")
                                actions.forEachIndexed { actionIndex, action ->
                                    if (actionIndex > 0) append(", and ")
                                    if (action.actionType == ActionType.BLOCK) {
                                        append(stringResource(R.string.rule_action_block).lowercase())
                                    } else {
                                        append(when(action.field) {
                                            TransactionField.CATEGORY -> stringResource(R.string.rule_action_set_category).lowercase()
                                            TransactionField.MERCHANT -> stringResource(R.string.rule_action_set_merchant).lowercase()
                                            TransactionField.TYPE -> stringResource(R.string.rule_action_set_type).lowercase()
                                            TransactionField.NARRATION -> stringResource(R.string.rule_action_set_desc).lowercase()
                                            else -> "set field to "
                                        })
                                        append(" ")
                                        // Show user-friendly labels for transaction types in actions too
                                        if (action.field == TransactionField.TYPE) {
                                            append(ruleTransactionTypeLabel(action.value, context))
                                        } else {
                                            append(action.value)
                                        }
                                    }
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }

    // Confirm before BLOCK discards the other (possibly filled-in) actions.
    if (pendingBlockAction != null) {
        AlertDialog(
            onDismissRequest = { pendingBlockAction = null },
            title = { Text(stringResource(R.string.rule_block_title)) },
            text = {
                Text(stringResource(R.string.rule_block_confirm_body))
            },
            confirmButton = {
                TextButton(onClick = {
                    actions = listOf(pendingBlockAction!!)
                    pendingBlockAction = null
                }) { Text(stringResource(R.string.rule_block_btn)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingBlockAction = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConditionFieldSelector(
    condition: RuleCondition,
    onConditionChange: (RuleCondition) -> Unit,
    allAccounts: List<RulesViewModel.AccountInfo> = emptyList()
) {
    var fieldDropdownExpanded by remember { mutableStateOf(false) }

    // Field selector
    ExposedDropdownMenuBox(
        expanded = fieldDropdownExpanded,
        onExpandedChange = { fieldDropdownExpanded = !fieldDropdownExpanded }
    ) {
        val fieldOptions = listOf(
            TransactionField.AMOUNT to stringResource(R.string.add_amount_required).replace("*", "").trim(),
            TransactionField.TYPE to stringResource(R.string.rule_field_type),
            TransactionField.CATEGORY to stringResource(R.string.txn_category_label),
            TransactionField.MERCHANT to stringResource(R.string.txn_merchant_label),
            TransactionField.SMS_TEXT to stringResource(R.string.rule_field_sms),
            TransactionField.BANK_NAME to stringResource(R.string.rule_field_bank),
            TransactionField.TRANSACTION_TIME to stringResource(R.string.rule_field_time_day),
            TransactionField.TRANSACTION_HOUR to stringResource(R.string.rule_field_hour),
            TransactionField.TRANSACTION_DAY_OF_WEEK to stringResource(R.string.rule_field_dow),
            TransactionField.TRANSACTION_DAY_OF_MONTH to stringResource(R.string.rule_field_dom),
            TransactionField.TRANSACTION_DATE to stringResource(R.string.rule_field_date),
            TransactionField.ACCOUNT to stringResource(R.string.filter_account_label)
        )
        TextField(
            value = fieldOptions.firstOrNull { it.first == condition.field }?.second ?: stringResource(R.string.add_amount_required).replace("*", "").trim(),
            onValueChange = { },
            readOnly = true,
            label = { Text(stringResource(R.string.rule_field_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fieldDropdownExpanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = fieldDropdownExpanded,
            onDismissRequest = { fieldDropdownExpanded = false }
        ) {
            fieldOptions.forEach { (field, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        // Reset value AND operator: the previous operator may be invalid for the
                        // new field (e.g. keeping "<" from Amount when switching to Transaction
                        // Type, which only supports is / is not).
                        onConditionChange(
                            condition.copy(
                                field = field,
                                value = "",
                                operator = field.defaultConditionOperator()
                            )
                        )
                        fieldDropdownExpanded = false
                    }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(Spacing.sm))

    // Operator selector
    val operators = conditionOperatorsForField(condition.field)

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.fillMaxWidth()
    ) {
        operators.forEach { (op, labelRes) ->
            FilterChip(
                selected = condition.operator == op,
                onClick = { onConditionChange(condition.copy(operator = op)) },
                label = { Text(stringResource(labelRes)) }
            )
        }
    }

    Spacer(modifier = Modifier.height(Spacing.sm))

    // Value input
    when (condition.field) {
        TransactionField.TYPE -> {
            Text(
                text = stringResource(R.string.rule_select_type),
                style = MaterialTheme.typography.bodySmall
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.fillMaxWidth()
            ) {
                RULE_TRANSACTION_TYPE_OPTIONS.forEach { (type, displayLabelRes) ->
                    FilterChip(
                        selected = condition.value.equals(type, ignoreCase = true),
                        onClick = { onConditionChange(condition.copy(value = type)) },
                        label = {
                            Text(stringResource(displayLabelRes), style = MaterialTheme.typography.bodySmall)
                        }
                    )
                }
            }
        }

        TransactionField.TRANSACTION_DAY_OF_WEEK -> {
            val days = listOf(
                "1" to "Mon", "2" to "Tue", "3" to "Wed", "4" to "Thu",
                "5" to "Fri", "6" to "Sat", "7" to "Sun"
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (condition.operator == ConditionOperator.IN ||
                    condition.operator == ConditionOperator.NOT_IN
                ) {
                    val selectedDays = condition.value.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
                    days.forEach { (value, label) ->
                        FilterChip(
                            selected = value in selectedDays,
                            onClick = {
                                val newSet = if (value in selectedDays) selectedDays - value else selectedDays + value
                                onConditionChange(condition.copy(value = newSet.sorted().joinToString(",")))
                            },
                            label = { Text(label, style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                } else {
                    days.forEach { (value, label) ->
                        FilterChip(
                            selected = condition.value == value,
                            onClick = { onConditionChange(condition.copy(value = value)) },
                            label = { Text(label, style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                }
            }
        }

        TransactionField.TRANSACTION_DAY_OF_MONTH -> {
            TextField(
                value = condition.value,
                onValueChange = { onConditionChange(condition.copy(value = it)) },
                label = { Text(stringResource(R.string.rule_field_dom)) },
                placeholder = { Text(stringResource(R.string.rule_name_placeholder).split(",").getOrNull(0) ?: "e.g., 1") }, // Reuse or generic
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        TransactionField.TRANSACTION_TIME -> {
            var showTimePicker by remember { mutableStateOf(false) }
            val initialHour = condition.value.split(":").getOrNull(0)?.toIntOrNull() ?: 9
            val initialMinute = condition.value.split(":").getOrNull(1)?.toIntOrNull() ?: 0
            val timePickerState = rememberTimePickerState(
                initialHour = initialHour,
                initialMinute = initialMinute
            )

            TextField(
                value = condition.value,
                onValueChange = { },
                readOnly = true,
                label = { Text(stringResource(R.string.rule_time_label)) },
                placeholder = { Text(stringResource(R.string.txn_alias_placeholder)) }, // TODO: specific placeholder
                trailingIcon = {
                    IconButton(onClick = { showTimePicker = true }) {
                        Icon(Icons.Default.AccessTime, contentDescription = stringResource(R.string.txn_select_time))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            if (showTimePicker) {
                AlertDialog(
                    onDismissRequest = { showTimePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            val formatted = String.format("%02d:%02d", timePickerState.hour, timePickerState.minute)
                            onConditionChange(condition.copy(value = formatted))
                            showTimePicker = false
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
                    },
                    text = { TimePicker(state = timePickerState) }
                )
            }
        }

        TransactionField.TRANSACTION_HOUR -> {
            TextField(
                value = condition.value,
                onValueChange = { onConditionChange(condition.copy(value = it)) },
                label = { Text(stringResource(R.string.rule_hour_label)) },
                placeholder = { Text("e.g., 9") }, // TODO: generic
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        TransactionField.TRANSACTION_DATE -> {
            TextField(
                value = condition.value,
                onValueChange = { onConditionChange(condition.copy(value = it)) },
                label = { Text(stringResource(R.string.rule_field_date)) },
                placeholder = { Text("e.g., 2026-03-21") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        TransactionField.ACCOUNT -> {
            // Account dropdown picker
            var accountDropdownExpanded by remember { mutableStateOf(false) }

            val selectedAccount = allAccounts.firstOrNull {
                "${it.bankName}||${it.accountLast4}" == condition.value
            }

            val displayText = selectedAccount?.let {
                it.displayName
            } ?: if (condition.value.isNotBlank()) {
                // Show raw value if account no longer exists
                condition.value
            } else {
                "Select an account"
            }

            Column {
                ExposedDropdownMenuBox(
                    expanded = accountDropdownExpanded,
                    onExpandedChange = { accountDropdownExpanded = !accountDropdownExpanded }
                ) {
                    TextField(
                        value = displayText,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Account") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountDropdownExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = accountDropdownExpanded,
                        onDismissRequest = { accountDropdownExpanded = false }
                    ) {
                        allAccounts.forEach { account ->
                            val key = "${account.bankName}||${account.accountLast4}"
                            val accountTypeLabel = when {
                                account.isCreditCard -> "Credit"
                                account.accountType != null -> account.accountType
                                else -> ""
                            }
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                    ) {
                                        FinancialAccountIdentity(
                                            bankName = account.bankName,
                                            accountLast4 = account.accountLast4
                                        )
                                        if (accountTypeLabel.isNotBlank()) {
                                            AssistChip(
                                                onClick = {},
                                                label = { Text(accountTypeLabel, style = MaterialTheme.typography.labelSmall) },
                                                modifier = Modifier.height(24.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    onConditionChange(condition.copy(value = key))
                                    accountDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                if (allAccounts.isEmpty()) {
                    Text(
                        text = "No accounts found. Add an account first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs)
                    )
                }
            }
        }

        else -> {
            TextField(
                value = condition.value,
                onValueChange = { onConditionChange(condition.copy(value = it)) },
                label = { Text("Value") },
                placeholder = {
                    Text(
                        when(condition.field) {
                            TransactionField.AMOUNT -> "e.g., 200"
                            TransactionField.MERCHANT -> "e.g., Swiggy"
                            TransactionField.SMS_TEXT -> "e.g., salary"
                            TransactionField.CATEGORY -> "e.g., Food & Dining"
                            TransactionField.BANK_NAME -> "e.g., HDFC Bank"
                            else -> "Enter value"
                        }
                    )
                },
                keyboardOptions = if (condition.field == TransactionField.AMOUNT) {
                    KeyboardOptions(keyboardType = KeyboardType.Number)
                } else {
                    KeyboardOptions.Default
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogicalOperatorToggle(
    selected: LogicalOperator,
    onSelect: (LogicalOperator) -> Unit
) {
    val options = listOf(LogicalOperator.AND, LogicalOperator.OR)
    SingleChoiceSegmentedButtonRow {
        options.forEachIndexed { index, op ->
            SegmentedButton(
                selected = selected == op,
                onClick = { onSelect(op) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = {
                    Text(
                        text = op.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionEditor(
    action: RuleAction,
    onActionChange: (RuleAction) -> Unit
) {
    var actionTypeDropdownExpanded by remember { mutableStateOf(false) }
    var actionFieldDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // Action type selector
        ExposedDropdownMenuBox(
            expanded = actionTypeDropdownExpanded,
            onExpandedChange = { actionTypeDropdownExpanded = !actionTypeDropdownExpanded }
        ) {
            TextField(
                value = when(action.actionType) {
                    ActionType.BLOCK -> stringResource(R.string.rule_action_block)
                    ActionType.SET -> stringResource(R.string.rule_action_set)
                    ActionType.APPEND -> "Append to Field"
                    ActionType.PREPEND -> "Prepend to Field"
                    ActionType.CLEAR -> stringResource(R.string.rule_action_clear)
                    ActionType.ADD_TAG -> "Add Tag"
                    ActionType.REMOVE_TAG -> "Remove Tag"
                },
                onValueChange = { },
                readOnly = true,
                label = { Text(stringResource(R.string.rule_action_type_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = actionTypeDropdownExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(
                expanded = actionTypeDropdownExpanded,
                onDismissRequest = { actionTypeDropdownExpanded = false }
            ) {
                listOf(
                    ActionType.BLOCK to stringResource(R.string.rule_action_block),
                    ActionType.SET to stringResource(R.string.rule_action_set),
                    ActionType.CLEAR to stringResource(R.string.rule_action_clear)
                ).forEach { (type, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onActionChange(
                                action.copy(
                                    actionType = type,
                                    value = if (type == ActionType.BLOCK) "" else action.value
                                )
                            )
                            actionTypeDropdownExpanded = false
                        }
                    )
                }
            }
        }

        // Show message for BLOCK action or field selector for others
        if (action.actionType == ActionType.BLOCK) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.xs),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Icon(
                        Icons.Default.Block,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = stringResource(R.string.rule_block_info),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        } else {
            // Action field selector for non-BLOCK actions
            ExposedDropdownMenuBox(
                expanded = actionFieldDropdownExpanded,
                onExpandedChange = { actionFieldDropdownExpanded = !actionFieldDropdownExpanded }
            ) {
                TextField(
                    value = when(action.field) {
                        TransactionField.CATEGORY -> stringResource(R.string.rule_action_set_category)
                        TransactionField.MERCHANT -> stringResource(R.string.rule_action_set_merchant)
                        TransactionField.TYPE -> stringResource(R.string.rule_action_set_type)
                        TransactionField.NARRATION -> stringResource(R.string.rule_action_set_desc)
                        TransactionField.BANK_NAME -> stringResource(R.string.rule_action_set_account)
                        else -> stringResource(R.string.rule_action_set)
                    },
                    onValueChange = { },
                    readOnly = true,
                    label = { Text(stringResource(R.string.rule_action_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = actionFieldDropdownExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = actionFieldDropdownExpanded,
                    onDismissRequest = { actionFieldDropdownExpanded = false }
                ) {
                    listOf(
                        TransactionField.CATEGORY to stringResource(R.string.rule_action_set_category),
                        TransactionField.MERCHANT to stringResource(R.string.rule_action_set_merchant),
                        TransactionField.TYPE to stringResource(R.string.rule_action_set_type),
                        TransactionField.NARRATION to stringResource(R.string.rule_action_set_desc),
                        TransactionField.BANK_NAME to stringResource(R.string.rule_action_set_account)
                    ).forEach { (field, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onActionChange(action.copy(field = field, value = ""))
                                actionFieldDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Dynamic value input based on selected action field
            when (action.field) {
                TransactionField.CATEGORY -> {
                    // Category chips and input
                    val commonCategories = listOf(
                        "Food & Dining", "Transportation", "Shopping",
                        "Bills & Utilities", "Entertainment", "Healthcare",
                        "Investments", "Others"
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        commonCategories.forEach { category ->
                            FilterChip(
                                selected = action.value == category,
                                onClick = { onActionChange(action.copy(value = category)) },
                                label = { Text(category, style = MaterialTheme.typography.bodySmall) }
                            )
                        }
                    }

                    TextField(
                        value = action.value,
                        onValueChange = { onActionChange(action.copy(value = it)) },
                        label = { Text(stringResource(R.string.rule_category_name_label)) },
                        placeholder = { Text(stringResource(R.string.rule_name_placeholder).split(",").getOrNull(0) ?: "e.g., Rent") }, // Reuse
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                TransactionField.TYPE -> {
                    // Transaction type chips with user-friendly labels
                    Text(
                        text = "Select transaction type:",
                        style = MaterialTheme.typography.bodySmall
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RULE_TRANSACTION_TYPE_OPTIONS.forEach { (type, displayLabelRes) ->
                            FilterChip(
                                selected = action.value.equals(type, ignoreCase = true),
                                onClick = { onActionChange(action.copy(value = type)) },
                                label = {
                                    Text(
                                        stringResource(displayLabelRes),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            )
                        }
                    }
                }

                TransactionField.MERCHANT -> {
                    // Merchant name input with common suggestions
                    val commonMerchants = listOf(
                        "Amazon", "Swiggy", "Zomato", "Uber",
                        "Netflix", "Google", "Flipkart"
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        commonMerchants.forEach { merchant ->
                            ElevatedAssistChip(
                                onClick = { onActionChange(action.copy(value = merchant)) },
                                label = { Text(merchant, style = MaterialTheme.typography.bodySmall) }
                            )
                        }
                    }

                    TextField(
                        value = action.value,
                        onValueChange = { onActionChange(action.copy(value = it)) },
                        label = { Text(stringResource(R.string.rule_merchant_name_label)) },
                        placeholder = { Text("e.g., Amazon") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                TransactionField.NARRATION -> {
                    // Description/Narration input
                    TextField(
                        value = action.value,
                        onValueChange = { onActionChange(action.copy(value = it)) },
                        label = { Text(stringResource(R.string.txn_desc_label)) },
                        placeholder = { Text(stringResource(R.string.rule_desc_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 3
                    )
                }

                TransactionField.BANK_NAME -> {
                    // Account / bank the transaction belongs to.
                    TextField(
                        value = action.value,
                        onValueChange = { onActionChange(action.copy(value = it)) },
                        label = { Text(stringResource(R.string.rule_bank_name_label)) },
                        placeholder = { Text("e.g., KBank") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                else -> {
                    // Generic text input for other fields
                    TextField(
                        value = action.value,
                        onValueChange = { onActionChange(action.copy(value = it)) },
                        label = { Text(stringResource(R.string.rule_value_label)) },
                        placeholder = { Text(stringResource(R.string.rule_desc_placeholder).replace("กฎนี้ทำหน้าที่อะไร?", "กรอกข้อมูลที่ต้องการ")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }
    }
}