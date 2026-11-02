package dialer.dominik.pl.nagranie;

import android.os.ParcelFileDescriptor;

/**
 * Most IPC: dialer ↔ daemon shell (UID 2000) z Wireless ADB.
 * Wersja 3: + grantPermission (pm grant z UID shell).
 */
interface INagrywanieShell {

    int getWersja() = 1;

    /**
     * Dwa AudioRecord (np. UPLINK + DOWNLINK).
     * @return maska: bit0=uplink OK, bit1=downlink OK
     */
    int startDual(
        int zrodloUplink,
        int zrodloDownlink,
        int sampleRate,
        in ParcelFileDescriptor fdUplink,
        in ParcelFileDescriptor fdDownlink
    ) = 2;

    /** Jedno źródło VOICE_* / MIC / VOICE_COMMUNICATION. */
    int startSingle(
        int zrodlo,
        int sampleRate,
        int channelMask,
        in ParcelFileDescriptor fdPcm
    ) = 3;

    /**
     * Ukryty AudioPolicy loopback: USAGE_VOICE_COMMUNICATION (+ MEDIA).
     * Bez dialogu MediaProjection — wymaga CAPTURE_VOICE_COMMUNICATION_OUTPUT (shell ma od API 34).
     * @return 1 = OK, 0 = fail
     */
    int startPlayback(
        int sampleRate,
        int channelMask,
        in ParcelFileDescriptor fdPcm
    ) = 4;

    void stop() = 10;

    int getStan() = 11;

    @nullable
    String getOstatniBlad() = 12;

    /** `pm grant` z UID shell — WRITE_SECURE_SETTINGS i runtime. */
    boolean grantPermission(String packageName, String permission) = 13;

    void destroy() = 16777114;
}
