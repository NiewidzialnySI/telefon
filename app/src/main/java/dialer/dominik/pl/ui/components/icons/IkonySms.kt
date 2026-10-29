package dialer.dominik.pl.ui.components.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Ikony wiadomości i nagrań (zakładki boczne). */
object IkonySms {

    /** Dymek wiadomości — czysta, prosta ikona. */
    val Wiadomosc: ImageVector by lazy {
        ImageVector.Builder(
            name = "Wiadomosc",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(20f, 2f)
                horizontalLineTo(4f)
                curveTo(2.9f, 2f, 2f, 2.9f, 2f, 4f)
                verticalLineTo(22f)
                lineTo(6f, 18f)
                horizontalLineTo(20f)
                curveTo(21.1f, 18f, 22f, 17.1f, 22f, 16f)
                verticalLineTo(4f)
                curveTo(22f, 2.9f, 21.1f, 2f, 20f, 2f)
                close()
            }
        }.build()
    }

    /** Ikona nagrań rozmów. */
    val Nagrania: ImageVector by lazy {
        ImageVector.Builder(
            name = "Nagrania",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 3f)
                curveTo(10.34f, 3f, 9f, 4.34f, 9f, 6f)
                verticalLineTo(12f)
                curveTo(9f, 13.66f, 10.34f, 15f, 12f, 15f)
                curveTo(13.66f, 15f, 15f, 13.66f, 15f, 12f)
                verticalLineTo(6f)
                curveTo(15f, 4.34f, 13.66f, 3f, 12f, 3f)
                close()
                moveTo(19f, 11f)
                verticalLineTo(12f)
                curveTo(19f, 15.87f, 15.87f, 19f, 12f, 19f)
                curveTo(8.13f, 19f, 5f, 15.87f, 5f, 12f)
                verticalLineTo(11f)
                horizontalLineTo(3f)
                verticalLineTo(12f)
                curveTo(3f, 16.53f, 6.33f, 20.25f, 10.75f, 20.91f)
                verticalLineTo(23f)
                horizontalLineTo(13.25f)
                verticalLineTo(20.91f)
                curveTo(17.67f, 20.25f, 21f, 16.53f, 21f, 12f)
                verticalLineTo(11f)
                horizontalLineTo(19f)
                close()
            }
        }.build()
    }

    /** Archiwum (ukryte wiadomości) — delikatny kontur (stroke), mniej wypełniony. */
    val Archiwum: ImageVector by lazy {
        ImageVector.Builder(
            name = "Archiwum",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Pokrywka otwarta (linia) — lekki kontur.
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3f, 3f)
                lineTo(21f, 3f)
                lineTo(20f, 8f)
                lineTo(4f, 8f)
                lineTo(3f, 3f)
            }
            // Korpus pudełka — kontur.
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 8f)
                lineTo(20f, 8f)
                lineTo(20f, 21f)
                lineTo(4f, 21f)
                lineTo(4f, 8f)
            }
            // Delikatne "żąberka" (listwy) wewnątrz — subtelne.
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(7f, 13f)
                lineTo(17f, 13f)
            }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(7f, 17f)
                lineTo(17f, 17f)
            }
        }.build()
    }

    /** WRÓĆ Z ARCHIWUM — strzałka powrotu w lewo (wyjście z ukrytych do skrzynki), styl stroke. */
    val WroćZArchiwum: ImageVector by lazy {
        ImageVector.Builder(
            name = "WrocZArchiwum",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Główna strzałka powrotu: z prawej-góry do lewej, z łukiem (jak "cofnij").
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(19f, 6f)
                // Skok w lewo po linii prostej, grot w lewo u góry.
                lineTo(7f, 6f)
                lineTo(7f, 6f)
            }
            // Grot strzałki (w lewo).
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(7f, 2.5f)
                lineTo(3f, 6f)
                lineTo(7f, 9.5f)
            }
            // Ogon strzałki: okrągła podstawa pod spodem -> element archiwum (pudełko poniżej).
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(4f, 13f)
                lineTo(20f, 13f)
                lineTo(20f, 21f)
                lineTo(4f, 21f)
                lineTo(4f, 13f)
            }
        }.build()
    }
}