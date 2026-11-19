package com.corpserun.ghost;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Reads the length of an Ogg Vorbis file: the last page's granule position (samples) over the sample rate from the
 * Vorbis identification header. Enough for timing gameplay to a sound, no decoding needed.
 */
final class OggDuration {

    private static final byte[] VORBIS_ID = "\u0001vorbis".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] OGG_PAGE = "OggS".getBytes(StandardCharsets.ISO_8859_1);

    private OggDuration() {
    }

    static double seconds(InputStream in) throws IOException {
        byte[] data = in.readAllBytes();
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        int id = indexOf(data, VORBIS_ID, 0);
        if (id < 0) {
            throw new IOException("not an Ogg Vorbis file");
        }
        // After "\1vorbis": version (4 bytes), channels (1), sample rate (4).
        int sampleRate = buf.getInt(id + VORBIS_ID.length + 5);

        long granule = -1;
        for (int page = indexOf(data, OGG_PAGE, 0); page >= 0; page = indexOf(data, OGG_PAGE, page + 1)) {
            if (page + 14 <= data.length) {
                long g = buf.getLong(page + 6);
                if (g >= 0) {
                    granule = g;
                }
            }
        }
        if (granule < 0 || sampleRate <= 0) {
            throw new IOException("no length information");
        }
        return granule / (double) sampleRate;
    }

    private static int indexOf(byte[] data, byte[] pattern, int from) {
        outer:
        for (int i = from; i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
