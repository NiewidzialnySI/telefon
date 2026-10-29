package dialer.dominik.pl.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import dialer.dominik.pl.ui.theme.ZielonyNeon
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Edycja załącznika MMS:
 * - domyślnie podgląd: pan + pinch zoom (przycięcie wyłączone)
 * - Przytnij → pojawia się kwadrat z uchwytami
 * - Pedzel → rysowanie z ręki (kolor + grubość)
 * - strzałka / kwadrat / koło
 */
private enum class NarzedzieEdycji {
    /** Tylko oglądanie / zoom / pan — kadr niewidoczny. */
    PRZEGLADAJ,
    PRZYTNIJ,
    PEDZEL,
    STRZALKA,
    KWADRAT,
    KOLO
}

private enum class RogKadru { TL, TR, BL, BR, SRODEK }

private sealed class Adnotacja {
    data class Strzalka(val start: Offset, val end: Offset, val kolor: Color, val grubosc: Float) : Adnotacja()
    data class Kwadrat(val a: Offset, val b: Offset, val kolor: Color, val grubosc: Float) : Adnotacja()
    data class Kolo(val a: Offset, val b: Offset, val kolor: Color, val grubosc: Float) : Adnotacja()
    data class Pedzel(val punkty: List<Offset>, val kolor: Color, val grubosc: Float) : Adnotacja()
}

private data class TransformWidoku(
    val viewport: Size,
    val bmpW: Int,
    val bmpH: Int,
    val zoom: Float,
    val pan: Offset
) {
    val bazaSkali: Float
        get() = if (viewport.width <= 0f || viewport.height <= 0f || bmpW <= 0 || bmpH <= 0) 1f
        else min(viewport.width / bmpW, viewport.height / bmpH)

    val skala: Float get() = bazaSkali * zoom
    val lewo: Float get() = viewport.width / 2f - bmpW * skala / 2f + pan.x
    val gora: Float get() = viewport.height / 2f - bmpH * skala / 2f + pan.y

    fun ekranNaObraz(p: Offset): Offset = Offset(
        ((p.x - lewo) / skala).coerceIn(0f, bmpW.toFloat()),
        ((p.y - gora) / skala).coerceIn(0f, bmpH.toFloat())
    )

    fun obrazNaEkran(p: Offset): Offset = Offset(lewo + p.x * skala, gora + p.y * skala)

    fun rectObrazNaEkran(r: Rect): Rect = Rect(
        obrazNaEkran(Offset(r.left, r.top)),
        obrazNaEkran(Offset(r.right, r.bottom))
    )
}

private val KOLORY_PEDZLA = listOf(
    ZielonyNeon,
    Color.White,
    Color(0xFFFF5252),
    Color(0xFFFFD740),
    Color(0xFF40C4FF),
    Color(0xFFE040FB),
    Color.Black
)

@Composable
internal fun DialogEdycjiZalacznika(
    context: Context,
    uri: Uri,
    onAnuluj: () -> Unit,
    onZapisz: (noweUri: Uri, podglad: Bitmap) -> Unit
) {
    val zrodlo = remember(uri) { wczytajBitmapeDoEdycji(context, uri) }
    if (zrodlo == null) {
        Dialog(onDismissRequest = onAnuluj) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF0D1611))
                    .padding(24.dp)
            ) {
                Text("Nie udało się otworzyć zdjęcia", color = Color.White)
            }
        }
        return
    }

    var baza by remember(uri) { mutableStateOf(zrodlo) }
    var adnotacje by remember { mutableStateOf<List<Adnotacja>>(emptyList()) }
    var historia by remember { mutableStateOf<List<List<Adnotacja>>>(emptyList()) }
    // Domyślnie BEZ przycięcia — tylko podgląd + zoom.
    var narzedzie by remember { mutableStateOf(NarzedzieEdycji.PRZEGLADAJ) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(Size.Zero) }
    var kadr by remember(baza.width, baza.height) {
        mutableStateOf(domyslnyKadrKwadrat(baza.width, baza.height))
    }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }
    var roboczyPedzel by remember { mutableStateOf<List<Offset>?>(null) }

    var kolorPedzla by remember { mutableStateOf(ZielonyNeon) }
    var gruboscPedzla by remember { mutableFloatStateOf(8f) }

    val density = LocalDensity.current
    val uchwytPx = with(density) { 32.dp.toPx() }

    fun pushHistoria() {
        historia = (historia + listOf(adnotacje)).takeLast(30)
    }

    fun ograniczPan(nowy: Offset, z: Float): Offset {
        val t = TransformWidoku(viewport, baza.width, baza.height, z, Offset.Zero)
        val maxX = max(0f, (baza.width * t.skala - viewport.width) / 2f + 80f)
        val maxY = max(0f, (baza.height * t.skala - viewport.height) / 2f + 80f)
        return Offset(nowy.x.coerceIn(-maxX, maxX), nowy.y.coerceIn(-maxY, maxY))
    }

    fun ustawZoom(nowy: Float) {
        val z = nowy.coerceIn(0.6f, 8f)
        zoom = z
        pan = ograniczPan(pan, z)
    }

    fun transformTeraz(): TransformWidoku =
        TransformWidoku(viewport, baza.width, baza.height, zoom, pan)

    fun trafienieRog(ekran: Offset, k: Rect): RogKadru? {
        // Zawsze świeży transform (zoom/pan) — nie snapshot z momentu startu pointerInput.
        val e = transformTeraz().rectObrazNaEkran(k)
        val rogi = listOf(
            RogKadru.TL to Offset(e.left, e.top),
            RogKadru.TR to Offset(e.right, e.top),
            RogKadru.BL to Offset(e.left, e.bottom),
            RogKadru.BR to Offset(e.right, e.bottom)
        )
        rogi.forEach { (rog, p) ->
            if (hypot(ekran.x - p.x, ekran.y - p.y) <= uchwytPx) return rog
        }
        // Środek dopiero gdy jesteśmy wyraźnie wewnątrz (nie na krawędzi).
        val inset = uchwytPx * 0.6f
        val wewnatrz = Rect(e.left + inset, e.top + inset, e.right - inset, e.bottom - inset)
        if (wewnatrz.width > 0 && wewnatrz.height > 0 && wewnatrz.contains(ekran)) return RogKadru.SRODEK
        return null
    }

    fun zapiszKoncowy() {
        val wynik = if (narzedzie == NarzedzieEdycji.PRZYTNIJ) {
            val wyciety = wytnijKadr(baza, kadr)
            zlozObrazEdycji(wyciety, przeskalujAdnotacjeDoKadru(adnotacje, kadr), null)
        } else {
            zlozObrazEdycji(baza, adnotacje, null)
        }
        val zapis = zapiszBitmapeZalacznika(context, wynik) ?: return onAnuluj()
        onZapisz(zapis, przeskalujPodglad(wynik))
    }

    LaunchedEffect(baza) {
        zoom = 1f
        pan = Offset.Zero
        kadr = domyslnyKadrKwadrat(baza.width, baza.height)
    }

    // Przy wejściu w tryb przycięcia — świeży kwadrat na środku.
    LaunchedEffect(narzedzie) {
        if (narzedzie == NarzedzieEdycji.PRZYTNIJ) {
            kadr = domyslnyKadrKwadrat(baza.width, baza.height)
        }
    }

    BackHandler(onBack = onAnuluj)

    Dialog(
        onDismissRequest = onAnuluj,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { viewport = Size(it.width.toFloat(), it.height.toFloat()) }
                    // Klucze TYLKO narzędzie + rozmiar bitmapy.
                    // kadr/zoom/pan NIE mogą być kluczami — inaczej każdy ruch uchwytu
                    // anuluje pointerInput i „puszcza” palec po kilku pikselach.
                    .pointerInput(narzedzie, baza.width, baza.height) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val startPos = down.position
                            val rog = if (narzedzie == NarzedzieEdycji.PRZYTNIJ) {
                                trafienieRog(startPos, kadr)
                            } else null

                            // —— Przycięcie: uchwyty rogów / przesuwanie kwadratu ——
                            if (narzedzie == NarzedzieEdycji.PRZYTNIJ && rog != null) {
                                val startKadr = kadr
                                val startObraz = transformTeraz().ekranNaObraz(startPos)
                                val bmpW = baza.width
                                val bmpH = baza.height
                                drag(down.id) { change ->
                                    change.consume()
                                    val teraz = transformTeraz().ekranNaObraz(change.position)
                                    kadr = gdyPrzesunRog(
                                        startKadr, rog,
                                        teraz.x - startObraz.x, teraz.y - startObraz.y,
                                        bmpW, bmpH
                                    )
                                }
                                return@awaitEachGesture
                            }

                            var pastSlop = false
                            var drawing = false
                            var transforming = false
                            val minSlop = 10f
                            val startImg = transformTeraz().ekranNaObraz(startPos)
                            val punktyPedzla = mutableListOf<Offset>()
                            val kolorStroke = kolorPedzla
                            val gruboscStroke = gruboscPedzla

                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }

                                if (pressed.size >= 2) {
                                    transforming = true
                                    drawing = false
                                    dragStart = null
                                    dragCurrent = null
                                    roboczyPedzel = null
                                    punktyPedzla.clear()
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    ustawZoom(zoom * zoomChange)
                                    pan = ograniczPan(pan + panChange, zoom)
                                    pressed.forEach { if (it.positionChanged()) it.consume() }
                                } else if (!transforming) {
                                    val p = pressed.firstOrNull()?.position ?: break
                                    when (narzedzie) {
                                        NarzedzieEdycji.PRZEGLADAJ,
                                        NarzedzieEdycji.PRZYTNIJ -> {
                                            // 1 palec = pan zdjęcia (przy przycięciu: poza uchwytami).
                                            val panChange = event.calculatePan()
                                            if (panChange != Offset.Zero) {
                                                pan = ograniczPan(pan + panChange, zoom)
                                                pressed.forEach { if (it.positionChanged()) it.consume() }
                                            }
                                        }
                                        NarzedzieEdycji.PEDZEL -> {
                                            val dist = hypot(p.x - startPos.x, p.y - startPos.y)
                                            if (!pastSlop && dist > minSlop) {
                                                pastSlop = true
                                                drawing = true
                                                punktyPedzla += startImg
                                            }
                                            if (drawing) {
                                                val img = transformTeraz().ekranNaObraz(p)
                                                if (punktyPedzla.isEmpty() ||
                                                    hypot(
                                                        img.x - punktyPedzla.last().x,
                                                        img.y - punktyPedzla.last().y
                                                    ) > 1.5f
                                                ) {
                                                    punktyPedzla += img
                                                    roboczyPedzel = punktyPedzla.toList()
                                                }
                                                pressed.forEach { if (it.positionChanged()) it.consume() }
                                            }
                                        }
                                        else -> {
                                            val dist = hypot(p.x - startPos.x, p.y - startPos.y)
                                            if (!pastSlop && dist > minSlop) {
                                                pastSlop = true
                                                drawing = true
                                                dragStart = startImg
                                            }
                                            if (drawing) {
                                                dragCurrent = transformTeraz().ekranNaObraz(p)
                                                pressed.forEach { if (it.positionChanged()) it.consume() }
                                            }
                                        }
                                    }
                                }
                            } while (pressed.isNotEmpty())

                            if (drawing && narzedzie == NarzedzieEdycji.PEDZEL && punktyPedzla.size >= 2) {
                                pushHistoria()
                                adnotacje = adnotacje + Adnotacja.Pedzel(
                                    punktyPedzla.toList(), kolorStroke, gruboscStroke
                                )
                            } else if (drawing && dragStart != null && dragCurrent != null) {
                                val a = dragStart!!
                                val b = dragCurrent!!
                                if (hypot(b.x - a.x, b.y - a.y) > 8f) {
                                    pushHistoria()
                                    adnotacje = adnotacje + when (narzedzie) {
                                        NarzedzieEdycji.STRZALKA ->
                                            Adnotacja.Strzalka(a, b, kolorStroke, gruboscStroke)
                                        NarzedzieEdycji.KWADRAT ->
                                            Adnotacja.Kwadrat(a, b, kolorStroke, gruboscStroke)
                                        NarzedzieEdycji.KOLO ->
                                            Adnotacja.Kolo(a, b, kolorStroke, gruboscStroke)
                                        else -> return@awaitEachGesture
                                    }
                                }
                            }
                            dragStart = null
                            dragCurrent = null
                            roboczyPedzel = null
                        }
                    }
            ) {
                val imageBitmap = remember(baza) { baza.asImageBitmap() }
                val roboczy = when {
                    roboczyPedzel != null && roboczyPedzel!!.size >= 2 ->
                        Adnotacja.Pedzel(roboczyPedzel!!, kolorPedzla, gruboscPedzla)
                    dragStart != null && dragCurrent != null ->
                        when (narzedzie) {
                            NarzedzieEdycji.STRZALKA ->
                                Adnotacja.Strzalka(dragStart!!, dragCurrent!!, kolorPedzla, gruboscPedzla)
                            NarzedzieEdycji.KWADRAT ->
                                Adnotacja.Kwadrat(dragStart!!, dragCurrent!!, kolorPedzla, gruboscPedzla)
                            NarzedzieEdycji.KOLO ->
                                Adnotacja.Kolo(dragStart!!, dragCurrent!!, kolorPedzla, gruboscPedzla)
                            else -> null
                        }
                    else -> null
                }

                Canvas(Modifier.fillMaxSize()) {
                    if (viewport.width <= 0f) return@Canvas
                    val t = TransformWidoku(size, baza.width, baza.height, zoom, pan)
                    drawImage(
                        image = imageBitmap,
                        dstOffset = IntOffset(t.lewo.roundToInt(), t.gora.roundToInt()),
                        dstSize = IntSize(
                            (baza.width * t.skala).roundToInt().coerceAtLeast(1),
                            (baza.height * t.skala).roundToInt().coerceAtLeast(1)
                        )
                    )
                    (adnotacje + listOfNotNull(roboczy)).forEach { a ->
                        rysujAdnotacjeNaEkranie(a, t)
                    }

                    if (narzedzie == NarzedzieEdycji.PRZYTNIJ) {
                        val e = t.rectObrazNaEkran(kadr)
                        val dim = Color.Black.copy(alpha = 0.58f)
                        drawRect(dim, Offset.Zero, Size(size.width, e.top.coerceAtLeast(0f)))
                        drawRect(dim, Offset(0f, e.bottom), Size(size.width, (size.height - e.bottom).coerceAtLeast(0f)))
                        drawRect(dim, Offset(0f, e.top), Size(e.left.coerceAtLeast(0f), e.height))
                        drawRect(dim, Offset(e.right, e.top), Size((size.width - e.right).coerceAtLeast(0f), e.height))
                        drawRect(
                            ZielonyNeon,
                            Offset(e.left, e.top),
                            Size(e.width, e.height),
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                        // Koła-uchwyty na rogach — wygodniejsze niż same „L”.
                        listOf(
                            Offset(e.left, e.top),
                            Offset(e.right, e.top),
                            Offset(e.left, e.bottom),
                            Offset(e.right, e.bottom)
                        ).forEach { p ->
                            drawCircle(Color.Black.copy(alpha = 0.35f), radius = 11.dp.toPx(), center = p)
                            drawCircle(ZielonyNeon, radius = 8.dp.toPx(), center = p)
                            drawCircle(Color(0xFF06231A), radius = 3.5.dp.toPx(), center = p)
                        }
                    }
                }
            }

            // Zoom +/- zawsze dostępne (wygodne przycinanie).
            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF121A16).copy(alpha = 0.9f))
                    .border(1.dp, ZielonyNeon.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ZoomBtn("+") { ustawZoom(zoom * 1.25f) }
                Text(
                    "${(zoom * 100).roundToInt()}%",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                ZoomBtn("−") { ustawZoom(zoom / 1.25f) }
            }

            // Góra
            Row(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Pillka("Anuluj", jasna = false, onClick = onAnuluj)
                Spacer(Modifier.weight(1f))
                Pillka(
                    "Cofnij",
                    jasna = false,
                    enabled = historia.isNotEmpty() || adnotacje.isNotEmpty(),
                    onClick = {
                        when {
                            historia.isNotEmpty() -> {
                                adnotacje = historia.last()
                                historia = historia.dropLast(1)
                            }
                            adnotacje.isNotEmpty() -> adnotacje = adnotacje.dropLast(1)
                        }
                    }
                )
                Spacer(Modifier.width(8.dp))
                Pillka("Gotowe", jasna = true, onClick = { zapiszKoncowy() })
            }

            // Dół
            Column(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))))
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (narzedzie == NarzedzieEdycji.PRZYTNIJ) {
                    Text(
                        text = "Zastosuj przycięcie",
                        color = Color(0xFF06231A),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .padding(bottom = 10.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(ZielonyNeon)
                            .clickable {
                                baza = wytnijKadr(baza, kadr)
                                adnotacje = przeskalujAdnotacjeDoKadru(adnotacje, kadr)
                                historia = emptyList()
                                narzedzie = NarzedzieEdycji.PRZEGLADAJ
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }

                if (narzedzie == NarzedzieEdycji.PEDZEL ||
                    narzedzie == NarzedzieEdycji.STRZALKA ||
                    narzedzie == NarzedzieEdycji.KWADRAT ||
                    narzedzie == NarzedzieEdycji.KOLO
                ) {
                    PanelPedzla(
                        kolor = kolorPedzla,
                        grubosc = gruboscPedzla,
                        onKolor = { kolorPedzla = it },
                        onGrubosc = { gruboscPedzla = it }
                    )
                    Spacer(Modifier.height(10.dp))
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF121A16).copy(alpha = 0.94f))
                        .border(1.dp, ZielonyNeon.copy(alpha = 0.16f), RoundedCornerShape(22.dp))
                        .padding(horizontal = 6.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NarzedzieDock(narzedzie == NarzedzieEdycji.PRZYTNIJ, "Przytnij", {
                        narzedzie = if (narzedzie == NarzedzieEdycji.PRZYTNIJ) {
                            NarzedzieEdycji.PRZEGLADAJ
                        } else {
                            NarzedzieEdycji.PRZYTNIJ
                        }
                    }) { IkonaPrzytnij(it) }
                    NarzedzieDock(narzedzie == NarzedzieEdycji.PEDZEL, "Pedzel", {
                        narzedzie = NarzedzieEdycji.PEDZEL
                    }) { IkonaPedzel(it) }
                    NarzedzieDock(narzedzie == NarzedzieEdycji.STRZALKA, "Strzałka", {
                        narzedzie = NarzedzieEdycji.STRZALKA
                    }) { IkonaStrzalkaNarzedzia(it) }
                    NarzedzieDock(narzedzie == NarzedzieEdycji.KWADRAT, "Kwadrat", {
                        narzedzie = NarzedzieEdycji.KWADRAT
                    }) { IkonaKwadrat(it) }
                    NarzedzieDock(narzedzie == NarzedzieEdycji.KOLO, "Koło", {
                        narzedzie = NarzedzieEdycji.KOLO
                    }) { IkonaKolo(it) }
                }

                Text(
                    text = when (narzedzie) {
                        NarzedzieEdycji.PRZEGLADAJ -> "Przesuwaj jednym palcem · pinch lub +/− = zoom"
                        NarzedzieEdycji.PRZYTNIJ -> "Dopasuj kwadrat uchwytami · zoom +/− · potem Zastosuj"
                        NarzedzieEdycji.PEDZEL -> "Rysuj palcem · wybierz kolor i grubość"
                        NarzedzieEdycji.STRZALKA -> "Przeciągnij strzałkę"
                        NarzedzieEdycji.KWADRAT -> "Przeciągnij prostokąt"
                        NarzedzieEdycji.KOLO -> "Przeciągnij owal"
                    },
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun PanelPedzla(
    kolor: Color,
    grubosc: Float,
    onKolor: (Color) -> Unit,
    onGrubosc: (Float) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF121A16).copy(alpha = 0.94f))
            .border(1.dp, ZielonyNeon.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
            .padding(12.dp)
    ) {
        Text("Kolor", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KOLORY_PEDZLA.forEach { k ->
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(k)
                        .then(
                            if (k == kolor) Modifier.border(2.dp, Color.White, CircleShape)
                            else Modifier.border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                        )
                        .clickable { onKolor(k) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Grubość", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(4f to "Cienki", 8f to "Śr.", 14f to "Gruby", 22f to "Bardzo").forEach { (g, label) ->
                val on = grubosc == g
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onGrubosc(g) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Canvas(Modifier.size(width = 36.dp, height = 16.dp)) {
                        drawLine(
                            if (on) ZielonyNeon else Color.White.copy(alpha = 0.55f),
                            Offset(4f, size.height / 2f),
                            Offset(size.width - 4f, size.height / 2f),
                            strokeWidth = g * 0.9f,
                            cap = StrokeCap.Round
                        )
                    }
                    Text(
                        label,
                        color = if (on) ZielonyNeon else Color.White.copy(alpha = 0.45f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomBtn(tekst: String, onClick: () -> Unit) {
    Text(
        tekst,
        color = ZielonyNeon,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .wrapContentSize(Alignment.Center)
            .padding(4.dp)
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.rysujAdnotacjeNaEkranie(
    a: Adnotacja,
    t: TransformWidoku
) {
    when (a) {
        is Adnotacja.Strzalka -> {
            val s = t.obrazNaEkran(a.start)
            val e = t.obrazNaEkran(a.end)
            val w = max(3f, a.grubosc * t.skala * 0.55f)
            rysujStrzalkeEkran(s, e, a.kolor, w)
        }
        is Adnotacja.Kwadrat -> {
            val r = t.rectObrazNaEkran(
                Rect(min(a.a.x, a.b.x), min(a.a.y, a.b.y), max(a.a.x, a.b.x), max(a.a.y, a.b.y))
            )
            drawRect(
                a.kolor, Offset(r.left, r.top), Size(r.width, r.height),
                style = Stroke(width = max(3f, a.grubosc * t.skala * 0.55f), join = StrokeJoin.Round)
            )
        }
        is Adnotacja.Kolo -> {
            val r = t.rectObrazNaEkran(
                Rect(min(a.a.x, a.b.x), min(a.a.y, a.b.y), max(a.a.x, a.b.x), max(a.a.y, a.b.y))
            )
            drawOval(
                a.kolor, Offset(r.left, r.top), Size(r.width, r.height),
                style = Stroke(width = max(3f, a.grubosc * t.skala * 0.55f))
            )
        }
        is Adnotacja.Pedzel -> {
            if (a.punkty.size < 2) return
            val path = Path()
            val p0 = t.obrazNaEkran(a.punkty.first())
            path.moveTo(p0.x, p0.y)
            for (i in 1 until a.punkty.size) {
                val p = t.obrazNaEkran(a.punkty[i])
                path.lineTo(p.x, p.y)
            }
            drawPath(
                path, a.kolor,
                style = Stroke(
                    width = max(2f, a.grubosc * t.skala * 0.55f),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

/** Kwadrat ~68% krótszego boku, wyśrodkowany. */
private fun domyslnyKadrKwadrat(w: Int, h: Int): Rect {
    val side = min(w, h) * 0.68f
    val l = (w - side) / 2f
    val t = (h - side) / 2f
    return Rect(l, t, l + side, t + side)
}

private fun gdyPrzesunRog(
    start: Rect,
    rog: RogKadru,
    dx: Float,
    dy: Float,
    maxW: Int,
    maxH: Int
): Rect {
    val minRoz = min(maxW, maxH) * 0.10f
    var l = start.left
    var t = start.top
    var r = start.right
    var b = start.bottom
    when (rog) {
        RogKadru.TL -> {
            l = (start.left + dx).coerceIn(0f, start.right - minRoz)
            t = (start.top + dy).coerceIn(0f, start.bottom - minRoz)
        }
        RogKadru.TR -> {
            r = (start.right + dx).coerceIn(start.left + minRoz, maxW.toFloat())
            t = (start.top + dy).coerceIn(0f, start.bottom - minRoz)
        }
        RogKadru.BL -> {
            l = (start.left + dx).coerceIn(0f, start.right - minRoz)
            b = (start.bottom + dy).coerceIn(start.top + minRoz, maxH.toFloat())
        }
        RogKadru.BR -> {
            r = (start.right + dx).coerceIn(start.left + minRoz, maxW.toFloat())
            b = (start.bottom + dy).coerceIn(start.top + minRoz, maxH.toFloat())
        }
        RogKadru.SRODEK -> {
            val ww = start.width
            val hh = start.height
            l = (start.left + dx).coerceIn(0f, maxW - ww)
            t = (start.top + dy).coerceIn(0f, maxH - hh)
            r = l + ww
            b = t + hh
        }
    }
    return Rect(l, t, r, b)
}

private fun przeskalujAdnotacjeDoKadru(adnotacje: List<Adnotacja>, kadr: Rect): List<Adnotacja> {
    fun map(p: Offset) = Offset(p.x - kadr.left, p.y - kadr.top)
    return adnotacje.mapNotNull { a ->
        when (a) {
            is Adnotacja.Strzalka -> a.copy(start = map(a.start), end = map(a.end))
            is Adnotacja.Kwadrat -> a.copy(a = map(a.a), b = map(a.b))
            is Adnotacja.Kolo -> a.copy(a = map(a.a), b = map(a.b))
            is Adnotacja.Pedzel -> a.copy(punkty = a.punkty.map(::map))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.rysujStrzalkeEkran(
    start: Offset,
    end: Offset,
    kolor: Color,
    width: Float
) {
    drawLine(kolor, start, end, strokeWidth = width, cap = StrokeCap.Round)
    val angle = atan2(end.y - start.y, end.x - start.x)
    val dl = max(22f, width * 4.5f)
    drawLine(kolor, end, Offset(end.x - dl * cos(angle - 0.45f), end.y - dl * sin(angle - 0.45f)), strokeWidth = width, cap = StrokeCap.Round)
    drawLine(kolor, end, Offset(end.x - dl * cos(angle + 0.45f), end.y - dl * sin(angle + 0.45f)), strokeWidth = width, cap = StrokeCap.Round)
}

@Composable
private fun Pillka(tekst: String, jasna: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else 0.35f
    Text(
        text = tekst,
        color = if (jasna) Color(0xFF06231A).copy(alpha = alpha) else Color.White.copy(alpha = 0.85f * alpha),
        fontWeight = if (jasna) FontWeight.Bold else FontWeight.Medium,
        fontSize = 14.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (jasna) ZielonyNeon.copy(alpha = if (enabled) 1f else 0.35f)
                else Color.White.copy(alpha = 0.10f)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    )
}

@Composable
private fun NarzedzieDock(
    aktywne: Boolean,
    etykieta: String,
    onClick: () -> Unit,
    ikona: @Composable (Color) -> Unit
) {
    val kolor = if (aktywne) ZielonyNeon else Color.White.copy(alpha = 0.72f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(68.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (aktywne) ZielonyNeon.copy(alpha = 0.18f) else Color.Transparent)
                .then(
                    if (aktywne) Modifier.border(1.5.dp, ZielonyNeon.copy(alpha = 0.55f), CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) { ikona(kolor) }
        Spacer(Modifier.height(4.dp))
        Text(etykieta, color = kolor, fontSize = 11.sp, fontWeight = if (aktywne) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun IkonaPrzytnij(kolor: Color) {
    Canvas(Modifier.size(22.dp)) {
        val s = size.minDimension
        drawRect(kolor, Offset(s * 0.18f, s * 0.18f), Size(s * 0.64f, s * 0.64f), style = Stroke(width = s * 0.1f))
        drawLine(kolor, Offset(s * 0.08f, s * 0.35f), Offset(s * 0.35f, s * 0.35f), strokeWidth = s * 0.1f, cap = StrokeCap.Round)
        drawLine(kolor, Offset(s * 0.35f, s * 0.08f), Offset(s * 0.35f, s * 0.35f), strokeWidth = s * 0.1f, cap = StrokeCap.Round)
    }
}

@Composable
private fun IkonaPedzel(kolor: Color) {
    Canvas(Modifier.size(22.dp)) {
        val s = size.minDimension
        // Ołówek / pędzel
        drawLine(kolor, Offset(s * 0.22f, s * 0.78f), Offset(s * 0.72f, s * 0.28f), strokeWidth = s * 0.14f, cap = StrokeCap.Round)
        drawCircle(kolor, radius = s * 0.1f, center = Offset(s * 0.22f, s * 0.78f))
        drawLine(kolor, Offset(s * 0.62f, s * 0.22f), Offset(s * 0.78f, s * 0.38f), strokeWidth = s * 0.08f, cap = StrokeCap.Round)
    }
}

@Composable
private fun IkonaStrzalkaNarzedzia(kolor: Color) {
    Canvas(Modifier.size(22.dp)) {
        val s = size.minDimension
        val start = Offset(s * 0.18f, s * 0.72f)
        val end = Offset(s * 0.78f, s * 0.22f)
        drawLine(kolor, start, end, strokeWidth = s * 0.11f, cap = StrokeCap.Round)
        val angle = atan2(end.y - start.y, end.x - start.x)
        val dl = s * 0.28f
        drawLine(kolor, end, Offset(end.x - dl * cos(angle - 0.5f), end.y - dl * sin(angle - 0.5f)), strokeWidth = s * 0.11f, cap = StrokeCap.Round)
        drawLine(kolor, end, Offset(end.x - dl * cos(angle + 0.5f), end.y - dl * sin(angle + 0.5f)), strokeWidth = s * 0.11f, cap = StrokeCap.Round)
    }
}

@Composable
private fun IkonaKwadrat(kolor: Color) {
    Canvas(Modifier.size(22.dp)) {
        val s = size.minDimension
        drawRect(kolor, Offset(s * 0.2f, s * 0.2f), Size(s * 0.6f, s * 0.6f), style = Stroke(width = s * 0.11f, join = StrokeJoin.Round))
    }
}

@Composable
private fun IkonaKolo(kolor: Color) {
    Canvas(Modifier.size(22.dp)) {
        val s = size.minDimension
        drawCircle(kolor, radius = s * 0.32f, center = Offset(s / 2f, s / 2f), style = Stroke(width = s * 0.11f))
    }
}

private fun wczytajBitmapeDoEdycji(context: Context, uri: Uri): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val maxBok = 2048
        var sample = 1
        val bok = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        while (bok / sample > maxBok) sample *= 2
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        }?.copy(Bitmap.Config.ARGB_8888, true)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun wytnijKadr(src: Bitmap, kadr: Rect): Bitmap {
    val l = kadr.left.roundToInt().coerceIn(0, src.width - 1)
    val t = kadr.top.roundToInt().coerceIn(0, src.height - 1)
    val r = kadr.right.roundToInt().coerceIn(l + 1, src.width)
    val b = kadr.bottom.roundToInt().coerceIn(t + 1, src.height)
    return Bitmap.createBitmap(src, l, t, r - l, b - t)
}

private fun zlozObrazEdycji(baza: Bitmap, adnotacje: List<Adnotacja>, kadr: Rect?): Bitmap {
    val wyjscie = baza.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = AndroidCanvas(wyjscie)
    adnotacje.forEach { a ->
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            style = AndroidPaint.Style.STROKE
            strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND
            when (a) {
                is Adnotacja.Strzalka -> {
                    color = a.kolor.toArgb()
                    strokeWidth = max(5f, a.grubosc)
                }
                is Adnotacja.Kwadrat -> {
                    color = a.kolor.toArgb()
                    strokeWidth = max(5f, a.grubosc)
                }
                is Adnotacja.Kolo -> {
                    color = a.kolor.toArgb()
                    strokeWidth = max(5f, a.grubosc)
                }
                is Adnotacja.Pedzel -> {
                    color = a.kolor.toArgb()
                    strokeWidth = max(3f, a.grubosc)
                }
            }
        }
        when (a) {
            is Adnotacja.Strzalka -> {
                canvas.drawLine(a.start.x, a.start.y, a.end.x, a.end.y, paint)
                val angle = atan2(a.end.y - a.start.y, a.end.x - a.start.x)
                val dl = max(28f, paint.strokeWidth * 5f)
                canvas.drawLine(a.end.x, a.end.y, a.end.x - dl * cos(angle - 0.45f), a.end.y - dl * sin(angle - 0.45f), paint)
                canvas.drawLine(a.end.x, a.end.y, a.end.x - dl * cos(angle + 0.45f), a.end.y - dl * sin(angle + 0.45f), paint)
            }
            is Adnotacja.Kwadrat ->
                canvas.drawRect(min(a.a.x, a.b.x), min(a.a.y, a.b.y), max(a.a.x, a.b.x), max(a.a.y, a.b.y), paint)
            is Adnotacja.Kolo ->
                canvas.drawOval(min(a.a.x, a.b.x), min(a.a.y, a.b.y), max(a.a.x, a.b.x), max(a.a.y, a.b.y), paint)
            is Adnotacja.Pedzel -> {
                if (a.punkty.size < 2) return@forEach
                val path = android.graphics.Path()
                path.moveTo(a.punkty[0].x, a.punkty[0].y)
                for (i in 1 until a.punkty.size) path.lineTo(a.punkty[i].x, a.punkty[i].y)
                canvas.drawPath(path, paint)
            }
        }
    }
    return if (kadr != null) wytnijKadr(wyjscie, kadr) else wyjscie
}

private fun Color.toArgb(): Int =
    android.graphics.Color.argb(
        (alpha * 255).roundToInt().coerceIn(0, 255),
        (red * 255).roundToInt().coerceIn(0, 255),
        (green * 255).roundToInt().coerceIn(0, 255),
        (blue * 255).roundToInt().coerceIn(0, 255)
    )

private fun zapiszBitmapeZalacznika(context: Context, bitmap: Bitmap): Uri? {
    return try {
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        val plik = File(dir, "edit_${System.nanoTime()}.jpg")
        plik.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 94, out) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", plik)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun przeskalujPodglad(src: Bitmap, maxBok: Int = 512): Bitmap {
    val bok = max(src.width, src.height)
    if (bok <= maxBok) return src
    val skala = maxBok.toFloat() / bok
    return Bitmap.createScaledBitmap(
        src,
        (src.width * skala).toInt().coerceAtLeast(1),
        (src.height * skala).toInt().coerceAtLeast(1),
        true
    )
}
