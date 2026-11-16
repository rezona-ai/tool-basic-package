/**
 * [INPUT]: 依赖 ./RuleViewModel，依赖 ui/folders 的 rememberFolderPicker，依赖 ui/components（Forms、RenameUi），依赖 :core:ui 组件与颜色 token，依赖 :core:storage 的已授权文件夹
 * [OUTPUT]: 对外提供 RuleScreen
 * [POS]: ui/rule 的规则页（核心操作页）：1 选文件夹（现场选，或从已授权的文件夹里选）→ 2 调规则 → 3 勾选文件、看新旧名对照 → 执行。
 *        有问题（重名、非法字符…）时主按钮不可用；执行时显示真实进度
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.renamer.ui.rule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appbase.core.CoreServices
import com.appbase.core.storage.files
import com.appbase.core.ui.AppCard
import com.appbase.core.ui.GradientButton
import com.appbase.core.ui.IconBadge
import com.appbase.core.ui.TopBar
import com.appbase.core.ui.theme.Bg
import com.appbase.core.ui.theme.Danger
import com.appbase.core.ui.theme.Line
import com.appbase.core.ui.theme.OnAccent
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.TextPrimary
import com.appbase.core.ui.theme.TextSecondary
import com.appbase.renamer.data.CaseMode
import com.appbase.renamer.data.DateFormat
import com.appbase.renamer.data.DateThen
import com.appbase.renamer.data.RuleType
import com.appbase.renamer.data.SortOrder
import com.appbase.renamer.ui.components.AppTextField
import com.appbase.renamer.ui.components.ChoiceChip
import com.appbase.renamer.ui.components.NameChange
import com.appbase.renamer.ui.components.SecondaryButton
import com.appbase.renamer.ui.components.StepLabel
import com.appbase.renamer.ui.folders.rememberFolderPicker

private val SEPARATORS = listOf("_" to "_", "-" to "-", " " to "space", "" to "none")

@Composable
fun RuleScreen(type: RuleType, onBack: () -> Unit, onDone: (batchId: Long) -> Unit) {
    val vm: RuleViewModel = viewModel(key = "rule-${type.name}") { RuleViewModel(type) }
    val pick = rememberFolderPicker { vm.chooseFolder(it) }
    val access = CoreServices.files.access
    val grants by access.grants.collectAsState()

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize().imePadding()) {
            TopBar(type.label, onBack = onBack)
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                item {
                    StepLabel(1, "Folder")
                    val folder = vm.folder
                    AppCard(Modifier.fillMaxWidth()) {
                        if (folder == null) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Choose the folder that holds the files you want to rename.", color = TextSecondary, fontSize = 14.sp)
                                SecondaryButton("Choose folder") { pick(null) }
                                if (grants.isNotEmpty()) {
                                    Text("Or use a folder you allowed before", color = TextSecondary, fontSize = 12.sp)
                                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        grants.forEach { g -> ChoiceChip(access.display(g.path), selected = false) { vm.chooseFolder(g.path) } }
                                    }
                                }
                            }
                        } else {
                            Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconBadge(Icons.Rounded.Folder, Primary, size = 36.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(access.display(folder), Modifier.weight(1f), color = TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                TextButton(onClick = { pick(null) }) { Text("Change", color = Primary) }
                            }
                        }
                    }
                }
                item {
                    StepLabel(2, "Rule")
                    AppCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { RuleOptions(vm) }
                    }
                }
                if (vm.folder != null) {
                    item { FilesHeader(vm) }
                    val files = vm.files
                    when {
                        files == null -> item {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Primary) }
                        }
                        vm.loadError != null -> item { Text(vm.loadError!!, color = Danger, fontSize = 14.sp) }
                        files.isEmpty() -> item { Text("No files in this folder. Folders inside it are not renamed.", color = TextSecondary, fontSize = 14.sp) }
                        else -> items(vm.entries, key = { it.file.name }) { entry ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.toggle(entry.file.name) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = entry.selected,
                                    onCheckedChange = { vm.toggle(entry.file.name) },
                                    colors = CheckboxDefaults.colors(checkedColor = Primary, checkmarkColor = OnAccent),
                                )
                                val plan = entry.plan
                                if (plan == null) {
                                    NameChange(entry.file.name, entry.file.name, Modifier.weight(1f))
                                } else {
                                    NameChange(plan.oldName, plan.newName, Modifier.weight(1f), problem = plan.problem, problemColor = Danger)
                                }
                            }
                            HorizontalDivider(color = Line)
                        }
                    }
                }
            }
            BottomBar(vm, onDone)
        }
        vm.progress?.let { (done, total) -> ProgressOverlay(done, total) }
    }
}

@Composable
private fun RuleOptions(vm: RuleViewModel) {
    val r = vm.rule
    when (r.type) {
        RuleType.NUMBER -> {
            AppTextField(r.baseName, { vm.rule = r.copy(baseName = it) }, "Base name")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextField(
                    r.start.toString(),
                    { v -> vm.rule = r.copy(start = v.filter(Char::isDigit).take(6).toIntOrNull() ?: 0) },
                    "Start at",
                    Modifier.weight(1f),
                    keyboardType = KeyboardType.Number,
                )
                Column(Modifier.weight(1f)) {
                    Text("Digits", fontSize = 12.sp, color = TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(2, 3, 4).forEach { d -> ChoiceChip("$d", selected = r.digits == d) { vm.rule = r.copy(digits = d) } }
                    }
                }
            }
            Separators(vm)
            Choices("Number in order of", SortOrder.entries, vm.sort, { it.label }) { vm.sort = it }
        }
        RuleType.DATE -> {
            Choices("Date format", DateFormat.entries, r.dateFormat, { it.label }) { vm.rule = r.copy(dateFormat = it) }
            Choices("Then", DateThen.entries, r.dateThen, { it.label }) { vm.rule = r.copy(dateThen = it) }
            Separators(vm)
            Text("Uses the date each file was last changed.", fontSize = 12.sp, color = TextSecondary)
        }
        RuleType.AFFIX -> {
            AppTextField(r.prefix, { vm.rule = r.copy(prefix = it) }, "Add before the name")
            AppTextField(r.suffix, { vm.rule = r.copy(suffix = it) }, "Add after the name")
        }
        RuleType.REPLACE -> {
            AppTextField(r.find, { vm.rule = r.copy(find = it) }, "Find")
            AppTextField(r.replace, { vm.rule = r.copy(replace = it) }, "Replace with (leave empty to remove)")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ignore upper / lower case", Modifier.weight(1f), color = TextPrimary, fontSize = 14.sp)
                Switch(
                    checked = r.ignoreCase,
                    onCheckedChange = { vm.rule = r.copy(ignoreCase = it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Primary, checkedThumbColor = OnAccent),
                )
            }
        }
        RuleType.CASE -> Choices("Change the name to", CaseMode.entries, r.caseMode, { it.label }) { vm.rule = r.copy(caseMode = it) }
    }
    Text("File extensions are always kept.", fontSize = 12.sp, color = TextSecondary)
}

@Composable
private fun Separators(vm: RuleViewModel) {
    Choices("Separator", SEPARATORS, SEPARATORS.first { it.first == vm.rule.separator }, { it.second }) { vm.rule = vm.rule.copy(separator = it.first) }
}

@Composable
private fun <T> Choices(title: String, options: List<T>, current: T, label: (T) -> String, onPick: (T) -> Unit) {
    Column {
        Text(title, fontSize = 12.sp, color = TextSecondary)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { o -> ChoiceChip(label(o), selected = o == current) { onPick(o) } }
        }
    }
}

@Composable
private fun FilesHeader(vm: RuleViewModel) {
    val total = vm.files?.size ?: 0
    Column {
        StepLabel(3, "Files · ${vm.selected.size} of $total selected")
        if (total > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (vm.rule.type != RuleType.NUMBER) {
                        SortOrder.entries.forEach { s -> ChoiceChip("By ${s.label.lowercase()}", selected = vm.sort == s) { vm.sort = s } }
                    }
                }
                val all = vm.selected.size == total
                TextButton(onClick = { vm.selectAll(!all) }) { Text(if (all) "Select none" else "Select all", color = Primary) }
            }
        }
    }
}

@Composable
private fun BottomBar(vm: RuleViewModel, onDone: (Long) -> Unit) {
    val ready = vm.changes.size - vm.problems
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        when {
            vm.folder == null -> Unit
            vm.problems > 0 -> Text("Fix ${vm.problems} name problem${if (vm.problems > 1) "s" else ""} above, or untick those files.", color = Danger, fontSize = 13.sp)
            vm.files != null && ready == 0 -> Text("Nothing to rename with this rule yet.", color = TextSecondary, fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        GradientButton(
            if (ready > 0) "Rename $ready file${if (ready > 1) "s" else ""}" else "Rename",
            enabled = ready > 0 && vm.problems == 0 && vm.progress == null,
        ) { vm.run(onDone) }
    }
}

@Composable
private fun ProgressOverlay(done: Int, total: Int) {
    Box(Modifier.fillMaxSize().background(Bg.copy(alpha = 0.92f)).clickable(enabled = true, onClick = {}), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Renaming…", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else done.toFloat() / total },
                modifier = Modifier.fillMaxWidth(),
                color = Primary,
                trackColor = Line,
            )
            Spacer(Modifier.height(8.dp))
            Text("${done / 2} of ${total / 2} files", color = TextSecondary, fontSize = 13.sp)
        }
    }
}
