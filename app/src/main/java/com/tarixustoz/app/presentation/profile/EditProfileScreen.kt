package com.tarixustoz.app.presentation.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tarixustoz.app.presentation.theme.BrandPurple
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    viewModel: EditProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collectLatest { event ->
            when (event) {
                is EditProfileEvent.SavedSuccess -> {
                    snackbarHostState.showSnackbar("Profil muvaffaqiyatli saqlandi!")
                    onBack()
                }
                is EditProfileEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Profilni tahrirlash",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("edit_profile_back_btn")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Orqaga"
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.saveProfile() },
                        enabled = !uiState.isLoading,
                        modifier = Modifier.testTag("edit_profile_top_save_btn")
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = BrandPurple,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = BrandPurple,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Saqlash",
                                    fontWeight = FontWeight.Bold,
                                    color = BrandPurple,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Hero Avatar Picker Card
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.2.dp, BrandPurple.copy(alpha = 0.2f)),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        contentAlignment = Alignment.BottomEnd,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = BrandPurple,
                            modifier = Modifier.size(92.dp),
                            border = BorderStroke(3.dp, MaterialTheme.colorScheme.surface)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = uiState.avatar.ifEmpty { "AS" },
                                    fontSize = if (uiState.avatar.length > 2) 38.sp else 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                        Surface(
                            shape = CircleShape,
                            color = BrandPurple,
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
                            modifier = Modifier.size(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    contentDescription = "Avatar o'zgartirish",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "Avatarni tanlang",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Belgingiz yoki rasmingiz profil va reytingda ko‘rinadi",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(14.dp))

                    val avatars = listOf(
                        "AS", "🎓", "🏛️", "⚔️", "👑",
                        "📜", "🏹", "🦁", "🌟", "📚", "🏆", "💡"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        avatars.take(6).forEach { item ->
                            AvatarOptionChip(
                                label = item,
                                isSelected = uiState.avatar == item,
                                onClick = { viewModel.onAvatarChange(item) }
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        avatars.drop(6).take(6).forEach { item ->
                            AvatarOptionChip(
                                label = item,
                                isSelected = uiState.avatar == item,
                                onClick = { viewModel.onAvatarChange(item) }
                            )
                        }
                    }
                }
            }

            // Section 1: Personal Information
            SectionCard(
                title = "Shaxsiy ma'lumotlar",
                icon = Icons.Default.Person
            ) {
                // Name Input
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = { viewModel.onNameChange(it) },
                    label = { Text("Ism va familiya") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = BrandPurple) },
                    trailingIcon = {
                        if (uiState.name.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onNameChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Tozalash")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_profile_name_input")
                )

                // Bio Input
                OutlinedTextField(
                    value = uiState.bio,
                    onValueChange = { viewModel.onBioChange(it) },
                    label = { Text("Maqom / Bio (Qisqacha o'zingiz haqingizda)") },
                    placeholder = { Text("Tarix bilimdoni • DTM 189 ball...") },
                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = BrandPurple) },
                    trailingIcon = {
                        if (uiState.bio.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onBioChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Tozalash")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_profile_bio_input")
                )

                // Phone Input
                OutlinedTextField(
                    value = uiState.phone,
                    onValueChange = { viewModel.onPhoneChange(it) },
                    label = { Text("Telefon raqam (Ixtiyoriy)") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = BrandPurple) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_profile_phone_input")
                )

                // Target Input
                OutlinedTextField(
                    value = uiState.target,
                    onValueChange = { viewModel.onTargetChange(it) },
                    label = { Text("O'quv maqsadi yoki mo'ljal") },
                    placeholder = { Text("Masalan: DTM 189 ball • Milliy sertifikat") },
                    leadingIcon = { Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = BrandPurple) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_profile_target_input")
                )
            }

            // Section 2: Grade Selection
            SectionCard(
                title = "Sinfingizni tanlang",
                icon = Icons.Default.School
            ) {
                Text(
                    text = "Tanlangan sinf bo‘yicha darslik va testlar tavsiya etiladi",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(6, 7, 8, 9, 10, 11).forEach { g ->
                        val selected = uiState.grade == g
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) BrandPurple else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (selected) BorderStroke(1.5.dp, BrandPurple) else null,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clickable { viewModel.onGradeChange(g) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "$g-sinf",
                                    fontSize = 14.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Save Button
            Button(
                onClick = { viewModel.saveProfile() },
                enabled = !uiState.isLoading,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("edit_profile_main_save_btn")
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "O‘zgarishlarni saqlash",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AvatarOptionChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (isSelected) BrandPurple else MaterialTheme.colorScheme.surfaceVariant,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.surface) else null,
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = if (label.length > 2) 18.sp else 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = BrandPurple.copy(alpha = 0.12f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = BrandPurple,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            content()
        }
    }
}
