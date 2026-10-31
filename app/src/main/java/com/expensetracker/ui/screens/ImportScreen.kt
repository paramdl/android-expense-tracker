package com.expensetracker.ui.screens

import android.content.ClipboardManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expensetracker.data.TxType
import com.expensetracker.ui.AppViewModel
import com.expensetracker.ui.Fmt
import com.expensetracker.ui.components.Caption
import com.expensetracker.ui.components.SectionCard
import com.expensetracker.ui.components.SectionTitle
import com.expensetracker.ui.theme.negativeColor
import com.expensetracker.ui.theme.positiveColor

/**
 * Turns a bank message the user hands over into a transaction.
 *
 * This is the permission-free route to the same parser the SMS reader uses:
 * the message arrives either through the system share sheet or off the
 * clipboard, so the app never touches the inbox.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    vm: AppViewModel,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val currency by vm.currency.collectAsStateWithLifecycle()
    val shared by vm.sharedText.collectAsStateWithLifecycle()
    val filePreview by vm.importPreview.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { vm.openImportFile(it, displayName(ctx, it)) }
    }

    var text by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var resultOk by remember { mutableStateOf(false) }

    // A message shared in from another app lands straight in the box.
    LaunchedEffect(shared) {
        shared?.let {
            text = it
            vm.setSharedText(null)
        }
    }

    val preview = remember(text) { vm.previewMessage(text) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad).imePadding(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SectionCard {
                    SectionTitle("Import a spreadsheet")
                    Spacer(Modifier.height(8.dp))
                    Caption(
                        "Bring in a CSV or Excel export from another money app. Columns are " +
                            "detected automatically, and anything imported twice is skipped."
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            pickFile.launch(
                                arrayOf(
                                    "text/csv", "text/comma-separated-values", "text/plain",
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "application/vnd.ms-excel", "*/*"
                                )
                            )
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text(if (busy) "Reading…" else "Choose a CSV or Excel file") }
                }
            }

            filePreview?.let { p ->
                item {
                    SectionCard {
                        if (p.problem != null) {
                            SectionTitle("Cannot read that file")
                            Spacer(Modifier.height(8.dp))
                            Caption(p.problem!!)
                        } else {
                            SectionTitle("Ready to import")
                            Spacer(Modifier.height(10.dp))
                            Text(
                                p.readable.toString() + " of " + p.totalRows + " rows readable",
                                style = MaterialTheme.typography.titleMedium,
                                color = positiveColor()
                            )
                            Spacer(Modifier.height(8.dp))
                            Caption("Columns: " + p.header.filter { it.isNotBlank() }.joinToString(", "))
                            if (p.samples.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Caption("FIRST FEW")
                                Spacer(Modifier.height(6.dp))
                                p.samples.forEach {
                                    Text(it, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = { vm.clearImportPreview() },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Cancel") }
                                Button(
                                    onClick = { vm.runImport() },
                                    enabled = !busy && p.readable > 0,
                                    modifier = Modifier.weight(1f)
                                ) { Text("Import " + p.readable) }
                            }
                        }
                    }
                }
            }

            item {
                SectionCard {
                    SectionTitle("Or paste a single message")
                    Spacer(Modifier.height(8.dp))
                    Caption(
                        "In your messaging app, open a bank alert, tap Share and pick Expense " +
                            "Tracker — or copy the message and tap Paste below. The same reader " +
                            "that handles automatic capture pulls out the amount, merchant and " +
                            "account. No permissions are involved: nothing is read from your " +
                            "inbox, you hand over one message at a time."
                    )
                }
            }

            item {
                SectionCard {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it; result = null },
                        label = { Text("Bank message") },
                        placeholder = { Text("Rs.450.00 debited from a/c XXXX1234 to VPA swiggy@ybl…") },
                        minLines = 4,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                readClipboard(ctx)?.let { text = it; result = null }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Paste") }
                        OutlinedButton(
                            onClick = { text = ""; result = null },
                            enabled = text.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) { Text("Clear") }
                    }
                }
            }

            if (preview != null) {
                item {
                    val income = preview.type == TxType.INCOME
                    val accent = if (income) positiveColor() else negativeColor()
                    SectionCard {
                        SectionTitle("Found a transaction")
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (preview.merchant.isNotEmpty()) preview.merchant
                                    else if (income) "Income" else "Expense",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (preview.accountHint.isNotEmpty()) {
                                    Spacer(Modifier.height(2.dp))
                                    Caption(preview.accountHint)
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                Fmt.signed(currency, preview.amount, income),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = accent
                            )
                        }
                    }
                }
            } else if (text.isNotBlank()) {
                item {
                    SectionCard {
                        Caption(
                            "No transaction recognised yet. The message needs an amount and a " +
                                "word like debited, credited, spent or paid. OTPs, reminders and " +
                                "failed payments are ignored on purpose."
                        )
                    }
                }
            }

            result?.let { msg ->
                item {
                    SectionCard {
                        Text(
                            msg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (resultOk) positiveColor() else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        vm.importMessage(text) { ok, msg ->
                            resultOk = ok
                            result = msg
                            if (ok) text = ""
                        }
                    },
                    enabled = preview != null,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) {
                    Text("Add to ledger", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** Best-effort file name, used only to tell CSV from XLSX. */
private fun displayName(ctx: Context, uri: android.net.Uri): String {
    runCatching {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) return c.getString(idx) ?: ""
        }
    }
    return uri.lastPathSegment ?: ""
}

private fun readClipboard(ctx: Context): String? {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val clip = cm.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(ctx)?.toString()?.takeIf { it.isNotBlank() }
}
