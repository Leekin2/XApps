package io.github.leekin2.xgoogle;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Map;
import java.util.zip.CRC32;

/** Adapts one verified model without editing the input or persisted data. */
public final class SignalModelAdapter {
    private static byte[] cachedOriginal;
    private static byte[] cachedAdapted;
    private static final String HASH = "8554a20caff43eb07c93b1162a1c06ee2a9152ef5e86dc39ebd15450e59c71f5";
    private static final Map<String, String> NAMES = Map.of(
        "Framer", "SignalFramer", "Window", "SignalWindow",
        "FftAutoScale", "SignalFftAutoScale", "Rfft", "SignalRfft",
        "Energy", "SignalEnergy", "FilterBank", "SignalFilterBank",
        "FilterBankSquareRoot", "SignalFilterBankSquareRoot",
        "FilterBankSpectralSubtraction", "SignalFilterBankSpectralSubtraction",
        "Stacker", "SignalStacker", "Pcan", "SignalPCAN");

    private static int field(ByteBuffer b, int table, int index) {
        int vt = table - b.getInt(table);
        if (4 + index * 2 >= Short.toUnsignedInt(b.getShort(vt))) return 0;
        int offset = Short.toUnsignedInt(b.getShort(vt + 4 + index * 2));
        return offset == 0 ? 0 : table + offset;
    }

    public static synchronized byte[] adapt(byte[] original) throws Exception {
        // Compare the entire content: length or object identity cannot validate a model.
        if (cachedOriginal != null && Arrays.equals(cachedOriginal, original)) {
            return cachedAdapted.clone();
        }
        original = original.clone();
        StringBuilder hash = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(original)) {
            hash.append(Character.forDigit((value & 255) >>> 4, 16));
            hash.append(Character.forDigit(value & 15, 16));
        }
        if (!HASH.contentEquals(hash)) throw new IllegalArgumentException("Unrecognized model");
        ByteBuffer input = ByteBuffer.wrap(original).order(ByteOrder.LITTLE_ENDIAN);
        byte[] copy = Arrays.copyOf(original, original.length + 1024);
        ByteBuffer output = ByteBuffer.wrap(copy).order(ByteOrder.LITTLE_ENDIAN);
        int slot = field(input, input.getInt(0), 1);
        int vector = slot + input.getInt(slot);
        int end = original.length;
        int changed = 0;
        for (int i = 0; i < input.getInt(vector); i++) {
            int entry = vector + 4 + i * 4;
            slot = field(input, entry + input.getInt(entry), 1);
            if (slot == 0) continue;
            int string = slot + input.getInt(slot);
            String name = new String(original, string + 4, input.getInt(string), StandardCharsets.US_ASCII);
            String replacement = NAMES.get(name);
            if (replacement == null) continue;
            byte[] text = replacement.getBytes(StandardCharsets.US_ASCII);
            end = (end + 3) & ~3;
            output.putInt(slot, end - slot);
            output.putInt(end, text.length);
            System.arraycopy(text, 0, copy, end + 4, text.length);
            end += 4 + text.length + 1;
            changed++;
        }
        if (changed != 10) throw new IllegalStateException("Unexpected operator count");
        // The DSP checks the final little-endian CRC32 before parsing TFLite.
        // The old trailer is now unreferenced padding inside the FlatBuffer.
        int payloadEnd = (end + 3) & ~3;
        CRC32 crc = new CRC32();
        crc.update(copy, 0, payloadEnd);
        output.putInt(payloadEnd, (int) crc.getValue());
        cachedAdapted = Arrays.copyOf(copy, payloadEnd + 4);
        cachedOriginal = original;
        return cachedAdapted.clone();
    }
}
