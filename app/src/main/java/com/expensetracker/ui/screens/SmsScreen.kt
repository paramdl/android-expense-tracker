package com.expensetracker.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expensetracker.data.DateRanges
import com.expensetracker.data.PendingSms
import com.expensetracker.data.TxType
import com.expensetracker.ui.AppViewModel
import com.expensetracker.ui.Fmt
import com.expensetracker.ui.components.Caption
import com.expensetracker.ui.components.EmptyState
import com.expensetracker.ui.components.SectionCard
import com.expensetracker.ui.components.SectionTitle
import com.expensetracker.ui.theme.negativeColor
import com.expensetracker.ui.theme.positiveColor

private val SMS_PERMISSIONS = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)

@Composable
fun SmsScreen(vm: AppViewModel, contentPadding: PaddingValues) {
    val ctx = LocalContext.current
    val pending by vm.pending.collectAsStateWithLifecycle()
    val currency by vm.currency.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val backup by vm.backupState.collectAsStateWithLifecycle()

    var granted by remember {
        mutableStateOf(
            SMS_PERMISSIONS.all {
                ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }
    var smsEnabled by remember { mutableStateOf(vm.settings.smsEnabled) }
    var autoRecord by remember { mutableStateOf(vm.settings.smsAutoRecord) }
    var deniedOnce by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        granted = result.values.all { it }
        if (granted) {
            smsEnabled = true
            vm.setSmsEnabled(true)
            vm.scanInbox(full = true)
        } else {
            deniedOnce = true
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "Automatic from SMS",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }

        if (!granted) {
            item {
                SectionCard {
                    Text("📩", style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(10.dp))
                    Text("Read bank alerts automatically", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Caption(
                        "The app scans bank and UPI messages already on this phone, pulls out the " +
                            "amount, merchant and account, and queues them for you to confirm. " +
                            "Messages are read on-device only and are never uploaded anywhere."
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { permLauncher.launch(SMS_PERMISSIONS) },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text("Allow SMS access") }

                    if (deniedOnce) {
                        Spacer(Modifier.height(10.dp))
                        Caption("Permission was denied. You can still enable it from Android settings.")
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                ctx.startActivity(
                                    Intent(
                                        AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:" + ctx.packageName)
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Open app settings") }
                    }
                    Spacer(Modifier.height(12.dp))
                    Caption("You can always add transactions by hand — SMS reading is optional.")
                }
            }
        } else {
            item {
                SectionCard {
                    SectionTitle("Settings")
                    Spacer(Modifier.height(14.dp))
                    ToggleRow(
                        title = "Read incoming bank SMS",
                        subtitle = "New alerts are parsed the moment they arrive",
                        checked = smsEnabled,
                        onChange = { smsEnabled = it; vm.setSmsEnabled(it) }
                    )
                    Spacer(Modifier.height(14.dp))
                    ToggleRow(
                        title = "Record without reviewing",
                        subtitle = "Skip this queue and add matches straight to the ledger",
                        checked = autoRecord,
                        enabled = smsEnabled,
                        onChange = { autoRecord = it; vm.setSmsAutoRecord(it) }
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { vm.scanInbox(full = false) },
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) { Text(if (busy) "Scanning…" else "Scan new") }
                        OutlinedButton(
                            onClick = { vm.scanInbox(full = true) },
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) { Text("Rescan all") }
                    }
                    Spacer(Modifier.height(10.dp))
                    Caption(
                        if (backup.lastSmsScanAt > 0)
                            "Last scan: " + DateRanges.iso(backup.lastSmsScanAt)
                        else "Inbox has not been scanned yet."
                    )
                }
            }
        }

        if (pending.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        pending.size.toString() + " to review",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row {
                        TextButton(onClick = { vm.ignoreAllSms() }) { Text("Ignore all") }
                        TextButton(onClick = { vm.acceptAllSms() }) { Text("Add all") }
                    }
                }
            }
            items(pending, key = { it.id }) { p ->
                PendingCard(
                    p = p,
                    currency = currency,
                    onAccept = { vm.acceptSms(p) },
                    onIgnore = { vm.ignoreSms(p.id) }
                )
            }
        } else if (granted) {
            item {
                EmptyState(
                    emoji = "✅",
                    title = "Nothing waiting",
                    body = "Parsed bank alerts will appear here for you to confirm before they " +
                        "reach your ledger."
                )
            }
        }

        item { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
private fun PendingCard(
    p: PendingSms,
    currency: String,
    onAccept: () -> Unit,
    onIgnore: () -> Unit
) {
    val income = p.type == TxType.INCOME
    val accent = if (income) positiveColor() else negativeColor()
    var expanded by remember { mutableStateOf(false) }

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(4.dp)).background(accent)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (p.merchant.isNotEmpty()) p.merchant else p.sender,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                Caption(
                    listOfNotNull(
                        p.sender.takeIf { p.merchant.isNotEmpty() },
                        p.accountHint.takeIf { it.isNotEmpty() },
                        DateRanges.dayShort(p.receivedAt) + " · " + DateRanges.timeOfDay(p.receivedAt)
                    ).joinToString(" · ")
                )
            }
            Text(
                text = Fmt.signed(currency, p.amount, income),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = accent
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = p.body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) 20 else 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(10.dp)
                .clickable { expanded = !expanded }
        )

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onIgnore, modifier = Modifier.weight(1f)) { Text("Ignore") }
            Button(onClick = onAccept, modifier = Modifier.weight(1f)) { Text("Add") }
        }
    }
}

@Composable
fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(2.dp))
            Caption(subtitle)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
