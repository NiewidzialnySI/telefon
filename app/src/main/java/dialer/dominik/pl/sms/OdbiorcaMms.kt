package dialer.dominik.pl.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import com.android.mms.service_alt.DownloadRequest
import com.android.mms.service_alt.MmsConfig
import com.android.mms.transaction.DownloadManager
import com.klinker.android.send_message.MmsReceivedReceiver
import dialer.dominik.pl.ui.screens.uniewaznijCacheSkrzynki
import java.io.File

/**
 * Po pobraniu PDU (PushReceiver → DownloadManager → broadcast `MMS_RECEIVED`)
 * zapisuje MMS do Telephony.
 *
 * NIE dziedziczy po [MmsReceivedReceiver]: w mmslib 1.0.1
 * `parseSenderAddressFromPdu` robi NPE gdy PDU nie ma `From` (typowe dla
 * wygasłych / uszkodzonych NotificationInd) i zabija cały proces.
 *
 * Manifest: `taskAffinity="com.klinker.android.messaging.MMS_RECEIVED"`.
 */
class MmsPobranyIZapisanyReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "OdbiorcaMms"

        @Volatile
        var ostatniePobranieMs: Long = 0L
            internal set
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != null &&
            intent.action != MmsReceivedReceiver.MMS_RECEIVED &&
            intent.action != "com.klinker.android.messaging.MMS_RECEIVED"
        ) {
            // Explicit broadcast z DownloadManager i tak ustawia action MMS_RECEIVED.
        }
        val pending = goAsync()
        val appCtx = context.applicationContext
        Thread {
            val path = intent.getStringExtra(MmsReceivedReceiver.EXTRA_FILE_PATH)
            val location = intent.getStringExtra(MmsReceivedReceiver.EXTRA_LOCATION_URL)
            try {
                zapiszPobranyPdu(appCtx, intent, path, location)
            } catch (e: Exception) {
                Log.e(TAG, "Błąd zapisu MMS (bezpieczny receiver)", e)
                uniewaznijCacheSkrzynki()
                StatusWiadomosciBus.emituj(
                    AktualizacjaStatusuWiadomosci(
                        messageUri = null,
                        messageId = null,
                        lokalnyKlucz = null,
                        stan = StanWysylki.BLAD
                    )
                )
                // Wygasły / pusty download — usuń NotificationInd, żeby nie próbować w kółko.
                if (!location.isNullOrBlank()) {
                    usunNotificationInd(appCtx, location)
                    oznaczProbeJakoMartwa(appCtx, location)
                }
            } finally {
                if (!location.isNullOrBlank()) {
                    runCatching { DownloadManager.finishDownload(location) }
                }
                if (!path.isNullOrBlank()) {
                    runCatching { File(path).delete() }
                }
                pending.finish()
            }
        }.start()
    }

    private fun zapiszPobranyPdu(
        context: Context,
        intent: Intent,
        path: String?,
        location: String?
    ) {
        if (path.isNullOrBlank()) {
            Log.w(TAG, "Brak file_path w intenście MMS")
            return
        }
        val plik = File(path)
        if (!plik.exists() || plik.length() == 0L) {
            Log.w(TAG, "Pusty plik PDU: $path")
            if (!location.isNullOrBlank()) {
                usunNotificationInd(context, location)
                oznaczProbeJakoMartwa(context, location)
            }
            return
        }

        // Już mamy pełną treść dla tego ct_l? Nie zapisuj drugi raz (duplikat zdjęcia).
        if (!location.isNullOrBlank() && juzMamyMmsDlaLocation(context, location)) {
            Log.i(TAG, "MMS już w skrzynce dla ct_l — pomijam persist, usuwam NotificationInd")
            usunNotificationInd(context, location)
            oznaczJakoZrobione(context, location)
            runCatching { plik.delete() }
            return
        }

        val response = plik.readBytes()
        val subId = intent.getIntExtra(
            MmsReceivedReceiver.SUBSCRIPTION_ID,
            SubscriptionManager.getDefaultSmsSubscriptionId()
        )
        val trId = intent.getStringExtra(MmsReceivedReceiver.EXTRA_TRANSACTION_ID)
        val overridden = MmsConfig.Overridden(MmsConfig(context), null)

        // Bezpieczny persist (bez parseSenderAddressFromPdu z NPE).
        val messageUri = DownloadRequest.persist(
            context,
            response,
            overridden,
            location,
            trId,
            subId,
            /*creator*/ null
        )

        runCatching { plik.delete() }

        if (messageUri != null) {
            // mmslib kasuje NotificationInd po tr_id==ct_l (bug) — u nas zawsze po ct_l.
            if (!location.isNullOrBlank()) {
                usunNotificationInd(context, location)
                oznaczJakoZrobione(context, location)
            }
            ostatniePobranieMs = System.currentTimeMillis()
            uniewaznijCacheSkrzynki()
            StatusWiadomosciBus.emituj(
                AktualizacjaStatusuWiadomosci(
                    messageUri = messageUri.toString(),
                    messageId = messageUri.lastPathSegment?.toLongOrNull(),
                    lokalnyKlucz = null,
                    stan = StanWysylki.WYSŁANO
                )
            )
            // Jak SMS: heads-up + dymek zaraz po zapisie — także gdy apka jest w tle / zamknięta
            // (proces budzi WAP_PUSH → DownloadManager → ten receiver).
            powiadomOPrzychodzacymMms(context, messageUri)
            Log.i(TAG, "MMS zapisany: $messageUri")
        } else {
            Log.w(TAG, "DownloadRequest.persist zwrócił null (location=$location)")
            if (!location.isNullOrBlank()) {
                usunNotificationInd(context, location)
                oznaczProbeJakoMartwa(context, location)
            }
        }
    }
}

/** Powiadomienie + dymek jak przy SMS, po udanym persist MMS. */
private fun powiadomOPrzychodzacymMms(context: Context, messageUri: Uri) {
    try {
        val msgId = messageUri.lastPathSegment?.toLongOrNull() ?: return
        val nadawca = dialer.dominik.pl.ui.screens.pobierzAdresMms(
            context,
            msgId,
            Telephony.Mms.MESSAGE_BOX_INBOX
        )?.takeIf { it.isNotBlank() } ?: "Nieznany"
        val tekst = dialer.dominik.pl.ui.screens.pobierzTekstMms(context, msgId)
        val tresc = if (tekst.isNotBlank()) tekst else "📷 [MMS]"
        // DATE w Telephony.Mms jest w sekundach — na powiadomienie bierzemy teraz
        // (świeży heads-up); treść i tak pochodzi z PDU.
        OdbiorcaSms.powiadomOPrzychodzacej(context, nadawca, tresc, System.currentTimeMillis())
        Log.i("OdbiorcaMms", "Powiadomienie MMS od $nadawca")
    } catch (e: Exception) {
        Log.e("OdbiorcaMms", "Nie udało się pokazać powiadomienia MMS", e)
    }
}

private const val PREFS_MMS_RETRY = "mms_download_retry"
private const val KEY_MARTWE = "martwe_ct_l"
private const val KEY_ZROBIONE = "zrobione_ct_l"
private const val KEY_OSTATNIA_PROBA_MS = "ostatnia_proba_ms"

private fun prefsMms(context: Context) =
    context.getSharedPreferences(PREFS_MMS_RETRY, Context.MODE_PRIVATE)

private fun dodajDoSeta(context: Context, key: String, location: String, max: Int = 80) {
    val p = prefsMms(context)
    val set = p.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
    set.add(location)
    while (set.size > max) set.remove(set.first())
    p.edit().putStringSet(key, set).apply()
}

private fun oznaczProbeJakoMartwa(context: Context, location: String) =
    dodajDoSeta(context, KEY_MARTWE, location)

private fun oznaczJakoZrobione(context: Context, location: String) =
    dodajDoSeta(context, KEY_ZROBIONE, location)

private fun czyMartwy(context: Context, location: String): Boolean =
    prefsMms(context).getStringSet(KEY_MARTWE, emptySet())?.contains(location) == true ||
        prefsMms(context).getStringSet(KEY_ZROBIONE, emptySet())?.contains(location) == true

/** Czy w skrzynce jest już pełny MMS (nie NotificationInd) z tym samym content-location. */
private fun juzMamyMmsDlaLocation(context: Context, location: String): Boolean {
    return try {
        context.contentResolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf(Telephony.Mms._ID),
            "${Telephony.Mms.MESSAGE_TYPE}!=? AND ct_l=?",
            arrayOf("130", location),
            null
        )?.use { it.moveToFirst() } == true
    } catch (_: Exception) {
        false
    }
}

private fun usunNotificationInd(context: Context, location: String) {
    try {
        val n = context.contentResolver.delete(
            Telephony.Mms.CONTENT_URI,
            "${Telephony.Mms.MESSAGE_TYPE}=? AND ct_l=?",
            arrayOf("130", location)
        )
        Log.i("OdbiorcaMms", "Usunięto NotificationInd ct_l ($n) dla $location")
    } catch (e: Exception) {
        Log.w("OdbiorcaMms", "usunNotificationInd: ${e.message}")
    }
}

/**
 * Dociąga świeże NotificationInd (m_type=130). Pomija te, dla których treść
 * już jest w skrzynce — inaczej to samo zdjęcie pojawiało się wielokrotnie.
 */
fun dociagnijOczekujaceMms(context: Context) {
    val appCtx = context.applicationContext
    val prefs = prefsMms(appCtx)
    val teraz = System.currentTimeMillis()
    val ostatnia = prefs.getLong(KEY_OSTATNIA_PROBA_MS, 0L)
    if (teraz - ostatnia < 60_000L) return
    prefs.edit().putLong(KEY_OSTATNIA_PROBA_MS, teraz).apply()

    Thread {
        try {
            // Najpierw: posprzątaj duplikaty treści + zbędne NotificationInd.
            usunDuplikatyMmsPoCtL(appCtx)
            wyczyśćZbedneNotificationInd(appCtx)

            val minEpochSek = (teraz / 1000L) - 3L * 24L * 3600L
            val cursor = appCtx.contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(Telephony.Mms._ID, "ct_l", "tr_id", Telephony.Mms.DATE),
                "${Telephony.Mms.MESSAGE_BOX}=? AND ${Telephony.Mms.MESSAGE_TYPE}=? AND ${Telephony.Mms.DATE}>=?",
                arrayOf(
                    Telephony.Mms.MESSAGE_BOX_INBOX.toString(),
                    "130",
                    minEpochSek.toString()
                ),
                "${Telephony.Mms.DATE} DESC"
            ) ?: return@Thread

            cursor.use { c ->
                val idIdx = c.getColumnIndex(Telephony.Mms._ID)
                val locIdx = c.getColumnIndex("ct_l")
                val trIdx = c.getColumnIndex("tr_id")
                var ile = 0
                val subId = SubscriptionManager.getDefaultSmsSubscriptionId()
                while (c.moveToNext() && ile < 2) {
                    val id = if (idIdx >= 0) c.getLong(idIdx) else continue
                    val location = if (locIdx >= 0) c.getString(locIdx) else null
                    if (location.isNullOrBlank()) continue
                    if (czyMartwy(appCtx, location)) {
                        usunNotificationInd(appCtx, location)
                        continue
                    }
                    if (juzMamyMmsDlaLocation(appCtx, location)) {
                        usunNotificationInd(appCtx, location)
                        oznaczJakoZrobione(appCtx, location)
                        continue
                    }
                    val trId = if (trIdx >= 0) c.getString(trIdx) else null
                    val notifUri = Uri.withAppendedPath(Telephony.Mms.CONTENT_URI, id.toString())
                    Log.i("OdbiorcaMms", "Dociągam oczekujący MMS id=$id")
                    try {
                        DownloadManager.getInstance().downloadMultimediaMessage(
                            appCtx,
                            location,
                            trId,
                            notifUri,
                            false,
                            subId
                        )
                        ile++
                    } catch (e: Exception) {
                        Log.w("OdbiorcaMms", "download id=$id: ${e.message}")
                        oznaczProbeJakoMartwa(appCtx, location)
                    }
                }
            }

            val stareSek = (teraz / 1000L) - 14L * 24L * 3600L
            try {
                appCtx.contentResolver.delete(
                    Telephony.Mms.CONTENT_URI,
                    "${Telephony.Mms.MESSAGE_TYPE}=? AND ${Telephony.Mms.DATE}<?",
                    arrayOf("130", stareSek.toString())
                )
            } catch (_: Exception) {
            }
        } catch (e: Exception) {
            Log.w("OdbiorcaMms", "dociagnijOczekujaceMms: ${e.message}")
        }
    }.start()
}

/**
 * Gdy to samo zdjęcie zapisało się kilka razy (ten sam ct_l), zostaw najnowszy wpis.
 */
private fun usunDuplikatyMmsPoCtL(context: Context) {
    try {
        val poLoc = linkedMapOf<String, MutableList<Pair<Long, Long>>>() // ct_l → [(id, date)]
        context.contentResolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf(Telephony.Mms._ID, "ct_l", Telephony.Mms.DATE, Telephony.Mms.MESSAGE_TYPE),
            "${Telephony.Mms.MESSAGE_TYPE}!=?",
            arrayOf("130"),
            "${Telephony.Mms.DATE} DESC"
        )?.use { c ->
            val idIdx = c.getColumnIndex(Telephony.Mms._ID)
            val locIdx = c.getColumnIndex("ct_l")
            val dateIdx = c.getColumnIndex(Telephony.Mms.DATE)
            while (c.moveToNext()) {
                val loc = if (locIdx >= 0) c.getString(locIdx) else null
                if (loc.isNullOrBlank()) continue
                val id = if (idIdx >= 0) c.getLong(idIdx) else continue
                val date = if (dateIdx >= 0) c.getLong(dateIdx) else 0L
                poLoc.getOrPut(loc) { mutableListOf() }.add(id to date)
            }
        }
        var usunieto = 0
        poLoc.values.forEach { lista ->
            if (lista.size < 2) return@forEach
            // lista jest DESC po dacie z query — zostaw pierwszy, resztę wywal
            lista.drop(1).forEach { (id, _) ->
                try {
                    context.contentResolver.delete(
                        Telephony.Mms.CONTENT_URI,
                        "${Telephony.Mms._ID}=?",
                        arrayOf(id.toString())
                    )
                    usunieto++
                } catch (_: Exception) {
                }
            }
        }
        if (usunieto > 0) {
            Log.i("OdbiorcaMms", "Usunięto $usunieto zduplikowanych MMS (ten sam ct_l)")
            uniewaznijCacheSkrzynki()
        }
    } catch (e: Exception) {
        Log.w("OdbiorcaMms", "usunDuplikaty: ${e.message}")
    }
}

/** Kasuje NotificationInd, dla których pełny MMS z tym samym ct_l już istnieje. */
private fun wyczyśćZbedneNotificationInd(context: Context) {
    try {
        context.contentResolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf(Telephony.Mms._ID, "ct_l"),
            "${Telephony.Mms.MESSAGE_TYPE}=?",
            arrayOf("130"),
            null
        )?.use { c ->
            val locIdx = c.getColumnIndex("ct_l")
            while (c.moveToNext()) {
                val loc = if (locIdx >= 0) c.getString(locIdx) else null
                if (loc.isNullOrBlank()) continue
                if (juzMamyMmsDlaLocation(context, loc)) {
                    usunNotificationInd(context, loc)
                    oznaczJakoZrobione(context, loc)
                }
            }
        }
    } catch (e: Exception) {
        Log.w("OdbiorcaMms", "wyczyscZbedne: ${e.message}")
    }
}

/** SmsManager dla domyślnej subskrypcji SMS (dual-SIM). */
internal fun smsManagerDlaDomyslnejSubskrypcji(context: Context): SmsManager {
    val subId = SubscriptionManager.getDefaultSmsSubscriptionId()
    return if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
        context.getSystemService(SmsManager::class.java)?.createForSubscriptionId(subId)
            ?: SmsManager.getSmsManagerForSubscriptionId(subId)
    } else {
        context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
    }
}
