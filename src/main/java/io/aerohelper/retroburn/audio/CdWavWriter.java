// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.audio;

import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.engine.AudioDisc;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Turns raw CD audio PCM (signed 16-bit little-endian, stereo, 44.1 kHz) into a WAV file with the
 * canonical 44-byte header, padded with silence to whole 2352-byte sectors. See {@code
 * docs/design/LLD-audio-cd.md} section 5.3.
 */
public final class CdWavWriter {

    private static final int COPY_BUFFER = 1 << 20;

    private CdWavWriter() {}

    /**
     * Writes {@code wav} from {@code pcm} and deletes {@code pcm}.
     *
     * @return the number of 2352-byte sectors of audio in the WAV file
     */
    public static long fromPcm(Path pcm, Path wav, CancelToken cancel) throws IOException {
        long pcmBytes = Files.size(pcm);
        long sectors = CdAudio.sectorsForBytes(pcmBytes);
        if (sectors == 0) {
            throw new IOException("No audio was decoded into " + pcm.getFileName());
        }
        long dataBytes = sectors * CdAudio.BYTES_PER_SECTOR;
        try (InputStream in = Files.newInputStream(pcm);
                OutputStream out = Files.newOutputStream(wav)) {
            out.write(header(dataBytes));
            byte[] buffer = new byte[COPY_BUFFER];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                cancel.throwIfCancelled();
                out.write(buffer, 0, read);
            }
            out.write(new byte[(int) (dataBytes - pcmBytes)]);
        }
        Files.delete(pcm);
        return sectors;
    }

    /** The 44-byte header of a CD audio WAV file holding {@code dataBytes} bytes of samples. */
    static byte[] header(long dataBytes) {
        if (dataBytes > 0xFFFF_FFFFL - 36) {
            throw new IllegalArgumentException("Too much audio for one WAV file: " + dataBytes);
        }
        ByteBuffer header = ByteBuffer.allocate(AudioDisc.WAV_HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        header.putInt((int) (36 + dataBytes));
        header.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
        header.putInt(16);
        header.putShort((short) 1);
        header.putShort((short) CdAudio.CHANNELS);
        header.putInt(CdAudio.SAMPLE_RATE);
        header.putInt(CdAudio.BYTES_PER_SECOND);
        header.putShort((short) (CdAudio.CHANNELS * CdAudio.BITS_PER_SAMPLE / 8));
        header.putShort((short) CdAudio.BITS_PER_SAMPLE);
        header.put("data".getBytes(StandardCharsets.US_ASCII));
        header.putInt((int) dataBytes);
        return header.array();
    }
}
