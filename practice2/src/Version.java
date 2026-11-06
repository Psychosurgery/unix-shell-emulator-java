import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public final class Version implements Comparable<Version> {
    private static final Pattern FORMAT = Pattern.compile("(?i)v?(?:([0-9]+)!)?([0-9]+(?:\\.[0-9]+)*)(?:(a|b|rc)([0-9]+))?(?:\\.post([0-9]+))?(?:\\.dev([0-9]+))?(?:\\+([a-z0-9]+(?:[._-][a-z0-9]+)*))?");
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
        epoch = number(m.group(1), BigInteger.ZERO);
        release = Arrays.stream(m.group(2).split("\\.")).map(BigInteger::new).toList();
        String kind = m.group(3);
        preKind = kind == null ? (m.group(6) != null && m.group(5) == null ? -1 : 3)
                : switch (kind.toLowerCase()) { case "a" -> 0; case "b" -> 1; default -> 2; };
        pre = number(m.group(4), BigInteger.ZERO);
        post = number(m.group(5), BigInteger.valueOf(-1));
        dev = number(m.group(6), null);
        local = m.group(7) == null ? "" : m.group(7).toLowerCase().replaceAll("[-_]", ".");
    }

    private static BigInteger number(String value, BigInteger fallback) {
        return value == null ? fallback : new BigInteger(value);
    }

    public boolean prerelease() {
        return preKind < 3 || dev != null;
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
        int c = epoch.compareTo(other.epoch);
        if (c != 0) return c;
        for (int i = 0; i < Math.max(parts(), other.parts()); i++) {
            c = part(i).compareTo(other.part(i));
            if (c != 0) return c;
        }
        c = Integer.compare(preKind, other.preKind);
        if (c != 0) return c;
        c = pre.compareTo(other.pre);
        if (c != 0) return c;
        c = post.compareTo(other.post);
        if (c != 0) return c;
        if (dev == null || other.dev == null) return dev == other.dev ? 0 : dev == null ? 1 : -1;
        return dev.compareTo(other.dev);
    }

    @Override
    public int compareTo(Version other) {
        int c = publicCompare(other);
        if (c != 0) return c;
        if (local.isEmpty() || other.local.isEmpty()) return local.isEmpty() == other.local.isEmpty() ? 0 : local.isEmpty() ? -1 : 1;
        String[] left = local.split("\\.");
        String[] right = other.local.split("\\.");
        for (int i = 0; i < Math.min(left.length, right.length); i++) {
            boolean a = left[i].matches("[0-9]+");
            boolean b = right[i].matches("[0-9]+");
            c = a && b ? new BigInteger(left[i]).compareTo(new BigInteger(right[i]))
                    : a != b ? (a ? 1 : -1) : left[i].compareTo(right[i]);
            if (c != 0) return c;
        }
        return Integer.compare(left.length, right.length);
    }

    public static boolean matches(String version, String specification) {
        Version value = new Version(version);
        String spec = specification.strip();
        if (spec.startsWith("(") && spec.endsWith(")")) spec = spec.substring(1, spec.length() - 1).strip();
        if (spec.isEmpty()) return true;
        boolean result = true;
        for (String clause : spec.split(",", -1)) {
            var m = Pattern.compile("(===|~=|==|!=|<=|>=|<|>)\\s*(\\S+)").matcher(clause.strip());
            if (!m.matches()) throw new IllegalArgumentException("invalid version constraint: " + specification);
            String op = m.group(1);
            String expected = m.group(2);
            if (op.equals("===")) {
                result &= version.equalsIgnoreCase(expected);
                continue;
            }
            boolean wildcard = expected.endsWith(".*");
            Version target = new Version(wildcard ? expected.substring(0, expected.length() - 2) : expected);
            if (wildcard && !op.equals("==") && !op.equals("!=")) {
                throw new IllegalArgumentException("wildcard needs == or !=");
            }
            int cmp = target.hasLocal() ? value.compareTo(target) : value.publicCompare(target);
            boolean equal = wildcard ? value.prefix(target, target.parts()) : cmp == 0;
            boolean accepted = switch (op) {
                case "==" -> equal;
                case "!=" -> !equal;
                case ">=" -> cmp >= 0;
                case "<=" -> cmp <= 0;
                case ">" -> cmp > 0 && (target.post.signum() >= 0 || value.post.signum() < 0 || !value.prefix(target, Math.max(value.parts(), target.parts())));
                case "<" -> cmp < 0 && (target.prerelease() || !value.prefix(target, Math.max(value.parts(), target.parts())));
                case "~=" -> {
                    if (target.parts() < 2) throw new IllegalArgumentException("~= needs two version segments");
                    yield cmp >= 0 && value.prefix(target, target.parts() - 1);
                }
                default -> throw new IllegalArgumentException("invalid constraint");
            };
            result &= accepted;
        }
        return result;
    }
}
