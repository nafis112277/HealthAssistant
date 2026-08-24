package com.srgroup.healthassistant.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.srgroup.healthassistant.ModelState
import com.srgroup.healthassistant.data.model.ChatMessage

@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    modelState: ModelState,
    isReplying: Boolean,
    onSend: (String) -> Unit,
    onDownloadModel: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to latest message.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(Modifier.fillMaxSize()) {
        // Pinned safety disclaimer.
        Surface(color = MaterialTheme.colorScheme.errorContainer) {
            Text(
                "এই সহায়ক ডাক্তার নয়। রোগ নির্ণয় বা চিকিৎসা দেয় না। " +
                    "জরুরি উপসর্গে অবিলম্বে ডাক্তার বা হাসপাতালে যোগাযোগ করুন।",
                modifier = Modifier.padding(10.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Model status banner — visible until model is Ready.
        when (val state = modelState) {
            is ModelState.NotDownloaded -> Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "চ্যাটের জন্য AI মডেল ডাউনলোড করতে হবে (কয়েকশ MB-কয়েক GB)।",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onDownloadModel) { Text("ডাউনলোড") }
                }
            }
            is ModelState.Downloading -> Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                    Text("মডেল ডাউনলোড হচ্ছে... ${state.progressPercent}%", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { state.progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            ModelState.Loading -> Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("AI মডেল লোড হচ্ছে...", style = MaterialTheme.typography.bodySmall)
                }
            }
            is ModelState.Failed -> Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Text(
                    state.message ?: "AI মডেল লোড ব্যর্থ। ডিভাইসে RAM কম থাকতে পারে। জরুরি বিষয়ে ডাক্তারের সাথে যোগাযোগ করুন।",
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            ModelState.Ready -> Unit // no banner needed
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg -> ChatBubble(msg) }

            // Typing indicator while waiting for AI reply.
            if (isReplying) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Text("উত্তর তৈরি হচ্ছে...", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val inputEnabled = !isReplying && modelState == ModelState.Ready
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("আপনার লক্ষণ লিখুন...") },
                enabled = inputEnabled
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    if (input.isNotBlank()) {
                        onSend(input)
                        input = ""
                    }
                },
                enabled = input.isNotBlank() && inputEnabled
            ) { Text("পাঠান") }
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage) {
    val bubbleColor = if (msg.isUser) MaterialTheme.colorScheme.primaryContainer
                       else MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start
    ) {
        Surface(color = bubbleColor, shape = MaterialTheme.shapes.medium) {
            Text(msg.text, modifier = Modifier.padding(12.dp))
        }
        msg.urgency?.let { level ->
            val (badgeColor, label) = when (level) {
                "High"   -> Color(0xFFD32F2F) to "⚠ উচ্চ ঝুঁকি — ডাক্তার দেখান"
                "Medium" -> Color(0xFFF9A825) to "মধ্যম ঝুঁকি"
                else     -> Color(0xFF388E3C) to "কম ঝুঁকি"
            }
            Text(
                label,
                color = badgeColor,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
