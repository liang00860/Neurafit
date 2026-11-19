package com.neurafit.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.neurafit.app.auth.AuthRepository
import com.neurafit.app.auth.AuthResult
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    initialMode: AuthMode = AuthMode.SignIn,
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit = {}
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val authRepository = remember(context) { AuthRepository(context) }
    val coroutineScope = rememberCoroutineScope()
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    var identifier by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var isSubmitting by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    fun submit() {
        if (isSubmitting) return

        val nextError = when {
            mode == AuthMode.SignIn && identifier.isBlank() -> "请输入账号。"
            mode == AuthMode.SignIn && password.isBlank() -> "请输入密码。"
            mode == AuthMode.Register && username.isBlank() -> "请输入用户名。"
            mode == AuthMode.Register && email.isBlank() -> "请输入邮箱。"
            mode == AuthMode.Register && password.length < 6 -> "密码至少需要 6 位。"
            else -> null
        }
        if (nextError != null) {
            errorMessage = nextError
            return
        }

        isSubmitting = true
        errorMessage = null
        coroutineScope.launch {
            val result = if (mode == AuthMode.SignIn) {
                authRepository.login(identifier.trim(), password)
            } else {
                authRepository.register(username.trim(), email.trim(), password)
            }
            isSubmitting = false
            when (result) {
                is AuthResult.Success -> onLoginSuccess()
                is AuthResult.Error -> errorMessage = localizeAuthError(result.message)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg),
    ) {
        Box(
            modifier = Modifier
                .size(400.dp)
                .offset(x = 150.dp, y = (-100).dp)
                .blur(120.dp)
                .background(Lime.copy(alpha = 0.15f), shape = RoundedCornerShape(100))
                .align(Alignment.TopEnd),
        )
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(x = (-100).dp, y = 100.dp)
                .blur(100.dp)
                .background(Color(0xFFFF4D4D).copy(alpha = 0.1f), shape = RoundedCornerShape(100))
                .align(Alignment.BottomStart),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "NEURAFIT",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.sp,
                )
                Text(
                    text = if (mode == AuthMode.SignIn) "登录" else "注册",
                    color = Zinc400,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.sp,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    val direction = if (targetState == AuthMode.Register) {
                        AnimatedContentTransitionScope.SlideDirection.Left
                    } else {
                        AnimatedContentTransitionScope.SlideDirection.Right
                    }
                    (slideIntoContainer(direction, tween(400)) + fadeIn(tween(400))).togetherWith(
                        slideOutOfContainer(direction, tween(400)) + fadeOut(tween(400))
                    )
                },
                label = "headerText"
            ) { currentMode ->
                Column {
                    Text(
                        text = if (currentMode == AuthMode.SignIn) "欢迎\n回来" else "创建\n账号",
                        color = Color.White,
                        fontSize = 54.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 54.sp,
                        letterSpacing = 0.sp,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (currentMode == AuthMode.SignIn) {
                            "重新进入你的训练空间，继续你的提升之旅。"
                        } else {
                            "创建你的训练档案，并通过云端账号完成同步。"
                        },
                        color = Zinc400,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        modifier = Modifier.fillMaxWidth(0.86f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkCard)
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                    .padding(24.dp)
                    .animateContentSize(tween(400)),
            ) {
                Column {
                    AuthModeSwitch(
                        mode = mode,
                        onModeChange = {
                            mode = it
                            errorMessage = null
                        },
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    AnimatedContent(
                        targetState = mode,
                        transitionSpec = {
                            val direction = if (targetState == AuthMode.Register) {
                                AnimatedContentTransitionScope.SlideDirection.Left
                            } else {
                                AnimatedContentTransitionScope.SlideDirection.Right
                            }
                            (slideIntoContainer(direction, tween(400)) + fadeIn(tween(400))).togetherWith(
                                slideOutOfContainer(direction, tween(400)) + fadeOut(tween(400))
                            )
                        },
                        label = "formFields"
                    ) { currentMode ->
                        Column {
                            if (currentMode == AuthMode.SignIn) {
                                FieldLabel("账号")
                                CustomTextField(
                                    value = identifier,
                                    onValueChange = { identifier = it },
                                    placeholder = "请输入用户名或邮箱",
                                    enabled = !isSubmitting,
                                )
                            } else {
                                FieldLabel("用户名")
                                CustomTextField(
                                    value = username,
                                    onValueChange = { username = it },
                                    placeholder = "请输入用户名",
                                    enabled = !isSubmitting,
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                FieldLabel("邮箱")
                                CustomTextField(
                                    value = email,
                                    onValueChange = { email = it },
                                    placeholder = "请输入邮箱地址",
                                    enabled = !isSubmitting,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    FieldLabel("密码")
                    CustomTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = "请输入至少 6 位密码",
                        isPassword = true,
                        enabled = !isSubmitting,
                    )

                    errorMessage?.let { message ->
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = message,
                            color = Color(0xFFFF9B9B),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    Button(
                        onClick = ::submit,
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Lime,
                        ),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            AnimatedContent(
                                targetState = mode,
                                transitionSpec = {
                                    fadeIn(tween(400)) togetherWith fadeOut(tween(400))
                                },
                                    label = "buttonText"
                            ) { currentMode ->
                                Text(
                                    text = if (isSubmitting) {
                                        "提交中"
                                    } else if (currentMode == AuthMode.SignIn) {
                                        "登录"
                                    } else {
                                        "注册"
                                    },
                                    color = Color.Black,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.sp,
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            LineIcon(icon = FitIcon.ArrowRight, tint = Color.Black, size = 18.dp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                AnimatedContent(
                    targetState = mode,
                    transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
                    label = "bottomHint"
                ) { currentMode ->
                    Row(horizontalArrangement = Arrangement.Center) {
                        Text(
                            text = if (currentMode == AuthMode.SignIn) "还没有账号？" else "已有账号？",
                            color = Zinc400,
                            fontSize = 14.sp,
                        )
                        Text(
                            text = if (currentMode == AuthMode.SignIn) "去注册" else "去登录",
                            color = Lime,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable {
                                mode = if (mode == AuthMode.SignIn) AuthMode.Register else AuthMode.SignIn
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthModeSwitch(
    mode: AuthMode,
    onModeChange: (AuthMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AuthModeTab(
            text = "登录",
            selected = mode == AuthMode.SignIn,
            onClick = { onModeChange(AuthMode.SignIn) },
            modifier = Modifier.weight(1f),
        )
        AuthModeTab(
            text = "注册",
            selected = mode == AuthMode.Register,
            onClick = { onModeChange(AuthMode.Register) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AuthModeTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Lime else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) Color.Black else Zinc400,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.sp,
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = Zinc400,
        letterSpacing = 0.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
fun CustomTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false,
    enabled: Boolean = true,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        textStyle = TextStyle(
            color = Color.White,
            fontSize = 16.sp,
        ),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        cursorBrush = SolidColor(Color.White),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = Zinc500,
                        fontSize = 16.sp,
                    )
                }
                innerTextField()
            }
        },
    )
}

enum class AuthMode {
    SignIn,
    Register,
}

private fun localizeAuthError(message: String): String =
    when (message.trim()) {
        "Please enter your account and password." -> "请输入账号和密码。"
        "Account or password is incorrect." -> "账号或密码错误。"
        "This account has been disabled." -> "该账号已被禁用。"
        "Please enter a username." -> "请输入用户名。"
        "Please enter an email address." -> "请输入邮箱地址。"
        "Please enter a valid email address." -> "请输入有效的邮箱地址。"
        "Password must be at least 6 characters." -> "密码至少需要 6 位。"
        "Username or email already exists." -> "用户名或邮箱已存在。"
        else -> message
    }
