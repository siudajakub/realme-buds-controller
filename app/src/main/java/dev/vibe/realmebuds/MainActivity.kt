package dev.vibe.realmebuds

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.vibe.realmebuds.bluetooth.AncMode
import dev.vibe.realmebuds.ui.theme.RealmeBudsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RealmeBudsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BudsRoute()
                }
            }
        }
    }
}

@Composable
private fun BudsRoute(
    viewModel: BudsViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.refreshPermissions()
    }

    LaunchedEffect(Unit) {
        viewModel.refreshPermissions()
    }

    BudsScreen(
        uiState = uiState,
        permissionsGranted = hasPermissions(context, viewModel.requiredPermissions()),
        onRequestPermissions = {
            permissionLauncher.launch(viewModel.requiredPermissions())
        },
        onConnect = viewModel::connect,
        onDisconnect = viewModel::disconnect,
        onAncMode = viewModel::setAncMode,
        onBattery = viewModel::queryBattery,
        onInfo = viewModel::queryDeviceInfo,
        onEq = viewModel::queryEq,
        onCustomHexChange = viewModel::updateCustomHex,
        onSendCustomHex = viewModel::sendCustomHex,
        onCycleTouchSide = viewModel::cycleTouchSide,
        onCycleTouchType = viewModel::cycleTouchType,
        onCycleTouchAction = viewModel::cycleTouchAction,
        onSendTouchConfig = viewModel::sendTouchConfig,
        onStartBleDiagnostics = viewModel::startBleDiagnostics,
        onStopBleDiagnostics = viewModel::stopBleDiagnostics,
    )
}

@Composable
private fun BudsScreen(
    uiState: BudsUiState,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onAncMode: (AncMode) -> Unit,
    onBattery: () -> Unit,
    onInfo: () -> Unit,
    onEq: () -> Unit,
    onCustomHexChange: (String) -> Unit,
    onSendCustomHex: () -> Unit,
    onCycleTouchSide: () -> Unit,
    onCycleTouchType: () -> Unit,
    onCycleTouchAction: () -> Unit,
    onSendTouchConfig: () -> Unit,
    onStartBleDiagnostics: () -> Unit,
    onStopBleDiagnostics: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header(uiState)

        if (!permissionsGranted) {
            ActionCard {
                Text(
                    text = "Bluetooth wymaga uprawnień",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onRequestPermissions,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Nadaj uprawnienia")
                }
            }
        }

        ConnectionCard(
            uiState = uiState,
            canConnect = permissionsGranted,
            onConnect = onConnect,
            onDisconnect = onDisconnect,
        )

        BatteryCard(uiState)
        TouchConfigCard(
            uiState = uiState,
            onCycleTouchSide = onCycleTouchSide,
            onCycleTouchType = onCycleTouchType,
            onCycleTouchAction = onCycleTouchAction,
            onSendTouchConfig = onSendTouchConfig,
        )

        ActionCard {
            Text(
                text = "Tryb hałasu",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeButton("ANC", uiState.connected) { onAncMode(AncMode.On) }
                ModeButton("Przezroczystość", uiState.connected) { onAncMode(AncMode.Transparency) }
                ModeButton("Normalny", uiState.connected) { onAncMode(AncMode.Off) }
            }
        }

        ActionCard {
            Text(
                text = "Odczyty",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallActionButton("Bateria", uiState.connected, onBattery, Modifier.weight(1f))
                SmallActionButton("Info test", uiState.connected, onInfo, Modifier.weight(1f))
                SmallActionButton("EQ test", uiState.connected, onEq, Modifier.weight(1f))
            }
        }

        ActionCard {
            Text(
                text = "Pakiet HEX (raw)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.customHexInput,
                onValueChange = onCustomHexChange,
                minLines = 2,
                label = { Text("AA ...") },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onSendCustomHex,
                enabled = uiState.connected,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Wyślij")
            }
        }

        BleDiagnosticsCard(
            uiState = uiState,
            onStartBleDiagnostics = onStartBleDiagnostics,
            onStopBleDiagnostics = onStopBleDiagnostics,
        )

        LogCard(uiState.logLines)
    }
}

@Composable
private fun Header(uiState: BudsUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Realme Buds Controller",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = uiState.status,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "Transport: ${uiState.transport}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
        )
    }
}

@Composable
private fun ConnectionCard(
    uiState: BudsUiState,
    canConnect: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    ActionCard {
        Text(
            text = uiState.deviceName ?: "Słuchawki",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        StatusLine("Słuchawki sparowane", uiState.bondedDeviceFound)
        StatusLine("UUID 0000079A", uiState.opoUuidFound)
        FirmwareLine(uiState.firmwareVersion)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onConnect,
                enabled = canConnect && !uiState.connected && !uiState.scanning && !uiState.connecting,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(if (uiState.connecting) "Łączę" else "Połącz")
            }
            OutlinedButton(
                onClick = onDisconnect,
                enabled = uiState.connected || uiState.scanning || uiState.connecting,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text("Rozłącz")
            }
        }
    }
}

@Composable
private fun FirmwareLine(firmwareVersion: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Firmware", style = MaterialTheme.typography.bodyMedium)
        Text(
            text = firmwareVersion ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusLine(
    label: String,
    ok: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = if (ok) "tak" else "nie",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun TouchConfigCard(
    uiState: BudsUiState,
    onCycleTouchSide: () -> Unit,
    onCycleTouchType: () -> Unit,
    onCycleTouchAction: () -> Unit,
    onSendTouchConfig: () -> Unit,
) {
    ActionCard {
        Text(
            text = "Gesty",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallActionButton(
                text = uiState.selectedTouchSide.label,
                enabled = uiState.connected,
                onClick = onCycleTouchSide,
                modifier = Modifier.weight(1f),
            )
            SmallActionButton(
                text = uiState.selectedTouchType.label,
                enabled = uiState.connected,
                onClick = onCycleTouchType,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallActionButton(
                text = uiState.selectedTouchAction.label,
                enabled = uiState.connected,
                onClick = onCycleTouchAction,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onSendTouchConfig,
                enabled = uiState.connected,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
            ) {
                Text("Ustaw")
            }
        }
    }
}

@Composable
private fun BatteryCard(uiState: BudsUiState) {
    ActionCard {
        Text(
            text = "Bateria",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BatteryTile("L", uiState.leftBattery, Modifier.weight(1f))
            BatteryTile("R", uiState.rightBattery, Modifier.weight(1f))
            BatteryTile("Etui", uiState.caseBattery, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BatteryTile(
    label: String,
    value: Int?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(64.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                text = value?.let { "$it%" } ?: "—",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ModeButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        Text(text)
    }
}

@Composable
private fun SmallActionButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.height(44.dp),
    ) {
        Text(text)
    }
}

@Composable
private fun BleDiagnosticsCard(
    uiState: BudsUiState,
    onStartBleDiagnostics: () -> Unit,
    onStopBleDiagnostics: () -> Unit,
) {
    ActionCard {
        Text(
            text = "Diagnostyka BLE",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onStartBleDiagnostics,
                enabled = !uiState.scanning,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text("Skanuj")
            }
            OutlinedButton(
                onClick = onStopBleDiagnostics,
                enabled = uiState.scanning,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text("Stop")
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(uiState.bleAdvertisements) { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun LogCard(lines: List<String>) {
    ActionCard {
        Text(
            text = "Log",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(lines) { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun ActionCard(content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            content = content,
        )
    }
}

private fun hasPermissions(context: Context, permissions: Array<String>): Boolean {
    return permissions.all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
