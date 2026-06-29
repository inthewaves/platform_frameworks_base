package app.grapheneos.goscompat.securespawn.shared;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class SecureSpawnCmdlineCheck {
    private static final String CMDLINE_PATH = "/proc/self/cmdline";
    private static final int PACKAGE_NAME_BUFFER_SIZE = 256;

    private SecureSpawnCmdlineCheck() {
    }

    public static CmdlinePackageNameRead run(boolean execSpawned) throws IOException {
        byte[] cmdline = readCmdline();
        byte[] packageName = new byte[PACKAGE_NAME_BUFFER_SIZE];
        int index = 0;
        int maxIndex = 0;
        int bytesReadByParser = 0;
        int resetCount = 0;
        boolean wouldOverflow = false;
        boolean stoppedEarly = false;

        // Mirrors a fixed-size package-name parser without writing past the Java array.
        for (byte rawByte : cmdline) {
            int ch = rawByte & 0xff;
            bytesReadByParser++;
            if (ch == ':' || isAsciiSpace(ch)) {
                stoppedEarly = true;
                break;
            }

            if (ch == '\\' || ch == '/') {
                Arrays.fill(packageName, (byte) 0);
                index = 0;
                resetCount++;
            } else {
                if (index < packageName.length) {
                    packageName[index] = rawByte;
                } else {
                    wouldOverflow = true;
                }
                index++;
                maxIndex = Math.max(maxIndex, index);
            }
        }

        int packageNameLength = firstNulOffsetOrLength(packageName);
        String parsedPackageName = new String(packageName, 0, packageNameLength,
                StandardCharsets.UTF_8);
        int firstNulOffset = firstNulOffsetOrMinusOne(cmdline);
        int bytesAfterFirstNul = firstNulOffset < 0 ? 0 : cmdline.length - firstNulOffset - 1;
        int nonZeroBytesAfterFirstNul = countNonZeroBytesAfter(cmdline, firstNulOffset);

        return new CmdlinePackageNameRead(
                execSpawned,
                cmdline.length,
                firstNulOffset,
                bytesAfterFirstNul,
                nonZeroBytesAfterFirstNul,
                bytesReadByParser,
                index,
                maxIndex,
                resetCount,
                stoppedEarly,
                wouldOverflow,
                parsedPackageName,
                escapedCmdline(cmdline));
    }

    private static byte[] readCmdline() throws IOException {
        try (FileInputStream input = new FileInputStream(CMDLINE_PATH);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[128];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
            return output.toByteArray();
        }
    }

    private static boolean isAsciiSpace(int ch) {
        return ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r'
                || ch == '\f' || ch == 0x0b;
    }

    private static int firstNulOffsetOrLength(byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] == 0) {
                return i;
            }
        }
        return bytes.length;
    }

    private static int firstNulOffsetOrMinusOne(byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] == 0) {
                return i;
            }
        }
        return -1;
    }

    private static int countNonZeroBytesAfter(byte[] bytes, int offset) {
        if (offset < 0 || offset >= bytes.length - 1) {
            return 0;
        }

        int count = 0;
        for (int i = offset + 1; i < bytes.length; i++) {
            if (bytes[i] != 0) {
                count++;
            }
        }
        return count;
    }

    private static String escapedCmdline(byte[] cmdline) {
        StringBuilder result = new StringBuilder(cmdline.length);
        for (byte rawByte : cmdline) {
            int ch = rawByte & 0xff;
            if (ch == 0) {
                result.append("\\0");
            } else if (ch >= 0x20 && ch <= 0x7e) {
                result.append((char) ch);
            } else {
                result.append("\\x");
                if (ch < 0x10) {
                    result.append('0');
                }
                result.append(Integer.toHexString(ch));
            }
        }
        return result.toString();
    }

    public record CmdlinePackageNameRead(
            boolean execSpawned,
            int rawByteCount,
            int firstNulOffset,
            int bytesAfterFirstNul,
            int nonZeroBytesAfterFirstNul,
            int bytesReadByParser,
            int finalIndex,
            int maxIndex,
            int resetCount,
            boolean stoppedEarly,
            boolean wouldOverflow,
            String parsedPackageName,
            String escapedCmdline) {
        @Override
        public String toString() {
            return "execSpawned=" + execSpawned()
                    + "\nrawByteCount=" + rawByteCount()
                    + "\nfirstNulOffset=" + firstNulOffset()
                    + "\nbytesAfterFirstNul=" + bytesAfterFirstNul()
                    + "\nnonZeroBytesAfterFirstNul=" + nonZeroBytesAfterFirstNul()
                    + "\nbytesReadByParser=" + bytesReadByParser()
                    + "\nfinalIndex=" + finalIndex()
                    + "\nmaxIndex=" + maxIndex()
                    + "\nresetCount=" + resetCount()
                    + "\nstoppedEarly=" + stoppedEarly()
                    + "\nwouldOverflow=" + wouldOverflow()
                    + "\nparsedPackageName=" + parsedPackageName()
                    + "\ncmdline=" + escapedCmdline();
        }
    }
}
