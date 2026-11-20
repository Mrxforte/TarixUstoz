package com.tarixustoz.app.presentation.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tarixustoz.app.R
import com.tarixustoz.app.presentation.theme.BrandPurple
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private tailrec fun Context.findActivityOrNull(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivityOrNull()
    else -> null
}

private val AUTH_ROLE_OPTIONS = listOf(
    "O'quvchi" to "Maktab o‘quvchisi (6–11-sinf)",
    "Abituriyent" to "OTMga tayyorlanuvchi",
    "Talaba" to "Oliy ta’lim talabasi (Student)",
    "O'qituvchi" to "Tarix fani o‘qituvchisi"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    canNavigateBack: Boolean = false,
    onBack: () -> Unit = {},
    onAuthSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivityOrNull() }
    val coroutineScope = rememberCoroutineScope()

    var isSignUpMode by remember { mutableStateOf(false) }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("O'quvchi") }
    var roleMenuExpanded by remember { mutableStateOf(false) }
    var selectedGrade by remember { mutableIntStateOf(7) }
    var gradeMenuExpanded by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    var isForgotPasswordMode by remember { mutableStateOf(false) }
    var resetEmailInput by remember { mutableStateOf("") }

    val backdropImages = remember {
        listOf(
            R.drawable.intro_registan,
            R.drawable.intro_buxoro,
            R.drawable.intro_xiva,
            R.drawable.intro_oqsaroy
        )
    }
    var bgIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000L)
            bgIndex = (bgIndex + 1) % backdropImages.size
        }
    }

    LaunchedEffect(uiState.isSignedIn) {
        if (uiState.isSignedIn) {
            onAuthSuccess()
        }
    }

    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
        onDispose {}
    }

    fun launchGoogleSignIn() {
        val hostActivity = activity
        if (hostActivity == null) {
            viewModel.setError("Google kirish oynasini ochib bo‘lmadi")
            return
        }
        coroutineScope.launch {
            viewModel.setLoading(true)
            try {
                val webClientId = context.getString(R.string.default_web_client_id)
                val credentialManager = CredentialManager.create(hostActivity)
                val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(webClientId)
                    .build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(signInWithGoogleOption)
                    .build()

                val response = try {
                    credentialManager.getCredential(hostActivity, request)
                } catch (_: NoCredentialException) {
                    val fallbackOption = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(webClientId)
                        .build()
                    val fallbackReq = GetCredentialRequest.Builder()
                        .addCredentialOption(fallbackOption)
                        .build()
                    credentialManager.getCredential(hostActivity, fallbackReq)
                }

                val credential = response.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                    viewModel.handleGoogleSignInResult(
                        idToken = googleCred.idToken,
                        onComplete = {
                            Toast.makeText(context, "Xush kelibsiz!", Toast.LENGTH_SHORT).show()
                            onAuthSuccess()
                        }
                    )
                } else {
                    viewModel.setError("Google hisob ma’lumoti tasdiqlanmadi")
                }
            } catch (_: GetCredentialCancellationException) {
                viewModel.setLoading(false)
            } catch (e: GetCredentialException) {
                viewModel.setError(
                    e.message?.takeIf { it.isNotBlank() }
                        ?: "Qurilmada Google hisobi topilmadi yoki Google Play xizmatlari mavjud emas. Email orqali kiring."
                )
            } catch (e: Exception) {
                viewModel.setError(e.message ?: "Google orqali kirishda xatolik yuz berdi")
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Crossfade(
            targetState = backdropImages[bgIndex],
            animationSpec = tween(durationMillis = 1200),
            label = "auth_bg_crossfade"
        ) { drawableRes ->
            AsyncImage(
                model = remember(drawableRes, context) {
                    ImageRequest.Builder(context)
                        .data(drawableRes)
                        .crossfade(false)
                        .build()
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xD90B0E26),
                            Color(0xEB141238),
                            Color(0xFA0E1028)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (canNavigateBack) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("auth_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Orqaga",
                            tint = Color.White
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(12.dp))
            }

            AsyncImage(
                model = remember(context) {
                    ImageRequest.Builder(context)
                        .data(R.drawable.app_icon_emblem)
                        .crossfade(false)
                        .build()
                },
                contentDescription = "Tarix Ustoz",
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(18.dp))
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Tarix Ustoz",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Text(
                text = "Natijalaringiz, reytingingiz va biletlaringiz bulutda saqlanadi",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.78f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Spacer(Modifier.height(20.dp))

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF171A36).copy(alpha = 0.92f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (isForgotPasswordMode) {
                        // Dedicated Forgot Password Mode inside Auth Card
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(
                                onClick = {
                                    isForgotPasswordMode = false
                                    viewModel.clearMessages()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Orqaga",
                                    tint = Color.White
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Parolni tiklash 🔑",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Text(
                            text = "Email manzilingizni kiriting. Parolni qayta tiklash havolasi elektron pochtangizga yuboriladi.",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            lineHeight = 18.sp
                        )

                        if (!uiState.errorMessage.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0x33EF4444),
                                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = uiState.errorMessage!!,
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        if (!uiState.infoMessage.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0x3310B981),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = uiState.infoMessage!!,
                                    color = Color(0xFF86EFAC),
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        val fieldColors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF818CF8),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                            focusedLabelColor = Color(0xFFC7D2FE),
                            unfocusedLabelColor = Color.White.copy(alpha = 0.68f),
                            cursorColor = Color(0xFFFBBF24)
                        )

                        OutlinedTextField(
                            value = resetEmailInput,
                            onValueChange = { resetEmailInput = it },
                            label = { Text("Email manzil") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFFC7D2FE)) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = fieldColors,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_reset_email_input")
                        )

                        Button(
                            onClick = {
                                if (resetEmailInput.isNotBlank()) {
                                    viewModel.sendPasswordReset(resetEmailInput)
                                } else {
                                    viewModel.setError("Email manzilingizni kiriting")
                                }
                            },
                            enabled = !uiState.isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("auth_reset_submit_button")
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = "Havolani yuborish",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            TextButton(
                                onClick = {
                                    isForgotPasswordMode = false
                                    viewModel.clearMessages()
                                }
                            ) {
                                Text(
                                    text = "⬅ Kirish sahifasiga qaytish",
                                    color = Color(0xFFFDE68A),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    } else {
                        // Sign In / Sign Up Switcher
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White.copy(alpha = 0.08f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(4.dp)) {
                                listOf(false to "Kirish", true to "Ro‘yxatdan o‘tish").forEach { (signUpTab, label) ->
                                    val selected = isSignUpMode == signUpTab
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (selected) BrandPurple else Color.Transparent,
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .clickable {
                                                isSignUpMode = signUpTab
                                                viewModel.clearMessages()
                                            }
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(vertical = 10.dp)
                                        ) {
                                            Text(
                                                text = label,
                                                color = Color.White,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (!uiState.errorMessage.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0x33EF4444),
                                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = uiState.errorMessage!!,
                                        color = Color(0xFFFCA5A5),
                                        fontSize = 13.sp
                                    )
                                    if (uiState.suggestSignUp && !isSignUpMode) {
                                        OutlinedButton(
                                            onClick = {
                                                isSignUpMode = true
                                                viewModel.clearMessages()
                                            },
                                            border = BorderStroke(1.dp, Color(0xFFFDE68A).copy(alpha = 0.7f)),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .testTag("auth_switch_to_signup_button")
                                        ) {
                                            Text(
                                                text = "Ro‘yxatdan o‘tishga o‘tish →",
                                                color = Color(0xFFFDE68A),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (!uiState.infoMessage.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0x3310B981),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = uiState.infoMessage!!,
                                    color = Color(0xFF86EFAC),
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        val fieldColors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF818CF8),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                            focusedLabelColor = Color(0xFFC7D2FE),
                            unfocusedLabelColor = Color.White.copy(alpha = 0.68f),
                            cursorColor = Color(0xFFFBBF24)
                        )

                        if (isSignUpMode) {
                            OutlinedTextField(
                                value = fullName,
                                onValueChange = { fullName = it },
                                label = { Text("Ism va familiya") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFFC7D2FE)) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = fieldColors,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_name_input")
                            )
                        }

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email manzil") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFFC7D2FE)) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = fieldColors,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_email_input")
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Parol") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFC7D2FE)) },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (passwordVisible) "Parolni yashirish" else "Parolni ko‘rsatish",
                                        tint = Color.White.copy(alpha = 0.75f)
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = fieldColors,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_password_input")
                        )

                        if (isSignUpMode) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                ExposedDropdownMenuBox(
                                    expanded = roleMenuExpanded,
                                    onExpandedChange = { roleMenuExpanded = !roleMenuExpanded },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = selectedRole,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Maqomingiz") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Badge, contentDescription = null, tint = Color(0xFFC7D2FE))
                                        },
                                        trailingIcon = {
                                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleMenuExpanded)
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = fieldColors,
                                        modifier = Modifier
                                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                            .fillMaxWidth()
                                            .testTag("auth_role_dropdown")
                                    )
                                    ExposedDropdownMenu(
                                        expanded = roleMenuExpanded,
                                        onDismissRequest = { roleMenuExpanded = false }
                                    ) {
                                        AUTH_ROLE_OPTIONS.forEach { (roleKey, roleSubtitle) ->
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(roleKey, fontWeight = FontWeight.SemiBold)
                                                        Text(
                                                            roleSubtitle,
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    selectedRole = roleKey
                                                    roleMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                AnimatedVisibility(visible = selectedRole == "O'quvchi") {
                                    ExposedDropdownMenuBox(
                                        expanded = gradeMenuExpanded,
                                        onExpandedChange = { gradeMenuExpanded = !gradeMenuExpanded },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedTextField(
                                            value = "$selectedGrade-sinf",
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("Sinfingiz") },
                                            leadingIcon = {
                                                Icon(Icons.Default.School, contentDescription = null, tint = Color(0xFFC7D2FE))
                                            },
                                            trailingIcon = {
                                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = gradeMenuExpanded)
                                            },
                                            singleLine = true,
                                            shape = RoundedCornerShape(14.dp),
                                            colors = fieldColors,
                                            modifier = Modifier
                                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                                .fillMaxWidth()
                                                .testTag("auth_grade_dropdown")
                                        )
                                        ExposedDropdownMenu(
                                            expanded = gradeMenuExpanded,
                                            onDismissRequest = { gradeMenuExpanded = false }
                                        ) {
                                            (6..11).forEach { grade ->
                                                DropdownMenuItem(
                                                    text = { Text("$grade-sinf tarixi", fontWeight = FontWeight.Medium) },
                                                    onClick = {
                                                        selectedGrade = grade
                                                        gradeMenuExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                TextButton(
                                    onClick = {
                                        resetEmailInput = email
                                        isForgotPasswordMode = true
                                        viewModel.clearMessages()
                                    }
                                ) {
                                    Text(
                                        text = "Parolni unutdingizmi?",
                                        color = Color(0xFFFDE68A),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (isSignUpMode) {
                                    viewModel.signUpWithEmail(
                                        name = fullName,
                                        email = email,
                                        password = password,
                                        role = selectedRole,
                                        grade = selectedGrade
                                    ) {
                                        Toast.makeText(context, "Hisob yaratildi!", Toast.LENGTH_SHORT).show()
                                        onAuthSuccess()
                                    }
                                } else {
                                    viewModel.signInWithEmail(email, password) {
                                        Toast.makeText(context, "Xush kelibsiz!", Toast.LENGTH_SHORT).show()
                                        onAuthSuccess()
                                    }
                                }
                            },
                            enabled = !uiState.isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("auth_email_submit_button")
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = if (isSignUpMode) "Ro‘yxatdan o‘tish" else "Kirish",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = Color.White.copy(alpha = 0.16f)
                            )
                            Text(
                                text = "  yoki  ",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = Color.White.copy(alpha = 0.16f)
                            )
                        }

                        OutlinedButton(
                            onClick = { launchGoogleSignIn() },
                            enabled = !uiState.isLoading,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF1F2937)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("auth_google_signin_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = BrandPurple.copy(alpha = 0.12f),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.AccountCircle,
                                            contentDescription = null,
                                            tint = BrandPurple,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "Google orqali kirish",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1F2937)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}
