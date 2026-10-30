package dialer.dominik.pl.backup

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Backup / przywracanie na Dysku Google + profil konta.
 * Folder: [NAZWA_FOLDERU]. Zakres: drive.file + profil.
 */
object BackupDyskGoogle {

    private const val TAG = "BackupDyskGoogle"
    const val NAZWA_FOLDERU = "Telefon-Backup-data"
    private const val MIME_FOLDER = "application/vnd.google-apps.folder"
    private const val MIME_JSON = "application/json"
    private const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
    private const val SCOPE_PROFILE = "https://www.googleapis.com/auth/userinfo.profile"
    private const val SCOPE_EMAIL = "https://www.googleapis.com/auth/userinfo.email"
    private const val DRIVE = "https://www.googleapis.com/drive/v3"
    private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
    private const val USERINFO = "https://www.googleapis.com/oauth2/v3/userinfo"
    /** v2: prefs + klucze ADB + transkrypcje + meta nagrań. */
    private const val WERSJA_FORMATU = 2
    /** Limit łączny WAV w backupie (Drive multipart). */
    private const val MAX_NAGRANIA_BAJTÓW = 40L * 1024L * 1024L

    sealed class AuthWynik {
        data class Token(val accessToken: String) : AuthWynik()
        data class WymagaZgody(val pendingIntent: android.app.PendingIntent) : AuthWynik()
        data class Blad(val komunikat: String) : AuthWynik()
    }

    sealed class Wynik {
        data class Sukces(val komunikat: String) : Wynik()
        data class Blad(val komunikat: String) : Wynik()
    }

    private fun zakresy() = listOf(
        Scope(SCOPE_DRIVE_FILE),
        Scope(SCOPE_PROFILE),
        Scope(SCOPE_EMAIL),
        Scope("openid")
    )

    /** Tylko autoryzacja — bez wykonywania backupu (serwis zrobi resztę). */
    suspend fun autoryzuj(activity: Activity): AuthWynik = withContext(Dispatchers.IO) {
        try {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(zakresy())
                .build()
            val wynik = Tasks.await(
                Identity.getAuthorizationClient(activity).authorize(request)
            )
            if (wynik.hasResolution()) {
                val pi = wynik.pendingIntent
                    ?: return@withContext AuthWynik.Blad("Brak okna zgody Google")
                return@withContext AuthWynik.WymagaZgody(pi)
            }
            val token = wynik.accessToken
            if (token.isNullOrBlank()) AuthWynik.Blad("Brak tokenu dostępu")
            else AuthWynik.Token(token)
        } catch (e: Exception) {
            Log.e(TAG, "authorize", e)
            AuthWynik.Blad(e.message?.takeIf { it.isNotBlank() } ?: "Błąd autoryzacji Google")
        }
    }

    suspend fun tokenZIntentu(
        activity: Activity,
        data: android.content.Intent?
    ): AuthWynik = withContext(Dispatchers.IO) {
        try {
            val wynik = Identity.getAuthorizationClient(activity)
                .getAuthorizationResultFromIntent(data)
            val token = wynik.accessToken
            if (token.isNullOrBlank()) AuthWynik.Blad("Brak tokenu dostępu")
            else AuthWynik.Token(token)
        } catch (e: Exception) {
            Log.e(TAG, "getAuthorizationResultFromIntent", e)
            AuthWynik.Blad("Odmowa dostępu do Dysku Google")
        }
    }

    /** Pobiera imię/zdjęcie i opcjonalnie datę ostatniego backupu z Dysku. */
    suspend fun odswiezProfilIMeta(context: Context, token: String) = withContext(Dispatchers.IO) {
        try {
            val info = getJson(token, USERINFO)
            val imie = info.optString("name")
                .ifBlank { info.optString("given_name") }
                .ifBlank { info.optString("email").substringBefore("@") }
                .ifBlank { "Konto Google" }
            val email = info.optString("email")
            val foto = info.optString("picture").takeIf { it.isNotBlank() }
            BackupStan.zapiszProfil(
                context,
                BackupStan.Profil(imie = imie, email = email, zdjecieUrl = foto)
            )
        } catch (e: Exception) {
            Log.w(TAG, "profil", e)
        }
        try {
            val folderId = znajdzFolder(token) ?: return@withContext
            val meta = znajdzNajnowszyBackupMeta(token, folderId) ?: return@withContext
            BackupStan.ustawOstatniZDysku(meta.createdMs)
            if (meta.createdMs > 0L) {
                context.getSharedPreferences("backup_meta", Context.MODE_PRIVATE).edit()
                    .putLong("ostatni_backup_ms", meta.createdMs)
                    .apply()
            }
        } catch (e: Exception) {
            Log.w(TAG, "meta backup", e)
        }
    }

    fun wykonajBackup(context: Context, token: String): Wynik {
        return try {
            BackupStan.postep(0.08f, "Folder na Dysku…")
            val folderId = znajdzLubUtworzFolder(token)
            BackupStan.postep(0.22f, "Pakowanie danych…")
            val nazwa = nazwaPlikuBackup()
            val json = zbudujJsonBackup(context)
            val ocena = ocenBackupJson(json)
            // Nie wysyłaj pustej / prawie pustej kopii — nie zaśmiecaj Drive i nie
            // ryzykuj, że „najnowsza” przywróci pustkę.
            if (ocena.pusty) {
                return Wynik.Blad(
                    "Backup odrzucony: lokalne dane wyglądają na puste " +
                        "(${ocena.opis}). Nie nadpisuję dobrej kopii na Dysku.",
                )
            }
            BackupStan.postep(0.45f, "Wysyłanie na Dysk…")
            wgrajPlik(token, folderId, nazwa, json)
            BackupStan.postep(0.95f, "Kończenie…")
            val teraz = System.currentTimeMillis()
            BackupStan.zapiszOstatniBackup(context, teraz)
            Wynik.Sukces("Zapisano: $NAZWA_FOLDERU/$nazwa (${ocena.opis})")
        } catch (e: Exception) {
            Log.e(TAG, "backup", e)
            Wynik.Blad(e.message?.takeIf { it.isNotBlank() } ?: "Błąd backupu")
        }
    }

    fun wykonajPrzywroc(context: Context, token: String): Wynik {
        return try {
            BackupStan.postep(0.1f, "Szukanie kopii…")
            val folderId = znajdzLubUtworzFolder(token)
            // Nie bierz ślepo najnowszego pliku — może być pusty; wybierz najlepszy.
            val kandydaci = znajdzKandydatowBackup(token, folderId)
                .ifEmpty { return Wynik.Blad("Brak kopii w folderze $NAZWA_FOLDERU") }

            var najlepszy: Pair<PlikMeta, String>? = null
            var najlepszaOcena = OcenaBackup(0, 0, 0, maAdb = false, pusty = true, opis = "brak")
            for (meta in kandydaci) {
                BackupStan.postep(0.25f, "Sprawdzam ${meta.name}…")
                val json = runCatching { pobierzTrescPliku(token, meta.id) }.getOrNull()
                    ?: continue
                val ocena = ocenBackupJson(json)
                if (ocena.pusty) {
                    Log.w(TAG, "pomijam pusty backup ${meta.name}")
                    continue
                }
                if (ocena.wynik > najlepszaOcena.wynik) {
                    najlepszaOcena = ocena
                    najlepszy = meta to json
                }
            }
            val wybrany = najlepszy
                ?: return Wynik.Blad(
                    "Na Dysku są tylko puste/uszkodzone kopie — lokalnych danych nie ruszam.",
                )

            val lokalnyJson = zbudujJsonBackup(context)
            val lokalna = ocenBackupJson(lokalnyJson)
            // Lokalnie dużo danych, a kopia chudsza → nie pozwalaj na „przywróć pustkę”.
            if (!lokalna.pusty && najlepszaOcena.wynik * 2 < lokalna.wynik) {
                return Wynik.Blad(
                    "Odmowa przywracania: lokalnie masz bogatsze dane (${lokalna.opis}) " +
                        "niż wybrana kopia (${najlepszaOcena.opis}). " +
                        "Najpierw zrób świeży backup albo wyczyść dane aplikacji.",
                )
            }

            BackupStan.postep(0.75f, "Przywracanie ${wybrany.first.name}…")
            przywrocZJson(context, wybrany.second)
            BackupStan.postep(0.95f, "Kończenie…")
            Wynik.Sukces("Przywrócono: ${wybrany.first.name} (${najlepszaOcena.opis})")
        } catch (e: Exception) {
            Log.e(TAG, "przywroc", e)
            Wynik.Blad(e.message?.takeIf { it.isNotBlank() } ?: "Błąd przywracania")
        }
    }

    /** Jakość kopii — do odrzucania pustych uploadów / restore. */
    data class OcenaBackup(
        val wynik: Int,
        val prefsKlucze: Int,
        val nagrania: Int,
        val maAdb: Boolean,
        val pusty: Boolean,
        val opis: String,
    )

    fun ocenBackupJson(json: String): OcenaBackup {
        return try {
            val root = JSONObject(json)
            if (root.optString("aplikacja").isBlank() && !root.has("prefs")) {
                return OcenaBackup(0, 0, 0, false, true, "brak struktury")
            }
            val prefs = root.optJSONObject("prefs") ?: JSONObject()
            var klucze = 0
            val pk = prefs.keys()
            while (pk.hasNext()) {
                val nazwa = pk.next()
                if (nazwa == "backup_meta") continue
                klucze += prefs.optJSONObject(nazwa)?.length() ?: 0
            }
            val pliki = root.optJSONObject("pliki") ?: JSONObject()
            val nagrania = pliki.optJSONObject("nagrania")?.length() ?: 0
            val metaN = pliki.optJSONArray("nagrania_meta")?.length() ?: 0
            val tr = pliki.optJSONObject("transkrypcje")?.length() ?: 0
            val maAdb = pliki.optJSONObject("adb")?.optString("adbkey").orEmpty().isNotBlank()
            val wynik = klucze * 3 + nagrania * 20 + metaN * 2 + tr * 5 + if (maAdb) 50 else 0
            // Pusty = praktycznie zero ustawień użytkownika i zero plików.
            val pusty = klucze < 2 && nagrania == 0 && !maAdb && tr == 0
            OcenaBackup(
                wynik = wynik,
                prefsKlucze = klucze,
                nagrania = nagrania,
                maAdb = maAdb,
                pusty = pusty,
                opis = "prefs=$klucze nagrania=$nagrania adb=$maAdb wynik=$wynik",
            )
        } catch (e: Exception) {
            OcenaBackup(0, 0, 0, false, true, "uszkodzony JSON: ${e.message}")
        }
    }

    // ------------------- Drive REST -------------------

    private data class PlikMeta(val id: String, val name: String, val createdMs: Long)

    private fun znajdzFolder(token: String): String? {
        val q = URLEncoder.encode(
            "name='$NAZWA_FOLDERU' and mimeType='$MIME_FOLDER' and trashed=false",
            "UTF-8"
        )
        val lista = getJson(token, "$DRIVE/files?q=$q&spaces=drive&fields=files(id,name)")
        val files = lista.optJSONArray("files")
        if (files != null && files.length() > 0) {
            return files.getJSONObject(0).getString("id")
        }
        return null
    }

    private fun znajdzLubUtworzFolder(token: String): String {
        znajdzFolder(token)?.let { return it }
        val meta = JSONObject()
            .put("name", NAZWA_FOLDERU)
            .put("mimeType", MIME_FOLDER)
        return postJson(token, "$DRIVE/files?fields=id,name", meta.toString()).getString("id")
    }

    private fun znajdzNajnowszyBackupMeta(token: String, folderId: String): PlikMeta? =
        znajdzKandydatowBackup(token, folderId).firstOrNull()

    /** Ostatnie kopie (nowe → stare) — restore wybierze najlepszą niepustą. */
    private fun znajdzKandydatowBackup(token: String, folderId: String, limit: Int = 8): List<PlikMeta> {
        val q = URLEncoder.encode(
            "'$folderId' in parents and mimeType='$MIME_JSON' and trashed=false",
            "UTF-8"
        )
        val lista = getJson(
            token,
            "$DRIVE/files?q=$q&orderBy=createdTime desc&pageSize=$limit&fields=files(id,name,createdTime,size)"
        )
        val files = lista.optJSONArray("files") ?: return emptyList()
        val out = ArrayList<PlikMeta>(files.length())
        for (i in 0 until files.length()) {
            val f = files.getJSONObject(i)
            // Pomiń podejrzanie małe pliki (< ~80 B = prawie pusty JSON).
            val size = f.optString("size").toLongOrNull() ?: -1L
            if (size in 0 until 80L) continue
            out += PlikMeta(
                id = f.getString("id"),
                name = f.getString("name"),
                createdMs = parsujIso(f.optString("createdTime")),
            )
        }
        return out
    }

    private fun parsujIso(iso: String): Long {
        if (iso.isBlank()) return 0L
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            fmt.parse(iso)?.time ?: 0L
        } catch (_: Exception) {
            try {
                val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                fmt.parse(iso)?.time ?: 0L
            } catch (_: Exception) {
                0L
            }
        }
    }

    private fun wgrajPlik(token: String, folderId: String, nazwa: String, tresc: String) {
        val boundary = "telefon_backup_" + System.currentTimeMillis()
        val meta = JSONObject()
            .put("name", nazwa)
            .put("mimeType", MIME_JSON)
            .put("parents", JSONArray().put(folderId))
        val body = buildString {
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(meta.toString()).append("\r\n")
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(tresc).append("\r\n")
            append("--").append(boundary).append("--\r\n")
        }
        val bytes = body.toByteArray(Charsets.UTF_8)
        val url = "$UPLOAD/files?uploadType=multipart&fields=id,name"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            setFixedLengthStreamingMode(bytes.size)
        }
        conn.outputStream.use { out ->
            var wyslane = 0
            val chunk = 8 * 1024
            while (wyslane < bytes.size) {
                val n = minOf(chunk, bytes.size - wyslane)
                out.write(bytes, wyslane, n)
                wyslane += n
                val frac = wyslane.toFloat() / bytes.size
                BackupStan.postep(0.45f + 0.45f * frac, "Wysyłanie na Dysk…")
            }
            out.flush()
        }
        val kod = conn.responseCode
        val odp = odczytaj(conn)
        if (kod !in 200..299) {
            throw IllegalStateException("Upload $kod: ${odp.take(200)}")
        }
    }

    private fun pobierzTrescPliku(token: String, fileId: String): String {
        val conn = (URL("$DRIVE/files/$fileId?alt=media").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
        }
        val kod = conn.responseCode
        val odp = odczytaj(conn)
        if (kod !in 200..299) {
            throw IllegalStateException("Download $kod: ${odp.take(200)}")
        }
        return odp
    }

    private fun getJson(token: String, url: String): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
        }
        val kod = conn.responseCode
        val odp = odczytaj(conn)
        if (kod !in 200..299) {
            throw IllegalStateException("GET $kod: ${odp.take(200)}")
        }
        return JSONObject(odp)
    }

    private fun postJson(token: String, url: String, body: String): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
        val kod = conn.responseCode
        val odp = odczytaj(conn)
        if (kod !in 200..299) {
            throw IllegalStateException("POST $kod: ${odp.take(200)}")
        }
        return JSONObject(odp)
    }

    private fun odczytaj(conn: HttpURLConnection): String {
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        return stream?.use { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText() } ?: ""
    }

    private fun nazwaPlikuBackup(): String {
        val fmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
        return "telefon-backup-${fmt.format(Date())}.json"
    }

    fun zbudujJsonBackup(context: Context): String {
        val prefsObj = JSONObject()
        val prefsNazwy = mutableListOf<String>()
        val dir = File(context.applicationInfo.dataDir, "shared_prefs")
        dir.listFiles()?.filter { it.name.endsWith(".xml") }?.forEach { plik ->
            val nazwa = plik.name.removeSuffix(".xml")
            if (nazwa == "backup_meta") return@forEach
            val sp = context.getSharedPreferences(nazwa, Context.MODE_PRIVATE)
            prefsObj.put(nazwa, serializujPrefs(sp))
            prefsNazwy += nazwa
        }

        val pliki = JSONObject()
        // Klucz ADB — bez niego po reinstalacji trzeba parować od zera.
        val adbDir = File(context.filesDir, "adb")
        val adbKey = File(adbDir, "adbkey")
        val adbCrt = File(adbDir, "adbkey.crt")
        if (adbKey.isFile && adbCrt.isFile) {
            pliki.put(
                "adb",
                JSONObject()
                    .put("adbkey", android.util.Base64.encodeToString(adbKey.readBytes(), android.util.Base64.NO_WRAP))
                    .put("adbkey_crt", android.util.Base64.encodeToString(adbCrt.readBytes(), android.util.Base64.NO_WRAP)),
            )
        }

        val nagrDir = dialer.dominik.pl.nagranie.NagrywaniePliki.katalog(context)
        val metaNagran = JSONArray()
        val wavB64 = JSONObject()
        var sumaWav = 0L
        var pominieteWav = 0
        nagrDir.listFiles()
            ?.filter { it.isFile && (it.extension == "wav" || it.extension == "m4a") }
            ?.sortedByDescending { it.lastModified() }
            ?.forEach { f ->
                metaNagran.put(
                    JSONObject()
                        .put("nazwa", f.name)
                        .put("bajty", f.length())
                        .put("mtime", f.lastModified()),
                )
                val txt = dialer.dominik.pl.nagranie.TranskrypcjaRozmow.plikTranskrypcji(f)
                if (txt.isFile) {
                    val tr = pliki.optJSONObject("transkrypcje")
                        ?: JSONObject().also { pliki.put("transkrypcje", it) }
                    tr.put(f.name, txt.readText())
                }
                if (sumaWav + f.length() <= MAX_NAGRANIA_BAJTÓW && f.length() < 15L * 1024L * 1024L) {
                    wavB64.put(
                        f.name,
                        android.util.Base64.encodeToString(f.readBytes(), android.util.Base64.NO_WRAP),
                    )
                    sumaWav += f.length()
                } else {
                    pominieteWav++
                }
            }
        if (wavB64.length() > 0) pliki.put("nagrania", wavB64)
        pliki.put("nagrania_meta", metaNagran)

        val zawiera = JSONArray()
            .put("shared_prefs (${prefsNazwy.size})")
            .put(if (pliki.has("adb")) "klucz ADB (pair)" else "brak klucza ADB")
            .put("nagrania WAV/M4A: ${wavB64.length()} plików (~${sumaWav / 1024} KB)")
            .put("transkrypcje: ${pliki.optJSONObject("transkrypcje")?.length() ?: 0}")
        val pomija = JSONArray()
            .put("SMS/MMS/CallLog/Kontakty (system Android — nie w sandboxie aplikacji)")
            .put("model Whisper/Vosk (~GB) — pobierany ponownie")
            .put("cache logo firm")
        if (pominieteWav > 0) {
            pomija.put("$pominieteWav nagrań powyżej limitu ${MAX_NAGRANIA_BAJTÓW / (1024 * 1024)} MB")
        }

        return JSONObject()
            .put("wersja", WERSJA_FORMATU)
            .put("aplikacja", context.packageName)
            .put("utworzono", System.currentTimeMillis())
            .put("zawiera", zawiera)
            .put("pomija", pomija)
            .put("prefs", prefsObj)
            .put("pliki", pliki)
            .put(
                "ocena",
                JSONObject()
                    .put("prefs_plikow", prefsNazwy.size)
                    .put("nagrania_w_kopii", wavB64.length())
                    .put("ma_adb", pliki.has("adb")),
            )
            .toString(2)
    }

    /**
     * Przy restore: nie czyść lokalnego pliku prefs, jeśli w kopii ta sekcja jest pusta,
     * a lokalnie ma wpisy (ochrona przed częściowo pustym JSON).
     */
    private fun prefsLokalnieMaDane(context: Context, nazwa: String): Boolean {
        val all = context.getSharedPreferences(nazwa, Context.MODE_PRIVATE).all
        return all.isNotEmpty()
    }

    private fun serializujPrefs(sp: SharedPreferences): JSONObject {
        val o = JSONObject()
        for ((k, v) in sp.all) {
            when (v) {
                null -> Unit
                is String -> o.put(k, JSONObject().put("t", "s").put("v", v))
                is Int -> o.put(k, JSONObject().put("t", "i").put("v", v))
                is Long -> o.put(k, JSONObject().put("t", "l").put("v", v))
                is Float -> o.put(k, JSONObject().put("t", "f").put("v", v.toDouble()))
                is Boolean -> o.put(k, JSONObject().put("t", "b").put("v", v))
                is Set<*> -> {
                    val arr = JSONArray()
                    v.filterIsInstance<String>().forEach { arr.put(it) }
                    o.put(k, JSONObject().put("t", "ss").put("v", arr))
                }
            }
        }
        return o
    }

    fun przywrocZJson(context: Context, json: String) {
        val root = JSONObject(json)
        if (root.optInt("wersja", 0) > WERSJA_FORMATU) {
            throw IllegalStateException("Nieobsługiwana wersja kopii")
        }
        val prefs = root.optJSONObject("prefs") ?: throw IllegalStateException("Brak sekcji prefs")
        // Druga bariera: nie przywracaj, jeśli cała kopia jest pusta.
        val ocena = ocenBackupJson(json)
        if (ocena.pusty) {
            throw IllegalStateException("Kopia jest pusta — lokalnych danych nie nadpisuję")
        }
        val klucze = prefs.keys()
        while (klucze.hasNext()) {
            val nazwa = klucze.next()
            if (nazwa == "backup_meta") continue
            val wpisy = prefs.getJSONObject(nazwa)
            // Pusta sekcja w JSON + lokalne dane → zostaw lokalne (nie clear()).
            if (wpisy.length() == 0 && prefsLokalnieMaDane(context, nazwa)) {
                Log.w(TAG, "pomijam pustą sekcję prefs '$nazwa' — lokalnie są dane")
                continue
            }
            val ed = context.getSharedPreferences(nazwa, Context.MODE_PRIVATE).edit().clear()
            val ik = wpisy.keys()
            while (ik.hasNext()) {
                val klucz = ik.next()
                val cell = wpisy.getJSONObject(klucz)
                when (cell.getString("t")) {
                    "s" -> ed.putString(klucz, cell.getString("v"))
                    "i" -> ed.putInt(klucz, cell.getInt("v"))
                    "l" -> ed.putLong(klucz, cell.getLong("v"))
                    "f" -> ed.putFloat(klucz, cell.getDouble("v").toFloat())
                    "b" -> ed.putBoolean(klucz, cell.getBoolean("v"))
                    "ss" -> {
                        val set = mutableSetOf<String>()
                        val arr = cell.getJSONArray("v")
                        for (i in 0 until arr.length()) set.add(arr.getString(i))
                        ed.putStringSet(klucz, set)
                    }
                }
            }
            // commit — od razu widoczne po restarcie / kolejnych odczytach.
            ed.commit()
        }

        val pliki = root.optJSONObject("pliki") ?: return
        // ADB keypair
        pliki.optJSONObject("adb")?.let { adb ->
            val dir = File(context.filesDir, "adb").also { it.mkdirs() }
            val keyB64 = adb.optString("adbkey")
            val crtB64 = adb.optString("adbkey_crt")
            if (keyB64.isNotBlank() && crtB64.isNotBlank()) {
                File(dir, "adbkey").writeBytes(
                    android.util.Base64.decode(keyB64, android.util.Base64.DEFAULT),
                )
                File(dir, "adbkey.crt").writeBytes(
                    android.util.Base64.decode(crtB64, android.util.Base64.DEFAULT),
                )
            }
        }
        // Nagrania WAV/M4A
        val nagrDir = dialer.dominik.pl.nagranie.NagrywaniePliki.katalog(context)
        pliki.optJSONObject("nagrania")?.let { nagr ->
            val names = nagr.keys()
            while (names.hasNext()) {
                val nazwa = names.next()
                val b64 = nagr.optString(nazwa)
                if (b64.isBlank()) continue
                File(nagrDir, nazwa).writeBytes(
                    android.util.Base64.decode(b64, android.util.Base64.DEFAULT),
                )
            }
        }
        // Transkrypcje obok plików
        pliki.optJSONObject("transkrypcje")?.let { tr ->
            val names = tr.keys()
            while (names.hasNext()) {
                val nazwaWav = names.next()
                val tekst = tr.optString(nazwaWav)
                val wav = File(nagrDir, nazwaWav)
                dialer.dominik.pl.nagranie.TranskrypcjaRozmow.plikTranskrypcji(wav)
                    .writeText(tekst)
            }
        }
    }
}
