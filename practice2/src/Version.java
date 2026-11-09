import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public final class Version implements Comparable<Version> {
    private static final Pattern FORMAT = Pattern.compile(
            "(?i)v?(?:(?<epoch>[0-9]+)!)?(?<release>[0-9]+(?:\\.[0-9]+)*)"
                    + "(?:(?<kind>a|b|rc)(?<pre>[0-9]+))?(?:\\.post(?<post>[0-9]+))?"
                    + "(?:\\.dev(?<dev>[0-9]+))?(?:\\+(?<local>[a-z0-9]+(?:[._-][a-z0-9]+)*))?");
    private static final Pattern CLAUSE = Pattern.compile("(===|~=|==|!=|<=|>=|<|>)\\s*(\\S+)");
    private static final int FINAL_RELEASE = 3;
    private static final int MIN_COMPATIBLE_SEGMENTS = 2;
    private static final String WILDCARD = ".*";
    private final BigInteger epoch;
    private final List<BigInteger> release;
    private final int preKind;
    private final BigInteger pre;
    private final BigInteger post;
    private final BigInteger dev;
    private final String local;

    public Version(String text) {
        var m = FORMAT.matcher(text);
        if (!m.matches()) throw new IllegalArgumentException("unsupported version: " + text);
        epoch = number(m.group("epoch"), BigInteger.ZERO);
        release = Arrays.stream(m.group("release").split("\\.")).map(BigInteger::new).toList();
        String kind = m.group("kind");
        preKind = kind == null ? (m.group("dev") != null && m.group("post") == null ? -1 : FINAL_RELEASE)
                : switch (kind.toLowerCase()) { case "a" -> 0; case "b" -> 1; default -> 2; };
        pre = number(m.group("pre"), BigInteger.ZERO);
        post = number(m.group("post"), BigInteger.valueOf(-1));
        dev = number(m.group("dev"), null);
        String suffix = m.group("local");
        local = suffix == null ? "" : suffix.toLowerCase().replaceAll("[-_]", ".");
    }

    public static boolean isValid(String text) {
        return FORMAT.matcher(text).matches();
    }

    private static BigInteger number(String value, BigInteger fallback) {
        return value == null ? fallback : new BigInteger(value);
    }

    public boolean prerelease() {
        return preKind < FINAL_RELEASE || dev != null;
    }

    public boolean hasLocal() {
        return !local.isEmpty();
    }

    public int parts() {
        return release.size();
    }

    public boolean prefix(Version other, int count) {
        if (!epoch.equals(other.epoch)) return false;
        for (int i = 0; i < count; i++) if (!part(i).equals(other.part(i))) return false;
        return true;
    }

    private BigInteger part(int index) {
        return index < release.size() ? release.get(index) : BigInteger.ZERO;
    }

    public int publicCompare(Version other) {
        int c = compareRelease(other);
        if (c != 0) return c;
        c = Integer.compare(preKind, other.preKind);
        if (c != 0) return c;
        c = pre.compareTo(other.pre);
        if (c != 0) return c;
        c = post.compareTo(other.post);
        if (c != 0) return c;
        if (dev == null || other.dev == null) return Boolean.compare(dev == null, other.dev == null);
        return dev.compareTo(other.dev);
    }

    private int compareRelease(Version other) {
        int c = epoch.compareTo(other.epoch);
        if (c != 0) return c;
        for (int i = 0; i < Math.max(parts(), other.parts()); i++) {
            c = part(i).compareTo(other.part(i));
            if (c != 0) return c;
        }
        return 0;
    }

    @Override
    public int compareTo(Version other) {
        int c = publicCompare(other);
        if (c != 0) return c;
        if (local.isEmpty() || other.local.isEmpty()) {
            return Boolean.compare(!local.isEmpty(), !other.local.isEmpty());
        }
        String[] left = local.split("\\.");
        String[] right = other.local.split("\\.");
        for (int i = 0; i < Math.min(left.length, right.length); i++) {
            c = compareLocalSegment(left[i], right[i]);
            if (c != 0) return c;
        }
        return Integer.compare(left.length, right.length);
    }

    private static int compareLocalSegment(String left, String right) {
        boolean leftNumeric = left.matches("[0-9]+");
        boolean rightNumeric = right.matches("[0-9]+");
        if (leftNumeric != rightNumeric) return Boolean.compare(leftNumeric, rightNumeric);
        return leftNumeric ? new BigInteger(left).compareTo(new BigInteger(right)) : left.compareTo(right);
    }

    public static boolean matches(String version, String specification) {
        Version value = new Version(version);
        String spec = specification.strip();
        if (spec.startsWith("(") && spec.endsWith(")")) spec = spec.substring(1, spec.length() - 1).strip();
        if (spec.isEmpty()) return true;
        boolean result = true;
        for (String clause : spec.split(",", -1)) {
            var m = CLAUSE.matcher(clause.strip());
            if (!m.matches()) throw new IllegalArgumentException("invalid version constraint: " + specification);
            String op = m.group(1);
            String expected = m.group(2);
            result &= op.equals("===") ? version.equalsIgnoreCase(expected) : value.matchesClause(op, expected);
        }
        return result;
    }

    private boolean matchesClause(String op, String expected) {
        boolean wildcard = expected.endsWith(WILDCARD);
        String targetText = wildcard ? expected.substring(0, expected.length() - WILDCARD.length()) : expected;
        Version target = new Version(targetText);
        if (wildcard && !op.equals("==") && !op.equals("!=")) {
            throw new IllegalArgumentException("wildcard needs == or !=");
        }
        int cmp = target.hasLocal() ? compareTo(target) : publicCompare(target);
        boolean equal = wildcard ? prefix(target, target.parts()) : cmp == 0;
        return compareConstraint(op, target, cmp, equal);
    }

    private boolean compareConstraint(String op, Version target, int cmp, boolean equal) {
        return switch (op) {
            case "==" -> equal;
            case "!=" -> !equal;
            case ">=" -> cmp >= 0;
            case "<=" -> cmp <= 0;
            case ">" -> cmp > 0 && permitsLater(target);
            case "<" -> cmp < 0 && permitsEarlier(target);
            case "~=" -> compatible(target, cmp);
            default -> throw new IllegalArgumentException("invalid constraint");
        };
    }

    private boolean permitsLater(Version target) {
        return target.post.signum() >= 0 || post.signum() < 0 || !sameRelease(target);
    }

    private boolean permitsEarlier(Version target) {
        return target.prerelease() || !sameRelease(target);
    }

    private boolean sameRelease(Version target) {
        return prefix(target, Math.max(parts(), target.parts()));
    }

    private boolean compatible(Version target, int comparison) {
        if (target.parts() < MIN_COMPATIBLE_SEGMENTS) {
            throw new IllegalArgumentException("~= needs two version segments");
        }
        return comparison >= 0 && prefix(target, target.parts() - 1);
    }
}
