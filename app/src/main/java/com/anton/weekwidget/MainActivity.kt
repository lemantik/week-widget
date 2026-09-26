package com.anton.weekwidget

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Экран настройки: только запросить доступ к календарю. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { SetupScreen() }
            }
        }
    }

    @Composable
    private fun SetupScreen() {
        var granted by remember { mutableStateOf(CalendarRepository.hasPermission(this)) }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            granted = ok
            Scheduler.scheduleAll(this)
        }

        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text(
                text = if (granted)
                    "Доступ к календарю есть. Добавьте виджет «Неделя»: долгое нажатие на главном экране → Виджеты."
                else
                    "Виджету нужен доступ к календарю, чтобы показывать события.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(16.dp))
            if (granted) {
                Button(onClick = { Scheduler.scheduleAll(this@MainActivity) }) { Text("Обновить виджет") }
            } else {
                Button(onClick = { launcher.launch(Manifest.permission.READ_CALENDAR) }) { Text("Дать доступ") }
            }
        }
    }
}
