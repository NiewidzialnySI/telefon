package dialer.dominik.pl.ui.components.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Ikony dialera, klawiatury i ekranu rozmowy. */
object IkonyDialer {

    /** Słuchawka — zakończenie połączenia. */
    val Sluchawka: ImageVector by lazy {
        ImageVector.Builder(
            name = "Sluchawka",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 9f)
                curveTo(10.4f, 9f, 8.85f, 9.25f, 7.4f, 9.72f)
                verticalLineTo(12.81f)
                curveTo(7.4f, 13.36f, 7.1f, 13.85f, 6.62f, 14.1f)
                curveTo(5.62f, 14.61f, 4.71f, 15.26f, 3.91f, 16.01f)
                curveTo(3.65f, 16.26f, 3.33f, 16.39f, 3f, 16.39f)
                curveTo(2.67f, 16.39f, 2.35f, 16.26f, 2.1f, 16.01f)
                lineTo(0.39f, 14.3f)
                curveTo(0.14f, 14.05f, 0f, 13.73f, 0f, 13.4f)
                curveTo(0f, 13.07f, 0.14f, 12.75f, 0.39f, 12.5f)
                curveTo(3.51f, 9.51f, 7.58f, 8f, 12f, 8f)
                curveTo(16.42f, 8f, 20.49f, 9.51f, 23.61f, 12.5f)
                curveTo(23.86f, 12.75f, 24f, 13.07f, 24f, 13.4f)
                curveTo(24f, 13.73f, 23.86f, 14.05f, 23.61f, 14.3f)
                lineTo(21.9f, 16.01f)
                curveTo(21.65f, 16.26f, 21.33f, 16.39f, 21f, 16.39f)
                curveTo(20.67f, 16.39f, 20.35f, 16.26f, 20.1f, 16.01f)
                curveTo(19.29f, 15.25f, 18.38f, 14.6f, 17.38f, 14.1f)
                curveTo(16.9f, 13.85f, 16.6f, 13.36f, 16.6f, 12.81f)
                verticalLineTo(9.72f)
                curveTo(15.15f, 9.25f, 13.6f, 9f, 12f, 9f)
                close()
            }
        }.build()
    }

    /** Cofanie ostatniej cyfry — strzałka z krzyżykiem w prostokącie. */
    val Cofnij: ImageVector by lazy {
        ImageVector.Builder(
            name = "Cofnij",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(22f, 3f)
                horizontalLineTo(7f)
                curveTo(6.31f, 3f, 5.77f, 3.36f, 5.41f, 3.88f)
                lineTo(1f, 12f)
                lineTo(5.41f, 20.12f)
                curveTo(5.77f, 20.64f, 6.31f, 21f, 7f, 21f)
                horizontalLineTo(22f)
                curveTo(23.1f, 21f, 24f, 20.1f, 24f, 19f)
                verticalLineTo(5f)
                curveTo(24f, 3.9f, 23.1f, 3f, 22f, 3f)
                close()
                moveTo(19f, 15.59f)
                lineTo(17.59f, 17f)
                lineTo(14f, 13.41f)
                lineTo(10.41f, 17f)
                lineTo(9f, 15.59f)
                lineTo(12.59f, 12f)
                lineTo(9f, 8.41f)
                lineTo(10.41f, 7f)
                lineTo(14f, 10.59f)
                lineTo(17.59f, 7f)
                lineTo(19f, 8.41f)
                lineTo(15.41f, 12f)
                close()
            }
        }.build()
    }

    /** Klawiatura numeryczna. */
    val Klawiatura: ImageVector by lazy {
        ImageVector.Builder(
            name = "Klawiatura",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(4f, 6f)
                horizontalLineTo(6f)
                verticalLineTo(8f)
                horizontalLineTo(4f)
                close()
                moveTo(7f, 6f)
                horizontalLineTo(9f)
                verticalLineTo(8f)
                horizontalLineTo(7f)
                close()
                moveTo(10f, 6f)
                horizontalLineTo(12f)
                verticalLineTo(8f)
                horizontalLineTo(10f)
                close()
                moveTo(13f, 6f)
                horizontalLineTo(15f)
                verticalLineTo(8f)
                horizontalLineTo(13f)
                close()
                moveTo(16f, 6f)
                horizontalLineTo(18f)
                verticalLineTo(8f)
                horizontalLineTo(16f)
                close()
                moveTo(19f, 6f)
                horizontalLineTo(20f)
                verticalLineTo(8f)
                horizontalLineTo(19f)
                close()
                moveTo(4f, 9f)
                horizontalLineTo(5f)
                verticalLineTo(11f)
                horizontalLineTo(4f)
                close()
                moveTo(6f, 9f)
                horizontalLineTo(8f)
                verticalLineTo(11f)
                horizontalLineTo(6f)
                close()
                moveTo(9f, 9f)
                horizontalLineTo(11f)
                verticalLineTo(11f)
                horizontalLineTo(9f)
                close()
                moveTo(12f, 9f)
                horizontalLineTo(14f)
                verticalLineTo(11f)
                horizontalLineTo(12f)
                close()
                moveTo(15f, 9f)
                horizontalLineTo(17f)
                verticalLineTo(11f)
                horizontalLineTo(15f)
                close()
                moveTo(18f, 9f)
                horizontalLineTo(20f)
                verticalLineTo(11f)
                horizontalLineTo(18f)
                close()
                moveTo(4f, 12f)
                horizontalLineTo(7f)
                verticalLineTo(14f)
                horizontalLineTo(4f)
                close()
                moveTo(8f, 12f)
                horizontalLineTo(11f)
                verticalLineTo(14f)
                horizontalLineTo(8f)
                close()
                moveTo(12f, 12f)
                horizontalLineTo(15f)
                verticalLineTo(14f)
                horizontalLineTo(12f)
                close()
                moveTo(16f, 12f)
                horizontalLineTo(20f)
                verticalLineTo(14f)
                horizontalLineTo(16f)
                close()
                moveTo(7f, 15f)
                horizontalLineTo(17f)
                verticalLineTo(17f)
                horizontalLineTo(7f)
                close()
            }
        }.build()
    }

    /** Ikona wyciszenia (przekreślony mikrofon) — poprawna Material mic_off. */
    val MikrofonWylaczony: ImageVector by lazy {
        ImageVector.Builder(
            name = "MikrofonWylaczony",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(15f, 10.6f)
                verticalLineTo(5f)
                curveTo(15f, 3.34f, 13.66f, 2f, 12f, 2f)
                curveTo(10.46f, 2f, 9.21f, 3.16f, 9.04f, 4.65f)
                lineTo(15f, 10.6f)
                close()
                moveTo(19f, 11f)
                horizontalLineToRelative(-1.7f)
                curveToRelative(0f, 0.58f, -0.1f, 1.13f, -0.27f, 1.64f)
                lineToRelative(1.27f, 1.27f)
                curveToRelative(0.44f, -0.88f, 0.7f, -1.87f, 0.7f, -2.91f)
                close()
                moveTo(4.41f, 2.86f)
                lineTo(3f, 4.27f)
                lineTo(9f, 10.27f)
                verticalLineTo(11f)
                curveTo(9f, 12.66f, 10.34f, 14f, 12f, 14f)
                curveToRelative(0.23f, 0f, 0.44f, -0.03f, 0.65f, -0.08f)
                lineToRelative(1.66f, 1.66f)
                curveToRelative(-0.71f, 0.33f, -1.5f, 0.52f, -2.31f, 0.52f)
                curveToRelative(-2.76f, 0f, -5.3f, -2.1f, -5.3f, -5.1f)
                horizontalLineTo(5f)
                curveTo(5f, 14.41f, 7.72f, 17.23f, 11f, 17.72f)
                verticalLineTo(21f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-3.28f)
                curveToRelative(0.91f, -0.13f, 1.77f, -0.45f, 2.55f, -0.9f)
                lineToRelative(4.2f, 4.2f)
                lineToRelative(1.41f, -1.41f)
                lineTo(4.41f, 2.86f)
                close()
            }
        }.build()
    }

    /** Głośnik. */
    val Glosnik: ImageVector by lazy {
        ImageVector.Builder(
            name = "Glosnik",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 9f)
                verticalLineTo(15f)
                horizontalLineTo(7f)
                lineTo(12f, 20f)
                verticalLineTo(4f)
                lineTo(7f, 9f)
                horizontalLineTo(3f)
                close()
                moveTo(16.5f, 12f)
                curveTo(16.5f, 10.23f, 15.48f, 8.71f, 14f, 7.97f)
                verticalLineTo(16.02f)
                curveTo(15.48f, 15.29f, 16.5f, 13.77f, 16.5f, 12f)
                close()
                moveTo(14f, 3.23f)
                verticalLineTo(5.29f)
                curveTo(16.89f, 6.15f, 19f, 8.83f, 19f, 12f)
                curveTo(19f, 15.17f, 16.89f, 17.85f, 14f, 18.71f)
                verticalLineTo(20.77f)
                curveTo(18.01f, 19.86f, 21f, 16.28f, 21f, 12f)
                curveTo(21f, 7.72f, 18.01f, 4.14f, 14f, 3.23f)
                close()
            }
        }.build()
    }

    /**
     * Ikona zakładki „Telefon" — odwzorowanie logo aplikacji:
     * koło (rdzeń), kropka w środku i trzy fale wychodzące w prawo.
     */
    val TelefonLogo: ImageVector by lazy {
        ImageVector.Builder(
            name = "TelefonLogo",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Rdzeń: pierścień (okrąg z otworem) — czytelny nawet w 22 dp
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 3.5f)
                curveTo(7.3f, 3.5f, 3.5f, 7.3f, 3.5f, 12f)
                curveTo(3.5f, 16.7f, 7.3f, 20.5f, 12f, 20.5f)
                curveTo(16.7f, 20.5f, 20.5f, 16.7f, 20.5f, 12f)
                curveTo(20.5f, 7.3f, 16.7f, 3.5f, 12f, 3.5f)
                close()
                moveTo(12f, 5.9f)
                curveTo(15.4f, 5.9f, 18.1f, 8.6f, 18.1f, 12f)
                curveTo(18.1f, 15.4f, 15.4f, 18.1f, 12f, 18.1f)
                curveTo(8.6f, 18.1f, 5.9f, 15.4f, 5.9f, 12f)
                curveTo(5.9f, 8.6f, 8.6f, 5.9f, 12f, 5.9f)
                close()
            }
            // Kropka źródłowa w środku
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 12f)
                moveToRelative(-1.9f, 0f)
                curveToRelative(0f, -1.05f, 0.85f, -1.9f, 1.9f, -1.9f)
                curveToRelative(1.05f, 0f, 1.9f, 0.85f, 1.9f, 1.9f)
                curveToRelative(0f, 1.05f, -0.85f, 1.9f, -1.9f, 1.9f)
                curveToRelative(-1.05f, 0f, -1.9f, -0.85f, -1.9f, -1.9f)
                close()
            }
            // Trzy fale — grube łuki po prawej
            path(fill = SolidColor(Color.Black)) {
                moveTo(14.9f, 9.0f)
                curveTo(16.1f, 9.8f, 16.7f, 10.8f, 16.7f, 12f)
                curveTo(16.7f, 13.2f, 16.1f, 14.2f, 14.9f, 15.0f)
                curveTo(14.7f, 14.6f, 14.5f, 14.2f, 14.4f, 13.8f)
                curveTo(15.2f, 13.3f, 15.6f, 12.7f, 15.6f, 12f)
                curveTo(15.6f, 11.3f, 15.2f, 10.7f, 14.4f, 10.2f)
                curveTo(14.5f, 9.8f, 14.7f, 9.4f, 14.9f, 9.0f)
                close()
                moveTo(17.4f, 6.6f)
                curveTo(19.4f, 7.9f, 20.4f, 9.9f, 20.4f, 12f)
                curveTo(20.4f, 14.1f, 19.4f, 16.1f, 17.4f, 17.4f)
                curveTo(17.2f, 17.0f, 17.0f, 16.6f, 16.9f, 16.2f)
                curveTo(18.5f, 15.1f, 19.3f, 13.6f, 19.3f, 12f)
                curveTo(19.3f, 10.4f, 18.5f, 8.9f, 16.9f, 7.8f)
                curveTo(17.0f, 7.4f, 17.2f, 7.0f, 17.4f, 6.6f)
                close()
            }
        }.build()
    }

    /**
     * Ikona „Ścisz" — klasyczny Material volume_off:
     * korpus głośnika + dwie fale (częściowo przecięte) + ukośna kreska.
     * Czytelna w ~24–28 dp (i w chipach ~20–22 dp).
     */
    val Wciszenie: ImageVector by lazy {
        ImageVector.Builder(
            name = "Wciszenie",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // Bliska fala (ucięta ukośną)
                moveTo(16.5f, 12f)
                curveTo(16.5f, 10.23f, 15.48f, 8.71f, 14f, 7.97f)
                verticalLineToRelative(2.21f)
                lineToRelative(2.45f, 2.45f)
                curveToRelative(0.03f, -0.2f, 0.05f, -0.41f, 0.05f, -0.63f)
                close()
                // Daleka fala (ucięta ukośną)
                moveTo(19f, 12f)
                curveToRelative(0f, 0.94f, -0.2f, 1.82f, -0.54f, 2.64f)
                lineToRelative(1.51f, 1.51f)
                curveTo(20.63f, 14.91f, 21f, 13.5f, 21f, 12f)
                curveToRelative(0f, -4.28f, -2.99f, -7.86f, -7f, -8.77f)
                verticalLineToRelative(2.06f)
                curveToRelative(2.89f, 0.86f, 5f, 3.54f, 5f, 6.71f)
                close()
                // Korpus + ukośna kreska + reszta głośnika pod kreską
                moveTo(4.27f, 3f)
                lineTo(3f, 4.27f)
                lineTo(7.73f, 9f)
                horizontalLineTo(3f)
                verticalLineToRelative(6f)
                horizontalLineToRelative(4f)
                lineToRelative(5f, 5f)
                verticalLineToRelative(-6.73f)
                lineToRelative(4.25f, 4.25f)
                curveToRelative(-0.67f, 0.52f, -1.42f, 0.93f, -2.25f, 1.18f)
                verticalLineToRelative(2.06f)
                curveToRelative(1.38f, -0.31f, 2.63f, -0.95f, 3.69f, -1.81f)
                lineTo(19.73f, 21f)
                lineTo(21f, 19.73f)
                lineToRelative(-9f, -9f)
                lineTo(4.27f, 3f)
                close()
                // Górny fragment membrany (nad kreską)
                moveTo(12f, 4f)
                lineTo(9.91f, 6.09f)
                lineTo(12f, 8.18f)
                verticalLineTo(4f)
                close()
            }
        }.build()
    }

    /**
     * Ikona „Dymek" (bubble) — okrąg z kropką w środku, jak chat-head Messengera.
     * Używana na ekranie trwającej rozmowy do zminimalizowania jej do dymka.
     */
    val Dymek: ImageVector by lazy {
        ImageVector.Builder(
            name = "Dymek",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Pierścień (okrąg z otworem)
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 4f)
                curveTo(7.58f, 4f, 4f, 7.58f, 4f, 12f)
                curveTo(4f, 16.42f, 7.58f, 20f, 12f, 20f)
                curveTo(16.42f, 20f, 20f, 16.42f, 20f, 12f)
                curveTo(20f, 7.58f, 16.42f, 4f, 12f, 4f)
                close()
                moveTo(12f, 5.5f)
                curveTo(15.59f, 5.5f, 18.5f, 8.41f, 18.5f, 12f)
                curveTo(18.5f, 15.59f, 15.59f, 18.5f, 12f, 18.5f)
                curveTo(8.41f, 18.5f, 5.5f, 15.59f, 5.5f, 12f)
                curveTo(5.5f, 8.41f, 8.41f, 5.5f, 12f, 5.5f)
                close()
            }
            // Kropka w środku
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 9.5f)
                moveToRelative(-1.25f, 0f)
                curveToRelative(0f, -0.69f, 0.56f, -1.25f, 1.25f, -1.25f)
                curveToRelative(0.69f, 0f, 1.25f, 0.56f, 1.25f, 1.25f)
                curveToRelative(0f, 0.69f, -0.56f, 1.25f, -1.25f, 1.25f)
                curveToRelative(-0.69f, 0f, -1.25f, -0.56f, -1.25f, -1.25f)
                close()
            }
        }.build()
    }

    /**
     * Ikona „Nagrywanie" — wypełnione kółko (jak dioda nagrywania).
     * Używana na ekranie trwającej rozmowy do włączania/wyłączania nagrywania.
     */
    val Nagrywanie: ImageVector by lazy {
        ImageVector.Builder(
            name = "Nagrywanie",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 3f)
                curveTo(7.03f, 3f, 3f, 7.03f, 3f, 12f)
                curveTo(3f, 16.97f, 7.03f, 21f, 12f, 21f)
                curveTo(16.97f, 21f, 21f, 16.97f, 21f, 12f)
                curveTo(21f, 7.03f, 16.97f, 3f, 12f, 3f)
                close()
            }
        }.build()
    }

    /**
     * Ikona „StrzalkaWGore" — strzałka w górę (do przewijania listy).
     */
    val StrzalkaWGore: ImageVector by lazy {
        ImageVector.Builder(
            name = "StrzalkaWGore",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(4f, 12f)
                lineTo(12f, 4f)
                lineTo(20f, 12f)
                horizontalLineToRelative(-4f)
                verticalLineToRelative(8f)
                horizontalLineToRelative(-8f)
                verticalLineToRelative(-8f)
                horizontalLineTo(4f)
                close()
            }
        }.build()
    }

    /**
     * Połączenie przychodzące — Material Icons `call_received` (Apache 2.0).
     * Czytelna strzałka ↘ w 16–20 dp.
     */
    val PolaczeniePrzychodzace: ImageVector by lazy {
        ImageVector.Builder(
            name = "PolaczeniePrzychodzace",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(20f, 5.41f)
                lineTo(18.59f, 4f)
                lineTo(7f, 15.59f)
                verticalLineTo(9f)
                horizontalLineTo(5f)
                verticalLineToRelative(10f)
                horizontalLineToRelative(10f)
                verticalLineToRelative(-2f)
                horizontalLineTo(8.41f)
                close()
            }
        }.build()
    }

    /**
     * Połączenie wychodzące — Material Icons `call_made` (Apache 2.0).
     */
    val PolaczenieWychodzace: ImageVector by lazy {
        ImageVector.Builder(
            name = "PolaczenieWychodzace",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(9f, 5f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(6.59f)
                lineTo(4f, 18.59f)
                lineTo(5.41f, 20f)
                lineTo(17f, 8.41f)
                verticalLineTo(15f)
                horizontalLineToRelative(2f)
                verticalLineTo(5f)
                close()
            }
        }.build()
    }

    /**
     * Połączenie nieodebrane — Material Icons `call_missed` (Apache 2.0).
     */
    val PolaczenieNieodebrane: ImageVector by lazy {
        ImageVector.Builder(
            name = "PolaczenieNieodebrane",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(19.59f, 7f)
                lineTo(12f, 14.59f)
                lineTo(6.41f, 9f)
                horizontalLineTo(11f)
                verticalLineTo(7f)
                horizontalLineTo(3f)
                verticalLineToRelative(8f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-4.59f)
                lineToRelative(7f, 7f)
                lineToRelative(9f, -9f)
                close()
            }
        }.build()
    }

    /**
     * Połączenie odrzucone — Material Icons `phone_disabled` (Apache 2.0).
     * Słuchawka z przekreśleniem — odróżnialna od nieodebranego.
     */
    val PolaczenieOdrzucone: ImageVector by lazy {
        ImageVector.Builder(
            name = "PolaczenieOdrzucone",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(17.34f, 14.54f)
                lineToRelative(-1.43f, -1.43f)
                curveToRelative(0.56f, -0.73f, 1.05f, -1.5f, 1.47f, -2.32f)
                lineToRelative(-2.2f, -2.2f)
                curveToRelative(-0.28f, -0.28f, -0.36f, -0.67f, -0.25f, -1.02f)
                curveToRelative(0.37f, -1.12f, 0.57f, -2.32f, 0.57f, -3.57f)
                curveToRelative(0f, -0.55f, 0.45f, -1f, 1f, -1f)
                horizontalLineTo(20f)
                curveToRelative(0.55f, 0f, 1f, 0.45f, 1f, 1f)
                curveToRelative(0f, 3.98f, -1.37f, 7.64f, -3.66f, 10.54f)
                close()
                moveTo(14.52f, 17.35f)
                curveTo(11.63f, 19.64f, 7.97f, 21f, 4f, 21f)
                curveToRelative(-0.55f, 0f, -1f, -0.45f, -1f, -1f)
                verticalLineToRelative(-3.49f)
                curveToRelative(0f, -0.55f, 0.45f, -1f, 1f, -1f)
                curveToRelative(1.24f, 0f, 2.45f, -0.2f, 3.57f, -0.57f)
                curveToRelative(0.35f, -0.12f, 0.75f, -0.03f, 1.02f, 0.24f)
                lineToRelative(2.2f, 2.2f)
                curveToRelative(0.81f, -0.42f, 1.58f, -0.9f, 2.3f, -1.46f)
                lineTo(1.39f, 4.22f)
                lineToRelative(1.42f, -1.41f)
                lineTo(21.19f, 21.2f)
                lineToRelative(-1.41f, 1.41f)
                lineToRelative(-5.26f, -5.26f)
                close()
            }
        }.build()
    }
}