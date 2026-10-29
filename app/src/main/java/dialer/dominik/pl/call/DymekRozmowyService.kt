package dialer.dominik.pl.call

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Nakładka dymka — awatar z pełnym obramowaniem (padding), przeciąganie,
 * snap L/P, klik = animacja odwrotna do zwijania → pełny ekran.
 */
class DymekRozmowyService : Service() {

    private var nadkladka: AwatarDymkaView? = null
    private var przygotujWidokPoDodaniu: ((AwatarDymkaView) -> Unit)? = null
    /** Widoczna średnica kółka. */
    private var rozmiarTresciPx = 0
    /** Padding wokół kółka — stroke + skala przy drag bez obcinania. */
    private var paddingPx = 0
    /** Całe okno = treść + 2×padding. */
    private var rozmiarOknaPx = 0
    private var parametry: WindowManager.LayoutParams? = null
    private var menedzerOkien: WindowManager? = null
    private var lewyLimit = 0
    private var gornyLimit = 0
    private var prawyLimit = 0
    private var dolnyLimit = 0
    private var pozycjaStartowa: Pair<Int, Int>? = null
    private var snapPoStarcieDoPrawej = false
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
    /** Top-left okna (z paddingiem). */
    private var px = 0
    private var py = 0
    private var animatorSnap: ValueAnimator? = null
    private var animacjaKliku = false

    override fun onCreate() {
        super.onCreate()
        val d = resources.displayMetrics.density
        rozmiarTresciPx = (64 * d).toInt()
        // Zapas na stroke (~2dp) + scale 1.12 przy drag — obramowanie nie znika na skraju.
        paddingPx = (10 * d).toInt()
        rozmiarOknaPx = rozmiarTresciPx + paddingPx * 2
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flagi: Int, idPoczatkowe: Int): Int {
        if (intent?.action == AKCJA_STOP) {
            generacjaStartu++
            handler.removeCallbacksAndMessages(null)
            animatorSnap?.cancel()
            przygotujWidokPoDodaniu = null
            usunNadkladke()
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            AKCJA_START_POZYCJA -> {
                przygotujWidokPoDodaniu = ostatniePrzygotowanie
                // startPozycja podaje top-left widocznego kółka — okno jest większe o padding.
                val s = pozycjaZadanegoStartu
                pozycjaStartowa = if (s != null) {
                    (s.first - paddingPx) to (s.second - paddingPx)
                } else null
                snapPoStarcieDoPrawej = true
                pozycjaZadanegoStartu = null
                animacjaStartowa = false
                wyczyscPrzygotowanie()
            }
            AKCJA_START_ANIMOWANY -> {
                przygotujWidokPoDodaniu = ostatniePrzygotowanie
                pozycjaStartowa = null
                snapPoStarcieDoPrawej = false
                animacjaStartowa = true
                wyczyscPrzygotowanie()
            }
            else -> {
                przygotujWidokPoDodaniu = null
                pozycjaStartowa = null
                snapPoStarcieDoPrawej = false
                animacjaStartowa = false
                wyczyscPrzygotowanie()
            }
        }
        val mojaGeneracja = ++generacjaStartu
        handler.removeCallbacksAndMessages(null)
        val opoznienie = if (intent?.action == AKCJA_START_POZYCJA) 0L else 80L
        handler.postDelayed({
            if (mojaGeneracja == generacjaStartu && StanPolaczenia.stan != StanPolaczenia.Stan.Brak &&
                StanPolaczenia.stan != StanPolaczenia.Stan.Rozlaczone
            ) {
                pokazNadkladke()
            } else if (mojaGeneracja == generacjaStartu) {
                stopSelf()
            }
        }, opoznienie)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        animatorSnap?.cancel()
        usunNadkladke()
        super.onDestroy()
    }

    private fun pokazNadkladke() {
        if (nadkladka != null) {
            if (snapPoStarcieDoPrawej) {
                snapPoStarcieDoPrawej = false
                animujDoKrawedzi(wymuszPrawa = true)
            }
            return
        }
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

        val start = pozycjaStartowa
        pozycjaStartowa = null
        val margines = (12 * resources.displayMetrics.density).toInt()
        if (start != null) {
            px = start.first.coerceIn(
                lewyLimit - paddingPx,
                (prawyLimit - rozmiarOknaPx + paddingPx).coerceAtLeast(lewyLimit - paddingPx)
            )
            py = start.second.coerceIn(
                gornyLimit - paddingPx,
                (dolnyLimit - rozmiarOknaPx + paddingPx).coerceAtLeast(gornyLimit - paddingPx)
            )
        } else {
            // Kółko przy prawej krawędzi (okno = kółko − padding).
            px = (prawyLimit - rozmiarTresciPx - margines - paddingPx).coerceAtLeast(lewyLimit - paddingPx)
            py = (gornyLimit + (120 * resources.displayMetrics.density).toInt() - paddingPx)
                .coerceAtMost((dolnyLimit - rozmiarOknaPx + paddingPx).coerceAtLeast(gornyLimit - paddingPx))
        }
        progPrzeciagniecia = ViewConfiguration.get(this).scaledTouchSlop
        val lp = WindowManager.LayoutParams(
            rozmiarOknaPx, rozmiarOknaPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = px
            y = py
        }.also { parametry = it }

        val view = AwatarDymkaView(this).also {
            it.ustawPaddingRysowania(paddingPx)
            it.pivotX = rozmiarOknaPx / 2f
            it.pivotY = rozmiarOknaPx / 2f
            it.clipToOutline = false
        }
        view.setOnTouchListener { _, ev -> obsluzDotyk(view, ev) }

        try {
            mojeLokalne.addView(view, lp)
            nadkladka = view
            val przygotowanie = przygotujWidokPoDodaniu
            przygotujWidokPoDodaniu = null
            if (przygotowanie != null) {
                przygotowanie(view)
            } else if (animacjaStartowa) {
                animacjaStartowa = false
                view.scaleX = 0.45f
                view.scaleY = 0.45f
                view.alpha = 0f
                view.animate().scaleX(1f).scaleY(1f).alpha(1f)
                    .setDuration(320).setInterpolator(OvershootInterpolator(1.1f)).start()
            }
            if (snapPoStarcieDoPrawej) {
                snapPoStarcieDoPrawej = false
                handler.post { animujDoKrawedzi(wymuszPrawa = true) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            nadkladka = null
            parametry = null
            menedzerOkien = null
        }
    }

    private fun obsluzDotyk(view: View, ev: MotionEvent): Boolean {
        if (animacjaKliku) return true
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animatorSnap?.cancel()
                activePointerId = ev.getPointerId(0)
                downRawX = ev.rawX
                downRawY = ev.rawY
                dragStartX = px
                dragStartY = py
                czyPrzeciagnieto = false
            }
            MotionEvent.ACTION_MOVE -> {
                val index = ev.findPointerIndex(activePointerId)
                if (index >= 0) {
                    val rawX = ev.getRawX(index)
                    val rawY = ev.getRawY(index)
                    val dx = rawX - downRawX
                    val dy = rawY - downRawY
                    if (!czyPrzeciagnieto && hypot(dx.toDouble(), dy.toDouble()) > progPrzeciagniecia) {
                        czyPrzeciagnieto = true
                        view.animate().cancel()
                        // Lekkie powiększenie — padding chroni obramowanie przed obcięciem.
                        view.scaleX = 1.1f
                        view.scaleY = 1.1f
                    }
                    if (czyPrzeciagnieto) {
                        ustawPozycjeDymka(dragStartX + dx.toInt(), dragStartY + dy.toInt())
                    }
                }
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
            }
            MotionEvent.ACTION_UP -> {
                val klik = !czyPrzeciagnieto
                activePointerId = MotionEvent.INVALID_POINTER_ID
                czyPrzeciagnieto = false
                if (klik) {
                    animujKlikIWroc(view)
                } else {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
                    animujDoKrawedzi(wymuszPrawa = false)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                czyPrzeciagnieto = false
                view.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
                animujDoKrawedzi(wymuszPrawa = false)
            }
        }
        return true
    }

    private fun animujDoKrawedzi(wymuszPrawa: Boolean) {
        animatorSnap?.cancel()
        val margines = (12 * resources.displayMetrics.density).toInt()
        // Przyklej widoczne kółko (nie padding) do krawędzi.
        val celLewy = lewyLimit + margines - paddingPx
        val celPrawy = prawyLimit - margines - rozmiarTresciPx - paddingPx
        val srodekKolka = px + paddingPx + rozmiarTresciPx / 2
        val srodekEkranu = (lewyLimit + prawyLimit) / 2
        val celX = when {
            wymuszPrawa -> celPrawy
            srodekKolka < srodekEkranu -> celLewy
            else -> celPrawy
        }.coerceIn(lewyLimit - paddingPx, (prawyLimit - rozmiarOknaPx + paddingPx).coerceAtLeast(lewyLimit - paddingPx))
        val startX = px
        val startY = py
        if (abs(startX - celX) < 2) {
            ustawPozycjeDymka(celX, startY)
            return
        }
        animatorSnap = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 280
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                ustawPozycjeDymka((startX + (celX - startX) * t).toInt(), startY)
            }
            start()
        }
    }

    /**
     * Wzorzec floating-bubble (Messenger / overlay dialery):
     * nie animuj dymka do środka — to powoduje „skok + miganie”.
     * Zapisz pozycję → od razu pełny ekran (Compose robi jedną animację) →
     * dymek znika lekko z opóźnieniem, żeby przykryć pierwszą klatkę Activity.
     */
    private fun animujKlikIWroc(view: View) {
        if (animacjaKliku) return
        animacjaKliku = true
        animatorSnap?.cancel()
        zapiszPozycjePowrotu()
        EkranRozmowy.pokaz(applicationContext, zDymka = true)
        // Overlay zostaje chwilę nad Activity (bez lotu), potem znika.
        view.animate()
            .alpha(0f)
            .setDuration(160)
            .setStartDelay(90)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    usunNadkladke()
                    stopSelf()
                }
            })
            .start()
    }

    private fun zapiszPozycjePowrotu() {
        DymekDane.powrotCx = px + rozmiarOknaPx / 2f
        DymekDane.powrotCy = py + rozmiarOknaPx / 2f
        DymekDane.maPowrot = true
    }

    private fun ustawPozycjeDymka(x: Int, y: Int) {
        val lp = parametry ?: return
        val minX = lewyLimit - paddingPx
        val maxX = (prawyLimit - rozmiarOknaPx + paddingPx).coerceAtLeast(minX)
        val minY = gornyLimit - paddingPx
        val maxY = (dolnyLimit - rozmiarOknaPx + paddingPx).coerceAtLeast(minY)
        px = x.coerceIn(minX, maxX)
        py = y.coerceIn(minY, maxY)
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
        czyPrzeciagnieto = false
        animacjaKliku = false
    }

    companion object {
        private const val AKCJA_STOP = "DIALER_STOP_DYMEK"
        private const val AKCJA_START_ANIMOWANY = "DIALER_START_ANIMOWANY_DYMEK"
        private const val AKCJA_START_POZYCJA = "DIALER_START_POZYCJA_DYMEK"

        private var ostatniePrzygotowanie: ((AwatarDymkaView) -> Unit)? = null
        @Volatile
        private var pozycjaZadanegoStartu: Pair<Int, Int>? = null

        private fun wyczyscPrzygotowanie() {
            ostatniePrzygotowanie = null
        }

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
 * Awatar dymka — kółko z insetem, żeby stroke nigdy nie był obcinany przez krawędź okna.
 */
class AwatarDymkaView(context: Context) : View(context) {
    private var pad = 0
    private val tlo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tekst = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(110, 231, 168)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val ramka = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(160, 105, 240, 174)
    }

    fun ustawPaddingRysowania(padding: Int) {
        pad = padding
        setPadding(0, 0, 0, 0)
        invalidate()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val stroke = 2.5f * resources.displayMetrics.density
        ramka.strokeWidth = stroke
        // Środek okna; promień zostawia miejsce na cały stroke wewnątrz obszaru treści.
        val cx = width / 2f
        val cy = height / 2f
        val rTresc = (width / 2f - pad).coerceAtLeast(1f)
        val rRysunek = rTresc - stroke / 2f

        val sciezka = Path().apply { addCircle(cx, cy, rRysunek, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(sciezka)

        val zdjecie = DymekDane.zdjecie
        if (zdjecie != null) {
            val bok = rRysunek * 2f
            canvas.drawBitmap(
                zdjecie,
                null,
                RectF(cx - rRysunek, cy - rRysunek, cx + rRysunek, cy + rRysunek),
                null
            )
        } else {
            tlo.shader = RadialGradient(
                cx, cy, rRysunek,
                intArrayOf(Color.rgb(0x12, 0x36, 0x2A), Color.rgb(0x08, 0x1A, 0x14)),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, rRysunek, tlo)
            tekst.textSize = rRysunek * 0.7f
            val y = cy - (tekst.descent() + tekst.ascent()) / 2
            canvas.drawText(DymekDane.inicjaly, cx, y, tekst)
        }
        canvas.restore()
        // Ramka PO clip — pełne obramowanie, nie ucięte.
        canvas.drawCircle(cx, cy, rRysunek, ramka)
    }
}
