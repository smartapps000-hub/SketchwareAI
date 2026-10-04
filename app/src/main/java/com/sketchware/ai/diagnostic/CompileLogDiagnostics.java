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
            new Rule("PNG chunk type|failed reading from input|\\.png: error: file failed to compile",
                    "An image file is damaged (cut off or corrupt), usually from an interrupted copy or download. "
                            + "Sketchware AI repairs damaged PNGs in the build's copy before AAPT2 runs and says which "
                            + "image it was; replace that image in Image Manager with a good copy."),
            new Rule("Exit code (139|134|135|136) from \\S*aapt2",
                    "AAPT2 itself crashed (exit code %s) while reading a resource; this is not an error in your code. "
                            + "The file named just above that line is the one it was reading. Damaged PNGs are now "
                            + "repaired before AAPT2 runs; if another kind of file is named, replace that file."),
            new Rule("failed linking file resources|failed linking references",
                    "AAPT2 couldn't link the app's resources. The line(s) just above this message name the exact file and line."),
            new Rule("Unreachable code",
                    "The marked line can never run because the statement before it always leaves the method or "
                            + "loop. Auto-fix repairs this during the build, so no manual edit is needed; if the build "
                            + "still stopped here, the Auto-fix report at the end of the log says what was tried."),
            new Rule("Duplicate local variable (\\w+)",
                    "The variable %s is declared twice in the same method (often the same Add Source Directly code "
                            + "pasted twice, or a name that a block already uses). Rename one or delete the copy."),
            new Rule("Duplicate (method|field) ([\\w()., ]+)",
                    "The same method or variable is defined twice in the activity (a MoreBlock and custom code with "
                            + "the same name, or code pasted twice). Remove or rename one."),
            new Rule("Unhandled exception type (\\w+)",
                    "This call can throw %s and Java requires handling it. Wrap the code in try { ... } catch (Exception e) { }."),
            new Rule("Type mismatch: cannot convert from ([\\w.<>\\[\\]]+ to [\\w.<>\\[\\]]+)",
                    "A value of the wrong type is used (%s). Check the block's input type, or convert it "
                            + "(String.valueOf(...), Double.parseDouble(...), (int) ...)."),
            new Rule("may not have been initialized",
                    "A variable is used before it gets a value on every path. Give it a starting value where it is "
                            + "declared (= 0, = \"\", = null)."),
            new Rule("must return a result of type (\\w+)",
                    "A method that returns %s has a path with no return. Add a return at the end of the MoreBlock/method."),
            new Rule("The method (\\w+)\\([^)]*\\) in the type \\w+ is not applicable",
                    "The method %s is called with the wrong number or types of arguments. Compare the call with its definition."),
            new Rule("Cannot make a static reference|non-static",
                    "Code in a static method uses something that belongs to the activity instance. Remove 'static' "
                            + "or pass the activity/context in."),
            new Rule("is not visible|has private access",
                    "The code uses a private member of another class, or an API hidden in the bundled Android 13 android.jar."),
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

    /** Structured result for the diagnostic sheet. */
    public static final class Result {
        /** Short form of the first error ("main.xml:8 · cardCornerRadius not found"), or null. */
        public final String firstError;
        /** One entry per matched known pattern, in rule order. */
        public final List<String> advice;

        Result(String firstError, List<String> advice) {
            this.firstError = firstError;
            this.advice = advice;
        }
    }

    public static Result analyze(String log) {
        if (log == null || log.trim().isEmpty()) {
            return new Result(null, new ArrayList<>());
        }
        String first = firstErrorLine(log);
        String shortFirst = first == null ? null : shorten(first);
        // ECJ reports "ERROR in <file> (at line N)", the code, a caret line, then the message.
        Matcher ecj = Pattern.compile("ERROR in \\S*?([^/\\\\\\s]+\\.java) \\(at line (\\d+)\\)\\s*\\n[^\\n]*\\n[^\\n]*\\n\\s*([^\\n]+)")
                .matcher(log);
        if (ecj.find()) {
            shortFirst = ecj.group(1) + ":" + ecj.group(2) + " · " + ecj.group(3).trim();
        }
        List<String> advice = new ArrayList<>();
        // What the build's own repair pass did comes first: it is specific to this build.
        int report = log.indexOf("Auto-fix report");
        if (report < 0) report = log.indexOf("Auto-fix: off");
        if (report >= 0) {
            int end = log.indexOf("\n\n", report);
            String text = log.substring(report, end < 0 ? log.length() : end).trim();
            advice.add(text.length() > 900 ? text.substring(0, 900) + "..." : text);
        }
        advice.addAll(matchAdvice(log));
        // The generic "failed linking" note only repeats what a specific resource rule said.
        if (advice.size() > 1) {
            for (java.util.Iterator<String> it = advice.iterator(); it.hasNext(); ) {
                if (it.next().startsWith("AAPT2 couldn't link")) it.remove();
            }
        }
        return new Result(shortFirst, advice);
    }

    private static Set<String> matchAdvice(String log) {
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
        return advice;
    }

    /**
     * ".../res/layout/main.xml:8: error: attribute cardCornerRadius (aka com.x:cardCornerRadius) not found."
     * becomes "main.xml:8 · attribute cardCornerRadius not found".
     */
    static String shorten(String line) {
        String first = line.split("\n")[0].trim();
        Matcher m = Pattern.compile("([^/\\\\\\s]+\\.(?:xml|java))((?::\\d+)*):?\\s*(?:error:)?\\s*(.*)").matcher(first);
        if (m.find()) {
            String location = m.group(1) + (m.group(2) == null ? "" : m.group(2).replaceAll(":(\\d+).*", ":$1"));
            String message = m.group(3).replaceAll("\\s*\\(aka [^)]*\\)", "").replaceAll("\\.$", "").trim();
            return message.isEmpty() ? location : location + " · " + message;
        }
        return first.length() > 160 ? first.substring(0, 157) + "…" : first;
    }

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
