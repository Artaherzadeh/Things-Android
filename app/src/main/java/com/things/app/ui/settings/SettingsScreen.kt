package com.things.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.things.app.ui.theme.ThingsColors
import com.things.app.ui.theme.ThingsTheme
import com.things.app.ui.theme.ThingsType

private val SheetBackground = Color(0xFFF9FAFB)
private val IconGray = Color(0xFF8E9096)
private val IconDark = Color(0xFF3A3D45)
private val CloudBlue = Color(0xFF5B8FE8)
private val HelpYellow = Color(0xFFF5B82E)

private enum class SettingsPage { Menu, ThingsCloud }

/** One row of the Settings menu. [plain] draws the icon on its own, without a colored tile. */
private class SettingsItem(
    val title: String,
    val icon: ImageVector,
    val tint: Color,
    val plain: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * The Settings popover: a rounded sheet over a dimmed page. The menu lists the settings groups,
 * and Things Cloud opens its own page in the same sheet with a back button.
 * Static glue: account email and sync time are parameters until real data exists.
 */
@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    accountEmail: String = "",
    lastUpdated: String = "",
    cloudEnabled: Boolean = false,
    cloudSupported: Boolean = false,
    onCloudEnabledChange: (Boolean) -> Unit = {},
    onOpenItem: (String) -> Unit = {},
) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.Menu) }

    val handleDismiss = {
        page = SettingsPage.Menu
        onDismiss()
    }

    BackHandler(enabled = (page == SettingsPage.ThingsCloud)) {
        page = SettingsPage.Menu
    }

    Dialog(
        onDismissRequest = handleDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(36.dp),
            color = SheetBackground,
            shadowElevation = 12.dp,
        ) {
            when (page) {
                SettingsPage.Menu -> SettingsMenu(
                    onClose = handleDismiss,
                    onThingsCloud = { page = SettingsPage.ThingsCloud },
                    onOther = onOpenItem,
                )
                SettingsPage.ThingsCloud -> ThingsCloudPage(
                    email = accountEmail,
                    lastUpdated = lastUpdated,
                    enabled = cloudEnabled,
                    cloudSupported = cloudSupported,
                    onEnabledChange = onCloudEnabledChange,
                    onBack = { page = SettingsPage.Menu },
                    onClose = handleDismiss,
                    onOpenItem = onOpenItem,
                )
            }
        }
    }
}

@Composable
private fun SettingsMenu(
    onClose: () -> Unit,
    onThingsCloud: () -> Unit,
    onOther: (String) -> Unit,
) {
    val main = listOf(
        SettingsItem("Things Cloud", Icons.Filled.CloudDone, CloudBlue, plain = true, onClick = onThingsCloud),
        SettingsItem("Calendar Events", Icons.Filled.CalendarMonth, Color(0xFFE5483D), onClick = { onOther("Calendar Events") }),
        SettingsItem("Reminders Inbox", Icons.Filled.Checklist, Color(0xFF4A8FE7), onClick = { onOther("Reminders Inbox") }),
        SettingsItem("Siri", Icons.Filled.Mic, IconDark, onClick = { onOther("Siri") }),
        SettingsItem("Shortcuts", Icons.Filled.Layers, Color(0xFF3B2F8F), onClick = { onOther("Shortcuts") }),
        SettingsItem("Appearance", Icons.Filled.TextFields, IconDark, onClick = { onOther("Appearance") }),
        SettingsItem("General", Icons.Filled.Tune, IconGray, onClick = { onOther("General") }),
    )
    val more = listOf(
        SettingsItem("Help", Icons.AutoMirrored.Filled.HelpOutline, HelpYellow, onClick = { onOther("Help") }),
        SettingsItem("Import", Icons.Filled.Download, IconGray, onClick = { onOther("Import") }),
        SettingsItem("About", Icons.Filled.Info, CloudBlue, onClick = { onOther("About") }),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(48.dp)) {
            Text(
                text = "Settings",
                modifier = Modifier.align(Alignment.Center),
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                color = ThingsColors.TextPrimary,
            )
            CircleButton(
                icon = Icons.Filled.Close,
                description = "Close",
                onClick = onClose,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
        Spacer(Modifier.height(12.dp))
        main.forEach { SettingsRow(it) }
        Spacer(Modifier.height(20.dp))
        more.forEach { SettingsRow(it) }
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Get Things for Mac and iPhone.",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = ThingsType.RowSubtitle,
        )
    }
}

@Composable
private fun SettingsRow(item: SettingsItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = item.onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (item.plain) Color.Transparent else item.tint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = if (item.plain) item.tint else Color.White,
                modifier = Modifier.size(if (item.plain) 32.dp else 20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = item.title,
            modifier = Modifier.weight(1f),
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = ThingsColors.TextPrimary,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = ThingsColors.TextSecondary,
        )
    }
}

@Composable
private fun ThingsCloudPage(
    email: String,
    lastUpdated: String,
    enabled: Boolean,
    cloudSupported: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onOpenItem: (String) -> Unit,
) {
    val isSignedOut = email.isBlank()
    val isConnected = !isSignedOut && cloudSupported

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(48.dp)) {
            CircleButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                description = "Back",
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            Text(
                text = "Things Cloud",
                modifier = Modifier.align(Alignment.Center),
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                color = ThingsColors.TextPrimary,
            )
            CircleButton(
                icon = Icons.Filled.Close,
                description = "Close",
                onClick = onClose,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
        Spacer(Modifier.height(20.dp))
        Icon(
            imageVector = Icons.Filled.CloudDone,
            contentDescription = null,
            tint = if (isConnected) CloudBlue else IconGray,
            modifier = Modifier.size(112.dp),
        )
        Spacer(Modifier.height(16.dp))
        Switch(
            checked = enabled && cloudSupported,
            onCheckedChange = if (cloudSupported) onEnabledChange else null,
            enabled = cloudSupported,
            colors = SwitchDefaults.colors(
                checkedTrackColor = CloudBlue,
                checkedThumbColor = Color.White,
            ),
            modifier = Modifier.semantics {
                contentDescription = "Things Cloud Sync"
            },
        )
        Spacer(Modifier.height(16.dp))
        if (isConnected) {
            Text(
                text = email,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                color = ThingsColors.TextPrimary,
            )
            Text(
                text = "Your account is active.",
                fontSize = 15.sp,
                color = ThingsColors.TextSecondary,
            )
            if (lastUpdated.isNotBlank()) {
                Text(
                    text = "Last Updated: $lastUpdated",
                    fontSize = 15.sp,
                    color = ThingsColors.TextSecondary,
                )
            }
        } else {
            if (email.isNotBlank()) {
                Text(
                    text = email,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ThingsColors.TextPrimary,
                )
            } else {
                Text(
                    text = "Signed Out",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ThingsColors.TextPrimary,
                )
            }
            Text(
                text = "Things Cloud sync is currently unavailable.",
                fontSize = 15.sp,
                color = ThingsColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(24.dp))
        CloudRow("Edit Account", Icons.Filled.Key) { onOpenItem("Edit Account") }
        CloudRow("Mail to Things", Icons.Filled.Mail) { onOpenItem("Mail to Things") }
        CloudRow("Troubleshooting", Icons.AutoMirrored.Filled.HelpOutline) { onOpenItem("Troubleshooting") }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CloudRow(title: String, icon: ImageVector, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = IconGray, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = ThingsColors.TextPrimary,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = ThingsColors.TextSecondary,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(ThingsColors.Divider))
    }
}

@Composable
private fun CircleButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = ThingsColors.TextPrimary, modifier = Modifier.size(22.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsDialogPreview() {
    ThingsTheme {
        SettingsDialog(onDismiss = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsDialogConnectedPreview() {
    ThingsTheme {
        SettingsDialog(
            onDismiss = {},
            accountEmail = "user@example.com",
            lastUpdated = "Just now",
            cloudEnabled = true,
            cloudSupported = true,
        )
    }
}
