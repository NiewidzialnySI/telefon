package dialer.dominik.pl.ui.screens

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dialer.dominik.pl.sms.WyslijPlanowanySmsReceiver
import dialer.dominik.pl.ui.theme.ZielonyMorski
import dialer.dominik.pl.ui.theme.ZielonyNeon
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Dialog planowania wysyłki SMS: wybór dnia (dziś/jutro/pojutrze) + godziny.
 * Pojawia się z animacją (scale + fade) — wołany w trakcie przytrzymania wysyłki.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogPlanowaniaSms(
    context: Context,
    numer: String,
    tresc: String,
    onZaplanowano: (Long) -> Unit,
    onAnulowano: () -> Unit
) {
    val kal = Calendar.getInstance()
    val tps = rememberTimePickerState(
        initialHour = kal.get(Calendar.HOUR_OF_DAY),
        initialMinute = kal.get(Calendar.MINUTE)
    )
    var przesuniecieDnia by rememberSaveable { mutableIntStateOf(0) }
    // Animacja pojawiania się okna: start od niewidocznego → przejście do widocznego daje wejście.
    var widoczne by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { widoczne = true }

    Dialog(
        onDismissRequest = onAnulowano,
        properties = DialogProperties()
    ) {
        AnimatedVisibility(
            visible = widoczne,
            enter = fadeIn(animationSpec = tween(200)) +
                scaleIn(initialScale = 0.75f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
            exit = fadeOut(animationSpec = tween(150)) +
                scaleOut(targetScale = 0.9f, animationSpec = tween(150))
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFF0B120F),
                border = androidx.compose.foundation.BorderStroke(1.dp, ZielonyNeon.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Zaplanuj wysłanie", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val dni = listOf("Dziś", "Jutro", "Pojutrze")
                        dni.forEachIndexed { i, nazwa ->
                            val aktywny = przesuniecieDnia == i
                            Text(
                                text = nazwa,
                                color = if (aktywny) Color(0xFF06231A) else Color.White.copy(alpha = 0.6f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .then(
                                        if (aktywny) Modifier.background(Brush.horizontalGradient(listOf(ZielonyMorski, ZielonyNeon)))
                                        else Modifier.background(Color(0xFF131C18))
                                    )
                                    .clickable { przesuniecieDnia = i }
                                    .padding(vertical = 10.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    TimePicker(state = tps, colors = TimePickerDefaults.colors(containerColor = Color.Transparent))
                    Spacer(Modifier.height(8.dp))
                    val tex = Calendar.getInstance()
                    tex.add(Calendar.DAY_OF_YEAR, przesuniecieDnia)
                    tex.set(Calendar.HOUR_OF_DAY, tps.hour)
                    tex.set(Calendar.MINUTE, tps.minute)
                    tex.set(Calendar.SECOND, 0)
                    Text(
                        text = "Wyśle: " + SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.getDefault()).format(tex.time),
                        color = ZielonyNeon, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onAnulowano,
                            modifier = Modifier.weight(1f)
                        ) { Text("Anuluj", color = Color.White.copy(alpha = 0.6f)) }
                        Spacer(Modifier.width(4.dp))
                        Button(
                            onClick = {
                                val cal = Calendar.getInstance()
                                cal.add(Calendar.DAY_OF_YEAR, przesuniecieDnia)
                                cal.set(Calendar.HOUR_OF_DAY, tps.hour)
                                cal.set(Calendar.MINUTE, tps.minute)
                                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                                if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
                                onZaplanowano(cal.timeInMillis)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ZielonyNeon,
                                contentColor = Color(0xFF06231A)
                            ),
                            modifier = Modifier.weight(1f)
                        ) { Text("Zaplanuj", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

fun zaplanujWiadomosc(context: Context, numer: String, tresc: String, czasMs: Long): Long {
    val prefs = context.getSharedPreferences("sms_planowane", Context.MODE_PRIVATE)
    val id = java.util.UUID.randomUUID().toString()
    prefs.edit().putString(id, "$numer\t$czasMs\t$tresc").apply()
    val intent = Intent(context, WyslijPlanowanySmsReceiver::class.java).putExtra("PLAN_ID", id)
    val pending = PendingIntent.getBroadcast(
        context, id.hashCode(), intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    android.util.Log.i("PlanowanySms", "Planuje wysylke numer=$numer czas=$czasMs")
    try {
        alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, czasMs, pending)
    } catch (e: SecurityException) {
        android.util.Log.w("PlanowanySms", "Brak SCHEDULE_EXACT_ALARM, przyblizony", e)
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, czasMs, pending)
    }
    return czasMs
}
