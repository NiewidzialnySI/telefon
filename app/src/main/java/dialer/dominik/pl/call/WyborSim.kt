package dialer.dominik.pl.call

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log

/**
 * Wybór karty SIM / [PhoneAccount] przy wychodzących `placeCall`.
 * Preferencja w SharedPreferences; brak wyboru = systemowy default.
 */
object WyborSim {
    private const val TAG = "WyborSim"
    private const val PREFS = "telefon_sim"
    private const val KLUCZ_COMPONENT = "account_component"
    private const val KLUCZ_ID = "account_id"

    data class KontoSim(
        val handle: PhoneAccountHandle,
        val etykieta: String
    )

    fun dostepneKonta(context: Context): List<KontoSim> {
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return emptyList()
        return try {
            telecom.callCapablePhoneAccounts
                .mapNotNull { handle ->
                    val account = telecom.getPhoneAccount(handle) ?: return@mapNotNull null
                    if (!account.hasCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER) &&
                        !account.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
                    ) {
                        // i tak pokaż call-capable z listy
                    }
                    val etykieta = account.label?.toString()?.takeIf { it.isNotBlank() }
                        ?: handle.id
                        ?: "SIM"
                    KontoSim(handle, etykieta)
                }
        } catch (e: SecurityException) {
            Log.w(TAG, "Brak uprawnień do listy PhoneAccount", e)
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Błąd listy PhoneAccount", e)
            emptyList()
        }
    }

    fun wybranyHandle(context: Context): PhoneAccountHandle? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val komponent = prefs.getString(KLUCZ_COMPONENT, null) ?: return null
        val id = prefs.getString(KLUCZ_ID, null) ?: return null
        return try {
            val cn = ComponentName.unflattenFromString(komponent) ?: return null
            PhoneAccountHandle(cn, id)
        } catch (e: Exception) {
            null
        }
    }

    fun zapiszWybor(context: Context, handle: PhoneAccountHandle?) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (handle == null) {
            prefs.remove(KLUCZ_COMPONENT).remove(KLUCZ_ID)
        } else {
            prefs.putString(KLUCZ_COMPONENT, handle.componentName.flattenToString())
            prefs.putString(KLUCZ_ID, handle.id)
        }
        prefs.apply()
    }

    /** Bundle do [TelecomManager.placeCall] z opcjonalnym PhoneAccount. */
    fun extrasDoPlaceCall(context: Context): Bundle {
        val extras = Bundle()
        val handle = wybranyHandle(context) ?: return extras
        // Sprawdź, czy konto nadal istnieje
        val nadal = dostepneKonta(context).any { it.handle == handle }
        if (!nadal) {
            zapiszWybor(context, null)
            return extras
        }
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
        return extras
    }

    fun etykietaWyboru(context: Context): String {
        val handle = wybranyHandle(context) ?: return "Automatyczny (system)"
        return dostepneKonta(context).find { it.handle == handle }?.etykieta
            ?: "Automatyczny (system)"
    }

    /** Przełącza: null → SIM1 → SIM2 → … → null. */
    fun przelaczNastepny(context: Context): String {
        val konta = dostepneKonta(context)
        if (konta.isEmpty()) {
            zapiszWybor(context, null)
            return "Brak kart SIM / kont"
        }
        val obecny = wybranyHandle(context)
        val idx = konta.indexOfFirst { it.handle == obecny }
        val nastepny = when {
            idx < 0 -> konta.first()
            idx >= konta.lastIndex -> null
            else -> konta[idx + 1]
        }
        zapiszWybor(context, nastepny?.handle)
        return nastepny?.etykieta ?: "Automatyczny (system)"
    }
}
