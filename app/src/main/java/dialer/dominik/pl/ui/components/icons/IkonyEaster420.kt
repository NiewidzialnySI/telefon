package dialer.dominik.pl.ui.components.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Ikony easter egga 420 (Apache 2.0 / własne ścieżki w stylu MDI):
 * - Joint — zwinięty joint (mute)
 * - Młynek — herb grinder (Bluetooth)
 * - Boombox — Material Design Icons `boombox` (Pictogrammers)
 * - Cannabis — MDI `cannabis` (zapas)
 */
object IkonyEaster420 {

    /** Joint — mute. */
    val Joint: ImageVector by lazy {
        ImageVector.Builder(
            name = "Joint420",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // korpus jointa (ukośny walec)
                moveTo(3.2f, 16.8f)
                lineTo(14.6f, 5.4f)
                curveTo(15.2f, 4.8f, 16.2f, 4.8f, 16.8f, 5.4f)
                lineTo(18.6f, 7.2f)
                curveTo(19.2f, 7.8f, 19.2f, 8.8f, 18.6f, 9.4f)
                lineTo(7.2f, 20.8f)
                curveTo(6.6f, 21.4f, 5.6f, 21.4f, 5f, 20.8f)
                lineTo(3.2f, 19f)
                curveTo(2.6f, 18.4f, 2.6f, 17.4f, 3.2f, 16.8f)
                close()
                // filtr
                moveTo(16.9f, 5.5f)
                lineTo(19.5f, 2.9f)
                curveTo(19.9f, 2.5f, 20.6f, 2.5f, 21f, 2.9f)
                lineTo(21.1f, 3f)
                curveTo(21.5f, 3.4f, 21.5f, 4.1f, 21.1f, 4.5f)
                lineTo(18.5f, 7.1f)
                close()
                // żar
                moveTo(2.4f, 19.8f)
                curveTo(1.6f, 20.6f, 1.5f, 22f, 2.6f, 22.6f)
                curveTo(3.4f, 21.9f, 3.6f, 20.8f, 3.2f, 19.9f)
                close()
            }
        }.build()
    }

    /** Młynek do zioła — Bluetooth. */
    val Mlynek: ImageVector by lazy {
        ImageVector.Builder(
            name = "Mlynek420",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // zewnętrzny pierścień
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveTo(6.48f, 22f, 12f, 22f)
                reflectiveCurveTo(22f, 17.52f, 22f, 12f)
                reflectiveCurveTo(17.52f, 2f, 12f, 2f)
                close()
                moveTo(12f, 4f)
                curveTo(16.41f, 4f, 20f, 7.59f, 20f, 12f)
                reflectiveCurveTo(16.41f, 20f, 12f, 20f)
                reflectiveCurveTo(4f, 16.41f, 4f, 12f)
                reflectiveCurveTo(7.59f, 4f, 12f, 4f)
                close()
                // zęby młynka
                moveTo(12f, 6.2f)
                lineTo(13.1f, 9.2f)
                lineTo(16.3f, 8.5f)
                lineTo(14.5f, 11.1f)
                lineTo(17.2f, 12.8f)
                lineTo(14.2f, 13.5f)
                lineTo(14.8f, 16.6f)
                lineTo(12f, 14.7f)
                lineTo(9.2f, 16.6f)
                lineTo(9.8f, 13.5f)
                lineTo(6.8f, 12.8f)
                lineTo(9.5f, 11.1f)
                lineTo(7.7f, 8.5f)
                lineTo(10.9f, 9.2f)
                close()
                // środek
                moveTo(12f, 10.5f)
                curveTo(11.17f, 10.5f, 10.5f, 11.17f, 10.5f, 12f)
                reflectiveCurveTo(11.17f, 13.5f, 12f, 13.5f)
                reflectiveCurveTo(13.5f, 12.83f, 13.5f, 12f)
                reflectiveCurveTo(12.83f, 10.5f, 12f, 10.5f)
                close()
            }
        }.build()
    }

    /** Boombox — MDI Pictogrammers (Apache 2.0). */
    val Boombox: ImageVector by lazy {
        ImageVector.Builder(
            name = "Boombox420",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(7f, 5f)
                lineTo(5f, 7f)
                verticalLineTo(8f)
                horizontalLineTo(3f)
                curveTo(2.45f, 8f, 2f, 8.45f, 2f, 9f)
                verticalLineTo(17f)
                curveTo(2f, 17.55f, 2.45f, 18f, 3f, 18f)
                horizontalLineTo(21f)
                curveTo(21.55f, 18f, 22f, 17.55f, 22f, 17f)
                verticalLineTo(9f)
                curveTo(22f, 8.45f, 21.55f, 8f, 21f, 8f)
                horizontalLineTo(19f)
                verticalLineTo(7f)
                lineTo(17f, 5f)
                horizontalLineTo(7f)
                close()
                moveTo(7f, 7f)
                horizontalLineTo(17f)
                verticalLineTo(8f)
                horizontalLineTo(7f)
                verticalLineTo(7f)
                close()
                moveTo(11f, 9f)
                horizontalLineTo(13f)
                curveTo(13.28f, 9f, 13.5f, 9.22f, 13.5f, 9.5f)
                reflectiveCurveTo(13.28f, 10f, 13f, 10f)
                horizontalLineTo(11f)
                curveTo(10.72f, 10f, 10.5f, 9.78f, 10.5f, 9.5f)
                reflectiveCurveTo(10.72f, 9f, 11f, 9f)
                close()
                // lewy głośnik
                moveTo(7.5f, 10.5f)
                curveTo(9.16f, 10.5f, 10.5f, 11.84f, 10.5f, 13.5f)
                reflectiveCurveTo(9.16f, 16.5f, 7.5f, 16.5f)
                reflectiveCurveTo(4.5f, 15.16f, 4.5f, 13.5f)
                reflectiveCurveTo(5.84f, 10.5f, 7.5f, 10.5f)
                close()
                // prawy głośnik
                moveTo(16.5f, 10.5f)
                curveTo(18.16f, 10.5f, 19.5f, 11.84f, 19.5f, 13.5f)
                reflectiveCurveTo(18.16f, 16.5f, 16.5f, 16.5f)
                reflectiveCurveTo(13.5f, 15.16f, 13.5f, 13.5f)
                reflectiveCurveTo(14.84f, 10.5f, 16.5f, 10.5f)
                close()
                moveTo(7.5f, 12f)
                curveTo(6.67f, 12f, 6f, 12.67f, 6f, 13.5f)
                reflectiveCurveTo(6.67f, 15f, 7.5f, 15f)
                reflectiveCurveTo(9f, 14.33f, 9f, 13.5f)
                reflectiveCurveTo(8.33f, 12f, 7.5f, 12f)
                close()
                moveTo(16.5f, 12f)
                curveTo(15.67f, 12f, 15f, 12.67f, 15f, 13.5f)
                reflectiveCurveTo(15.67f, 15f, 16.5f, 15f)
                reflectiveCurveTo(18f, 14.33f, 18f, 13.5f)
                reflectiveCurveTo(17.33f, 12f, 16.5f, 12f)
                close()
            }
        }.build()
    }
}
