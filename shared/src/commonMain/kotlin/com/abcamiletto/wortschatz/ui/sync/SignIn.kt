package com.abcamiletto.wortschatz.ui.sync

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.abcamiletto.wortschatz.data.ProgressSync
import com.abcamiletto.wortschatz.data.SignInResult
import com.abcamiletto.wortschatz.resources.Res
import com.abcamiletto.wortschatz.resources.icon
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

/** The Android app asks for the password once. The website has its own sign-in page, served before the app loads. */
@Composable
fun SignInRoute(sync: ProgressSync) {
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (working || password.isEmpty()) return
        working = true
        scope.launch {
            message = when (sync.signIn(password)) {
                is SignInResult.Success -> null
                SignInResult.WrongPassword -> "Wrong password"
                SignInResult.TooManyAttempts -> "Too many attempts. Try again in a few minutes."
                SignInResult.Offline -> "No connection. Try again."
            }
            working = false
        }
    }

    Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(20.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 340.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(
                painterResource(Res.drawable.icon),
                contentDescription = null,
                modifier = Modifier.size(96.dp).align(Alignment.CenterHorizontally),
            )
            Text(
                "Wortschatz",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    message = null
                },
                placeholder = { Text("Password") },
                singleLine = true,
                isError = message != null,
                supportingText = message?.let { text -> { Text(text) } },
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().testTag("password"),
            )
            Button(
                onClick = ::submit,
                enabled = !working && password.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(54.dp).testTag("signIn"),
            ) { Text("Sign in") }
        }
    }
}
