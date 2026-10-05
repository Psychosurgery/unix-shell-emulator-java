import java.util.ArrayList;
import java.util.List;

public record LsOptions(boolean all, boolean longFormat, List<String> targets) {
    public static LsOptions parse(List<String> arguments) throws VfsException {
        boolean all = false;
        boolean longFormat = false;
        boolean optionsEnded = false;
        List<String> targets = new ArrayList<>();
        for (String argument : arguments) {
            if (!optionsEnded && argument.equals("--")) {
                optionsEnded = true;
            } else if (!optionsEnded && argument.startsWith("-") && !argument.equals("-")) {
                for (char flag : argument.substring(1).toCharArray()) {
                    if (flag == 'a') {
                        all = true;
                    } else if (flag == 'l') {
                        longFormat = true;
                    } else {
                        throw new VfsException("ls: invalid option -- '" + flag + "'");
                    }
                }
            } else {
                targets.add(argument);
            }
        }
        return new LsOptions(all, longFormat, targets);
    }
}

