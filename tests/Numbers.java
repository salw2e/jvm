import java.math.*;
import java.util.*;

public class Numbers {
    public static void main(String[] args) {
        BigInteger f = BigInteger.ONE;
        for (int i = 2; i <= 40; i++) f = f.multiply(BigInteger.valueOf(i));
        System.out.println(f + " " + f.bitLength() + " " + f.mod(BigInteger.valueOf(1000007)) + " " + BigInteger.TWO.pow(200).toString(16));
        System.out.println(new BigDecimal("1.10").add(new BigDecimal("2.205")).setScale(2, RoundingMode.HALF_UP) + " " + BigDecimal.ONE.divide(new BigDecimal(7), 20, RoundingMode.DOWN) + " " + new BigInteger("123456789012345678901234567890").isProbablePrime(10));
        Random r = new Random(42);
        System.out.println(r.nextInt(100) + " " + r.nextLong() + " " + r.nextDouble() + " " + r.nextBoolean() + " " + r.nextGaussian());
        System.out.println(Integer.MAX_VALUE + 1 + " " + (Long.MIN_VALUE - 1) + " " + Integer.MIN_VALUE / -1 + " " + (5 & 3 | 8 ^ 2) + " " + (~7) + " " + (-17 >> 2) + " " + (-17 >>> 28) + " " + (1L << 63));
        System.out.println(0.1f + 0.2f + " " + (0.1 + 0.2) + " " + 1.0e-5 + " " + 123456789.0 + " " + 1e21 + " " + (float) 1e10 + " " + 100.0 / 3 + " " + Double.MIN_VALUE + " " + Float.MAX_VALUE + " " + -0.0 + " " + 0.0 / 0);
        System.out.println(Math.floor(-1.5) + " " + Math.ceil(-1.5) + " " + Math.rint(2.5) + " " + Math.round(-2.5) + " " + Math.hypot(3, 4) + " " + Math.atan2(1, 1) + " " + Math.exp(1) + " " + Math.log10(1000) + " " + Math.cbrt(27) + " " + Math.toDegrees(Math.PI) + " " + Math.max(-0.0, 0.0) + " " + Math.floorDiv(-7, 2));
        System.out.println(Integer.toString(255, 2) + " " + Integer.toHexString(-1) + " " + Integer.parseInt("zz", 36) + " " + Long.parseLong("-9223372036854775808") + " " + Integer.bitCount(255) + " " + Long.numberOfTrailingZeros(64) + " " + Integer.reverse(1) + " " + Integer.highestOneBit(100) + " " + Character.getNumericValue('7') + " " + (int) 'A' + " " + Integer.compare(3, 5) + " " + Integer.toOctalString(8));
        System.out.println(Double.compare(0.0, -0.0) + " " + Double.isNaN(0.0 / 0) + " " + (float) 0.1 + " " + (double) 0.1f + " " + Float.floatToIntBits(1.5f) + " " + Double.doubleToLongBits(-2.0) + " " + Double.toString(1.0E7) + " " + Double.valueOf("  3.25 ") + " " + (long) 1e19 + " " + (char) 65.7 + " " + (byte) -129 + " " + (short) 1e6 + " " + (int) -0.9f);
        Integer a = 127, b = 127, c = 128, d = 128;
        System.out.println((a == b) + " " + (c == d) + " " + c.equals(d) + " " + Long.valueOf(5).hashCode() + " " + Double.valueOf(1.5).hashCode() + " " + Boolean.hashCode(true) + " " + "".hashCode() + " " + Character.valueOf('x').compareTo('a'));
        System.out.println(String.format("%.3f %10.2e %+d %o %X %c %b %s %5.1f%% %-6sEND %03d %s", Math.PI, 12345.678, 5, 8, 255, 'q', true, null, 99.5, "ab", 7, 1.0f));
        long h = 1125899906842597L;
        for (int i = 0; i < 1000; i++) h = 31 * h + i * 7;
        double x = 0;
        for (int i = 1; i < 1000; i++) x += 1.0 / (i * i);
        float fl = 1;
        for (int i = 0; i < 20; i++) fl *= 1.1f;
        System.out.println(h + " " + x + " " + fl + " " + (5 / 2.0) + " " + (char) ('a' + 2) + " " + (1 + 2 + "3" + 4 + 5) + " " + 07 + 0x1F + 0b101 + " " + 1_000_000 + " " + 'a' + 'b' + " " + (int) 3.7e9 + " " + 10 % 3.5);
        System.out.println(new StringBuilder().append(1.5f).append(' ').append(2L).append(true).append((Object) null).append(new char[]{'h', 'i'}).insert(0, 9).deleteCharAt(1).toString());
        System.out.println(UUID.nameUUIDFromBytes("x".getBytes()) + " " + Objects.requireNonNullElse(null, "dflt") + " " + Long.toUnsignedString(-1L) + " " + Integer.toUnsignedLong(-1) + " " + Math.multiplyHigh(1L << 62, 8) + " " + Long.divideUnsigned(-1L, 3));
    }
}
