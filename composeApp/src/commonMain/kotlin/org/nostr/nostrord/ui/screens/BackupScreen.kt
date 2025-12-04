package org.nostr.nostrord.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.nostr.nostrord.network.NostrRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(onNavigateBack: () -> Unit) {
    val privateKey = NostrRepository.getPrivateKey()
    val publicKey = NostrRepository.getPublicKey()
    val clipboardManager = LocalClipboardManager.current
    var showCopiedMessage by remember { mutableStateOf(false) }

    LaunchedEffect(showCopiedMessage) {
        if (showCopiedMessage) {
            kotlinx.coroutines.delay(2000)
            showCopiedMessage = false
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isCompact = maxWidth < 600.dp

        if (isCompact) {
            MobileBackupScreen(
                privateKey = privateKey,
                publicKey = publicKey,
                showCopiedMessage = showCopiedMessage,
                onCopyPublicKey = {
                    publicKey?.let {
                        clipboardManager.setText(AnnotatedString(it))
                        showCopiedMessage = true
                    }
                },
                onCopyPrivateKey = {
                    privateKey?.let {
                        clipboardManager.setText(AnnotatedString(it))
                        showCopiedMessage = true
                    }
                },
                onNavigateBack = onNavigateBack
            )
        } else {
            DesktopBackupScreen(
                privateKey = privateKey,
                publicKey = publicKey,
                showCopiedMessage = showCopiedMessage,
                onCopyPublicKey = {
                    publicKey?.let {
                        clipboardManager.setText(AnnotatedString(it))
                        showCopiedMessage = true
                    }
                },
                onCopyPrivateKey = {
                    privateKey?.let {
                        clipboardManager.setText(AnnotatedString(it))
                        showCopiedMessage = true
                    }
                },
                onNavigateBack = onNavigateBack
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileBackupScreen(
    privateKey: String?,
    publicKey: String?,
    showCopiedMessage: Boolean,
    onCopyPublicKey: () -> Unit,
    onCopyPrivateKey: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup Keys", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF202225)
                )
            )
        },
        containerColor = Color(0xFF36393F),
        snackbarHost = {
            if (showCopiedMessage) {
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    containerColor = Color(0xFF57F287)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Success",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copied to clipboard", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Warning icon
            Icon(
                Icons.Default.Warning,
                contentDescription = "Warning",
                tint = Color(0xFFFFA500),
                modifier = Modifier
                    .size(48.dp)
                    .padding(8.dp)
            )

            Text(
                "Backup Your Keys",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Warning card - compact version
            WarningCard(isCompact = true)

            Spacer(modifier = Modifier.height(16.dp))

            // Public Key Section
            if (publicKey != null) {
                KeyCard(
                    title = "Public Key (npub)",
                    titleColor = Color(0xFF99AAB5),
                    keyValue = publicKey,
                    keyColor = Color.White,
                    buttonText = "Copy Public Key",
                    buttonColor = Color(0xFF5865F2),
                    onCopy = onCopyPublicKey,
                    isCompact = true
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Private Key Section
            if (privateKey != null) {
                KeyCard(
                    title = "Private Key (nsec)",
                    titleColor = Color(0xFFED4245),
                    keyValue = privateKey,
                    keyColor = Color(0xFFFF6B6B),
                    buttonText = "Copy Private Key",
                    buttonColor = Color(0xFFED4245),
                    onCopy = onCopyPrivateKey,
                    isCompact = true,
                    showSecretBadge = true
                )
            } else {
                NoKeyCard()
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Security tips - collapsible on mobile
            SecurityTipsCard(isCompact = true)
        }
    }
}

@Composable
private fun DesktopBackupScreen(
    privateKey: String?,
    publicKey: String?,
    showCopiedMessage: Boolean,
    onCopyPublicKey: () -> Unit,
    onCopyPrivateKey: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF36393F))
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            TextButton(onClick = onNavigateBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFF5865F2),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Back", color = Color(0xFF5865F2))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Centered content with max width
        Column(
            modifier = Modifier.widthIn(max = 600.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Warning icon
            Icon(
                Icons.Default.Warning,
                contentDescription = "Warning",
                tint = Color(0xFFFFA500),
                modifier = Modifier
                    .size(64.dp)
                    .padding(16.dp)
            )

            // Title
            Text(
                "Backup Your Private Key",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Warning card
            WarningCard(isCompact = false)

            Spacer(modifier = Modifier.height(24.dp))

            // Public Key Section
            if (publicKey != null) {
                KeyCard(
                    title = "Public Key (npub)",
                    titleColor = Color(0xFF99AAB5),
                    keyValue = publicKey,
                    keyColor = Color.White,
                    buttonText = "Copy Public Key",
                    buttonColor = Color(0xFF5865F2),
                    onCopy = onCopyPublicKey,
                    isCompact = false
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Private Key Section
            if (privateKey != null) {
                KeyCard(
                    title = "Private Key (nsec)",
                    titleColor = Color(0xFFED4245),
                    keyValue = privateKey,
                    keyColor = Color(0xFFFF6B6B),
                    buttonText = "Copy Private Key",
                    buttonColor = Color(0xFFED4245),
                    onCopy = onCopyPrivateKey,
                    isCompact = false,
                    showSecretBadge = true
                )
            } else {
                NoKeyCard()
            }

            // Copied message
            if (showCopiedMessage) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF57F287)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Success",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Copied to clipboard",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Security tips
            SecurityTipsCard(isCompact = false)
        }
    }
}

@Composable
private fun WarningCard(isCompact: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFED4245)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(if (isCompact) 12.dp else 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = Color.White,
                    modifier = Modifier.size(if (isCompact) 18.dp else 20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "CRITICAL SECURITY WARNING",
                    color = Color.White,
                    style = if (isCompact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (isCompact) {
                    "• Never share your private key\n" +
                    "• Store it in a secure password manager\n" +
                    "• If lost, you cannot recover your account"
                } else {
                    "• Never share your private key with anyone\n" +
                    "• Anyone with this key has full access to your account\n" +
                    "• Store it in a secure password manager\n" +
                    "• If lost, you cannot recover your account\n" +
                    "• Make multiple secure backups"
                },
                color = Color.White,
                style = if (isCompact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun KeyCard(
    title: String,
    titleColor: Color,
    keyValue: String,
    keyColor: Color,
    buttonText: String,
    buttonColor: Color,
    onCopy: () -> Unit,
    isCompact: Boolean,
    showSecretBadge: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2F3136)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(if (isCompact) 12.dp else 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    color = titleColor,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (showSecretBadge) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Secret",
                            tint = Color(0xFFED4245),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "SECRET",
                            color = Color(0xFFED4245),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                keyValue,
                color = keyColor,
                style = if (isCompact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                fontWeight = if (showSecretBadge) FontWeight.Bold else FontWeight.Normal,
                maxLines = if (isCompact) 2 else Int.MAX_VALUE,
                overflow = if (isCompact) TextOverflow.Ellipsis else TextOverflow.Clip
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onCopy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
            ) {
                Text(buttonText, color = Color.White)
            }
        }
    }
}

@Composable
private fun NoKeyCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2F3136)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "No private key found",
                color = Color(0xFF99AAB5),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Please log in first",
                color = Color(0xFF99AAB5),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SecurityTipsCard(isCompact: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2F3136)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(if (isCompact) 12.dp else 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Lightbulb,
                    contentDescription = "Tips",
                    tint = Color(0xFFFEE75C),
                    modifier = Modifier.size(if (isCompact) 18.dp else 20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Security Tips",
                    color = Color(0xFFFEE75C),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (isCompact) {
                    "1. Write it down and store safely\n" +
                    "2. Use a password manager\n" +
                    "3. Never store in plain text\n" +
                    "4. Never send via messages"
                } else {
                    "1. Write it down on paper and store in a safe place\n" +
                    "2. Use a password manager like 1Password or Bitwarden\n" +
                    "3. Never store it in plain text files or screenshots\n" +
                    "4. Never send it via email or messaging apps\n" +
                    "5. Consider using a hardware wallet for long-term storage"
                },
                color = Color.White,
                style = if (isCompact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodySmall
            )
        }
    }
}
