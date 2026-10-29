package dialer.dominik.pl.ui.screens

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dialer.dominik.pl.ui.theme.ZielonyNeon
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NagraniaScreen(
    context: Context,
    onBack: () -> Unit = {}
) {
    var nagrania by remember { mutableStateOf<List<File>>(emptyList()) }
    var odswiezenie by remember { mutableIntStateOf(0) }
    var odtwarzanyPlik by remember { mutableStateOf<File?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var czyOdtwarza by remember { mutableStateOf(false) }

    LaunchedEffect(odswiezenie) {
        val katalog = File(context.filesDir, "recordings")
        if (katalog.exists() && katalog.isDirectory) {
            nagrania = katalog.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
        } else {
            nagrania = emptyList()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                            title = {
                                Text(
                                    text = "NAGRANE ROZMOWY",
                                    color = Color.White.copy(alpha = 0.42f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 2.2.sp
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                        )
        }
    ) { odstepy ->
        if (nagrania.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(odstepy),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Brak nagranych rozmów",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 15.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(odstepy),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(nagrania, key = { it.absolutePath }) { plik ->
                    val jestOdtwarzany = odtwarzanyPlik == plik && czyOdtwarza

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF14221B)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, if (jestOdtwarzany) ZielonyNeon else Color(0xFF1E382C))
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = plik.name,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.height(4.dp))
                                val formatDaty = SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.getDefault())
                                val dataTekst = formatDaty.format(Date(plik.lastModified()))
                                val rozmiarMb = String.format(Locale.US, "%.1f MB", plik.length() / (1024.0 * 1024.0))
                                Text(
                                    text = "$dataTekst • $rozmiarMb",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 12.sp
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(onClick = {
                                    if (jestOdtwarzany) {
                                        mediaPlayer?.pause()
                                        czyOdtwarza = false
                                    } else {
                                        mediaPlayer?.release()
                                        mediaPlayer = MediaPlayer().apply {
                                            setDataSource(plik.absolutePath)
                                            prepare()
                                            start()
                                            setOnCompletionListener {
                                                czyOdtwarza = false
                                                odtwarzanyPlik = null
                                            }
                                        }
                                        odtwarzanyPlik = plik
                                        czyOdtwarza = true
                                    }
                                }) {
                                    Text(if (jestOdtwarzany) "⏸️" else "▶️", fontSize = 20.sp)
                                }

                                IconButton(onClick = {
                                    if (jestOdtwarzany) {
                                        mediaPlayer?.release()
                                        czyOdtwarza = false
                                        odtwarzanyPlik = null
                                    }
                                    plik.delete()
                                    odswiezenie++
                                }) {
                                    Text("🗑️", fontSize = 18.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
