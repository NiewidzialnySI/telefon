package dialer.dominik.pl.ui.components.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Ikony formularza kontaktu i zakładki Kontakty. */
object IkonyKontaktow {

    /** Ikona kontaktów — czysta, prosta sylwetka osoby. */
    val NowyKontakt: ImageVector by lazy {
        ImageVector.Builder(
            name = "NowyKontakt",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 12f)
                curveTo(14.21f, 12f, 16f, 10.21f, 16f, 8f)
                curveTo(16f, 5.79f, 14.21f, 4f, 12f, 4f)
                curveTo(9.79f, 4f, 8f, 5.79f, 8f, 8f)
                curveTo(8f, 10.21f, 9.79f, 12f, 12f, 12f)
                close()
                moveTo(12f, 14f)
                curveTo(9.33f, 14f, 4f, 15.34f, 4f, 18f)
                verticalLineTo(20f)
                horizontalLineTo(20f)
                verticalLineTo(18f)
                curveTo(20f, 15.34f, 14.67f, 14f, 12f, 14f)
                close()
            }
        }.build()
    }

    /** Ikona firmy / organizacji. */
    val Firma: ImageVector by lazy {
        ImageVector.Builder(
            name = "Firma",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 7f)
                verticalLineTo(3f)
                horizontalLineTo(2f)
                verticalLineTo(21f)
                horizontalLineTo(22f)
                verticalLineTo(7f)
                horizontalLineTo(12f)
                close()
                moveTo(6f, 19f)
                horizontalLineTo(4f)
                verticalLineTo(17f)
                horizontalLineTo(6f)
                verticalLineTo(19f)
                close()
                moveTo(6f, 15f)
                horizontalLineTo(4f)
                verticalLineTo(13f)
                horizontalLineTo(6f)
                verticalLineTo(15f)
                close()
                moveTo(6f, 11f)
                horizontalLineTo(4f)
                verticalLineTo(9f)
                horizontalLineTo(6f)
                verticalLineTo(11f)
                close()
                moveTo(6f, 7f)
                horizontalLineTo(4f)
                verticalLineTo(5f)
                horizontalLineTo(6f)
                verticalLineTo(7f)
                close()
                moveTo(10f, 19f)
                horizontalLineTo(8f)
                verticalLineTo(17f)
                horizontalLineTo(10f)
                verticalLineTo(19f)
                close()
                moveTo(10f, 15f)
                horizontalLineTo(8f)
                verticalLineTo(13f)
                horizontalLineTo(10f)
                verticalLineTo(15f)
                close()
                moveTo(10f, 11f)
                horizontalLineTo(8f)
                verticalLineTo(9f)
                horizontalLineTo(10f)
                verticalLineTo(11f)
                close()
                moveTo(10f, 7f)
                horizontalLineTo(8f)
                verticalLineTo(5f)
                horizontalLineTo(10f)
                verticalLineTo(7f)
                close()
                moveTo(20f, 19f)
                horizontalLineTo(12f)
                verticalLineTo(17f)
                horizontalLineTo(14f)
                verticalLineTo(15f)
                horizontalLineTo(12f)
                verticalLineTo(13f)
                horizontalLineTo(20f)
                verticalLineTo(19f)
                close()
                moveTo(20f, 11f)
                horizontalLineTo(18f)
                verticalLineTo(9f)
                horizontalLineTo(20f)
                verticalLineTo(11f)
                close()
            }
        }.build()
    }

    /** Ikona pseudonimu / etykiety. */
    val Pseudonim: ImageVector by lazy {
        ImageVector.Builder(
            name = "Pseudonim",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(21.41f, 11.58f)
                lineTo(12.41f, 2.58f)
                curveTo(12.05f, 2.22f, 11.55f, 2f, 11f, 2f)
                horizontalLineTo(4f)
                curveTo(2.9f, 2f, 2f, 2.9f, 2f, 4f)
                verticalLineTo(11f)
                curveTo(2f, 11.55f, 2.22f, 12.05f, 2.58f, 12.41f)
                lineTo(11.58f, 21.41f)
                curveTo(12.36f, 22.19f, 13.63f, 22.19f, 14.41f, 21.41f)
                lineTo(21.41f, 14.41f)
                curveTo(22.19f, 13.63f, 22.19f, 12.36f, 21.41f, 11.58f)
                close()
                moveTo(6.5f, 8f)
                curveTo(5.67f, 8f, 5f, 7.33f, 5f, 6.5f)
                curveTo(5f, 5.67f, 5.67f, 5f, 6.5f, 5f)
                curveTo(7.33f, 5f, 8f, 5.67f, 8f, 6.5f)
                curveTo(8f, 7.33f, 7.33f, 8f, 6.5f, 8f)
                close()
            }
        }.build()
    }

    /** Ikona zamknięcia / anulowania. */
    val Zamknij: ImageVector by lazy {
        ImageVector.Builder(
            name = "Zamknij",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(19f, 6.41f)
                lineTo(17.59f, 5f)
                lineTo(12f, 10.59f)
                lineTo(6.41f, 5f)
                lineTo(5f, 6.41f)
                lineTo(10.59f, 12f)
                lineTo(5f, 17.59f)
                lineTo(6.41f, 19f)
                lineTo(12f, 13.41f)
                lineTo(17.59f, 19f)
                lineTo(19f, 17.59f)
                lineTo(13.41f, 12f)
                close()
            }
        }.build()
    }

    /** Ikona dodania kontaktu — ludzik z plusem (person_add). */
    val DodajKontakt: ImageVector by lazy {
        ImageVector.Builder(
            name = "DodajKontakt",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // Sylwetka osoby — wyśrodkowana (głowa x=4..12, tułów x=0..16)
                moveTo(8f, 12f)
                curveTo(10.21f, 12f, 12f, 10.21f, 12f, 8f)
                curveTo(12f, 5.79f, 10.21f, 4f, 8f, 4f)
                curveTo(5.79f, 4f, 4f, 5.79f, 4f, 8f)
                curveTo(4f, 10.21f, 5.79f, 12f, 8f, 12f)
                close()
                moveTo(8f, 14f)
                curveTo(5.33f, 14f, 0f, 15.34f, 0f, 18f)
                verticalLineTo(20f)
                horizontalLineTo(16f)
                verticalLineTo(18f)
                curveTo(16f, 15.34f, 10.67f, 14f, 8f, 14f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                // Plus — wyśrodkowany na x=20, y=7 (prawy górny róg, w kadrze)
                moveTo(20f, 4f)
                horizontalLineTo(19f)
                verticalLineTo(8f)
                horizontalLineTo(15f)
                verticalLineTo(9f)
                horizontalLineTo(19f)
                verticalLineTo(13f)
                horizontalLineTo(20f)
                verticalLineTo(9f)
                horizontalLineTo(24f)
                verticalLineTo(8f)
                horizontalLineTo(20f)
                verticalLineTo(4f)
                close()
            }
        }.build()
    }

    /** Ikona ulubionych — pełna gwiazda. */
    val Gwiazda: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gwiazda",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 17.27f)
                lineTo(18.18f, 21f)
                lineTo(16.54f, 13.97f)
                lineTo(22f, 9.24f)
                lineTo(14.81f, 8.63f)
                lineTo(12f, 2f)
                lineTo(9.19f, 8.63f)
                lineTo(2f, 9.24f)
                lineTo(7.46f, 13.97f)
                lineTo(5.82f, 21f)
                close()
            }
        }.build()
    }

    /** Ikona ulubionych — pusta gwiazda (kontur). */
    val GwiazdaPusta: ImageVector by lazy {
        ImageVector.Builder(
            name = "GwiazdaPusta",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(22f, 9.24f)
                lineTo(14.81f, 8.62f)
                lineTo(12f, 2f)
                lineTo(9.19f, 8.63f)
                lineTo(2f, 9.24f)
                lineTo(7.45f, 13.97f)
                lineTo(5.82f, 21f)
                lineTo(12f, 17.27f)
                lineTo(18.18f, 21f)
                lineTo(16.54f, 13.97f)
                lineTo(22f, 9.24f)
                close()
                moveTo(12f, 15.4f)
                lineTo(8.24f, 17.67f)
                lineTo(9.24f, 13.39f)
                lineTo(5.92f, 10.51f)
                lineTo(10.3f, 10.13f)
                lineTo(12f, 6.1f)
                lineTo(13.7f, 10.13f)
                lineTo(18.08f, 10.51f)
                lineTo(14.76f, 13.39f)
                lineTo(15.76f, 17.67f)
                lineTo(12f, 15.4f)
                close()
            }
        }.build()
    }

    /** Ikona kosza — usunięcie kontaktu (Material delete). */
    val Kosz: ImageVector by lazy {
        ImageVector.Builder(
            name = "Kosz",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 19f)
                curveTo(6f, 20.1f, 6.9f, 21f, 8f, 21f)
                horizontalLineTo(16f)
                curveTo(17.1f, 21f, 18f, 20.1f, 18f, 19f)
                verticalLineTo(7f)
                horizontalLineTo(6f)
                verticalLineTo(19f)
                close()
                moveTo(8f, 9f)
                horizontalLineTo(16f)
                verticalLineTo(19f)
                horizontalLineTo(8f)
                verticalLineTo(9f)
                close()
                moveTo(15.5f, 4f)
                lineTo(14.5f, 3f)
                horizontalLineTo(9.5f)
                lineTo(8.5f, 4f)
                horizontalLineTo(5f)
                verticalLineTo(6f)
                horizontalLineTo(19f)
                verticalLineTo(4f)
                horizontalLineTo(15.5f)
                close()
            }
        }.build()
    }

    /** IKONA ZAŁĄCZNIKA (spinacz) — klasyczny, symetryczny, węższy (dobrze wycentrowany). */
    val Zlacznik: ImageVector by lazy {
        ImageVector.Builder(
            name = "Zlacznik",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Symetryczny spinacz: dolna pętla i dwie górne mniejsze — wycentrowany x=12.
                moveTo(6f, 14.5f)
                curveTo(6f, 18f, 9f, 20f, 12f, 20f)
                curveTo(15f, 20f, 18f, 18f, 18f, 14.5f)
                lineTo(18f, 10f)
                curveTo(18f, 8f, 16.5f, 6.5f, 14.5f, 6.5f)
                curveTo(12.5f, 6.5f, 11f, 8f, 11f, 10f)
                lineTo(11f, 13f)
                curveTo(11f, 14.1f, 11.9f, 15f, 13f, 15f)
                curveTo(14.1f, 15f, 15f, 14.1f, 15f, 13f)
                lineTo(15f, 11f)
            }
        }.build()
    }

    /** GALERIA (obrazek / zdjęcie) — używa w menu załączników. */
    val Galeria: ImageVector by lazy {
        ImageVector.Builder(
            name = "Galeria",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Ramka obrazka.
                moveTo(3f, 5f)
                lineTo(21f, 5f)
                lineTo(21f, 19f)
                lineTo(3f, 19f)
                lineTo(3f, 5f)
                close()
                // Góra/słońce (element w ramce).
                moveTo(8f, 11f)
                curveTo(8.8f, 10.2f, 10f, 10.2f, 10.8f, 11f)
                curveTo(11.6f, 10.2f, 12.8f, 10.2f, 13.6f, 11f)
                // Podstawa (góry).
                moveTo(3f, 15f)
                lineTo(9f, 9f)
                lineTo(15f, 15f)
                lineTo(18f, 12f)
                lineTo(21f, 15f)
            }
        }.build()
    }

    /** KAMERA (aparat) — używa w menu załączników. */
    val Kamera: ImageVector by lazy {
        ImageVector.Builder(
            name = "Kamera",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Korpus aparatu.
                moveTo(2f, 7f)
                lineTo(22f, 7f)
                lineTo(22f, 20f)
                lineTo(2f, 20f)
                lineTo(2f, 7f)
                close()
                // Wcięcie na obiektyw u góry.
                moveTo(5f, 7f)
                lineTo(8f, 4f)
                lineTo(16f, 4f)
                lineTo(19f, 7f)
                // Obiektyw (kółko).
                moveTo(12f, 9.5f)
                curveTo(14.5f, 9.5f, 16.5f, 11.6f, 16.4f, 14.1f)
                curveTo(16.4f, 16.6f, 14.3f, 18.6f, 11.8f, 18.6f)
                curveTo(9.3f, 18.6f, 7.3f, 16.5f, 7.4f, 14f)
                curveTo(7.4f, 11.5f, 9.5f, 9.5f, 12f, 9.5f)
                close()
            }
        }.build()
    }

    /** Ikona zegara (planowanej wysyłki) — klasyczny zegar: okrąg + wyraźne wskazówki (stroke). */
    val Zegar: ImageVector by lazy {
        ImageVector.Builder(
            name = "Zegar",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Okrągła tarcza — kontur.
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3.5f)
                curveTo(7.31f, 3.5f, 3.5f, 7.31f, 3.5f, 12f)
                curveTo(3.5f, 16.69f, 7.31f, 20.5f, 12f, 20.5f)
                curveTo(16.69f, 20.5f, 20.5f, 16.69f, 20.5f, 12f)
                curveTo(20.5f, 7.31f, 16.69f, 3.5f, 12f, 3.5f)
                close()
            }
            // Wskazówka godzinowa (krótka, na ~10).
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 12f)
                lineTo(9f, 9.2f)
            }
            // Wskazówka minutowa (dłuższa, na ~2).
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 12f)
                lineTo(15f, 7.5f)
            }
            // Środkowa oś.
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(11.3f, 11.3f)
                lineTo(12.7f, 11.3f)
                lineTo(12.7f, 12.7f)
                lineTo(11.3f, 12.7f)
                close()
            }
        }.build()
    }
}