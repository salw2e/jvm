import java.util.*;
import java.util.regex.*;
import java.time.*;
import java.nio.charset.StandardCharsets;

public class Text {
    public static void main(String[] args) throws Exception {
        Matcher m = Pattern.compile("(\\w+)@(\\w+)\\.com").matcher("mail bob@site.com and amy@corp.com now");
        while (m.find()) System.out.print(m.group(1) + "/" + m.group(2) + "@" + m.start() + " ");
        System.out.println("a1b22c333".replaceAll("\\d+", "#") + " " + "x,y;;z".split("[,;]").length + " " + "Hello".matches("[A-Z][a-z]+") + " " + Pattern.compile("^(?<y>\\d{4})-(\\d\\d)$").matcher("2024-05").replaceFirst("${y}/$2"));
        String s = "The Quick brown fox";
        System.out.println(s.toLowerCase() + "|" + s.charAt(4) + "|" + s.lastIndexOf('o') + "|" + s.replace("fox", "cat") + "|" + s.compareTo("The") + "|" + s.equalsIgnoreCase("THE QUICK BROWN FOX") + "|" + s.startsWith("Quick", 4) + "|" + String.valueOf(new char[]{'o', 'k'}) + "|" + s.chars().filter(Character::isUpperCase).count() + "|" + "  pad ".trim().length() + "|" + "a-b-c".lastIndexOf("-") + "|" + "ß".toUpperCase() + "|" + "İ".toLowerCase().length());
        byte[] utf = "héllo ✓ 日本 😀".getBytes(StandardCharsets.UTF_8);
        String back = new String(utf, StandardCharsets.UTF_8);
        System.out.println(utf.length + " " + back.length() + " " + back.codePointCount(0, back.length()) + " " + back.codePointAt(12) + " " + new String(back.getBytes("UTF-16"), "UTF-16").equals(back) + " " + back.getBytes(StandardCharsets.ISO_8859_1).length + " " + Character.isSurrogate(back.charAt(12)) + " " + new StringBuilder(back).reverse().charAt(0) + " " + Character.toChars(0x1F600).length + " " + (int) Character.toUpperCase('é') + " " + Character.isDigit('٣') + " " + Character.getType('a'));
        StringTokenizer st = new StringTokenizer("a b\tc");
        StringJoiner sj = new StringJoiner(", ", "{", "}");
        while (st.hasMoreTokens()) sj.add(st.nextToken());
        System.out.println(sj + " " + String.join("|", List.of("x", "y")) + " " + "abc".contains("") + " " + "Mississippi".replace('s', 'z').indexOf("zz", 4) + " " + "%d items".formatted(3) + " " + "tEST".substring(0, 1).toUpperCase() + "tEST".substring(1).toLowerCase() + " " + "a\tb\\n".length() + " " + (int) "é".charAt(0) + " " + "x".compareToIgnoreCase("X") + " " + "ab".concat("cd").endsWith("cd") + " " + Arrays.toString("a1b2".toCharArray()) + " " + "été".equals("été"));
        LocalDate d = LocalDate.of(2024, 2, 28).plusDays(2);
        System.out.println(d + " " + d.getDayOfWeek() + " " + d.isLeapYear() + " " + d.lengthOfMonth() + " " + Duration.ofMinutes(135).toHours() + " " + LocalDateTime.of(2020, 1, 1, 10, 30).plusHours(20) + " " + Period.between(LocalDate.of(2000, 1, 15), d) + " " + LocalTime.parse("10:15").plusMinutes(50) + " " + Instant.ofEpochSecond(86400 * 365).atZone(ZoneOffset.UTC).getYear() + " " + Year.isLeap(1900) + " " + d.withDayOfMonth(1).getDayOfYear());
        System.out.println(Base64.getEncoder().encodeToString("any carnal pleas".getBytes()) + " " + new String(Base64.getDecoder().decode("aGk=")) + " " + Integer.toHexString(Arrays.hashCode(java.security.MessageDigest.getInstance("SHA-256").digest("abc".getBytes()))) + " " + String.format("%08x", new java.util.zip.CRC32() {{ update("hello".getBytes()); }}.getValue()) + " " + HexFormat.of().formatHex(java.security.MessageDigest.getInstance("MD5").digest(new byte[0])));
        Properties p = new Properties();
        p.load(new java.io.StringReader("a=1\nb = two words\n# c\n"));
        System.out.println(new TreeMap<>(p) + " " + System.getProperty("java.version").startsWith("17") + " " + System.getProperty("user.dir").equals(new java.io.File("").getAbsolutePath()) + " " + System.lineSeparator().length() + " " + (System.getenv("PATH") != null) + " " + System.getProperty("nope", "dflt") + " " + (System.currentTimeMillis() > 1600000000000L) + " " + (System.nanoTime() != 0) + " " + Locale.getDefault().getLanguage().length() + " " + Arrays.asList(args));
        System.out.println(String.format("%tY-%<tm-%<td", new GregorianCalendar(2021, Calendar.MARCH, 9)) + " " + java.text.NumberFormat.getIntegerInstance(Locale.US).format(1234567) + " " + new java.text.DecimalFormat("#,##0.00").format(1234.5) + " " + new java.text.SimpleDateFormat("yyyy/MM/dd EEE", Locale.US).format(new Date(0L)).length() + " " + java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US).format(d) + " " + String.format(Locale.GERMANY, "%,.2f", 1234.5) + " " + java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE).format(d));
        System.out.println(String.class.getModule().getName() + " " + java.sql.Date.class.getModule().getName() + " " + (java.sql.Date.class.getClassLoader() == ClassLoader.getPlatformClassLoader()) + " " + ModuleLayer.boot().findModule("java.xml").isPresent() + " " + Text.class.getModule().isNamed() + " " + java.nio.charset.Charset.isSupported("Shift_JIS") + " " + java.nio.charset.Charset.forName("windows-1252").newEncoder().maxBytesPerChar());
        System.exit(args.length + 1);
    }
}
