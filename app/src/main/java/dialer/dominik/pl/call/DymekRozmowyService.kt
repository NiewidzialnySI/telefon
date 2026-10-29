package dialer.dominik.pl.call

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.os.Handler
import android.os.Looper
import android.view.ViewConfiguration

/**
 * Własny, minimalny dymek rozmowy — nakładka zawsze na wierzchu.
 *
 * Gdy użytkownik zminimalizuje pełnoekranową rozmowę (przesuwa uchwyt na ekranie
 * rozmowy), ta usługa pokazuje przezroczystą nakładkę z małym zielonym kółkiem
 * (ikona trwającego połączenia). Nakładka nie zasłania reszty ekranu — widac, co
 * jest pod spodem (SYSTEM_ALERT_WINDOW + przezroczyste tło). Kliknięcie w kółko
 * zamyka dymek i wraca do pełnoekranowej rozmowy.
 *
 * Usługa (nie tylko aktywność) — dymek musi przetrwać, gdy użytkownik przełączy się
 * do innej aplikacji w trakcie rozmowy.
 */
class DymekRozmowyService : Service() {

    private var nadkladka: View? = null
    private var przygotujWidokPoDodaniu: ((AwatarDymkaView) -> Unit)? = null
    private var animujWidokPoDodaniu: Boolean = false
    private var rozmiarPx = 0
    private var parametry: WindowManager.LayoutParams? = null
    private var menedzerOkien: WindowManager? = null
    private var szerokoscEkranu = 0
    private var wysokoscEkranu = 0
    private var lewyLimit = 0
    private var gornyLimit = 0
    private var prawyLimit = 0
    private var dolnyLimit = 0
    private var pozycjaStartowa: Pair<Int, Int>? = null
    private var animacjaStartowa = false
    private val handler = Handler(Looper.getMainLooper())
    private var generacjaStartu = 0
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var downRawX = 0f
    private var downRawY = 0f
    private var dragStartX = 0
    private var dragStartY = 0
    private var progPrzeciagniecia = 0
    private var czyPrzeciagnieto = false
    private var pierwszyTouch = false
    private var px = 0
    private var py = 0

    override fun onCreate() {
        super.onCreate()
        rozmiarPx = (64 * resources.displayMetrics.density).toInt()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flagi: Int, idPoczatkowe: Int): Int {
        if (intent?.action == AKCJA_STOP) {
            generacjaStartu++
            handler.removeCallbacksAndMessages(null)
            przygotujWidokPoDodaniu = null
            usunNadkladke()
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            AKCJA_START_POZYCJA -> {
                przygotujWidokPoDodaniu = ostatniePrzygotowanie
                pozycjaStartowa = pozycjaZadanegoStartu
                pozycjaZadanegoStartu = null
                animacjaStartowa = przygotujWidokPoDodaniu != null
                wyczyscPrzygotowanie()
            }
            AKCJA_START_ANIMOWANY -> {
                przygotujWidokPoDodaniu = ostatniePrzygotowanie
                pozycjaStartowa = null
                animacjaStartowa = true
                wyczyscPrzygotowanie()
            }
            else -> {
                przygotujWidokPoDodaniu = null
                pozycjaStartowa = null
                animacjaStartowa = false
                wyczyscPrzygotowanie()
            }
        }
        val mojaGeneracja = ++generacjaStartu
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (mojaGeneracja == generacjaStartu && StanPolaczenia.stan != StanPolaczenia.Stan.Brak &&
                StanPolaczenia.stan != StanPolaczenia.Stan.Rozlaczone
            ) {
                pokazNadkladke()
            } else if (mojaGeneracja == generacjaStartu) {
                stopSelf()
            }
        }, 200)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        usunNadkladke()
        super.onDestroy()
    }

    private fun pokazNadkladke() {
        if (nadkladka != null) return
        if (StanPolaczenia.stan == StanPolaczenia.Stan.Brak || StanPolaczenia.stan == StanPolaczenia.Stan.Rozlaczone) {
            stopSelf()
            return
        }
            val mojeLokalne = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
            menedzerOkien = mojeLokalne
            val metrics = mojeLokalne.currentWindowMetrics
        val bounds = metrics.bounds
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
            android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout()
        )
        lewyLimit = bounds.left + insets.left
        gornyLimit = bounds.top + insets.top
        prawyLimit = bounds.right - insets.right
        dolnyLimit = bounds.bottom - insets.bottom
        szerokoscEkranu = prawyLimit - lewyLimit
        wysokoscEkranu = dolnyLimit - gornyLimit
        if (szerokoscEkranu <= 0) szerokoscEkranu = bounds.width()
        if (wysokoscEkranu <= 0) wysokoscEkranu = bounds.height()

        val start = pozycjaStartowa
        pozycjaStartowa = null
        if (start != null) {
            px = start.first.coerceIn(lewyLimit, (prawyLimit - rozmiarPx).coerceAtLeast(lewyLimit))
            py = start.second.coerceIn(gornyLimit, (dolnyLimit - rozmiarPx).coerceAtLeast(gornyLimit))
        } else {
            val margines = (16 * resources.displayMetrics.density).toInt()
            px = (prawyLimit - rozmiarPx - margines).coerceAtLeast(lewyLimit)
            py = (gornyLimit + (120 * resources.displayMetrics.density).toInt())
                .coerceAtMost((dolnyLimit - rozmiarPx).coerceAtLeast(gornyLimit))
        }
        progPrzeciagniecia = ViewConfiguration.get(this).scaledTouchSlop
        val lp = WindowManager.LayoutParams(
            rozmiarPx, rozmiarPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.NO_GRAVITY   // pozycja przez jawne x/y (przeciągalny dymek)
            x = px
            y = py
            // Pod spodem widać resztę ekranu.
        }.also { parametry = it }

        val view = AwatarDymkaView(this)
        view.pivotX = rozmiarPx / 2f
        view.pivotY = rozmiarPx / 2f
        view.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    activePointerId = ev.getPointerId(0)
                    downRawX = ev.rawX
                    downRawY = ev.rawY
                    dragStartX = px
                    dragStartY = py
                    czyPrzeciagnieto = false
                    pierwszyTouch = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val index = ev.findPointerIndex(activePointerId)
                    if (index >= 0) {
                        val rawX = ev.getRawX(index)
                        val rawY = ev.getRawY(index)
                        val dx = rawX - downRawX
                        val dy = rawY - downRawY
                        if (!czyPrzeciagnieto && dx * dx + dy * dy > progPrzeciagniecia * progPrzeciagniecia) {
                            czyPrzeciagnieto = true
                        }
                        if (czyPrzeciagnieto) {
                            if (pierwszyTouch) {
                                view.animate().cancel()
                                view.scaleX = 1f
                                view.scaleY = 1f
                                view.alpha = 1f
                                pierwszyTouch = false
                            }
                            ustawPozycjeDymka(dragStartX + dx.toInt(), dragStartY + dy.toInt())
                        }
                    }
                    true
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    if (ev.getPointerId(ev.actionIndex) == activePointerId) {
                        val replacement = if (ev.actionIndex == 0) 1 else 0
                        if (replacement < ev.pointerCount) {
                            activePointerId = ev.getPointerId(replacement)
                            downRawX = ev.getRawX(replacement)
                            downRawY = ev.getRawY(replacement)
                            dragStartX = px
                            dragStartY = py
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val klik = !czyPrzeciagnieto
                    if (klik) view.performClick()
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                    czyPrzeciagnieto = false
                    if (klik) wrocDoPelnegoEkranu()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    pierwszyTouch = false
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                    czyPrzeciagnieto = false
                    true
                }
                else -> true
            }
        }

        try {
            mojeLokalne.addView(view, lp)
            nadkladka = view
            val przygotowanie = przygotujWidokPoDodaniu
            przygotujWidokPoDodaniu = null
            if (przygotowanie != null) {
                przygotowanie(view)
                animacjaStartowa = false
            } else if (animacjaStartowa) {
                animacjaStartowa = false
                view.scaleX = 0.4f
                view.scaleY = 0.4f
                view.alpha = 0f
                view.animate().scaleX(1f).scaleY(1f).alpha(1f)
                    .setDuration(380).setInterpolator(OvershootInterpolator(1.2f)).start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            nadkladka = null
            parametry = null
            menedzerOkien = null
        }
    }

    /** Przesuwa dymek o (dx, dy) w granicach dostępnego obszaru ekranu. */
    private fun ustawPozycjeDymka(x: Int, y: Int) {
        val lp = parametry ?: return
        px = x.coerceIn(lewyLimit, (prawyLimit - rozmiarPx).coerceAtLeast(lewyLimit))
        py = y.coerceIn(gornyLimit, (dolnyLimit - rozmiarPx).coerceAtLeast(gornyLimit))
        lp.x = px
        lp.y = py
        val menedzer = menedzerOkien ?: return
        val view = nadkladka ?: return
        try {
            menedzer.updateViewLayout(view, lp)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun usunNadkladke() {
        val view = nadkladka ?: return
        val menedzer = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        try {
            menedzer?.removeView(view)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        nadkladka = null
        parametry = null
        menedzerOkien = null
        activePointerId = MotionEvent.INVALID_POINTER_ID
        pierwszyTouch = false
        czyPrzeciagnieto = false
    }

    private fun wrocDoPelnegoEkranu() {
        usunNadkladke()
        // zDymka = true: EkranTrwajacejRozmowy wystartuje z awatarem w rogu
        // i przyleci nim na środek sprężyną (bez rozwijania ekranu).
        EkranRozmowy.pokaz(applicationContext, zDymka = true)
        stopSelf()
    }

    companion object {
        private const val AKCJA_STOP = "DIALER_STOP_DYMEK"

        fun start(context: Context, przygotujWidok: ((AwatarDymkaView) -> Unit)? = null) {
            if (przygotujWidok != null) {
                ostatniePrzygotowanie = przygotujWidok
                context.startService(
                    Intent(context, DymekRozmowyService::class.java).apply {
                        action = AKCJA_START_ANIMOWANY
                    }
                )
            } else {
                wyczyscPrzygotowanie()
                context.startService(Intent(context, DymekRozmowyService::class.java))
            }
        }

        private var ostatniePrzygotowanie: ((AwatarDymkaView) -> Unit)? = null
        @Volatile
        private var pozycjaZadanegoStartu: Pair<Int, Int>? = null

        private fun wyczyscPrzygotowanie() {
            ostatniePrzygotowanie = null
        }

        private const val AKCJA_START_ANIMOWANY = "DIALER_START_ANIMOWANY_DYMEK"
        private const val AKCJA_START_POZYCJA = "DIALER_START_POZYCJA_DYMEK"

        fun startPozycja(context: Context, x: Int, y: Int, przygotuj: ((AwatarDymkaView) -> Unit)? = null) {
            ostatniePrzygotowanie = przygotuj
            pozycjaZadanegoStartu = x to y
            context.startService(Intent(context, DymekRozmowyService::class.java).apply {
                action = AKCJA_START_POZYCJA
            })
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, DymekRozmowyService::class.java).apply { action = AKCJA_STOP }
            )
        }
    }
}

/**
 * Widok rysowany na Canvas pokazujący awatar kontaktu: zdjęcie (gdy jest) albo
 * inicjały na zielonym radialnym gradiencie, z cienkim zielonym obramowaniem.
 * Używa danych z [DymekDane], ustawianych przy minimalizacji rozmowy.
 */
class AwatarDymkaView(context: Context) : View(context) {
    private val tlo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val obramowanie = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
        color = Color.argb(89, 105, 240, 174)   // ZielonyNeon (0xFF69F0AE), alpha 0.35
    }
    private val tekst = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(110, 231, 168)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    var pokazRamke: Boolean = true
        set(value) {
            field = value
            invalidate()
        }
    var skalaRamki: Float = 1f
        set(value) {
            field = value
            invalidate()
        }
    private val ramkaWlocie = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(89, 105, 240, 174)
    }
    private var onPrzesun: ((Int, Int) -> Unit)? = null
    private var ostX = 0f
    private var ostY = 0f
    private var dragAktywny = false

    /**
     * Rejestruje obsługę przeciągania: callback dostaje deltę pomiędzy kolejnymi
     * pozycjami palca. Usługa przesuwa okno przez updateViewLayout.
     */
    fun setupDrag(przesun: (Int, Int) -> Unit) {
        onPrzesun = przesun
        setOnTouchListener { _, ev ->
            val akcja = ev.getAction()
            when {
                akcja == MotionEvent.ACTION_DOWN -> {
                    dragAktywny = true
                    ostX = ev.getRawX()
                    ostY = ev.getRawY()
                    true
                }
                akcja == MotionEvent.ACTION_MOVE && dragAktywny -> {
                    val rX = ev.getRawX()
                    val rY = ev.getRawY()
                    onPrzesun?.invoke((rX - ostX).toInt(), (rY - ostY).toInt())
                    ostX = rX
                    ostY = rY
                    true
                }
                (akcja == MotionEvent.ACTION_UP || akcja == MotionEvent.ACTION_CANCEL) -> {
                    dragAktywny = false
                    false
                }
                else -> false
            }
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val r = width / 2f
        val cx = r
        val cy = r

        val sciezka = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(sciezka)

        val zdjecie = DymekDane.zdjecie
        if (zdjecie != null) {
            val prostokat = RectF(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawBitmap(zdjecie, null, prostokat, null)
        } else {
            tlo.shader = RadialGradient(
                cx, cy, r,
                intArrayOf(Color.rgb(0x12, 0x36, 0x2A), Color.rgb(0x08, 0x1A, 0x14)),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, r, tlo)
            tekst.textSize = r * 0.7f
            val y = cy - (tekst.descent() + tekst.ascent()) / 2
            canvas.drawText(DymekDane.inicjaly, cx, y, tekst)
        }
        canvas.restore()
        if (pokazRamke) {
            ramkaWlocie.strokeWidth = obramowanie.strokeWidth * skalaRamki
            canvas.drawCircle(cx, cy, r - ramkaWlocie.strokeWidth / 2f, ramkaWlocie)
        }
    }
}