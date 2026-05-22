package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.entity.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatAppUI(viewModel: ChatViewModel) {
    val contactsList by viewModel.contacts.collectAsState()
    val groupsList by viewModel.groups.collectAsState()
    val messagesList by viewModel.allMessages.collectAsState()
    val callLogsList by viewModel.callLogs.collectAsState()
    val integrationsList by viewModel.integrations.collectAsState()
    val activeMessages by viewModel.activeChatMessages.collectAsState()
    val metricsTuple by viewModel.dashboardMetrics.collectAsState()

    var activeTab by remember { mutableStateOf("chats") } // "chats", "unified", "calls", "metrics", "settings"
    var chatFilter by remember { mutableStateOf("all") } // "all", "groups", "broadcasts"
    var currentContactDetailsId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        bottomBar = {
            if (viewModel.currentScreen != "chat_room") {
                NavigationBar {
                    NavigationBarItem(
                        selected = activeTab == "chats",
                        onClick = { activeTab = "chats" },
                        icon = { Icon(Icons.Default.Chat, contentDescription = "Chats") },
                        label = { Text("Chats") },
                        modifier = Modifier.testTag("nav_chats")
                    )
                    NavigationBarItem(
                        selected = activeTab == "unified",
                        onClick = { activeTab = "unified" },
                        icon = { Icon(Icons.Default.AllInbox, contentDescription = "Unified") },
                        label = { Text("Unified") },
                        modifier = Modifier.testTag("nav_unified")
                    )
                    NavigationBarItem(
                        selected = activeTab == "calls",
                        onClick = { activeTab = "calls" },
                        icon = { Icon(Icons.Default.Call, contentDescription = "Calls") },
                        label = { Text("Calls") },
                        modifier = Modifier.testTag("nav_calls")
                    )
                    NavigationBarItem(
                        selected = activeTab == "metrics",
                        onClick = { activeTab = "metrics" },
                        icon = { Icon(Icons.Default.Equalizer, contentDescription = "Analytics") },
                        label = { Text("Metrics") },
                        modifier = Modifier.testTag("nav_metrics")
                    )
                    NavigationBarItem(
                        selected = activeTab == "settings",
                        onClick = { activeTab = "settings" },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        modifier = Modifier.testTag("nav_settings")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = viewModel.currentScreen,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                },
                label = "ScreenTransition"
            ) { screen ->
                when (screen) {
                    "home" -> {
                        HomeScreen(
                            activeTab = activeTab,
                            viewModel = viewModel,
                            contactsList = contactsList,
                            groupsList = groupsList,
                            messagesList = messagesList,
                            callLogsList = callLogsList,
                            integrationsList = integrationsList,
                            metricsTuple = metricsTuple,
                            chatFilter = chatFilter,
                            onFilterChange = { chatFilter = it }
                        )
                    }
                    "chat_room" -> {
                        val chatId = viewModel.selectedChatId ?: ""
                        ChatRoomScreen(
                            chatId = chatId,
                            viewModel = viewModel,
                            activeMessages = activeMessages,
                            contactsList = contactsList,
                            groupsList = groupsList,
                            onShowDetails = { currentContactDetailsId = it }
                        )
                    }
                }
            }

            // Real-time Calling Overlays
            if (viewModel.activeCallState != ActiveCallState.Idle) {
                CallScreenOverlay(
                    callState = viewModel.activeCallState,
                    onEndCall = { viewModel.endCall() }
                )
            }

            // Floating Custom Alert dialogs
            if (viewModel.showAddContactDialog) {
                AddContactDialog(
                    onDismiss = { viewModel.showAddContactDialog = false },
                    onConfirm = { id, name, isEmail ->
                        viewModel.addNewContact(id, name, isEmail)
                        viewModel.showAddContactDialog = false
                    }
                )
            }

            if (viewModel.showCreateGroupDialog) {
                CreateGroupDialog(
                    contacts = contactsList,
                    onDismiss = { viewModel.showCreateGroupDialog = false },
                    onConfirm = { groupName, memberIds, isBroadcast ->
                        viewModel.createGroupOrBroadcast(groupName, memberIds, isBroadcast)
                        viewModel.showCreateGroupDialog = false
                    }
                )
            }

            // Contact notification configuration drawer
            currentContactDetailsId?.let { cid ->
                val matchingContact = contactsList.firstOrNull { it.id == cid }
                if (matchingContact != null) {
                    ContactDetailsOverlay(
                        contact = matchingContact,
                        onDismiss = { currentContactDetailsId = null },
                        onSaveSettings = { sound, vibrate, mute ->
                            viewModel.configureContactNotifications(cid, sound, vibrate, mute)
                            currentContactDetailsId = null
                        }
                    )
                } else {
                    currentContactDetailsId = null
                }
            }
        }
    }
}

// -------------------------------------------------------------
// HOME ROUTE MANAGER
// -------------------------------------------------------------
@Composable
fun HomeScreen(
    activeTab: String,
    viewModel: ChatViewModel,
    contactsList: List<Contact>,
    groupsList: List<ChatGroup>,
    messagesList: List<Message>,
    callLogsList: List<CallHistory>,
    integrationsList: List<UnifiedInboxIntegration>,
    metricsTuple: Triple<Double, Double, Double>,
    chatFilter: String,
    onFilterChange: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Elegant Header
            HomeTopBar(
                viewModel = viewModel,
                activeTab = activeTab
            )

            // Switch Screen Contents
            when (activeTab) {
                "chats" -> {
                    ChatsTabScreen(
                        viewModel = viewModel,
                        contactsList = contactsList,
                        groupsList = groupsList,
                        messagesList = messagesList,
                        chatFilter = chatFilter,
                        onFilterChange = onFilterChange
                    )
                }
                "unified" -> {
                    UnifiedInboxTabScreen(
                        viewModel = viewModel,
                        messagesList = messagesList,
                        integrationsList = integrationsList
                    )
                }
                "calls" -> {
                    CallsTabScreen(
                        viewModel = viewModel,
                        callLogsList = callLogsList
                    )
                }
                "metrics" -> {
                    MetricsDashboardScreen(
                        metricsTuple = metricsTuple,
                        messagesCount = messagesList.size,
                        contactsCount = contactsList.size
                    )
                }
                "settings" -> {
                    SettingsTabScreen(
                        viewModel = viewModel,
                        integrationsList = integrationsList
                    )
                }
            }
        }

        // Float FAB to insert Contacts/Groups when in chats tab
        if (activeTab == "chats") {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = { viewModel.showCreateGroupDialog = true },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("fab_create_group")
                ) {
                    Icon(Icons.Default.GroupAdd, contentDescription = "Add Group or Broadcast")
                }
                FloatingActionButton(
                    onClick = { viewModel.showAddContactDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("fab_add_contact")
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Add Friend")
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SECTOR: TOPBAR
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(viewModel: ChatViewModel, activeTab: String) {
    val title = when (activeTab) {
        "chats" -> "Whisper Messenger"
        "unified" -> "Unified Social Feed"
        "calls" -> "Secure Calls"
        "metrics" -> "Productivity Metrics"
        "settings" -> "Account & Settings"
        else -> "Secure Messenger"
    }

    CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        ),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    letterSpacing = 0.5.sp
                )
            }
        },
        actions = {
            IconButton(
                onClick = { viewModel.toggleTheme() },
                modifier = Modifier.testTag("toggle_dark_theme")
            ) {
                Icon(
                    imageVector = if (viewModel.isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Switch Theme",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    )
}

// -------------------------------------------------------------
// TAB 1: E2E CHATS (DIRECT, GROUPS, BROADCASTS)
// -------------------------------------------------------------
@Composable
fun ChatsTabScreen(
    viewModel: ChatViewModel,
    contactsList: List<Contact>,
    groupsList: List<ChatGroup>,
    messagesList: List<Message>,
    chatFilter: String,
    onFilterChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Filter Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = chatFilter == "all",
                onClick = { onFilterChange("all") },
                label = { Text("All Conversations") }
            )
            FilterChip(
                selected = chatFilter == "groups",
                onClick = { onFilterChange("groups") },
                label = { Text("Groups Only") }
            )
            FilterChip(
                selected = chatFilter == "broadcasts",
                onClick = { onFilterChange("broadcasts") },
                label = { Text("Broadcast Lists") }
            )
        }

        // List display
        if (contactsList.isEmpty() && groupsList.isEmpty()) {
            EmptyStatePlaceholder(
                icon = Icons.Default.Comment,
                title = "No Conversations Found",
                subtitle = "Tap the plus button below to add friends or create end-to-end encrypted group/broadcast arrays!"
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // Determine item display order based on latest interactions
                // Show Group matches
                if (chatFilter == "all" || chatFilter == "groups" || chatFilter == "broadcasts") {
                    val filteredGroups = groupsList.filter {
                        if (chatFilter == "broadcasts") it.isBroadcast else if (chatFilter == "groups") !it.isBroadcast else true
                    }
                    items(filteredGroups) { group ->
                        val groupMsgs = messagesList.filter { it.chatId == group.groupId }
                        val lastMsg = groupMsgs.maxByOrNull { it.timestamp }
                        val unreadCount = groupMsgs.count { !it.isRead && it.senderId != "me" }

                        ChatListItem(
                            title = group.name,
                            lastText = lastMsg?.content ?: "Created secure chat.",
                            timestamp = lastMsg?.timestamp ?: group.createdTimestamp,
                            isGroup = true,
                            isBroadcast = group.isBroadcast,
                            isEncrypted = true,
                            unreadCount = unreadCount,
                            avatarUrl = null,
                            onClick = { viewModel.selectChat(group.groupId) }
                        )
                    }
                }

                // Show Direct contacts
                if (chatFilter == "all") {
                    items(contactsList) { contact ->
                        val chatMsgs = messagesList.filter { it.chatId == contact.id }
                        val lastMsg = chatMsgs.maxByOrNull { it.timestamp }
                        val unreadCount = chatMsgs.count { !it.isRead && it.senderId != "me" }

                        ChatListItem(
                            title = contact.name,
                            lastText = lastMsg?.content ?: "No messages yet. Send encrypted message...",
                            timestamp = lastMsg?.timestamp ?: 0L,
                            isGroup = false,
                            isBroadcast = false,
                            isEncrypted = true,
                            unreadCount = unreadCount,
                            avatarUrl = contact.avatarUrl,
                            onClick = { viewModel.selectChat(contact.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatListItem(
    title: String,
    lastText: String,
    timestamp: Long,
    isGroup: Boolean,
    isBroadcast: Boolean,
    isEncrypted: Boolean,
    unreadCount: Int,
    avatarUrl: String?,
    onClick: () -> Unit
) {
    val systemTime = remember(timestamp) {
        if (timestamp > 0) {
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(timestamp))
        } else ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(modifier = Modifier.size(54.dp)) {
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = title,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                val backgroundColors = if (isBroadcast) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
                val icon = if (isBroadcast) Icons.Default.Campaign else if (isGroup) Icons.Default.Group else Icons.Default.Person
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(backgroundColors),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Status Indicator Circle for direct contacts
            if (!isGroup && !isBroadcast) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(StatusGreen)
                        .border(1.5.dp, MaterialTheme.colorScheme.background, CircleShape)
                        .align(Alignment.BottomEnd)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Titles and Text Details
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (systemTime.isNotEmpty()) {
                    Text(
                        text = systemTime,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isEncrypted) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Encrypted",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(14.dp)
                            .padding(end = 2.dp)
                    )
                }
                Text(
                    text = lastText,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = unreadCount.toString(),
                            fontSize = 11.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 86.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    )
}

// -------------------------------------------------------------
// TAB 2: UNIFIED SOCIAL INBOX (AGGREGATED MESSAGES)
// -------------------------------------------------------------
@Composable
fun UnifiedInboxTabScreen(
    viewModel: ChatViewModel,
    messagesList: List<Message>,
    integrationsList: List<UnifiedInboxIntegration>
) {
    val socialMessages = remember(messagesList) {
        messagesList.filter { it.chatId == "unified_inbox" }.sortedByDescending { it.timestamp }
    }

    val connectedIntegrations = remember(integrationsList) {
        integrationsList.filter { it.isConnected }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Connected Services Indicator strip
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Aggregated Social Pipelines",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (connectedIntegrations.isEmpty()) {
                    Text(
                        text = "No active integrations streaming. Go to Settings to link X, LinkedIn, Facebook or Instagram.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        connectedIntegrations.forEach { platform ->
                            SocialLabelIndicator(platformId = platform.platformId)
                        }
                    }
                }
            }
        }

        // List aggregated messages
        if (socialMessages.isEmpty()) {
            EmptyStatePlaceholder(
                icon = Icons.Default.AllInbox,
                title = "Unified Inbox is Empty",
                subtitle = "Linked platforms will sync update tokens here. Try sending key prompts or ensuring integrations are configured!"
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                items(socialMessages) { msg ->
                    UnifiedInboxMessageCard(message = msg)
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
fun UnifiedInboxMessageCard(message: Message) {
    val platformColors = when (message.senderId) {
        "x" -> Color(0xFF1DA1F2)
        "facebook" -> Color(0xFF1877F2)
        "instagram" -> Color(0xFFE4405F)
        "linkedin" -> Color(0xFF0A66C2)
        else -> MaterialTheme.colorScheme.secondary
    }

    val platformIcon = when (message.senderId) {
        "x" -> Icons.Default.AlternateEmail
        "facebook" -> Icons.Default.Facebook
        "instagram" -> Icons.Default.PhotoCamera
        "linkedin" -> Icons.Default.Work
        else -> Icons.Default.Share
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Platform badge icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(platformColors.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = platformIcon,
                    contentDescription = null,
                    tint = platformColors,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message.senderName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestamp)),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = message.content,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (message.mediaType != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = message.mediaUri ?: "Attachment File",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SocialLabelIndicator(platformId: String) {
    val color = when (platformId) {
        "x" -> Color(0xFF1DA1F2)
        "facebook" -> Color(0xFF1877F2)
        "instagram" -> Color(0xFFE4405F)
        "linkedin" -> Color(0xFF0A66C2)
        else -> Color.Gray
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = platformId.uppercase(),
            fontSize = 10.sp,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

// -------------------------------------------------------------
// TAB 3: CALL HISTORY (RINGING & SIMULATION HOOKS)
// -------------------------------------------------------------
@Composable
fun CallsTabScreen(viewModel: ChatViewModel, callLogsList: List<CallHistory>) {
    if (callLogsList.isEmpty()) {
        EmptyStatePlaceholder(
                icon = Icons.Default.PhoneCallback,
                title = "No Calls Placed",
                subtitle = "Tap a contact's profile in the chat box to trigger high-fidelity end-to-end encrypted audio or video call signals!"
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(callLogsList) { log ->
                CallHistoryListItem(
                    log = log,
                    onCallback = { viewModel.startCall(log.contactId, log.isVideo) }
                )
            }
        }
    }
}

@Composable
fun CallHistoryListItem(log: CallHistory, onCallback: () -> Unit) {
    val dateText = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(Date(log.timestamp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (log.wasMissed) Color.Red.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (log.isVideo) Icons.Default.Videocam else Icons.Default.Phone,
                    contentDescription = null,
                    tint = if (log.wasMissed) Color.Red else MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = log.contactName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (log.wasMissed) Icons.Default.CallMissed else Icons.Default.CallReceived,
                        contentDescription = null,
                        tint = if (log.wasMissed) Color.Red else StatusGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (log.wasMissed) "Missed • $dateText" else "Connected (${log.durationSecs}s) • $dateText",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        IconButton(
            onClick = onCallback,
            modifier = Modifier.testTag("callback_button_${log.contactId}")
        ) {
            Icon(
                imageVector = if (log.isVideo) Icons.Default.Videocam else Icons.Default.Call,
                contentDescription = "Callback",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    )
}

// -------------------------------------------------------------
// TAB 4: METRICS DASHBOARD GAUGE DISPLAY
// -------------------------------------------------------------
@Composable
fun MetricsDashboardScreen(
    metricsTuple: Triple<Double, Double, Double>,
    messagesCount: Int,
    contactsCount: Int
) {
    val averageResponse = metricsTuple.first
    val engagementRate = metricsTuple.second
    val encryptionCoverage = metricsTuple.third

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Real-Time Engagement & Encryption Speed",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Arc progress dial drawing
                    Box(
                        modifier = Modifier.size(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Track circular
                            drawArc(
                                color = Color.LightGray.copy(alpha = 0.25f),
                                startAngle = 135f,
                                sweepAngle = 270f,
                                useCenter = false,
                                size = Size(size.width, size.height),
                                style = Stroke(width = 16f, cap = StrokeCap.Round)
                            )
                            // Progress Arc based on Engagement
                            drawArc(
                                color = MintPrimary,
                                startAngle = 135f,
                                sweepAngle = (270f * (engagementRate / 100.0)).toFloat(),
                                useCenter = false,
                                size = Size(size.width, size.height),
                                style = Stroke(width = 16f, cap = StrokeCap.Round)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${engagementRate.toInt()}%",
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Engagement Level",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Secondary Stat: Response Delta Time
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Avg Response Time",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = String.format("%.1f mins", averageResponse),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Based on secure user response intervals.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }

                // Security stat Card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "E2E Encryption Coverage",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = String.format("%.1f%%", encryptionCoverage),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusGreen
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Strict end-to-end local routing coverage.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Data Matrix Ledger",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LedgerRow(label = "Encrypted Local Messages Saved", value = messagesCount.toString())
                    LedgerRow(label = "Identified Security Contacts", value = contactsCount.toString())
                    LedgerRow(label = "Private Broadcast Recipients Linked", value = "2 clients")
                }
            }
        }
    }
}

@Composable
fun LedgerRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

// -------------------------------------------------------------
// TAB 5: COMPREHENSIVE SETTINGS & SOCIAL SYNC
// -------------------------------------------------------------
@Composable
fun SettingsTabScreen(
    viewModel: ChatViewModel,
    integrationsList: List<UnifiedInboxIntegration>
) {
    var emailInput by remember { mutableStateOf(viewModel.myEmail) }
    var phoneInput by remember { mutableStateOf(viewModel.myNumber) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Credentials & Privacy Setup",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = {
                            emailInput = it
                            viewModel.myEmail = it
                        },
                        label = { Text("Privacy Email Account ID") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = {
                            phoneInput = it
                            viewModel.myNumber = it
                        },
                        label = { Text("Cellphone Number ID") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Whisper permits dual authentication registers (either cellphone number OR email). This bypasses cell tower tracking metrics, promoting sovereign end-to-end encryption grids.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Text(
                text = "Appearance & Theme Settings",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (viewModel.isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = "Theme Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Dark Mode Override",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (viewModel.isDarkMode) "Comfortable dark theme active" else "Bright light theme active",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = viewModel.isDarkMode,
                        onCheckedChange = { viewModel.setDarkModeEnabled(it) },
                        modifier = Modifier.testTag("switch_dark_mode")
                    )
                }
            }
        }

        item {
            Text(
                text = "Social Media Unified Sync",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SocialIntegrationController(
                        platformId = "x",
                        label = "X (Twitter)",
                        icon = Icons.Default.AlternateEmail,
                        integration = integrationsList.firstOrNull { it.platformId == "x" },
                        onToggle = { name, connect -> viewModel.toggleIntegrationState("x", name, connect) }
                    )
                    SocialIntegrationController(
                        platformId = "facebook",
                        label = "Facebook Messenger",
                        icon = Icons.Default.Facebook,
                        integration = integrationsList.firstOrNull { it.platformId == "facebook" },
                        onToggle = { name, connect -> viewModel.toggleIntegrationState("facebook", name, connect) }
                    )
                    SocialIntegrationController(
                        platformId = "instagram",
                        label = "Instagram direct",
                        icon = Icons.Default.PhotoCamera,
                        integration = integrationsList.firstOrNull { it.platformId == "instagram" },
                        onToggle = { name, connect -> viewModel.toggleIntegrationState("instagram", name, connect) }
                    )
                    SocialIntegrationController(
                        platformId = "linkedin",
                        label = "LinkedIn Messaging",
                        icon = Icons.Default.Work,
                        integration = integrationsList.firstOrNull { it.platformId == "linkedin" },
                        onToggle = { name, connect -> viewModel.toggleIntegrationState("linkedin", name, connect) }
                    )
                }
            }
        }
    }
}

@Composable
fun SocialIntegrationController(
    platformId: String,
    label: String,
    icon: ImageVector,
    integration: UnifiedInboxIntegration?,
    onToggle: (String, Boolean) -> Unit
) {
    val isConnected = integration?.isConnected ?: false
    var userNameInput by remember(integration) { mutableStateOf(integration?.username ?: "") }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (isConnected) {
                    Text(
                        text = "Connected as: $userNameInput",
                        fontSize = 11.sp,
                        color = StatusGreen
                    )
                } else {
                    OutlinedTextField(
                        value = userNameInput,
                        onValueChange = { userNameInput = it },
                        placeholder = { Text("Identifier or Handle") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .height(44.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                    )
                }
            }
        }

        Switch(
            checked = isConnected,
            onCheckedChange = { check ->
                val loginName = userNameInput.ifEmpty { "User_Handle_Token" }
                if (!check) {
                    onToggle("", false)
                } else {
                    onToggle(loginName, true)
                }
            },
            modifier = Modifier.testTag("switch_platform_$platformId")
        )
    }
}

// -------------------------------------------------------------
// CHATROOM / MESSAGING ROOM BOX
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomScreen(
    chatId: String,
    viewModel: ChatViewModel,
    activeMessages: List<Message>,
    contactsList: List<Contact>,
    groupsList: List<ChatGroup>,
    onShowDetails: (String) -> Unit
) {
    val isGroup = chatId.startsWith("group_")
    val isBroadcast = chatId.startsWith("broadcast_")
    
    val chatTitle = remember(chatId, contactsList, groupsList) {
        if (isBroadcast) {
            groupsList.firstOrNull { it.groupId == chatId }?.name ?: "Broadcast Panel"
        } else if (isGroup) {
            groupsList.firstOrNull { it.groupId == chatId }?.name ?: "Secure Group"
        } else {
            contactsList.firstOrNull { it.id == chatId }?.name ?: "Direct Private"
        }
    }

    val contactEmailStr = remember(chatId, contactsList) {
        contactsList.firstOrNull { it.id == chatId }?.id ?: ""
    }

    var messageText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Appbar
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            navigationIcon = {
                IconButton(
                    onClick = { viewModel.goBack() },
                    modifier = Modifier.testTag("chat_back_button")
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        if (!isGroup && !isBroadcast) {
                            onShowDetails(chatId)
                        }
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isBroadcast) Icons.Default.Campaign else if (isGroup) Icons.Default.Group else Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = chatTitle,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "End-to-End Encrypted",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            },
            actions = {
                if (!isGroup && !isBroadcast) {
                    // Call triggering options
                    IconButton(
                        onClick = { viewModel.startCall(chatId, isVideo = false) },
                        modifier = Modifier.testTag("action_voice_call")
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = "Voice Call", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(
                        onClick = { viewModel.startCall(chatId, isVideo = true) },
                        modifier = Modifier.testTag("action_video_call")
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                
                if (!isGroup && !isBroadcast) {
                    IconButton(
                        onClick = { onShowDetails(chatId) },
                        modifier = Modifier.testTag("action_contact_details")
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = "Contact Notifications Alert Config")
                    }
                }
            }
        )

        // Encryption Badge Overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                .padding(vertical = 6.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = StatusGreen,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Conversations are locked with cryptographic key hashing securely.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Messages Flow
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(activeMessages) { msg ->
                val isMe = msg.senderId == "me"
                val align = if (isMe) Alignment.End else Alignment.Start
                val bubbleColor = if (isMe) {
                    if (viewModel.isDarkMode) BubbleMeDark else BubbleMeLight
                } else {
                    if (viewModel.isDarkMode) BubbleOtherDark else BubbleOtherLight
                }
                val textColor = if (viewModel.isDarkMode) {
                    Color.White
                } else {
                    Color.Black
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = bubbleColor),
                        shape = RoundedCornerShape(
                            topStart = 12.dp,
                            topEnd = 12.dp,
                            bottomStart = if (isMe) 12.dp else 2.dp,
                            bottomEnd = if (isMe) 2.dp else 12.dp
                        ),
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .padding(vertical = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            // Secondary sender name label inside secure groups
                            if ((isGroup || isBroadcast) && !isMe) {
                                Text(
                                    text = msg.senderName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }

                            // Optional Media Preview inside bubble
                            if (msg.mediaType == "image" && msg.mediaUri != null) {
                                AsyncImage(
                                    model = msg.mediaUri,
                                    contentDescription = "Cryptographic Payload Image",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .padding(bottom = 6.dp),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            Text(
                                text = msg.content,
                                fontSize = 14.sp,
                                color = textColor
                            )

                            // Lock indicator
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.align(Alignment.End),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(msg.timestamp)),
                                    fontSize = 9.sp,
                                    color = textColor.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Delivered",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Broadcaster notice helper
        if (isBroadcast) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "📢 Client engagement broadcast: individual privately sent packets.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    // Simulate attaching a media layout securely
                    viewModel.sendMessage("Image Media attachment", mediaType = "image", mediaUri = "https://images.unsplash.com/photo-1543269865-cbf427effbad?auto=format&fit=crop&w=300&q=80")
                },
                modifier = Modifier.testTag("chat_attach_button")
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Attach Secure Media", tint = MaterialTheme.colorScheme.primary)
            }

            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                placeholder = { Text("Encrypted message...") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("message_input_field"),
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            )

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = {
                    if (messageText.isNotBlank()) {
                        viewModel.sendMessage(messageText)
                        messageText = ""
                    }
                },
                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                enabled = messageText.isNotBlank(),
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .testTag("message_send_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// CALL SCREENS OVERLAYS (SIMULATOR FOR PHONE / VIDEO)
// -------------------------------------------------------------
@Composable
fun CallScreenOverlay(callState: ActiveCallState, onEndCall: () -> Unit) {
    if (callState is ActiveCallState.Idle) return

    val contactName = if (callState is ActiveCallState.Outgoing) callState.contactName else (callState as ActiveCallState.Connected).contactName
    val isVideo = if (callState is ActiveCallState.Outgoing) callState.isVideo else (callState as ActiveCallState.Connected).isVideo
    val durationStr = if (callState is ActiveCallState.Connected) {
        val s = callState.durationSecs
        String.format("%02d:%02d", s / 60, s % 60)
    } else "Connecting securely..."

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxHeight()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = StatusGreen,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "End-to-End Encrypted Secure Call",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(30.dp))

                // Dummy avatar or video screen
                if (isVideo) {
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.DarkGray)
                            .border(2.dp, MintPrimary, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(80.dp))
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(126.dp)
                            .clip(CircleShape)
                            .background(MintPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = contactName.take(2).uppercase(),
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = contactName,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = durationStr,
                    color = if (callState is ActiveCallState.Connected) MintAccent else Color.LightGray,
                    fontSize = 15.sp
                )
            }

            // Controls
            Row(
                modifier = Modifier.padding(bottom = 50.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {},
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier.size(54.dp)
                ) {
                    Icon(Icons.Default.MicNone, contentDescription = "Mute", tint = Color.White)
                }

                // Close calling
                IconButton(
                    onClick = onEndCall,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Red),
                    modifier = Modifier
                        .size(72.dp)
                        .testTag("end_call_button")
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = "End Call", tint = Color.White, modifier = Modifier.size(28.dp))
                }

                IconButton(
                    onClick = {},
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier.size(54.dp)
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = "Speaker", tint = Color.White)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SECTOR: DIALOGS (ADD CONTACT, CREATE GROUP)
// -------------------------------------------------------------
@Composable
fun AddContactDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Boolean) -> Unit
) {
    var identifier by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var isEmail by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Add Security Recipient",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_contact_name_input")
                )

                OutlinedTextField(
                    value = identifier,
                    onValueChange = { identifier = it },
                    label = { Text(if (isEmail) "Email Address" else "Cellphone Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_contact_id_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Register with Email ID?", fontSize = 13.sp)
                    Switch(
                        checked = isEmail,
                        onCheckedChange = { isEmail = it },
                        modifier = Modifier.testTag("add_contact_email_register_switch")
                    )
                }

                Text(
                    text = "End-to-End Cryptography maps correctly whether cellphone lines OR private corporate emails are referenced.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("add_contact_cancel")) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (identifier.isNotBlank() && name.isNotBlank()) {
                                onConfirm(identifier, name, isEmail)
                            }
                        },
                        enabled = identifier.isNotBlank() && name.isNotBlank(),
                        modifier = Modifier.testTag("add_contact_confirm")
                    ) {
                        Text("Add")
                    }
                }
            }
        }
    }
}

@Composable
fun CreateGroupDialog(
    contacts: List<Contact>,
    onDismiss: () -> Unit,
    onConfirm: (String, List<String>, Boolean) -> Unit
) {
    var title by remember { mutableStateOf("") }
    val selectedMembers = remember { mutableStateListOf<String>() }
    var isBroadcast by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "New Secure Circle Array",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Group or Broadcast Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("group_title_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "Broadcast Mode?", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Renders outbound private delivery.", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = isBroadcast,
                        onCheckedChange = { isBroadcast = it },
                        modifier = Modifier.testTag("group_broadcast_switch")
                    )
                }

                Text(text = "Select Members:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

                LazyColumn(modifier = Modifier.height(110.dp)) {
                    items(contacts) { person ->
                        val isChecked = selectedMembers.contains(person.id)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isChecked) selectedMembers.remove(person.id) else selectedMembers.add(person.id)
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { check ->
                                    if (isChecked) selectedMembers.remove(person.id) else selectedMembers.add(person.id)
                                }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = person.name, fontSize = 13.sp)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("group_cancel")) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (title.isNotBlank() && selectedMembers.isNotEmpty()) {
                                onConfirm(title, selectedMembers.toList(), isBroadcast)
                            }
                        },
                        enabled = title.isNotBlank() && selectedMembers.isNotEmpty(),
                        modifier = Modifier.testTag("group_confirm")
                    ) {
                        Text("Initiate")
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// OVERLAY DETAILED CONTACT & ALERTS NOTIFICATIONS SETUP
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailsOverlay(
    contact: Contact,
    onDismiss: () -> Unit,
    onSaveSettings: (String, Boolean, Boolean) -> Unit
) {
    var selectedAlertMode by remember { mutableStateOf(contact.customNotificationSound) }
    var vibrateOn by remember { mutableStateOf(contact.customNotificationVibrate) }
    var userMuted by remember { mutableStateOf(contact.customNotificationMute) }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Alert Tuning Customizer",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                // Contact Details
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = contact.name.take(2).uppercase(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(text = contact.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(text = contact.id, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                HorizontalDivider()

                Text(
                    text = "Custom Notification Override",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                // 1. Mute Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (userMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = "Mute",
                            tint = if (userMuted) Color.Red else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = "Mute Alerts", fontSize = 13.sp)
                    }
                    Switch(
                        checked = userMuted,
                        onCheckedChange = { userMuted = it },
                        modifier = Modifier.testTag("mute_alerts_switch")
                    )
                }

                // 2. Alert sound dropdown mimicking selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Ringtone/Chime", fontSize = 14.sp)
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                            .clickable {
                                // Rotate custom modes
                                selectedAlertMode = when (selectedAlertMode) {
                                    "Default" -> "Chime"
                                    "Chime" -> "Silent"
                                    "Silent" -> "Pulse"
                                    else -> "Default"
                                }
                            }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = selectedAlertMode, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                }

                // 3. Vibrate Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Vibrations Override?", fontSize = 13.sp)
                    Switch(
                        checked = vibrateOn,
                        onCheckedChange = { vibrateOn = it },
                        modifier = Modifier.testTag("vibrate_switch")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("alert_cancel")) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { onSaveSettings(selectedAlertMode, vibrateOn, userMuted) },
                        modifier = Modifier.testTag("alert_save")
                    ) {
                        Text("Apply Override Settings")
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// PLACEHOLDERS / GENERIC
// -------------------------------------------------------------
@Composable
fun EmptyStatePlaceholder(imageVector: ImageVector? = null, icon: ImageVector, title: String, subtitle: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}
