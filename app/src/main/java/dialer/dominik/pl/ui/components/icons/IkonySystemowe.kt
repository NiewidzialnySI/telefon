package dialer.dominik.pl.ui.components.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Ikony systemowe — uprawnienia, statusy, nawigacja. */
object IkonySystemowe {

    /** Sprawdzenie w kółku — używane przy uprawnieniu udzielonym. */
    val Sprawdzenie: ImageVector by lazy {
        ImageVector.Builder(
            name = "Sprawdzenie",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
                curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
                curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
                close()
                moveTo(10f, 17f)
                lineTo(5f, 12f)
                lineTo(6.41f, 10.59f)
                lineTo(10f, 14.17f)
                lineTo(17.59f, 6.58f)
                lineTo(19f, 8f)
                close()
            }
        }.build()
    }

    /** Ustawienia — koło zębate. */
    val Ustawienia: ImageVector by lazy {
        ImageVector.Builder(
            name = "Ustawienia",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(19.14f, 12.94f)
                curveTo(19.18f, 12.64f, 19.2f, 12.33f, 19.2f, 12f)
                curveTo(19.2f, 11.68f, 19.18f, 11.36f, 19.14f, 11.06f)
                lineTo(21.1f, 9.55f)
                curveTo(21.3f, 9.4f, 21.35f, 9.11f, 21.24f, 8.9f)
                lineTo(19.34f, 5.6f)
                curveTo(19.24f, 5.39f, 18.95f, 5.3f, 18.73f, 5.36f)
                lineTo(16.46f, 6.13f)
                curveTo(15.86f, 5.67f, 15.19f, 5.3f, 14.47f, 5.06f)
                lineTo(14.18f, 2.66f)
                curveTo(14.16f, 2.5f, 14.02f, 2.38f, 13.85f, 2.38f)
                lineTo(10.15f, 2.38f)
                curveTo(9.98f, 2.38f, 9.84f, 2.5f, 9.82f, 2.66f)
                lineTo(9.53f, 5.06f)
                curveTo(8.81f, 5.3f, 8.14f, 5.67f, 7.54f, 6.13f)
                lineTo(5.27f, 5.36f)
                curveTo(5.05f, 5.3f, 4.76f, 5.39f, 4.66f, 5.6f)
                lineTo(2.76f, 8.9f)
                curveTo(2.65f, 9.11f, 2.7f, 9.4f, 2.9f, 9.55f)
                lineTo(4.86f, 11.06f)
                curveTo(4.82f, 11.36f, 4.8f, 11.68f, 4.8f, 12f)
                curveTo(4.8f, 12.33f, 4.82f, 12.64f, 4.86f, 12.94f)
                lineTo(2.9f, 14.45f)
                curveTo(2.7f, 14.6f, 2.65f, 14.89f, 2.76f, 15.1f)
                lineTo(4.66f, 18.4f)
                curveTo(4.76f, 18.61f, 5.05f, 18.7f, 5.27f, 18.64f)
                lineTo(7.54f, 17.87f)
                curveTo(8.14f, 18.33f, 8.81f, 18.7f, 9.53f, 18.94f)
                lineTo(9.82f, 21.34f)
                curveTo(9.84f, 21.5f, 9.98f, 21.62f, 10.15f, 21.62f)
                lineTo(13.85f, 21.62f)
                curveTo(14.02f, 21.62f, 14.16f, 21.5f, 14.18f, 21.34f)
                lineTo(14.47f, 18.94f)
                curveTo(15.19f, 18.7f, 15.86f, 18.33f, 16.46f, 17.87f)
                lineTo(18.73f, 18.64f)
                curveTo(18.95f, 18.7f, 19.24f, 18.61f, 19.34f, 18.4f)
                lineTo(21.24f, 15.1f)
                curveTo(21.35f, 14.89f, 21.3f, 14.6f, 21.1f, 14.45f)
                lineTo(19.14f, 12.94f)
                close()
                moveTo(12f, 15.6f)
                curveTo(10.02f, 15.6f, 8.4f, 13.98f, 8.4f, 12f)
                curveTo(8.4f, 10.02f, 10.02f, 8.4f, 12f, 8.4f)
                curveTo(13.98f, 8.4f, 15.6f, 10.02f, 15.6f, 12f)
                curveTo(15.6f, 13.98f, 13.98f, 15.6f, 12f, 15.6f)
                close()
            }
        }.build()
    }

    /** Tarcza — symbol ochrony/uprawnień. */
    val Tarcza: ImageVector by lazy {
        ImageVector.Builder(
            name = "Tarcza",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 1f)
                lineTo(3f, 5f)
                lineTo(3f, 11f)
                curveTo(3f, 16.55f, 6.84f, 21.74f, 12f, 23f)
                curveTo(17.16f, 21.74f, 21f, 16.55f, 21f, 11f)
                lineTo(21f, 5f)
                close()
                moveTo(12f, 11.99f)
                close()
            }
        }.build()
    }

    /** Strzałka w prawo — CTA. */
    val Strzalka: ImageVector by lazy {
        ImageVector.Builder(
            name = "Strzalka",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 4f)
                lineTo(10.59f, 5.41f)
                lineTo(16.17f, 11f)
                horizontalLineTo(4f)
                verticalLineTo(13f)
                horizontalLineTo(16.17f)
                lineTo(10.59f, 18.59f)
                lineTo(12f, 20f)
                lineTo(20f, 12f)
                close()
            }
        }.build()
    }

    /** Ikona Bluetooth. */
    val Bluetooth: ImageVector by lazy {
        ImageVector.Builder(
            name = "Bluetooth",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(17.71f, 7.71f)
                lineTo(12f, 2f)
                horizontalLineTo(11f)
                verticalLineTo(9.59f)
                lineTo(6.41f, 5f)
                lineTo(5f, 6.41f)
                lineTo(10.59f, 12f)
                lineTo(5f, 17.59f)
                lineTo(6.41f, 19.01f)
                lineTo(11f, 14.41f)
                verticalLineTo(22f)
                horizontalLineTo(12f)
                lineTo(17.71f, 16.29f)
                lineTo(13.41f, 12f)
                moveTo(13f, 5.83f)
                lineTo(15.17f, 8f)
                lineTo(13f, 10.17f)
                moveTo(13f, 18.17f)
                lineTo(15.17f, 16f)
                lineTo(13f, 13.83f)
                close()
            }
        }.build()
    }

    /** Kłódka — symbol dostępu. */
    val Klodka: ImageVector by lazy {
        ImageVector.Builder(
            name = "Klodka",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(18f, 8f)
                horizontalLineTo(17f)
                verticalLineTo(6f)
                curveTo(17f, 3.24f, 14.76f, 1f, 12f, 1f)
                curveTo(9.24f, 1f, 7f, 3.24f, 7f, 6f)
                verticalLineTo(8f)
                horizontalLineTo(6f)
                curveTo(4.9f, 8f, 4f, 8.9f, 4f, 10f)
                verticalLineTo(20f)
                curveTo(4f, 21.1f, 4.9f, 22f, 6f, 22f)
                horizontalLineTo(18f)
                curveTo(19.1f, 22f, 20f, 21.1f, 20f, 20f)
                verticalLineTo(10f)
                curveTo(20f, 8.9f, 19.1f, 8f, 18f, 8f)
                close()
                moveTo(12f, 17f)
                curveTo(10.9f, 17f, 10f, 16.1f, 10f, 15f)
                curveTo(10f, 13.9f, 10.9f, 13f, 12f, 13f)
                curveTo(13.1f, 13f, 14f, 13.9f, 14f, 15f)
                curveTo(14f, 16.1f, 13.1f, 17f, 12f, 17f)
                close()
                moveTo(8.9f, 8f)
                horizontalLineTo(15.1f)
                verticalLineTo(6f)
                curveTo(15.1f, 4.29f, 13.71f, 2.9f, 12f, 2.9f)
                curveTo(10.29f, 2.9f, 8.9f, 4.29f, 8.9f, 6f)
                close()
            }
        }.build()
    }

    /** Lupa (wyszukiwanie) — Material search icon. */
    val Lupa: ImageVector by lazy {
        ImageVector.Builder(
            name = "Lupa",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(15.5f, 14f)
                horizontalLineTo(14.71f)
                lineTo(14.43f, 13.73f)
                curveTo(15.41f, 12.59f, 16f, 11.11f, 16f, 9.5f)
                curveTo(16f, 5.91f, 13.09f, 3f, 9.5f, 3f)
                curveTo(5.91f, 3f, 3f, 5.91f, 3f, 9.5f)
                curveTo(3f, 13.09f, 5.91f, 16f, 9.5f, 16f)
                curveTo(11.11f, 16f, 12.59f, 15.41f, 13.73f, 14.43f)
                lineTo(14f, 14.71f)
                verticalLineTo(15.5f)
                lineTo(19f, 20.49f)
                lineTo(20.49f, 19f)
                lineTo(15.5f, 14f)
                close()
                moveTo(9.5f, 14f)
                curveTo(7.01f, 14f, 5f, 11.99f, 5f, 9.5f)
                curveTo(5f, 7.01f, 7.01f, 5f, 9.5f, 5f)
                curveTo(11.99f, 5f, 14f, 7.01f, 14f, 9.5f)
                curveTo(14f, 11.99f, 11.99f, 14f, 9.5f, 14f)
                close()
            }
        }.build()
    }

    /** Ołówek / edycja — Material "edit" (ścieżka ołówka). */
    val Edytuj: ImageVector by lazy {
        ImageVector.Builder(
            name = "Edytuj",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 17.25f)
                verticalLineTo(21f)
                horizontalLineTo(6.75f)
                lineTo(17.81f, 9.94f)
                lineTo(14.06f, 6.19f)
                lineTo(3f, 17.25f)
                close()
                moveTo(20.71f, 7.04f)
                curveTo(21.1f, 6.65f, 21.1f, 6.02f, 20.71f, 5.63f)
                lineTo(18.37f, 3.29f)
                curveTo(17.98f, 2.9f, 17.35f, 2.9f, 16.96f, 3.29f)
                lineTo(15.13f, 5.12f)
                lineTo(18.88f, 8.87f)
                lineTo(20.71f, 7.04f)
                close()
            }
        }.build()
    }

    /** Wi‑Fi — status sieci dla Wireless debugging. */
    val Wifi: ImageVector by lazy {
        ImageVector.Builder(
            name = "Wifi",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(1f, 9f)
                lineTo(3f, 11f)
                curveTo(7.97f, 6.03f, 16.03f, 6.03f, 21f, 11f)
                lineTo(23f, 9f)
                curveTo(16.93f, 2.93f, 7.08f, 2.93f, 1f, 9f)
                close()
                moveTo(5f, 13f)
                lineTo(7f, 15f)
                curveTo(9.76f, 12.24f, 14.24f, 12.24f, 17f, 15f)
                lineTo(19f, 13f)
                curveTo(15.14f, 9.14f, 8.87f, 9.14f, 5f, 13f)
                close()
                moveTo(9f, 17f)
                lineTo(12f, 20f)
                lineTo(15f, 17f)
                curveTo(13.35f, 15.34f, 10.66f, 15.34f, 9f, 17f)
                close()
            }
        }.build()
    }

    /** Wireless ADB — „bug” stylu debug (status adb_wifi). */
    val AdbWifi: ImageVector by lazy {
        ImageVector.Builder(
            name = "AdbWifi",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(20f, 8f)
                horizontalLineTo(17.19f)
                curveTo(16.74f, 7.22f, 16.12f, 6.55f, 15.37f, 6.04f)
                lineTo(17f, 4.41f)
                lineTo(15.59f, 3f)
                lineTo(13.42f, 5.17f)
                curveTo(12.96f, 5.06f, 12.49f, 5f, 12f, 5f)
                curveTo(11.51f, 5f, 11.04f, 5.06f, 10.59f, 5.17f)
                lineTo(8.41f, 3f)
                lineTo(7f, 4.41f)
                lineTo(8.62f, 6.04f)
                curveTo(7.88f, 6.55f, 7.26f, 7.22f, 6.81f, 8f)
                horizontalLineTo(4f)
                verticalLineTo(10f)
                horizontalLineTo(6.09f)
                curveTo(6.04f, 10.33f, 6f, 10.66f, 6f, 11f)
                verticalLineTo(12f)
                horizontalLineTo(4f)
                verticalLineTo(14f)
                horizontalLineTo(6f)
                verticalLineTo(15f)
                curveTo(6f, 15.34f, 6.04f, 15.67f, 6.09f, 16f)
                horizontalLineTo(4f)
                verticalLineTo(18f)
                horizontalLineTo(6.81f)
                curveTo(7.85f, 19.79f, 9.78f, 21f, 12f, 21f)
                curveTo(14.22f, 21f, 16.15f, 19.79f, 17.19f, 18f)
                horizontalLineTo(20f)
                verticalLineTo(16f)
                horizontalLineTo(17.91f)
                curveTo(17.96f, 15.67f, 18f, 15.34f, 18f, 15f)
                verticalLineTo(14f)
                horizontalLineTo(20f)
                verticalLineTo(12f)
                horizontalLineTo(18f)
                verticalLineTo(11f)
                curveTo(18f, 10.66f, 17.96f, 10.33f, 17.91f, 10f)
                horizontalLineTo(20f)
                verticalLineTo(8f)
                close()
                moveTo(14f, 14f)
                horizontalLineTo(10f)
                verticalLineTo(10f)
                horizontalLineTo(14f)
                verticalLineTo(14f)
                close()
            }
        }.build()
    }

    /** Notatka / notatnik — Material "description" icon (kartka z liniami tekstu).
        Używana na kafelku historii połączeń, by pokazać, że do rozmowy jest notatka. */
    val Notatka: ImageVector by lazy {
        ImageVector.Builder(
            name = "Notatka",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(14f, 2f)
                horizontalLineTo(6f)
                curveTo(4.9f, 2f, 4f, 2.9f, 4f, 4f)
                verticalLineTo(20f)
                curveTo(4f, 21.1f, 4.9f, 22f, 6f, 22f)
                horizontalLineTo(18f)
                curveTo(19.1f, 22f, 20f, 21.1f, 20f, 20f)
                verticalLineTo(8f)
                lineTo(14f, 2f)
                close()
                moveTo(16f, 20f)
                horizontalLineTo(8f)
                verticalLineTo(4f)
                horizontalLineTo(13f)
                verticalLineTo(9f)
                horizontalLineTo(18f)
                verticalLineTo(20f)
                close()
                horizontalLineTo(10f)
                verticalLineTo(16f)
                horizontalLineTo(14f)
                verticalLineTo(15f)
                horizontalLineTo(10f)
                verticalLineTo(16f)
                close()
                moveTo(10f, 14f)
                horizontalLineTo(14f)
                verticalLineTo(13f)
                horizontalLineTo(10f)
                verticalLineTo(14f)
                close()
            }
        }.build()
    }
}