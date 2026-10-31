package com.expensetracker.ui.screens

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expensetracker.data.Backup
import com.expensetracker.data.DateRanges
import com.expensetracker.ui.AppViewModel
import com.expensetracker.ui.Fmt
import com.expensetracker.ui.components.Caption
import com.expensetracker.ui.components.Pill
import com.expensetracker.ui.components.SectionCard
import com.expensetracker.ui.components.SectionTitle
import java.io.File

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onManage: (String) -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenImport: () -> Unit,
    contentPadding: PaddingValues
) {
    val ctx = LocalContext.current
    val currency by vm.currency.collectAsStateWithLifecycle()
    val theme by vm.theme.collectAsStateWithLifecycle()
    val budget by vm.budget.collectAsStateWithLifecycle()
    val lookups by vm.lookups.collectAsStateWithLifecycle()
    val dash by vm.dash.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val backup by vm.backupState.collectAsStateWithLifecycle()
    val rules by vm.rules.collectAsStateWithLifecycle()

    var showBudgetDialog by remember { mutableStateOf(false) }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf<Uri?>(null) }
    var showWipeDialog by remember { mutableStateOf(false) }
    var budgetText by remember { mutableStateOf("") }
    var currencyText by remember { mutableStateOf("") }

    val saveBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(Backup.MIME_JSON)
    ) { uri -> uri?.let { vm.writeBackupTo(it) } }

    val saveCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(Backup.MIME_CSV)
    ) { uri -> uri?.let { vm.writeCsvTo(it) } }

    val openBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { showRestoreDialog = it } }

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            // Persist the grant so backups keep working after a reboot.
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            vm.setBackupFolder(uri)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }

        // ------------------------------------------------------------ backup
        item {
            SectionCard {
                SectionTitle("Backup & Google Drive")
                Spacer(Modifier.height(6.dp))
                Caption(
                    "Your data lives on this phone. Save a backup file to Google Drive, or to any " +
                        "folder or app you choose."
                )
                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { saveBackup.launch(Backup.defaultFileName()) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("Save backup to Drive or files") }

                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { shareBackup(ctx, vm) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) { Text("Share backup…") }

                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { openBackup.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) { Text("Restore from a backup file") }

                Spacer(Modifier.height(18.dp))
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(16.dp))

                Text("Automatic daily backup", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Caption(
                    "Link a folder once — pick your Google Drive folder in the picker — and the app " +
                        "writes a fresh backup there each day it is opened."
                )
                Spacer(Modifier.height(14.dp))

                if (backup.folderUri == null) {
                    OutlinedButton(
                        onClick = { pickFolder.launch(null) },
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) { Text("Link a backup folder") }
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Folder linked", style = MaterialTheme.typography.bodyLarge)
                            Caption(
                                if (backup.lastBackupAt > 0)
                                    "Last backup " + DateRanges.iso(backup.lastBackupAt)
                                else "No backup written yet"
                            )
                        }
                        TextButton(onClick = { vm.clearBackupFolder() }) { Text("Unlink") }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { vm.backupToLinkedFolder() },
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) { Text("Back up now") }
                        OutlinedButton(
                            onClick = { pickFolder.launch(null) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Change") }
                    }
                }
            }
        }

        // ------------------------------------------------------------ export
        item {
            SectionCard {
                SectionTitle("Export")
                Spacer(Modifier.height(6.dp))
                Caption("A spreadsheet of every transaction in the period selected on the Reports tab.")
                Spacer(Modifier.height(14.dp))
                OutlinedButton(
                    onClick = { saveCsv.launch("expenses-" + DateRanges.fileStamp() + ".csv") },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) { Text("Export CSV") }
            }
        }

        // --------------------------------------------------------- preferences
        item {
            SectionCard {
                SectionTitle("Preferences")
                Spacer(Modifier.height(16.dp))

                SettingRow("Currency symbol", currency) {
                    currencyText = currency; showCurrencyDialog = true
                }
                Spacer(Modifier.height(14.dp))
                SettingRow(
                    "Monthly budget",
                    if (budget > 0) Fmt.money(currency, budget) else "Not set"
                ) {
                    budgetText = if (budget > 0) budget.toInt().toString() else ""
                    showBudgetDialog = true
                }

                Spacer(Modifier.height(18.dp))
                Caption("APPEARANCE")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("System", theme == "SYSTEM", { vm.setTheme("SYSTEM") })
                    Pill("Light", theme == "LIGHT", { vm.setTheme("LIGHT") })
                    Pill("Dark", theme == "DARK", { vm.setTheme("DARK") })
                }
            }
        }

        // -------------------------------------------------------------- manage
        item {
            SectionCard {
                SectionTitle("Tools & lists")
                Spacer(Modifier.height(14.dp))
                SettingRow("Import", "File, paste or share") { onOpenImport() }
                Spacer(Modifier.height(14.dp))
                SettingRow("Recurring transactions", rules.size.toString()) { onOpenRecurring() }
                Spacer(Modifier.height(14.dp))
                SettingRow("Categories", lookups.categories.size.toString()) { onManage("categories") }
                Spacer(Modifier.height(14.dp))
                SettingRow("People", lookups.people.size.toString()) { onManage("people") }
                Spacer(Modifier.height(14.dp))
                SettingRow("Accounts", lookups.accounts.size.toString()) { onManage("accounts") }
            }
        }

        // ---------------------------------------------------------------- data
        item {
            SectionCard {
                SectionTitle("Data")
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Caption("Transactions stored")
                    Caption(dash.txCount.toString())
                }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { showWipeDialog = true },
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Text("Delete all transactions", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        item {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Caption("Expense Tracker " + com.expensetracker.BuildConfig.VERSION_NAME)
                Spacer(Modifier.height(4.dp))
                Caption("Everything stays on your device unless you export it.")
            }
        }
    }

    // ------------------------------------------------------------- dialogs

    if (showCurrencyDialog) {
        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text("Currency symbol") },
            text = {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("₹", "$", "€", "£").forEach { s ->
                            Pill(s, currencyText == s, { currencyText = s })
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = currencyText,
                        onValueChange = { if (it.length <= 3) currencyText = it },
                        label = { Text("Symbol") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = currencyText.isNotBlank(),
                    onClick = { vm.setCurrency(currencyText.trim()); showCurrencyDialog = false }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showCurrencyDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showBudgetDialog) {
        AlertDialog(
            onDismissRequest = { showBudgetDialog = false },
            title = { Text("Monthly budget") },
            text = {
                Column {
                    Caption("Shows a progress meter on the dashboard. Leave empty to turn it off.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = budgetText,
                        onValueChange = { v -> budgetText = v.filter { it.isDigit() } },
                        label = { Text("Amount in " + currency) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setBudget(budgetText.toDoubleOrNull() ?: 0.0)
                    showBudgetDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showBudgetDialog = false }) { Text("Cancel") }
            }
        )
    }

    showRestoreDialog?.let { uri ->
        AlertDialog(
            onDismissRequest = { showRestoreDialog = null },
            title = { Text("Restore backup") },
            text = {
                Text(
                    "Merge adds the backup on top of what you already have. Replace deletes your " +
                        "current transactions first."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.restoreFrom(uri, replace = false); showRestoreDialog = null
                }) { Text("Merge") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showRestoreDialog = null }) { Text("Cancel") }
                    TextButton(onClick = {
                        vm.restoreFrom(uri, replace = true); showRestoreDialog = null
                    }) {
                        Text("Replace", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        )
    }

    if (showWipeDialog) {
        AlertDialog(
            onDismissRequest = { showWipeDialog = false },
            title = { Text("Delete all transactions?") },
            text = {
                Text(
                    "Every transaction is removed. Categories, people and accounts are kept. " +
                        "This cannot be undone — save a backup first if you are unsure."
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.wipeAll(); showWipeDialog = false }) {
                    Text("Delete everything", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Writes the backup to the app cache and hands it to the share sheet, so the
 * user can send it to Google Drive, Gmail, or anything else installed.
 */
private fun shareBackup(ctx: android.content.Context, vm: AppViewModel) {
    vm.backupJson { json, name ->
        runCatching {
            val dir = File(ctx.cacheDir, "backups").apply { mkdirs() }
            val file = File(dir, name)
            file.writeText(json)
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = Backup.MIME_JSON
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, name)
                // ClipData is what actually carries the read grant to the share
                // sheet and the chosen app; without it the preview cannot open
                // the file and some targets receive an unreadable uri.
                clipData = ClipData.newUri(ctx.contentResolver, name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(send, "Save backup to…"))
        }.onFailure { vm.toast("Could not prepare the backup file") }
    }
}
