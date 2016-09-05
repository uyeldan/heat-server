/*
 * The MIT License (MIT)
 * Copyright (c) 2016 Heat Ledger Ltd.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * */
package heat.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import heat.Constants;
import heat.HeatException;
import heat.crypto.Crypto;

public final class Convert {

    private static final char[] hexChars = { '0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f' };
    private static final long[] multipliers = {1, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000};

    public static final BigInteger two64 = new BigInteger("18446744073709551616");
    public static final long[] EMPTY_LONG = new long[0];
    public static final byte[] EMPTY_BYTE = new byte[0];
    public static final byte[][] EMPTY_BYTES = new byte[0][];
    public static final String[] EMPTY_STRING = new String[0];

    public static byte[] parseHexString(String hex) {
        if (hex == null) {
            return null;
        }
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int char1 = hex.charAt(i * 2);
            char1 = char1 > 0x60 ? char1 - 0x57 : char1 - 0x30;
            int char2 = hex.charAt(i * 2 + 1);
            char2 = char2 > 0x60 ? char2 - 0x57 : char2 - 0x30;
            if (char1 < 0 || char2 < 0 || char1 > 15 || char2 > 15) {
                throw new NumberFormatException("Invalid hex number: " + hex);
            }
            bytes[i] = (byte)((char1 << 4) + char2);
        }
        return bytes;
    }

    public static String toHexString(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        char[] chars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            chars[i * 2] = hexChars[((bytes[i] >> 4) & 0xF)];
            chars[i * 2 + 1] = hexChars[(bytes[i] & 0xF)];
        }
        return String.valueOf(chars);
    }

    public static boolean isHex(String str) {
        try {
            int val = Integer.parseInt(str,16);
        }
        catch (Exception e) {
            return false;
        }
        return true;
    }
    
    public static long parseUnsignedLong(String number) {
        if (number == null) {
            return 0;
        }
        return Long.parseUnsignedLong(number);
    }

    public static long parseLong(Object o) {
        if (o == null) {
            return 0;
        } else if (o instanceof Long) {
            return ((Long)o);
        } else if (o instanceof String) {
            return Long.parseLong((String)o);
        } else {
            throw new IllegalArgumentException("Not a long: " + o);
        }
    }

    public static long parseAccountId(String account) {
        System.out.println("parseAccountId account="+account);
        if (account == null || (account = account.trim()).isEmpty()) {
            return 0;
        }
        account = account.toUpperCase();
        if (account.startsWith("HEAT-")) {
            return Crypto.rsDecode(account.substring(5));
        } else {
            return Long.parseUnsignedLong(account);
        }
    }

    public static String rsAccount(long accountId) {
        return "HEAT-" + Crypto.rsEncode(accountId);
    }

    public static long fullHashToId(byte[] hash) {
        if (hash == null || hash.length < 8) {
            throw new IllegalArgumentException("Invalid hash: " + Arrays.toString(hash));
        }
        BigInteger bigInteger = new BigInteger(1, new byte[] {hash[7], hash[6], hash[5], hash[4], hash[3], hash[2], hash[1], hash[0]});
        return bigInteger.longValue();
    }

    public static long fromEpochTime(int epochTime) {
        return epochTime * 1000L + Constants.EPOCH_BEGINNING - 500L;
    }

    public static int toEpochTime(long currentTime) {
        return (int)((currentTime - Constants.EPOCH_BEGINNING + 500) / 1000);
    }

    public static String emptyToNull(String s) {
        return s == null || s.length() == 0 ? null : s;
    }

    public static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    public static byte[] emptyToNull(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        for (byte b : bytes) {
            if (b != 0) {
                return bytes;
            }
        }
        return null;
    }

    public static byte[][] nullToEmpty(byte[][] bytes) {
        return bytes == null ? EMPTY_BYTES : bytes;
    }

    public static long[] nullToEmpty(long[] array) {
        return array == null ? EMPTY_LONG : array;
    }

    public static long[] toArray(List<Long> list) {
        long[] result = new long[list.size()];
        for (int i = 0; i < list.size(); i++) {
            result[i] = list.get(i);
        }
        return result;
    }

    public static List<Long> toList(long[] array) {
        List<Long> result = new ArrayList<>(array.length);
        for (long elem : array) {
            result.add(elem);
        }
        return result;
    }

    public static byte[] toBytes(String s) {
        try {
            return s.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public static String toString(byte[] bytes) {
        try {
            return new String(bytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public static String readString(ByteBuffer buffer, int numBytes, int maxLength) throws HeatException.NotValidException {
        if (numBytes > 3 * maxLength) {
            throw new HeatException.NotValidException("Max parameter length exceeded");
        }
        byte[] bytes = new byte[numBytes];
        buffer.get(bytes);
        return Convert.toString(bytes);
    }

    public static String truncate(String s, String replaceNull, int limit, boolean dots) {
        return s == null ? replaceNull : s.length() > limit ? (s.substring(0, dots ? limit - 3 : limit) + (dots ? "..." : "")) : s;
    }

    public static long parseNXT(String nxt) {
        return parseStringFraction(nxt, 8, Constants.MAX_BALANCE_NXT);
    }

    private static long parseStringFraction(String value, int decimals, long maxValue) {
        String[] s = value.trim().split("\\.");
        if (s.length == 0 || s.length > 2) {
            throw new NumberFormatException("Invalid number: " + value);
        }
        long wholePart = Long.parseLong(s[0]);
        if (wholePart > maxValue) {
            throw new IllegalArgumentException("Whole part of value exceeds maximum possible");
        }
        if (s.length == 1) {
            return wholePart * multipliers[decimals];
        }
        long fractionalPart = Long.parseLong(s[1]);
        if (fractionalPart >= multipliers[decimals] || s[1].length() > decimals) {
            throw new IllegalArgumentException("Fractional part exceeds maximum allowed divisibility");
        }
        for (int i = s[1].length(); i < decimals; i++) {
            fractionalPart *= 10;
        }
        return wholePart * multipliers[decimals] + fractionalPart;
    }

    public static byte[] compress(byte[] bytes) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
            gzip.write(bytes);
            gzip.flush();
            gzip.close();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public static byte[] uncompress(byte[] bytes) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             GZIPInputStream gzip = new GZIPInputStream(bis);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int nRead;
            while ((nRead = gzip.read(buffer, 0, buffer.length)) > 0) {
                bos.write(buffer, 0, nRead);
            }
            bos.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}