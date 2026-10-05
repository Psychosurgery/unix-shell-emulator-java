import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Parser {
    private enum Quote {
        NONE, SINGLE, DOUBLE
    }

    private final Map<String, String> environment;

    public Parser(Map<String, String> environment) {
        this.environment = environment;
    }

    public List<String> parse(String line) throws ParseException {
        List<String> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        Quote quote = Quote.NONE;
        boolean started = false;
        for (int index = 0; index < line.length(); index++) {
            char symbol = line.charAt(index);
            if (quote == Quote.NONE && Character.isWhitespace(symbol)) {
                started = finishWord(words, word, started);
                continue;
            }
            if (isQuote(symbol, quote)) {
                quote = nextQuote(symbol, quote);
                started = true;
                continue;
            }
            index = appendSymbol(line, index, word, quote);
            started = true;
        }
        if (quote != Quote.NONE) {
            throw new ParseException("unclosed quote");
        }
        finishWord(words, word, started);
        return words;
    }

    private boolean isQuote(char symbol, Quote quote) {
        return symbol == '\'' && quote != Quote.DOUBLE
                || symbol == '"' && quote != Quote.SINGLE;
    }

    private Quote nextQuote(char symbol, Quote quote) {
        if (quote != Quote.NONE) {
            return Quote.NONE;
        }
        return symbol == '\'' ? Quote.SINGLE : Quote.DOUBLE;
    }

    private int appendSymbol(String line, int index, StringBuilder word, Quote quote)
            throws ParseException {
        char symbol = line.charAt(index);
        if (symbol == '\\' && quote != Quote.SINGLE) {
            return appendEscaped(line, index, word);
        }
        if (symbol == '$' && quote != Quote.SINGLE) {
            return appendVariable(line, index, word);
        }
        word.append(symbol);
        return index;
    }

    private boolean finishWord(List<String> words, StringBuilder word, boolean started) {
        if (started) {
            words.add(word.toString());
            word.setLength(0);
        }
        return false;
    }

    private int appendEscaped(String line, int index, StringBuilder word) throws ParseException {
        if (index + 1 >= line.length()) {
            throw new ParseException("unfinished escape");
        }
        word.append(line.charAt(index + 1));
        return index + 1;
    }

    private int appendVariable(String line, int index, StringBuilder word) throws ParseException {
        int start = index + 1;
        if (start < line.length() && line.charAt(start) == '{') {
            return appendBracedVariable(line, start, word);
        }
        int end = start;
        while (end < line.length() && isVariablePart(line.charAt(end))) {
            end++;
        }
        if (end == start) {
            word.append('$');
            return index;
        }
        word.append(environment.getOrDefault(line.substring(start, end), ""));
        return end - 1;
    }

    private int appendBracedVariable(String line, int open, StringBuilder word) throws ParseException {
        int close = line.indexOf('}', open + 1);
        if (close < 0) {
            throw new ParseException("unclosed variable reference");
        }
        String name = line.substring(open + 1, close);
        if (name.isEmpty() || !name.chars().allMatch(value -> isVariablePart((char) value))) {
            throw new ParseException("invalid variable name: " + name);
        }
        word.append(environment.getOrDefault(name, ""));
        return close;
    }

    private boolean isVariablePart(char symbol) {
        return Character.isLetterOrDigit(symbol) || symbol == '_';
    }
}

