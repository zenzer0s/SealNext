package com.junkfood.seal.ui.page.settings.telegram

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.junkfood.seal.R
import com.junkfood.seal.ui.common.booleanState
import com.junkfood.seal.ui.common.stringState
import com.junkfood.seal.ui.component.BackButton
import com.junkfood.seal.ui.component.ConfirmButton
import com.junkfood.seal.ui.component.DismissButton
import com.junkfood.seal.ui.component.PreferenceInfo
import com.junkfood.seal.ui.component.PreferenceItem
import com.junkfood.seal.ui.component.PreferenceSubtitle
import com.junkfood.seal.ui.component.PreferenceSwitch
import com.junkfood.seal.util.FAST_MODE
import com.junkfood.seal.util.PreferenceUtil.getString
import com.junkfood.seal.util.PreferenceUtil.updateBoolean
import com.junkfood.seal.util.PreferenceUtil.updateString
import com.junkfood.seal.util.TELEGRAM_BOT_TOKEN
import com.junkfood.seal.util.TELEGRAM_CHAT_ID
import com.junkfood.seal.util.TELEGRAM_UPLOAD
import com.junkfood.seal.util.TelegramUtil
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramPreferencesPage(onNavigateBack: () -> Unit) {
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var fastMode by FAST_MODE.booleanState
    var telegramUpload by TELEGRAM_UPLOAD.booleanState

    var showTokenDialog by remember { mutableStateOf(false) }
    var showChatIdDialog by remember { mutableStateOf(false) }

    var savedToken by TELEGRAM_BOT_TOKEN.stringState
    var savedChatId by TELEGRAM_CHAT_ID.stringState

    val tokenConfigured = savedToken.isNotBlank()
    val chatConfigured = savedChatId.isNotBlank()
    val fullyConfigured = tokenConfigured && chatConfigured

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.fast_mode_telegram)) },
                navigationIcon = { BackButton { onNavigateBack() } },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        LazyColumn(contentPadding = contentPadding) {
            item {
                PreferenceInfo(
                    text = stringResource(R.string.fast_mode_telegram_desc)
                )
            }

            item { PreferenceSubtitle(text = stringResource(R.string.fast_mode)) }

            item {
                PreferenceSwitch(
                    title = stringResource(R.string.fast_mode),
                    description = stringResource(R.string.fast_mode_desc),
                    icon = Icons.Outlined.Send,
                    isChecked = fastMode,
                    onClick = {
                        fastMode = !fastMode
                        FAST_MODE.updateBoolean(fastMode)
                    },
                )
            }

            item { PreferenceSubtitle(text = stringResource(R.string.telegram_cloud)) }

            item {
                PreferenceSwitch(
                    title = stringResource(R.string.telegram_upload),
                    description = stringResource(R.string.telegram_upload_desc),
                    icon = Icons.Outlined.Cloud,
                    isChecked = telegramUpload,
                    enabled = fullyConfigured,
                    onClick = {
                        telegramUpload = !telegramUpload
                        TELEGRAM_UPLOAD.updateBoolean(telegramUpload)
                    },
                )
            }

            item {
                PreferenceItem(
                    title = stringResource(R.string.telegram_bot_token),
                    description =
                        if (tokenConfigured) maskToken(savedToken)
                        else stringResource(R.string.tap_to_set),
                    icon = Icons.Outlined.Key,
                    onClick = { showTokenDialog = true },
                )
            }

            item {
                PreferenceItem(
                    title = stringResource(R.string.telegram_chat_id),
                    description =
                        if (chatConfigured) savedChatId
                        else stringResource(R.string.tap_to_set),
                    icon = Icons.Outlined.Numbers,
                    onClick = { showChatIdDialog = true },
                )
            }

            item {
                PreferenceItem(
                    title = stringResource(R.string.telegram_test_connection),
                    description = stringResource(R.string.telegram_test_connection_desc),
                    icon = Icons.Outlined.Bolt,
                    enabled = fullyConfigured,
                    onClick = {
                        scope.launch {
                            val token = TELEGRAM_BOT_TOKEN.getString()
                            val chatId = TELEGRAM_CHAT_ID.getString()
                            snackbarHostState.showSnackbar(
                                message =
                                    TelegramUtil.testConnection(token, chatId)
                                        .fold(
                                            onSuccess = { "✅ Connected!" },
                                            onFailure = { e -> "❌ ${e.message}" },
                                        )
                            )
                        }
                    },
                )
            }
        }
    }

    if (showTokenDialog) {
        TokenInputDialog(
            currentValue = savedToken,
            onDismiss = { showTokenDialog = false },
            onConfirm = { newToken ->
                savedToken = newToken
                TELEGRAM_BOT_TOKEN.updateString(newToken)
                if (newToken.isBlank() && telegramUpload) {
                    telegramUpload = false
                    TELEGRAM_UPLOAD.updateBoolean(false)
                }
                showTokenDialog = false
            },
        )
    }

    if (showChatIdDialog) {
        ChatIdInputDialog(
            currentValue = savedChatId,
            onDismiss = { showChatIdDialog = false },
            onConfirm = { newChatId ->
                savedChatId = newChatId
                TELEGRAM_CHAT_ID.updateString(newChatId)
                if (newChatId.isBlank() && telegramUpload) {
                    telegramUpload = false
                    TELEGRAM_UPLOAD.updateBoolean(false)
                }
                showChatIdDialog = false
            },
        )
    }
}

@Composable
private fun TokenInputDialog(
    currentValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(currentValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Key, null) },
        title = { Text(stringResource(R.string.telegram_bot_token)) },
        text = {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                value = value,
                onValueChange = { value = it },
                label = { Text(stringResource(R.string.telegram_bot_token)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                supportingText = {
                    Text(
                        stringResource(R.string.telegram_token_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
            )
        },
        dismissButton = { DismissButton { onDismiss() } },
        confirmButton = { ConfirmButton { onConfirm(value.trim()) } },
    )
}

@Composable
private fun ChatIdInputDialog(
    currentValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(currentValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Numbers, null) },
        title = { Text(stringResource(R.string.telegram_chat_id)) },
        text = {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                value = value,
                onValueChange = { value = it },
                label = { Text(stringResource(R.string.telegram_chat_id)) },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done,
                    ),
                supportingText = {
                    Text(
                        stringResource(R.string.telegram_chat_id_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
            )
        },
        dismissButton = { DismissButton { onDismiss() } },
        confirmButton = { ConfirmButton { onConfirm(value.trim()) } },
    )
}

private fun maskToken(token: String): String {
    if (token.length <= 8) return "••••••••"
    return token.take(4) + "••••••••" + token.takeLast(4)
}
