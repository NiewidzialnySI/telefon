package dialer.dominik.pl.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Rozpoznawanie alfa-nadawców SMS → domena → logo.
 *
 * Wzorzec pobierania jak w [get-brand-logos](https://github.com/rumizenzz/get-brand-logos):
 * poprawna domena marki + Google Favicon (PNG) + DuckDuckGo, z odrzuceniem
 * placeholderów (&lt; ~300 B).
 *
 * Dopasowanie: normalizacja + obcinanie sufiksów kraju/bramki (PL, Polska, SMS…)
 * + lokalna mapa typowych polskich nadawców z bramek A2P (brak publicznej kompletnej bazy;
 * SprawdzamNumer ma tylko wycinek).
 */

internal data class FirmaLogo(
    /** Znormalizowane aliasy nadawcy (bez spacji/znaków, lowercase). */
    val aliasy: List<String>,
    /** Domeny do spróbowania w kolejności (pierwsza z dobrym PNG wygrywa). */
    val domeny: List<String> = emptyList(),
    /**
     * Lokalny plik w assets/loga/ (np. „paypo.png”) — pierwszeństwo przed siecią.
     * Używane gdy favicon z Google/DDG jest niewyraźny albo myli markę.
     */
    val assetLogo: String? = null
)

/** Katalog cache — bump wersji unieważnia stare, błędne loga. */
private const val KATALOG_LOGOW = "loga_firm_v5"
private const val MIN_BAJTOW_LOGA = 300

/**
 * Ogólne / niejednoznaczne nadpisy — NIE zgaduj domeny (.pl/.com), bo wychodzi
 * przypadkowe „dziwne” logo (np. LEKARZ → lekarz.com).
 */
private val GENERICZNE_NADAWCY = setOf(
    "lekarz", "lekarka", "doktor", "info", "sms", "alert", "bank", "sklep",
    "apteka", "kod", "otp", "verify", "serwis", "test", "admin", "powiadomienie",
    "wiadomosc", "message", "notify", "system", "noreply", "no reply"
)

/** Sufiksy często doklejane do alfa-senderów PL („XTB PL”, „DPD Polska”, „Info SMS”). */
private val SUFIKSY_NADAWCY = listOf(
    "polska", "poland", "polish", "plk", "sms", "info", "app", "alert", "pl"
)

/**
 * Polskie / popularne marki SMS (alfa-sendery z bramek). Aliasy = formy From.
 * Najdłuższy pasujący alias wygrywa.
 */
private val FIRMY_LOGO: List<FirmaLogo> = listOf(
    // Komunikatory / tech
    FirmaLogo(listOf("signal", "signalapp"), listOf("signal.org")),
    FirmaLogo(listOf("whatsapp"), listOf("whatsapp.com")),
    FirmaLogo(listOf("telegram"), listOf("telegram.org")),
    FirmaLogo(listOf("microsoft", "msft"), listOf("microsoft.com")),
    FirmaLogo(listOf("google", "googlepl"), listOf("google.com")),
    FirmaLogo(listOf("apple", "appleid"), listOf("apple.com")),
    FirmaLogo(listOf("amazon", "amazonpl"), listOf("amazon.pl", "amazon.com")),
    FirmaLogo(listOf("facebook", "meta", "messenger"), listOf("facebook.com")),
    FirmaLogo(listOf("instagram"), listOf("instagram.com")),
    FirmaLogo(listOf("netflix"), listOf("netflix.com")),
    FirmaLogo(listOf("spotify"), listOf("spotify.com")),
    FirmaLogo(listOf("youtube"), listOf("youtube.com")),
    FirmaLogo(listOf("tiktok"), listOf("tiktok.com")),
    FirmaLogo(listOf("discord"), listOf("discord.com")),
    FirmaLogo(listOf("steam", "steampowered"), listOf("steampowered.com")),
    FirmaLogo(listOf("uber", "uberpl", "ubereats"), listOf("uber.com")),
    FirmaLogo(listOf("bolt", "boltpl", "boltfood"), listOf("bolt.eu")),
    FirmaLogo(listOf("wolt"), listOf("wolt.com")),
    FirmaLogo(listOf("glovo"), listOf("glovoapp.com")),
    FirmaLogo(listOf("pyszne", "pysznepl"), listOf("pyszne.pl")),

    // Market / retail PL
    FirmaLogo(listOf("mediaexpert", "mediaexprt"), listOf("mediaexpert.pl")),
    FirmaLogo(listOf("mediamarkt", "mediamarktpl"), listOf("mediamarkt.pl")),
    FirmaLogo(listOf("eurocom", "rtveuroagd", "euroagd"), listOf("euro.com.pl")),
    FirmaLogo(listOf("xkom", "xkompl"), listOf("x-kom.pl")),
    FirmaLogo(listOf("morele", "morelenet"), listOf("morele.net")),
    FirmaLogo(listOf("allegro", "allegropl"), listOf("allegro.pl")),
    FirmaLogo(listOf("olx", "olxpl"), listOf("olx.pl")),
    FirmaLogo(listOf("vinted"), listOf("vinted.pl")),
    FirmaLogo(listOf("zalando"), listOf("zalando.pl")),
    FirmaLogo(listOf("aboutyou"), listOf("aboutyou.pl")),
    FirmaLogo(listOf("reserved", "lpp"), listOf("reserved.com")),
    FirmaLogo(listOf("sinsay", "house", "mohito", "cropp"), listOf("sinsay.com")),
    FirmaLogo(listOf("ccc", "cccshoes"), listOf("ccc.eu")),
    // Favicon Google dla biedronka.pl jest zamazany (~16px) — lokalny PNG z ich CDN.
    FirmaLogo(
        listOf("biedronka", "biedronkapl", "biedr", "biedrsonda", "jbssapolska"),
        listOf("biedronka.pl"),
        assetLogo = "biedronka.png"
    ),
    FirmaLogo(listOf("lidl", "lidlpl", "winnicalidl"), listOf("lidl.pl")),
    FirmaLogo(listOf("kaufland"), listOf("kaufland.pl")),
    FirmaLogo(listOf("homla"), listOf("homla.com", "homla.pl")),
    FirmaLogo(listOf("kfc", "kfcpl"), listOf("kfc.pl", "kfc.com")),
    FirmaLogo(listOf("mcdonalds", "mcd", "mcdonald"), listOf("mcdonalds.pl")),
    FirmaLogo(listOf("lotto", "lottopl", "totalizator"), listOf("lotto.pl")),
    FirmaLogo(listOf("netto", "nettopl"), listOf("netto.pl")),
    FirmaLogo(listOf("dino", "dinopl"), listOf("marketdino.pl")),
    FirmaLogo(listOf("stokrotka"), listOf("stokrotka.pl")),
    FirmaLogo(listOf("auchan", "auchanpl"), listOf("auchan.pl")),
    FirmaLogo(listOf("carrefour", "carrefourpl"), listOf("carrefour.pl")),
    FirmaLogo(listOf("rossmann", "rossmannpl"), listOf("rossmann.pl")),
    FirmaLogo(listOf("hebe"), listOf("hebe.pl")),
    FirmaLogo(listOf("zabka", "zabkapl"), listOf("zabka.pl")),
    FirmaLogo(listOf("pepco"), listOf("pepco.pl")),
    FirmaLogo(listOf("dealz"), listOf("dealz.pl")),
    FirmaLogo(listOf("action"), listOf("action.com")),
    FirmaLogo(listOf("ikea", "ikeapl"), listOf("ikea.com")),
    FirmaLogo(listOf("castorama"), listOf("castorama.pl")),
    FirmaLogo(listOf("leroymerlin", "leroy"), listOf("leroymerlin.pl")),
    FirmaLogo(listOf("obi"), listOf("obi.pl")),
    FirmaLogo(listOf("empik"), listOf("empik.com")),
    FirmaLogo(listOf("decathlon"), listOf("decathlon.pl")),
    FirmaLogo(listOf("smyk"), listOf("smyk.com")),

    // Paliwo / energia
    FirmaLogo(listOf("myorlen", "orlen", "orlenpl", "orlenpaczka"), listOf("orlen.pl")),
    FirmaLogo(listOf("bppolska", "bppl"), listOf("bp.com")),
    FirmaLogo(listOf("shell", "shellpl"), listOf("shell.pl")),
    FirmaLogo(listOf("circlek"), listOf("circlek.pl")),
    FirmaLogo(listOf("pgnig", "pgnigobr", "orlenpgnig"), listOf("pgnig.pl")),
    FirmaLogo(listOf("tauron"), listOf("tauron.pl")),
    FirmaLogo(listOf("pge", "gkpge"), listOf("gkpge.pl")),
    FirmaLogo(listOf("enea"), listOf("enea.pl")),
    FirmaLogo(listOf("innogy", "eon", "eonpl"), listOf("eon.pl")),
    FirmaLogo(listOf("veolia"), listOf("veolia.pl")),

    // Kurierzy
    FirmaLogo(listOf("dpdpolska", "dpdpl", "dpd"), listOf("dpd.pl", "dpd.com")),
    FirmaLogo(listOf("inpost", "paczkomat", "paczkomaty"), listOf("inpost.pl")),
    FirmaLogo(listOf("dhl", "dhlpl", "dhlexpress"), listOf("dhl.com")),
    FirmaLogo(listOf("ups", "upspl"), listOf("ups.com")),
    FirmaLogo(listOf("gls", "glspl"), listOf("gls-group.com")),
    FirmaLogo(listOf("poczta", "pocztapolska", "ppocztapl", "envelo"), listOf("poczta-polska.pl")),
    FirmaLogo(listOf("fedex"), listOf("fedex.com")),
    FirmaLogo(listOf("ambro", "ambroexpress"), listOf("ambroexpress.pl")),
    FirmaLogo(listOf("geis"), listOf("geis.pl")),
    FirmaLogo(listOf("raben"), listOf("raben-group.com")),

    // Finanse / BNPL / brokerzy / banki
    FirmaLogo(listOf("xtb", "xtbpl", "xtradebrokers"), listOf("xtb.com", "xtb.pl")),
    FirmaLogo(listOf("etoro"), listOf("etoro.com")),
    FirmaLogo(listOf("trading212"), listOf("trading212.com")),
    FirmaLogo(listOf("binance"), listOf("binance.com")),
    FirmaLogo(listOf("zondacrypto", "zonda", "bitbay"), listOf("zondacrypto.com")),
    // paypo.com to INNA marka — tylko paypo.pl + lokalne logo z ich assets.
    FirmaLogo(listOf("paypo"), listOf("paypo.pl"), assetLogo = "paypo.png"),
    FirmaLogo(listOf("klarna"), listOf("klarna.com")),
    FirmaLogo(listOf("paypal"), listOf("paypal.com")),
    FirmaLogo(listOf("blik"), listOf("blik.com")),
    FirmaLogo(listOf("przelewy24", "p24"), listOf("przelewy24.pl")),
    FirmaLogo(listOf("payu"), listOf("payu.com")),
    FirmaLogo(listOf("tpay"), listOf("tpay.com")),
    FirmaLogo(listOf("autopay", "bluemedia"), listOf("autopay.pl")),
    FirmaLogo(listOf("revolut"), listOf("revolut.com")),
    FirmaLogo(listOf("wise", "transferwise"), listOf("wise.com")),
    FirmaLogo(listOf("mbank"), listOf("mbank.pl")),
    FirmaLogo(listOf("pkobp", "pkobank", "ipko", "inteligo"), listOf("pkobp.pl")),
    FirmaLogo(listOf("pekao", "pekaosa"), listOf("pekao.pl")),
    FirmaLogo(listOf("ing", "ingbank"), listOf("ing.pl")),
    FirmaLogo(listOf("santander", "bzwbk", "sanpl", "san"), listOf("santander.pl")),
    FirmaLogo(listOf("santanderconsumer", "scb"), listOf("santanderconsumer.pl", "santander.pl")),
    FirmaLogo(listOf("alior", "aliorbank"), listOf("aliorbank.pl")),
    FirmaLogo(listOf("millennium", "bankmillennium"), listOf("bankmillennium.pl")),
    FirmaLogo(listOf("citi", "citibank", "citibankhandlowy"), listOf("citibank.pl")),
    FirmaLogo(listOf("creditagricole", "cabank"), listOf("credit-agricole.pl")),
    FirmaLogo(listOf("bnparibas", "bnpparibas"), listOf("bnpparibas.pl")),
    FirmaLogo(listOf("velobank"), listOf("velobank.pl")),
    FirmaLogo(listOf("nestbank"), listOf("nestbank.pl")),
    FirmaLogo(listOf("bosbank", "bos"), listOf("bosbank.pl")),
    FirmaLogo(listOf("erste", "erstepl", "erstebank"), listOf("erstegroup.com", "erste.pl")),
    FirmaLogo(listOf("pzu"), listOf("pzu.pl")),
    FirmaLogo(listOf("warta"), listOf("warta.pl")),
    FirmaLogo(listOf("allianz"), listOf("allianz.pl")),
    FirmaLogo(listOf("ergohestia", "ergo"), listOf("ergohestia.pl")),
    // L4 Online (e-zwolnienia) ≠ Link4 (ubezpieczenia).
    FirmaLogo(
        listOf("l4online", "l4onlin", "l4"),
        listOf("l4.pl", "l4online.pl"),
        assetLogo = "l4.png"
    ),
    FirmaLogo(listOf("link4"), listOf("link4.pl")),
    FirmaLogo(listOf("uniqa"), listOf("uniqa.pl")),
    FirmaLogo(listOf("compensa"), listOf("compensa.pl")),
    FirmaLogo(listOf("generali"), listOf("generali.pl")),
    FirmaLogo(listOf("wonga"), listOf("wonga.pl")),
    FirmaLogo(listOf("provident"), listOf("provident.pl")),
    FirmaLogo(listOf("vivus"), listOf("vivus.pl")),
    FirmaLogo(listOf("manpower", "manpowergroup"), listOf("manpower.pl", "manpowergroup.com")),

    // Telekom / ISP
    FirmaLogo(listOf("play", "playpl", "playnow"), listOf("play.pl")),
    FirmaLogo(listOf("orange", "orangepl"), listOf("orange.pl")),
    FirmaLogo(listOf("tmobile", "tmobilepl"), listOf("t-mobile.pl")),
    FirmaLogo(listOf("pluspl", "pluss", "plus"), listOf("plus.pl")),
    FirmaLogo(listOf("njumobile", "nju"), listOf("njumobile.pl")),
    FirmaLogo(listOf("virginmobile", "virgin"), listOf("virginmobile.pl")),
    FirmaLogo(listOf("upc", "upcpl"), listOf("upc.pl")),
    FirmaLogo(listOf("vectra"), listOf("vectra.pl")),
    FirmaLogo(listOf("netia", "netiasa", "grupanetia"), listOf("netia.pl")),
    FirmaLogo(listOf("multimedia"), listOf("multimedia.pl")),
    FirmaLogo(listOf("polsatbox", "polsat", "cyfrowypolsat"), listOf("polsatbox.pl")),
    FirmaLogo(listOf("canalplus", "ncplus"), listOf("canalplus.com")),

    // Zdrowie
    FirmaLogo(listOf("luxmed", "luxmedpl"), listOf("luxmed.pl")),
    FirmaLogo(listOf("medicover"), listOf("medicover.pl")),
    FirmaLogo(listOf("enelmed", "enel"), listOf("enel.pl")),
    FirmaLogo(listOf("cmp", "centrummedyczne"), listOf("cmp.pl")),
    FirmaLogo(listOf("voxel"), listOf("voxel.pl")),
    FirmaLogo(listOf("doz", "dozpl"), listOf("doz.pl")),
    FirmaLogo(listOf("gemini", "geminapteka"), listOf("gemini.pl")),

    // Transport
    FirmaLogo(listOf("pkpintercity", "pkp", "intercity"), listOf("intercity.pl")),
    FirmaLogo(listOf("flixbus"), listOf("flixbus.pl")),
    FirmaLogo(listOf("koleo"), listOf("koleo.pl")),
    FirmaLogo(listOf("jakdojade"), listOf("jakdojade.pl")),
    FirmaLogo(listOf("ztm", "wtp"), listOf("wtp.waw.pl")),
    FirmaLogo(listOf("lot", "lotpl"), listOf("lot.com")),
    FirmaLogo(listOf("ryanair"), listOf("ryanair.com")),
    FirmaLogo(listOf("wizzair", "wizz"), listOf("wizzair.com")),
    FirmaLogo(listOf("booking", "bookingcom"), listOf("booking.com")),
    FirmaLogo(listOf("airbnb"), listOf("airbnb.com")),

    // Urzędy / systemowe
    FirmaLogo(listOf("alertrcb", "rcb", "rcbalert"), listOf("gov.pl")),
    FirmaLogo(listOf("eurzad", "eurzadskarbowy"), listOf("podatki.gov.pl")),
    FirmaLogo(listOf("mobywatel", "govpl"), listOf("gov.pl")),
    FirmaLogo(listOf("ktotam", "whoscall"), listOf("ktotam.app", "whoscall.com")),
    FirmaLogo(listOf("epuap"), listOf("epuap.gov.pl")),
    FirmaLogo(listOf("nfz"), listOf("nfz.gov.pl")),
    FirmaLogo(listOf("zus"), listOf("zus.pl")),
    FirmaLogo(listOf("krus"), listOf("krus.gov.pl")),
    FirmaLogo(listOf("pip"), listOf("pip.gov.pl")),

    // Inne częste z bramek
    FirmaLogo(listOf("unilever", "marketul"), listOf("unilever.com")),
    FirmaLogo(listOf("cdprojekt", "cdpr"), listOf("cdprojekt.com")),
    FirmaLogo(listOf("player", "playerpl"), listOf("player.pl")),
    FirmaLogo(listOf("tvn", "tvn24"), listOf("tvn24.pl")),
    FirmaLogo(listOf("wp", "wirtualnapolska"), listOf("wp.pl")),
    FirmaLogo(listOf("onet"), listOf("onet.pl")),
    FirmaLogo(listOf("interia"), listOf("interia.pl")),
    FirmaLogo(listOf("gazeta"), listOf("gazeta.pl")),
    FirmaLogo(listOf("multikino"), listOf("multikino.pl")),
    FirmaLogo(listOf("cinema", "cinemacity"), listOf("cinema-city.pl")),
    FirmaLogo(listOf("helios"), listOf("helios.pl"))
)

/** Klucz: małe litery, tylko a–z / 0–9 (bez PL diakrytyków — fold przez normalizujPolski). */
internal fun kluczFirmy(nazwa: String): String =
    normalizujPolski(nazwa).lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")

/**
 * Warianty klucza po obcięciu typowych sufiksów bramki/kraju.
 * „XTB PL” → xtbpl, xtb; „DPD Polska” → dpdpolska, dpd.
 */
internal fun wariantyKluczaFirmy(nazwa: String): List<String> {
    val k = kluczFirmy(nazwa)
    if (k.isEmpty()) return emptyList()
    val out = LinkedHashSet<String>()
    out.add(k)
    for (suf in SUFIKSY_NADAWCY) {
        if (k.endsWith(suf) && k.length - suf.length >= 3) {
            out.add(k.removeSuffix(suf))
        }
    }
    return out.toList()
}

/**
 * Dopasowuje nadawcę do marki: najdłuższy pasujący alias wygrywa
 * (np. „XTB PL” → xtb, „DPD Polska” → dpdpolska / dpd).
 */
internal fun dopasujFirme(nazwa: String): FirmaLogo? {
    val warianty = wariantyKluczaFirmy(nazwa)
    if (warianty.isEmpty()) return null

    var najlepszy: FirmaLogo? = null
    var najlepszyAlias = 0

    for (wariant in warianty) {
        for (firma in FIRMY_LOGO) {
            for (alias in firma.aliasy) {
                if (!pasujeAlias(wariant, alias)) continue
                if (alias.length > najlepszyAlias) {
                    najlepszyAlias = alias.length
                    najlepszy = firma
                }
            }
        }
    }
    return najlepszy
}

private fun pasujeAlias(klucz: String, alias: String): Boolean {
    if (alias.isEmpty()) return false
    if (klucz == alias) return true
    // Krótkie (ing, dpd, olx, xtb, pzu, san, l4…): TYLKO exact — inaczej „ing” ∈ „booking”.
    if (alias.length <= 3) return false
    // 4 znaki: prefix/suffix (np. „play”→„playpl”, „lidl”→„winnicalidl”).
    if (alias.length == 4) return klucz.startsWith(alias) || klucz.endsWith(alias)
    // ≥5: prefix / suffix / contains („myorlen”↔„orlen”).
    return klucz.startsWith(alias) || klucz.endsWith(alias) || klucz.contains(alias)
}

/**
 * Zwraca listę domen-kandydatów (znana marka lub zgadywanie .pl / .com po wariantach).
 */
internal fun domenyFirmy(nazwa: String): List<String> {
    val firma = dopasujFirme(nazwa)
    if (firma != null) return firma.domeny

    val warianty = wariantyKluczaFirmy(nazwa)
    if (warianty.isEmpty()) return emptyList()
    // Ogólniki (LEKARZ, INFO…) — bez zgadywania domeny.
    if (warianty.any { it in GENERICZNE_NADAWCY }) return emptyList()
    // Nieznana marka: najpierw krótsze rdzenie (.pl/.com), potem pełny klucz.
    val out = LinkedHashSet<String>()
    for (w in warianty.sortedBy { it.length }) {
        out.add("$w.pl")
        out.add("$w.com")
    }
    return out.toList()
}

/** @deprecated użyj [domenyFirmy] — skrót do pierwszej domeny. */
internal fun domenaFirmy(nazwa: String): String =
    domenyFirmy(nazwa).firstOrNull() ?: "placeholder.com"

/**
 * Pobiera logo firmy (alfa-nadawcy), cache w filesDir/[KATALOG_LOGOW].
 * Źródła (jak get-brand-logos): Google Favicon → DuckDuckGo icons.
 */
internal fun pobierzLogoFirmy(context: Context, nazwa: String): Bitmap? {
    if (nazwa.isBlank()) return null
    val klucz = kluczFirmy(nazwa)
    if (klucz.isBlank()) return null

    val katalog = java.io.File(context.filesDir, KATALOG_LOGOW)
    if (!katalog.exists()) katalog.mkdirs()
    // Sprzątanie starego cache z błędnymi mapowaniami (PayPo.com, zamazana Biedronka…).
    for (stary in listOf("loga_firm", "loga_firm_v2", "loga_firm_v3", "loga_firm_v4")) {
        val dir = java.io.File(context.filesDir, stary)
        if (dir.exists()) dir.deleteRecursively()
    }

    // 1) Bundlowany asset (ostre, poprawne marki).
    val firma = dopasujFirme(nazwa)
    firma?.assetLogo?.let { asset ->
        odczytajAssetLogo(context, asset)?.let { return it }
    }

    // Ogólniki bez mapy — od razu null (bez cache śmieci).
    if (firma == null && wariantyKluczaFirmy(nazwa).any { it in GENERICZNE_NADAWCY }) {
        return null
    }

    val cacheHit = java.io.File(katalog, "$klucz.png")
    if (cacheHit.exists() && cacheHit.length() >= MIN_BAJTOW_LOGA) {
        BitmapFactory.decodeFile(cacheHit.absolutePath)?.let { bmp ->
            // Odrzuć stare zamazane favicony (< 32px) — wymuś ponowne pobranie.
            if (bmp.width >= 32 && bmp.height >= 32) return bmp
            bmp.recycle()
            cacheHit.delete()
        }
    }

    val domeny = domenyFirmy(nazwa)
    for (domena in domeny) {
        val bajty = pobierzBajtyLoga(domena) ?: continue
        if (bajty.size < MIN_BAJTOW_LOGA) continue
        val bmp = BitmapFactory.decodeByteArray(bajty, 0, bajty.size) ?: continue
        if (bmp.width < 32 || bmp.height < 32) {
            bmp.recycle()
            continue
        }
        try {
            cacheHit.writeBytes(bajty)
        } catch (_: Exception) { /* cache best-effort */ }
        android.util.Log.i(
            "LogoFirmy",
            "OK nazwa=$nazwa klucz=$klucz warianty=${wariantyKluczaFirmy(nazwa)} domena=$domena B=${bajty.size} ${bmp.width}x${bmp.height}"
        )
        return bmp
    }

    android.util.Log.w("LogoFirmy", "brak logo dla nazwa=$nazwa klucz=$klucz domeny=$domeny")
    return null
}

private fun odczytajAssetLogo(context: Context, nazwaPliku: String): Bitmap? {
    return try {
        context.assets.open("loga/$nazwaPliku").use { strumien ->
            BitmapFactory.decodeStream(strumien)
        }?.takeIf { it.width >= 24 && it.height >= 24 }
    } catch (e: Exception) {
        android.util.Log.w("LogoFirmy", "asset loga/$nazwaPliku: ${e.message}")
        null
    }
}

/** Google s2 favicons (128px) → DuckDuckGo ip3. */
private fun pobierzBajtyLoga(domena: String): ByteArray? {
    val urle = listOf(
        "https://www.google.com/s2/favicons?sz=128&domain=$domena",
        "https://icons.duckduckgo.com/ip3/$domena.ico"
    )
    for (adres in urle) {
        try {
            val conn = (URL(adres).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) TelefonDialer/1.0")
            }
            val kod = conn.responseCode
            if (kod !in 200..299) {
                conn.disconnect()
                continue
            }
            val bajty = conn.inputStream.use { it.readBytes() }
            conn.disconnect()
            if (bajty.size >= MIN_BAJTOW_LOGA) {
                android.util.Log.i("LogoFirmy", "pobrano $domena z $adres (${bajty.size} B)")
                return bajty
            }
        } catch (e: Exception) {
            android.util.Log.w("LogoFirmy", "fail $adres: ${e.message}")
        }
    }
    return null
}
