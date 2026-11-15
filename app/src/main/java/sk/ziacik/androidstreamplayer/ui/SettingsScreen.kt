package sk.ziacik.androidstreamplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import sk.ziacik.androidstreamplayer.settings.SettingsController

@Composable
fun SettingsScreen(
    controller: SettingsController,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    BackHandler(onBack = onBack)

    Surface(
        modifier = modifier.fillMaxSize().testTag("settings-screen"),
        color = Color.Black,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 64.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onBack) { Text("Back") }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "SkTorrent",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (state.configured) "Account configured" else "Login required for CZ/SK tracker search",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )

            OutlinedTextField(
                value = state.username,
                onValueChange = controller::setUsername,
                modifier = Modifier.width(520.dp).testTag("sktorrent-username"),
                label = { Text("Username") },
                singleLine = true,
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = controller::setPassword,
                modifier = Modifier.width(520.dp).testTag("sktorrent-password"),
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = controller::save, modifier = Modifier.testTag("sktorrent-save")) {
                    Text("Save")
                }
                TextButton(onClick = controller::clear) {
                    Text("Clear")
                }
            }

            state.feedback?.let { feedback ->
                Text(
                    text = feedback,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}
