package com.sketchware.ai.diagnostic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline first-aid for compile logs: finds the first real error line and explains the most
 * common AAPT2 / ECJ / dexer failures in plain words. No network, no model.
 */
public final class CompileLogDiagnostics {

    private CompileLogDiagnostics() {
    }

    private static final class Rule {
        final Pattern pattern;
        final String advice;

        Rule(String regex, String advice) {
            this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            this.advice = advice;
        }
    }

    private static final Rule[] RULES = {
            new Rule("resource ([\\w.:/@-]+) .*not found|error: resource ([\\w.:/@-]+) not found",
                    "A layout or setting refers to a resource that doesn't exist: %s. Check image/colour/string names "
                            + "in the widget's properties and Inject code. A Text value that starts with @ or ? is read "
                            + "as a resource reference; put a \\ in front of it (\\@) to show it as text."),
            new Rule("attribute (card\\w+) \\(aka [\\w.]+:card\\w+\\) not found",
                    "The layout uses CardView attributes (%s) but the CardView library wasn't part of the build. "
                            + "Sketchware AI now adds it automatically when a layout contains a CardView; build again. "
                            + "If it still fails, turn on AppCompat and Design in the Library Manager."),
            new Rule("attribute ((?!card)\\w+) \\(aka [\\w.]+:\\w+\\) not found",
                    "The layout uses the library attribute app:%s, but the library that defines it isn't enabled. "
                            + "Turn on AppCompat and Design (or the library that provides it), or remove it from Inject."),
            new Rule("attribute (android:\\w+|app:\\w+) .*not found",
                    "The attribute %s isn't known when linking. It's misspelled, needs a library that isn't enabled, "
                            + "or is newer than the bundled android.jar (Android 13). Remove it from Inject or enable the library."),
            new Rule("unescaped apostrophe|apostrophe",
                    "A text contains an apostrophe ('). In strings it must be written as \\'."),
            new Rule("not well-formed|invalid token|mismatched tag|unbound prefix",
                    "An XML file isn't valid. Usually caused by Inject code with a missing quote, a missing xmlns "
                            + "prefix, or a stray < or & (write &amp;)."),
            new Rule("duplicate value for resource|resource previously defined|Duplicate resources",
                    "Two resources have the same name (for example an imported file and a generated one). Rename or remove one."),
            new Rule("failed linking file resources|failed linking references",
                    "AAPT2 couldn't link the app's resources. The line(s) just above this message name the exact file and line."),
            new Rule("(\\w+) cannot be resolved to a type",
                    "Java doesn't know the type %s. Add its import (Custom Imports / ASD), enable the library that "
                            + "provides it, or fix the spelling."),
            new Rule("cannot be resolved|is undefined for the type",
                    "Java can't find a variable or method named in the log. Check the block/custom code and the component names."),
            new Rule("Syntax error",
                    "Java syntax error in custom code (Add Source Directly, custom blocks, or a Java file). The line above shows where."),
            new Rule("Unsupported class file|invalid class file|major version",
                    "A library uses newer Java bytecode than the dexer understands. Choose D8 in Build Settings."),
            new Rule("Cannot fit requested classes in a single dex file|too many (method|field) references|64K",
                    "The app has more than 64K methods. Raise the Minimum SDK to 21+ (multidex) or remove unused libraries."),
            new Rule("OutOfMemory",
                    "The phone ran out of memory while building. Close other apps and try again."),
    };

    /** Human-readable diagnosis for the whole log. Never null. */
    public static String diagnose(String log) {
        if (log == null || log.trim().isEmpty()) {
            return "The compile log is empty.";
        }
        StringBuilder out = new StringBuilder();
        String first = firstErrorLine(log);
        if (first != null) {
            out.append("First error:\n").append(first).append("\n\n");
        }
        Set<String> advice = new LinkedHashSet<>();
        for (Rule rule : RULES) {
            Matcher m = rule.pattern.matcher(log);
            if (m.find()) {
                String detail = "";
                for (int g = 1; g <= m.groupCount(); g++) {
                    if (m.group(g) != null) {
                        detail = m.group(g);
                        break;
                    }
                }
                advice.add(rule.advice.contains("%s") ? String.format(rule.advice, detail) : rule.advice);
            }
        }
        if (advice.isEmpty()) {
            out.append("No known pattern matched. Use Copy or Share to send the full log for help.");
        } else {
            int i = 1;
            for (String a : advice) {
                out.append(i++).append(". ").append(a).append("\n\n");
            }
        }
        return out.toString().trim();
    }

    /** The first line that looks like the cause (file:line: error / ERROR in / Exception). */
    static String firstErrorLine(String log) {
        List<String> lines = new ArrayList<>();
        for (String line : log.split("\n")) lines.add(line.trim());
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            if (l.matches(".*\\.(xml|java)(:\\d+)?(:\\d+)?: error.*") || l.contains("ERROR in ")
                    || l.contains("error: ") && !l.contains("failed linking")) {
                StringBuilder sb = new StringBuilder(l);
                if (l.contains("ERROR in ") && i + 2 < lines.size()) {
                    sb.append('\n').append(lines.get(i + 1)).append('\n').append(lines.get(i + 2));
                }
                return sb.toString();
            }
        }
        return null;
    }
}
