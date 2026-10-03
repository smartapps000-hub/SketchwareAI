package com.sketchware.ai.project;

import java.util.ArrayList;
import java.util.List;

/**
 * A small Java tokenizer and statement splitter: just enough structure to find, for a statement
 * the compiler reported, the block it is in and the statement in front of it. Comments and
 * whitespace are skipped; string and char literals are single tokens, so braces and semicolons
 * inside them never count.
 */
final class JavaTokens {

    final String text;
    /** Token i covers text[start[i], end[i]). */
    final int[] start;
    final int[] end;
    final int count;

    JavaTokens(String text) {
        this.text = text;
        List<int[]> tokens = new ArrayList<>(text.length() / 4 + 16);
        int n = text.length();
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            char next = i + 1 < n ? text.charAt(i + 1) : 0;
            if (c == '/' && next == '/') {
                while (i < n && text.charAt(i) != '\n' && text.charAt(i) != '\r') i++;
                continue;
            }
            if (c == '/' && next == '*') {
                int close = text.indexOf("*/", i + 2);
                i = close < 0 ? n : close + 2;
                continue;
            }
            int from = i;
            if (c == '"' || c == '\'') {
                i++;
                while (i < n && text.charAt(i) != c && text.charAt(i) != '\n' && text.charAt(i) != '\r') {
                    if (text.charAt(i) == '\\') i++;
                    i++;
                }
                if (i < n && text.charAt(i) == c) i++;
                i = Math.min(i, n);
            } else if (Character.isJavaIdentifierStart(c)) {
                while (i < n && Character.isJavaIdentifierPart(text.charAt(i))) i++;
            } else if (Character.isDigit(c)) {
                while (i < n && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_'
                        || (text.charAt(i) == '.' && i + 1 < n && Character.isDigit(text.charAt(i + 1))))) i++;
            } else {
                i++;
            }
            tokens.add(new int[]{from, i});
        }
        count = tokens.size();
        start = new int[count];
        end = new int[count];
        for (int k = 0; k < count; k++) {
            start[k] = tokens.get(k)[0];
            end[k] = tokens.get(k)[1];
        }
    }

    boolean is(int index, String value) {
        if (index < 0 || index >= count || end[index] - start[index] != value.length()) return false;
        return text.startsWith(value, start[index]);
    }

    boolean isIdentifier(int index) {
        return index >= 0 && index < count && Character.isJavaIdentifierStart(text.charAt(start[index]));
    }

    /** Index of the first token starting at or after {@code offset}, or count. */
    int tokenAt(int offset) {
        int low = 0, high = count;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (start[mid] < offset) low = mid + 1;
            else high = mid;
        }
        return low;
    }

    /** Index of the '{' of the block that contains token {@code index}, or -1. */
    int enclosingOpen(int index) {
        int depth = 0;
        for (int i = index - 1; i >= 0; i--) {
            if (is(i, "}")) depth++;
            else if (is(i, "{")) {
                if (depth == 0) return i;
                depth--;
            }
        }
        return -1;
    }

    /** Index of the bracket matching the opening one at {@code open} ('(' or '{' or '['), or -1. */
    int matching(int open) {
        String o = text.substring(start[open], end[open]);
        String c = o.equals("(") ? ")" : o.equals("{") ? "}" : "]";
        int depth = 0;
        for (int i = open; i < count; i++) {
            if (is(i, o)) depth++;
            else if (is(i, c) && --depth == 0) return i;
        }
        return -1;
    }

    /** Index of the bracket matching the closing one at {@code close}, or -1. */
    int matchingBackward(int close) {
        String c = text.substring(start[close], end[close]);
        String o = c.equals(")") ? "(" : c.equals("}") ? "{" : "[";
        int depth = 0;
        for (int i = close; i >= 0; i--) {
            if (is(i, c)) depth++;
            else if (is(i, o) && --depth == 0) return i;
        }
        return -1;
    }

    /**
     * @return the index just after the statement starting at token {@code i}, or -1 if it can't be
     * told (malformed code, or a construct this splitter doesn't know)
     */
    int statementEnd(int i) {
        return statementEnd(i, 0);
    }

    private int statementEnd(int i, int nesting) {
        if (i < 0 || i >= count || nesting > 200) return -1;
        // label:
        if (isIdentifier(i) && is(i + 1, ":") && !is(i, "default") && !is(i, "case")) {
            return statementEnd(i + 2, nesting + 1);
        }
        if (is(i, ";")) return i + 1;
        if (is(i, "{")) {
            int close = matching(i);
            return close < 0 ? -1 : close + 1;
        }
        if (is(i, "if")) {
            int j = afterParens(i + 1);
            j = statementEnd(j, nesting + 1);
            if (j > 0 && is(j, "else")) j = statementEnd(j + 1, nesting + 1);
            return j;
        }
        if (is(i, "for") || is(i, "while") || is(i, "switch") || is(i, "synchronized")) {
            // "synchronized" as a method modifier never starts a statement inside a block.
            return statementEnd(afterParens(i + 1), nesting + 1);
        }
        if (is(i, "do")) {
            int j = statementEnd(i + 1, nesting + 1);
            if (j < 0 || !is(j, "while")) return -1;
            j = afterParens(j + 1);
            return j > 0 && is(j, ";") ? j + 1 : -1;
        }
        if (is(i, "try")) {
            int j = i + 1;
            if (is(j, "(")) j = afterParens(j);
            if (j < 0 || !is(j, "{")) return -1;
            j = matching(j);
            if (j < 0) return -1;
            j++;
            while (is(j, "catch")) {
                j = afterParens(j + 1);
                if (j < 0 || !is(j, "{")) return -1;
                j = matching(j);
                if (j < 0) return -1;
                j++;
            }
            if (is(j, "finally")) {
                if (!is(j + 1, "{")) return -1;
                j = matching(j + 1);
                if (j < 0) return -1;
                j++;
            }
            return j;
        }
        if (is(i, "else") || is(i, "catch") || is(i, "finally") || is(i, "case") || is(i, "}")
                || is(i, ")") || is(i, "]")) {
            return -1;
        }
        // Declaration, expression or jump statement: runs to the ';' outside all brackets. A local
        // class has no ';': it ends with its body.
        int round = 0, square = 0, curly = 0;
        for (int j = i; j < count; j++) {
            if (is(j, "(")) round++;
            else if (is(j, ")")) round--;
            else if (is(j, "[")) square++;
            else if (is(j, "]")) square--;
            else if (is(j, "{")) curly++;
            else if (is(j, "}")) curly--;
            if (round < 0 || square < 0 || curly < 0) return -1;
            if (round == 0 && square == 0 && curly == 0) {
                if (is(j, ";")) return j + 1;
                if ((is(j, "class") || is(j, "interface") || is(j, "enum")) && !is(j - 1, ".")) {
                    int open = j;
                    while (open < count && !is(open, "{") && !is(open, ";")) open++;
                    if (open >= count || !is(open, "{")) return -1;
                    int close = matching(open);
                    return close < 0 ? -1 : close + 1;
                }
            }
        }
        return -1;
    }

    /** @return the index after the ')' matching the '(' expected at {@code i}, or -1 */
    private int afterParens(int i) {
        if (i < 0 || !is(i, "(")) return -1;
        int close = matching(i);
        return close < 0 ? -1 : close + 1;
    }

    /**
     * The statement directly in front of token {@code target} in the same block.
     *
     * @return {first token, token after it (== target)}, or null if {@code target} isn't the start
     * of a statement with another statement before it in its block
     */
    int[] statementBefore(int target) {
        int open = enclosingOpen(target);
        if (open < 0) return null;
        int i = open + 1;
        int[] last = null;
        while (i < target) {
            if (is(i, "case") || (is(i, "default") && is(i + 1, ":"))) {
                int colon = i + 1;
                int round = 0;
                while (colon < target && !(round == 0 && is(colon, ":"))) {
                    if (is(colon, "(")) round++;
                    else if (is(colon, ")")) round--;
                    else if (is(colon, "{") || is(colon, "}") || is(colon, ";")) return null;
                    colon++;
                }
                if (colon >= target) return null;
                i = colon + 1;
                last = null; // a statement before the label is not "in front of" what follows it
                continue;
            }
            int e = statementEnd(i);
            if (e < 0 || e > target) return null;
            last = new int[]{i, e};
            i = e;
        }
        return i == target ? last : null;
    }

    // ------------------------------------------------------------------ reachability (JLS 14.22)

    private static final String[] TYPE_TOKENS = {".", "<", ">", ",", "?", "[", "]", "&"};

    private boolean isTypeToken(int i) {
        if (isIdentifier(i)) return true;
        for (String t : TYPE_TOKENS) if (is(i, t)) return true;
        return false;
    }

    /**
     * Whether the block opened at token {@code open} holds statements (a method, lambda,
     * initializer or control-statement body) rather than class members or initializer values.
     * Unsure means false: such a block is simply not analysed.
     */
    boolean isStatementBlock(int open) {
        return isStatementBlock(open, 0);
    }

    private boolean isStatementBlock(int open, int nesting) {
        if (open <= 0 || nesting > 200 || !is(open, "{")) return false;
        int p = open - 1;
        if (is(p, ")")) {
            int round = matchingBackward(p);
            if (round < 1) return false;
            int k = round - 1;
            if (is(k, "if") || is(k, "while") || is(k, "for") || is(k, "switch") || is(k, "catch")
                    || is(k, "synchronized") || is(k, "try")) {
                return true;
            }
            // name(...) {  is a method or constructor body unless "new" leads the name: an anonymous class.
            for (int i = k; i >= 0 && isTypeToken(i); i--) {
                if (is(i, "new")) return false;
            }
            return isIdentifier(k);
        }
        if (is(p, "else") || is(p, "try") || is(p, "finally") || is(p, "do") || is(p, "static")) return true;
        if (is(p, ">") && is(p - 1, "-")) return true; // lambda body
        if (is(p, ";") || is(p, "}") || is(p, ":")) return true; // nested block, initializer block, case body
        if (is(p, "{")) return isStatementBlock(p, nesting + 1);
        if (isIdentifier(p)) {
            // "... throws A, b.C {" is a method body; "class X {", "extends Y {", "new int[] {" are not.
            for (int i = p; i >= 0 && isTypeToken(i); i--) {
                if (is(i, "throws")) return true;
                if (is(i, "class") || is(i, "interface") || is(i, "enum") || is(i, "extends")
                        || is(i, "implements") || is(i, "new")) {
                    return false;
                }
            }
        }
        return false;
    }

    /** Whether the statement in tokens [i, e) can never complete normally, by the language's own rules. */
    boolean cannotComplete(int i, int e) {
        return cannotComplete(i, e, 0);
    }

    private boolean cannotComplete(int i, int e, int nesting) {
        if (i < 0 || e <= i || nesting > 200) return false;
        if (isIdentifier(i) && is(i + 1, ":")) return false; // labelled: a break may leave it
        if (is(i, "return") || is(i, "throw") || is(i, "break") || is(i, "continue")) return true;
        if (is(i, "{")) return blockCannotComplete(i, nesting + 1);
        if (is(i, "if")) {
            int then = afterParens(i + 1);
            int thenEnd = statementEnd(then);
            if (thenEnd < 0 || !is(thenEnd, "else")) return false;
            return cannotComplete(then, thenEnd, nesting + 1) && cannotComplete(thenEnd + 1, e, nesting + 1);
        }
        if (is(i, "try")) {
            int j = i + 1;
            if (is(j, "(")) j = afterParens(j);
            if (j < 0 || !is(j, "{")) return false;
            boolean none = blockCannotComplete(j, nesting + 1);
            int k = matching(j);
            if (k < 0) return false;
            k++;
            while (is(k, "catch")) {
                k = afterParens(k + 1);
                if (k < 0 || !is(k, "{")) return false;
                none &= blockCannotComplete(k, nesting + 1);
                k = matching(k);
                if (k < 0) return false;
                k++;
            }
            if (is(k, "finally") && is(k + 1, "{") && blockCannotComplete(k + 1, nesting + 1)) return true;
            return none;
        }
        if (is(i, "synchronized")) {
            int j = afterParens(i + 1);
            return j > 0 && is(j, "{") && blockCannotComplete(j, nesting + 1);
        }
        return false;
    }

    /** A block can't complete normally iff its last statement can't. */
    private boolean blockCannotComplete(int open, int nesting) {
        int close = matching(open);
        if (close < 0) return false;
        int i = open + 1;
        int lastStart = -1, lastEnd = -1;
        while (i < close) {
            if (is(i, "case") || (is(i, "default") && is(i + 1, ":"))) return false;
            int e = statementEnd(i);
            if (e < 0 || e > close) return false;
            lastStart = i;
            lastEnd = e;
            i = e;
        }
        return lastStart >= 0 && cannotComplete(lastStart, lastEnd, nesting);
    }

    /**
     * Statements that follow, in the same block, a statement that can't complete normally.
     *
     * @return for each: {first token of the statement in front, token after it, first dead token, the block's '{'}
     */
    List<int[]> deadCodeSites() {
        List<int[]> sites = new ArrayList<>();
        for (int open = 0; open < count; open++) {
            if (!is(open, "{") || !isStatementBlock(open)) continue;
            int close = matching(open);
            if (close < 0) continue;
            scanBlock(open, close, -1, sites);
        }
        return sites;
    }

    /**
     * The dead code a token is in, if the block structure alone shows it: an enclosing block where
     * an earlier statement can't complete normally.
     *
     * @return {first token of that statement, token after it, first dead token, the block's '{'}, or null
     */
    int[] deadSiteContaining(int token) {
        for (int open = enclosingOpen(token); open >= 0; open = enclosingOpen(open)) {
            if (!isStatementBlock(open)) continue;
            int close = matching(open);
            if (close < 0) continue;
            List<int[]> sites = new ArrayList<>();
            scanBlock(open, close, token, sites);
            if (!sites.isEmpty()) return sites.get(0);
        }
        return null;
    }

    /** With {@code until} >= 0, stops at the statement holding that token and reports only a site in front of it. */
    private void scanBlock(int open, int close, int until, List<int[]> sites) {
        int i = open + 1;
        int[] previous = null;
        boolean previousDead = false;
        int[] firstSite = null;
        while (i < close) {
            if (is(i, "case") || (is(i, "default") && is(i + 1, ":"))) {
                int colon = i + 1;
                int round = 0;
                while (colon < close && !(round == 0 && is(colon, ":"))) {
                    if (is(colon, "(")) round++;
                    else if (is(colon, ")")) round--;
                    else if (is(colon, "{") || is(colon, "}") || is(colon, ";")) return;
                    colon++;
                }
                if (colon >= close) return;
                i = colon + 1;
                previous = null;
                previousDead = false;
                firstSite = null; // code after a label is reachable through the switch
                continue;
            }
            int e = statementEnd(i);
            if (e < 0 || e > close) return;
            if (previous != null && previousDead) {
                int[] site = {previous[0], previous[1], i, open};
                if (until < 0) sites.add(site);
                else if (firstSite == null) firstSite = site;
            }
            if (until >= 0 && until >= i && until < e) {
                if (firstSite != null) sites.add(firstSite);
                return;
            }
            previousDead = cannotComplete(i, e);
            previous = new int[]{i, e};
            i = e;
        }
    }

    /**
     * Whether the innermost method around token {@code index} returns a value. A wrapped jump
     * (if (true) return x;) would leave such a method without a guaranteed return.
     */
    boolean inValueMethod(int index) {
        for (int open = enclosingOpen(index); open >= 0; open = enclosingOpen(open)) {
            int p = open - 1;
            if (is(p, ">") && is(p - 1, "-")) return false; // lambda: unknown, treated as void
            int nameEnd = p;
            if (isIdentifier(p) && !is(p, "else") && !is(p, "try") && !is(p, "finally") && !is(p, "do") && !is(p, "static")) {
                // possibly "... throws A, B {": step back to the ')'
                int i = p;
                while (i >= 0 && isTypeToken(i) && !is(i, "throws")) i--;
                if (i < 0 || !is(i, "throws")) continue;
                nameEnd = i - 1;
            }
            if (!is(nameEnd, ")")) continue;
            int round = matchingBackward(nameEnd);
            if (round < 2) return false;
            int name = round - 1;
            if (is(name, "if") || is(name, "while") || is(name, "for") || is(name, "switch") || is(name, "catch")
                    || is(name, "synchronized") || is(name, "try")) {
                continue; // a control statement's block: keep climbing
            }
            boolean anonymous = false;
            for (int i = name; i >= 0 && isTypeToken(i); i--) {
                if (is(i, "new")) anonymous = true;
            }
            if (anonymous || !isIdentifier(name)) continue;
            int type = name - 1;
            if (is(type, "void")) return false;
            // A constructor has no type in front of its name, only modifiers or the previous member's end.
            if (is(type, "public") || is(type, "protected") || is(type, "private") || is(type, "{") || is(type, "}")
                    || is(type, ";") || is(type, ")")) {
                return false;
            }
            return true;
        }
        return false;
    }

    /** Whether the block opened at {@code open} is the body of a switch. */
    boolean isSwitchBody(int open) {
        if (!is(open - 1, ")")) return false;
        int round = matchingBackward(open - 1);
        return round > 0 && is(round - 1, "switch");
    }
}
