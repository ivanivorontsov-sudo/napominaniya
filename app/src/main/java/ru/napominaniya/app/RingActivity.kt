package ru.napominaniya.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ru.napominaniya.app.core.RingService
import ru.napominaniya.app.core.RingState
import ru.napominaniya.app.data.Challenge
import ru.napominaniya.app.data.TimeUtil
import kotlin.math.sqrt
import kotlin.random.Random

/** Полноэкранный звонок поверх экрана блокировки */
class RingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() {} })
        setContent {
            AppTheme(forceDark = true) {
                val st by RingService.state.collectAsState()
                var seen by remember { mutableStateOf(false) }
                LaunchedEffect(st) { if (st != null) seen = true else if (seen) finish() }
                LaunchedEffect(Unit) { delay(4000); if (RingService.state.value == null) finish() }
                st?.let { RingScreen(it) }
            }
        }
    }
}

@Composable
private fun RingScreen(st: RingState) {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    var challengeOpen by remember { mutableStateOf(false) }
    val transition = rememberInfiniteTransition(label = "flash")
    val flash by transition.animateColor(Color(0xFF101418), Color(0xFF7F1D1D),
        infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "c")
    val bg = if (st.alert.flashScreen) flash else Color(0xFF101418)

    Column(Modifier.fillMaxSize().background(bg).systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(32.dp))
        Icon(if (st.kind == RingService.KIND_ALARM) Icons.Default.Alarm else Icons.Default.NotificationsActive, null,
            tint = Color(0xFFFFCC80), modifier = Modifier.size(56.dp))
        Text(TimeUtil.time(now), fontSize = 76.sp, fontWeight = FontWeight.Light, color = Color.White)
        Text(TimeUtil.dateShort(now), color = Color(0xFFB0BEC5), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(24.dp))
        Text(st.title, color = Color.White, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        if (st.text.isNotBlank() && st.kind == RingService.KIND_REMINDER)
            Text(st.text, color = Color(0xFFCFD8DC), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp), maxLines = 8)
        if (st.snoozeCount > 0) Text("Отложено уже ${st.snoozeCount} раз" + if (st.alert.maxSnoozes > 0) " из ${st.alert.maxSnoozes}" else "",
            color = Color(0xFFFFAB91), modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(32.dp))

        if (challengeOpen) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E272E))) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    when (st.alert.challenge) {
                        Challenge.MATH -> MathChallenge(st.alert.mathDifficulty, st.alert.mathCount) { RingService.command(ctx, RingService.ACT_DISMISS) }
                        Challenge.SHAKE -> ShakeChallenge(st.alert.shakeCount) { RingService.command(ctx, RingService.ACT_DISMISS) }
                        Challenge.TYPE_TEXT -> TypeChallenge(st.alert.challengePhrase) { RingService.command(ctx, RingService.ACT_DISMISS) }
                        Challenge.NONE -> {}
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (st.canSnooze) {
            Button({ RingService.command(ctx, RingService.ACT_SNOOZE) }, Modifier.fillMaxWidth().height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F))) {
                Icon(Icons.Default.Snooze, null); Spacer(Modifier.width(8.dp))
                Text("Отложить на ${st.snoozeMinutes} мин", fontSize = 18.sp)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 15, 30).filter { it != st.snoozeMinutes }.take(3).forEach { m ->
                    OutlinedButton({ RingService.command(ctx, RingService.ACT_SNOOZE, m) }, Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp)) { Text("$m мин", color = Color.White, maxLines = 1) }
                }
            }
        } else Text("Лимит откладываний исчерпан", color = Color(0xFFFFAB91), modifier = Modifier.padding(8.dp))

        if (!challengeOpen) Button({
            if (st.alert.challenge == Challenge.NONE) RingService.command(ctx, RingService.ACT_DISMISS) else challengeOpen = true
        }, Modifier.fillMaxWidth().height(64.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))) {
            Icon(Icons.Default.AlarmOff, null); Spacer(Modifier.width(8.dp))
            Text(if (st.kind == RingService.KIND_ALARM) "Выключить" else "Готово", fontSize = 18.sp)
        }
        Spacer(Modifier.height(16.dp))
    }
}

private data class Problem(val text: String, val answer: Int)

private fun makeProblem(d: Int): Problem = when (d) {
    1 -> { val a = Random.nextInt(10, 60); val b = Random.nextInt(10, 40); Problem("$a + $b", a + b) }
    2 -> { val a = Random.nextInt(3, 13); val b = Random.nextInt(3, 13); val c = Random.nextInt(10, 90); Problem("$a × $b + $c", a * b + c) }
    else -> { val a = Random.nextInt(11, 30); val b = Random.nextInt(3, 13); val c = Random.nextInt(50, 200); val e = Random.nextInt(10, 99); Problem("$a × $b − $c + $e", a * b - c + e) }
}

@Composable
private fun MathChallenge(difficulty: Int, count: Int, onSolved: () -> Unit) {
    var solved by remember { mutableIntStateOf(0) }
    var p by remember { mutableStateOf(makeProblem(difficulty)) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    Text("Пример ${solved + 1} из $count", color = Color(0xFFB0BEC5))
    Text("${p.text} = ?", fontSize = 34.sp, color = Color.White, fontWeight = FontWeight.Bold)
    OutlinedTextField(input, { input = it.filter { c -> c.isDigit() || c == '-' }; error = false }, singleLine = true, isError = error,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text("Ответ") })
    Button({
        if (input.toIntOrNull() == p.answer) {
            solved++; input = ""
            if (solved >= count) onSolved() else p = makeProblem(difficulty)
        } else { error = true; input = ""; p = makeProblem(difficulty) }
    }, Modifier.padding(top = 8.dp)) { Text("Проверить") }
    if (error) Text("Неверно, новый пример", color = Color(0xFFFF8A80))
}

@Composable
private fun ShakeChallenge(target: Int, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var count by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var last = 0L
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val g = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]) / SensorManager.GRAVITY_EARTH
                val t = System.currentTimeMillis()
                if (g > 2.0f && t - last > 250) { last = t; count++; if (count >= target) onDone() }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (acc != null) sm.registerListener(l, acc, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(l) }
    }
    Text("Потрясите телефон", color = Color.White, style = MaterialTheme.typography.titleLarge)
    Text("$count / $target", fontSize = 40.sp, color = Color.White)
    LinearProgressIndicator(progress = { count / target.toFloat() }, modifier = Modifier.fillMaxWidth().padding(8.dp))
}

@Composable
private fun TypeChallenge(phrase: String, onDone: () -> Unit) {
    var input by remember { mutableStateOf("") }
    Text("Введите фразу:", color = Color(0xFFB0BEC5))
    Text("«$phrase»", color = Color.White, style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(input, {
        input = it
        if (it.trim().equals(phrase.trim(), ignoreCase = true)) onDone()
    }, singleLine = true)
}
