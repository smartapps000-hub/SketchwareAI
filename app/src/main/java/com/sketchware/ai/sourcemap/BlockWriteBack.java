package com.sketchware.ai.sourcemap;

import com.besome.sketch.beans.BlockBean;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import a.a.a.eC;

/**
 * Makes a build-time repair permanent. A build repairs the generated Java only, so the same
 * repair is needed at every build. When the repaired lines all belong to one "add source
 * directly" block, the block's own code can take the repair instead: this class works out that
 * new code ({@link #collect}), and stores it in the block when asked ({@link #apply}).
 * <p>
 * Nothing is stored on trust: after the block's code is replaced the activity is generated
 * again, and the block's lines must be the repaired lines that compiled. If they aren't, the
 * block gets its old code back.
 */
public final class BlockWriteBack {

    /** The build's proposals: mysc/&lt;sc_id&gt;/autofix.json. */
    public static final String FILE_NAME = "autofix.json";
    /** What was stored in blocks, newest last, for undo: data/&lt;sc_id&gt;/autofix_history.json. */
    public static final String HISTORY_FILE = "autofix_history.json";

    public static final int NOT_APPLIED = 0;
    public static final int APPLIED = 1;
    /** The block's code is neither the old nor the new text: it was edited since the build. */
    public static final int CHANGED = 2;

    /** One block whose code could take the build's repair. */
    public static final class Proposal {
        /** "MainActivity.java" */
        public String file;
        /** The repaired generated file of the build. */
        public String path;
        public String kind;
        public String targetId;
        public String eventName;
        public String blockId;
        public int position;
        /** The block's lines in the generated file. */
        public int startLine;
        public int endLine;
        /** The block's code at build time, and with the repair. */
        public String before;
        public String after;
        /** The changed lines only, as they were and as they are now. */
        public List<String> removed = new ArrayList<>();
        public List<String> added = new ArrayList<>();
        /** First changed line inside the block's code, from 1. */
        public int firstChangedLine;
        /** What the build said it did. */
        public String message;
        /** The line inside the block's code that the build's message is about (from 1), or 0. */
        public int problemLine;
        /**
         * Set when the repair spans several blocks of one event (a "try {" block and its
         * "} catch" block): all proposals with the same group are stored and taken back together.
         */
        public String group;
        /** How many blocks the group has (1 when the proposal stands alone). */
        public int groupSize = 1;
        /** Open braces minus close braces the repair adds to this block alone; 0 unless grouped. */
        public int braces;

        /** "MainActivity → MoreBlock Bt_Perm → block 2 (add source directly)" */
        public String where() {
            SourceMap.EventInfo event = new SourceMap.EventInfo();
            event.kind = kind;
            event.targetId = targetId;
            event.eventName = eventName;
            String activity = file.endsWith(".java") ? file.substring(0, file.length() - 5) : file;
            return activity + " → " + SourceMaps.describe(event) + " → block " + position + " (add source directly)";
        }
    }

    /** Generates an activity's Java from the project's blocks as they are now. */
    public interface Generator {
        /** @return the generated text, or null if the file isn't generated */
        String generate(String javaFileName) throws Exception;
    }

    private BlockWriteBack() {
    }

    private static final Pattern FIX_LINE = Pattern.compile("^([\\w$]+\\.java):(\\d+) ");

    /** A block the build repaired that can't take the repair, and why. */
    public static final class Skipped {
        public String file;
        public int startLine;
        public int endLine;
        public String reason;
    }

    /** What {@link #collect} found for one build. */
    public static final class Collected {
        public final List<Proposal> proposals = new ArrayList<>();
        public final List<Skipped> skipped = new ArrayList<>();
        /** Generated files the build repaired that have a proven source map. */
        final List<String> mappedRepairedFiles = new ArrayList<>();
    }

    /**
     * Called by a build that compiled after repairs: which blocks could store them.
     *
     * @param data  the open project's data
     * @param fixes the repairs' messages ("MainActivity.java:152 unreachable code ...")
     */
    public static List<Proposal> collect(ErrorOrigins.Session session, eC data, List<String> fixes) {
        return collectAll(session, data, fixes).proposals;
    }

    /** As {@link #collect}, and for every repaired block that can't store its repair the reason. */
    public static Collected collectAll(ErrorOrigins.Session session, eC data, List<String> fixes) {
        Collected collected = new Collected();
        if (session == null || data == null) return collected;
        for (String path : session.mappedFiles()) {
            try {
                collectFile(session, data, fixes, path, collected);
            } catch (Exception e) {
                // No proposal for this file.
            }
        }
        return collected;
    }

    /**
     * Why the repair a build message names isn't offered for storing, in words for the log; null
     * when it is offered or the message names no line.
     *
     * @param scId for the reason a file has no source map
     */
    public static String whyNot(ErrorOrigins.Session session, Collected collected, String scId, String fixMessage) {
        Matcher m = FIX_LINE.matcher(fixMessage);
        if (!m.find()) return null;
        String fileName = m.group(1);
        int line = Integer.parseInt(m.group(2));
        if (covers(collected.proposals, fixMessage)) return null;
        // The message names the line the compiler complained about; the repair itself is usually
        // in front of it (the statement that was wrapped). The closest repaired block before it says why.
        Skipped nearest = null;
        for (Skipped skipped : collected.skipped) {
            if (!skipped.file.equals(fileName)) continue;
            if (line >= skipped.startLine && line <= skipped.endLine) return skipped.reason;
            if (skipped.endLine <= line && (nearest == null || skipped.endLine > nearest.endLine)) nearest = skipped;
        }
        if (nearest != null) return nearest.reason;
        String path = null;
        if (session != null) {
            for (String mapped : session.mappedFiles()) {
                if (new File(mapped).getName().equals(fileName)) path = mapped;
            }
        }
        if (path == null) {
            String problem = SourceMaps.problem(scId, fileName);
            return problem == null ? "there is no proven source map for " + fileName + " (it is a custom Java file, or it was changed after it was generated)"
                    : "the lines of " + fileName + " couldn't be matched to blocks for certain (" + problem + ")";
        }
        SourceMap.Origin origin = session.map(path).resolve(line);
        if (origin == null || origin.block == null) {
            return "line " + line + " is code Sketchware writes around your blocks (a listener, a component or the "
                    + "activity's frame), not a block's own code";
        }
        return "the repaired lines are in a different block than the line the message names";
    }

    /** One changed block before it is decided whether it can stand alone. */
    private static final class Draft {
        Proposal proposal;
        String eventKey;
    }

    private static void collectFile(ErrorOrigins.Session session, eC data, List<String> fixes, String path,
                                    Collected collected) throws Exception {
        String original = session.original(path);
        SourceMap map = session.map(path);
        if (original == null || map == null) return;
        String healed = new String(SourceMaps.read(new File(path)), StandardCharsets.UTF_8);
        if (healed.equals(original)) return;
        String[] was = original.split("\n", -1);
        String[] now = healed.split("\n", -1);
        if (was.length != now.length) {
            skip(collected, new File(path).getName(), 1, Integer.MAX_VALUE - 1, "the repair added or removed lines in "
                    + new File(path).getName() + ", so its lines can't be matched to blocks one for one");
            return;
        }
        collected.mappedRepairedFiles.add(path);

        // Changed lines, grouped by the block they belong to.
        Map<String, List<Integer>> byBlock = new LinkedHashMap<>();
        Map<String, SourceMap.Origin> origins = new LinkedHashMap<>();
        String fileName = new File(path).getName();
        for (int i = 0; i < was.length; i++) {
            if (was[i].equals(now[i])) continue;
            SourceMap.Origin origin = map.resolve(i + 1);
            if (origin == null || origin.block == null) {
                skip(collected, fileName, i + 1, i + 1, "line " + (i + 1) + " is code Sketchware writes around your "
                        + "blocks (a listener, a component or the activity's frame), not a block's own code");
                continue;
            }
            String key = origin.block.event + ":" + origin.block.blockId;
            List<Integer> lines = byBlock.get(key);
            if (lines == null) {
                byBlock.put(key, lines = new ArrayList<>());
                origins.put(key, origin);
            }
            lines.add(i + 1);
        }

        List<Draft> drafts = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> group : byBlock.entrySet()) {
            SourceMap.Origin origin = origins.get(group.getKey());
            SourceMap.Entry block = origin.block;
            String where = "block " + block.position + " of " + SourceMaps.describe(origin.event);
            if (!"addSourceDirectly".equals(block.opCode)) {
                skip(collected, fileName, block.start, block.end, "the repaired line comes from the block \""
                        + block.opCode + "\" (" + where + "); its Java is written by Sketchware, and only the code of "
                        + "an add-source-directly block can take a repair");
                continue;
            }
            BlockBean bean = find(data, fileName, origin.event, block.blockId);
            if (bean == null || !"addSourceDirectly".equals(bean.opCode) || bean.parameters.isEmpty()) {
                skip(collected, fileName, block.start, block.end, where + " isn't in the open project any more");
                continue;
            }
            String code = bean.parameters.get(0);
            if (code == null || code.isEmpty() || code.charAt(0) == '@') {
                skip(collected, fileName, block.start, block.end, where + " takes its code from another block's value, not from text");
                continue;
            }
            String[] lines = code.split("\n", -1);
            int count = block.end - block.start + 1;
            // The block's generated lines must be its code's lines, one for one.
            boolean sameLines = lines.length == count;
            for (int i = 0; i < count && sameLines; i++) {
                sameLines = essence(was[block.start - 1 + i]).equals(essence(lines[i]));
            }
            if (!sameLines) {
                skip(collected, fileName, block.start, block.end, "the code of " + where + " and the lines generated "
                        + "from it don't match one for one (" + lines.length + " lines of code, " + count + " generated)");
                continue;
            }

            Proposal proposal = new Proposal();
            StringBuilder after = new StringBuilder(code.length() + 64);
            for (int i = 0; i < count; i++) {
                String old = was[block.start - 1 + i];
                String fresh = now[block.start - 1 + i];
                if (i > 0) after.append('\n');
                if (old.equals(fresh)) {
                    after.append(lines[i]);
                    continue;
                }
                // Keep the indentation and line end the user's line has; take the repaired code.
                String line = lines[i];
                int indent = 0;
                while (indent < line.length() && (line.charAt(indent) == ' ' || line.charAt(indent) == '\t')) indent++;
                String body = stripLineEnd(fresh);
                int skip = 0;
                while (skip < body.length() && (body.charAt(skip) == ' ' || body.charAt(skip) == '\t')) skip++;
                after.append(line, 0, indent).append(body, skip, body.length());
                if (line.endsWith("\r")) after.append('\r');
                if (proposal.firstChangedLine == 0) proposal.firstChangedLine = i + 1;
                proposal.removed.add(old.trim());
                proposal.added.add(fresh.trim());
            }
            if (proposal.firstChangedLine == 0) continue;
            proposal.after = after.toString();
            proposal.braces = braceBalance(proposal.after) - braceBalance(code);
            proposal.before = code;
            proposal.file = fileName;
            proposal.path = path;
            proposal.kind = origin.event.kind;
            proposal.targetId = origin.event.targetId;
            proposal.eventName = origin.event.eventName;
            proposal.blockId = block.blockId;
            proposal.position = block.position;
            proposal.startLine = block.start;
            proposal.endLine = block.end;
            proposal.message = messageFor(fixes, fileName, block.start, block.end);
            if (fixes != null) {
                for (String fix : fixes) {
                    Matcher m = FIX_LINE.matcher(fix);
                    if (!m.find() || !m.group(1).equals(fileName)) continue;
                    int line = Integer.parseInt(m.group(2));
                    if (line >= block.start && line <= block.end) {
                        proposal.problemLine = line - block.start + 1;
                        break;
                    }
                }
            }
            Draft draft = new Draft();
            draft.proposal = proposal;
            draft.eventKey = String.valueOf(block.event);
            drafts.add(draft);
        }

        /*
         * A repair that opens a brace in one block and closes it in another ("try {" in one
         * add-source-directly block, "} catch ..." in a later one) can only be stored in all of
         * those blocks together. That is possible when every repaired line of the event is in an
         * add-source-directly block and the braces of all those blocks together balance.
         */
        Map<String, Integer> balance = new LinkedHashMap<>();
        Map<String, Integer> members = new LinkedHashMap<>();
        for (Draft draft : drafts) {
            if (draft.proposal.braces == 0) continue;
            Integer sum = balance.get(draft.eventKey);
            balance.put(draft.eventKey, (sum == null ? 0 : sum) + draft.proposal.braces);
            Integer n = members.get(draft.eventKey);
            members.put(draft.eventKey, (n == null ? 0 : n) + 1);
        }
        for (Draft draft : drafts) {
            Proposal proposal = draft.proposal;
            if (proposal.braces == 0) {
                collected.proposals.add(proposal);
                continue;
            }
            boolean othersSkipped = false;
            for (Skipped skipped : collected.skipped) {
                if (!skipped.file.equals(fileName)) continue;
                SourceMap.Origin origin = map.resolve(skipped.startLine);
                if (origin != null && origin.block != null && draft.eventKey.equals(String.valueOf(origin.block.event))) othersSkipped = true;
            }
            if (balance.get(draft.eventKey) == 0 && !othersSkipped) {
                proposal.group = fileName + ":" + draft.eventKey;
                proposal.groupSize = members.get(draft.eventKey);
                // The message names the first line that can't run: the one after the statement the blocks open and close.
                proposal.message = messageFor(fixes, fileName, firstLine(drafts, draft.eventKey), lastLine(drafts, draft.eventKey) + 1);
                collected.proposals.add(proposal);
            } else {
                skip(collected, fileName, proposal.startLine, proposal.endLine, "the repair opens or closes a brace in block "
                        + proposal.position + " of " + proposal.where().replaceFirst(" → block \\d+.*$", "")
                        + " that is matched by code outside add-source-directly blocks, so the block can't hold it alone");
            }
        }
    }

    private static int firstLine(List<Draft> drafts, String eventKey) {
        int line = Integer.MAX_VALUE;
        for (Draft draft : drafts) if (draft.eventKey.equals(eventKey)) line = Math.min(line, draft.proposal.startLine);
        return line;
    }

    private static int lastLine(List<Draft> drafts, String eventKey) {
        int line = 0;
        for (Draft draft : drafts) if (draft.eventKey.equals(eventKey)) line = Math.max(line, draft.proposal.endLine);
        return line;
    }

    private static void skip(Collected collected, String file, int start, int end, String reason) {
        Skipped skipped = new Skipped();
        skipped.file = file;
        skipped.startLine = start;
        skipped.endLine = end;
        skipped.reason = reason;
        collected.skipped.add(skipped);
    }

    private static String messageFor(List<String> fixes, String fileName, int start, int end) {
        StringBuilder text = new StringBuilder();
        if (fixes != null) {
            for (String fix : fixes) {
                Matcher m = FIX_LINE.matcher(fix);
                if (!m.find() || !m.group(1).equals(fileName)) continue;
                int line = Integer.parseInt(m.group(2));
                if (line < start || line > end) continue;
                if (text.length() > 0) text.append('\n');
                text.append(fix.substring(m.end()));
            }
        }
        return text.length() == 0 ? "The build repaired this block's code so that it compiles." : text.toString();
    }

    /** @return true if one of the proposals covers the line a repair message names */
    public static boolean covers(List<Proposal> proposals, String fixMessage) {
        Matcher m = FIX_LINE.matcher(fixMessage);
        if (!m.find()) return false;
        int line = Integer.parseInt(m.group(2));
        for (Proposal proposal : proposals) {
            if (!proposal.file.equals(m.group(1))) continue;
            if (line >= proposal.startLine && line <= proposal.endLine) return true;
            if (proposal.group == null) continue;
            // Between the blocks of a group: the statement the group's blocks open and close.
            int first = Integer.MAX_VALUE, last = 0;
            for (Proposal member : proposals) {
                if (!proposal.group.equals(member.group)) continue;
                first = Math.min(first, member.startLine);
                last = Math.max(last, member.endLine);
            }
            // The message names the first unreachable line, which follows the wrapped statement.
            if (line >= first && line <= last + 1) return true;
        }
        return false;
    }

    /** The proposals that are stored and taken back together with {@code proposal} (itself included). */
    public static List<Proposal> groupOf(List<Proposal> all, Proposal proposal) {
        List<Proposal> group = new ArrayList<>();
        if (proposal.group == null || all == null) {
            group.add(proposal);
            return group;
        }
        for (Proposal member : all) if (proposal.group.equals(member.group)) group.add(member);
        if (group.isEmpty()) group.add(proposal);
        return group;
    }

    /** The state of a group: APPLIED or NOT_APPLIED only when every block agrees. */
    public static int state(eC data, List<Proposal> group) {
        int state = -1;
        for (Proposal member : group) {
            int own = state(data, member);
            if (state == -1) state = own;
            else if (state != own) return CHANGED;
        }
        return state == -1 ? CHANGED : state;
    }

    /**
     * Stores a repair that spans several blocks: all of them or none.
     *
     * @return null when stored; otherwise why not, with every block left as it was
     */
    public static String apply(eC data, List<Proposal> group, Generator generator, File historyDirectory) {
        if (group.size() == 1) return apply(data, group.get(0), generator, historyDirectory);
        List<BlockBean> beans = new ArrayList<>();
        for (Proposal member : group) {
            BlockBean bean = find(data, member);
            if (bean == null || bean.parameters.isEmpty()) return "A block of this repair isn't in the project any more.";
            if (!member.before.equals(bean.parameters.get(0))) {
                return "A block's code was edited since the build. Build again first.";
            }
            beans.add(bean);
        }
        for (int i = 0; i < group.size(); i++) beans.get(i).parameters.set(0, group.get(i).after);
        String problem = null;
        try {
            for (Proposal member : group) {
                problem = prove(member, generator);
                if (problem != null) break;
            }
        } catch (Throwable t) {
            problem = "Generating the code again failed: " + t;
        }
        if (problem != null) {
            for (int i = 0; i < group.size(); i++) beans.get(i).parameters.set(0, group.get(i).before);
            return problem;
        }
        for (Proposal member : group) note(historyDirectory, member);
        return null;
    }

    /** Takes a multi-block repair back: all blocks or none. */
    public static String undo(eC data, List<Proposal> group) {
        if (group.size() == 1) return undo(data, group.get(0));
        List<BlockBean> beans = new ArrayList<>();
        for (Proposal member : group) {
            BlockBean bean = find(data, member);
            if (bean == null || bean.parameters.isEmpty()) return "A block of this repair isn't in the project any more.";
            if (!member.after.equals(bean.parameters.get(0))) {
                return "A block's code was edited after the fix was stored; it was left alone.";
            }
            beans.add(bean);
        }
        for (int i = 0; i < group.size(); i++) beans.get(i).parameters.set(0, group.get(i).before);
        return null;
    }

    public static void save(File projectMyscDirectory, List<Proposal> proposals) {
        File target = new File(projectMyscDirectory, FILE_NAME);
        try {
            if (proposals.isEmpty()) {
                target.delete();
                return;
            }
            writeAtomically(target, new Gson().toJson(proposals.toArray(new Proposal[0])));
        } catch (Exception e) {
            target.delete();
        }
    }

    public static List<Proposal> load(File projectMyscDirectory) {
        List<Proposal> proposals = new ArrayList<>();
        try {
            File source = new File(projectMyscDirectory, FILE_NAME);
            if (!source.isFile()) return proposals;
            Proposal[] loaded = new Gson().fromJson(new String(SourceMaps.read(source), StandardCharsets.UTF_8), Proposal[].class);
            if (loaded != null) {
                for (Proposal proposal : loaded) {
                    if (proposal != null && proposal.file != null && proposal.before != null && proposal.after != null) {
                        proposals.add(proposal);
                    }
                }
            }
        } catch (Exception e) {
            // No proposals.
        }
        return proposals;
    }

    /** Whether the block holds the old code, the repaired code, or something else by now. */
    public static int state(eC data, Proposal proposal) {
        BlockBean bean = find(data, proposal);
        if (bean == null || bean.parameters.isEmpty()) return CHANGED;
        String code = bean.parameters.get(0);
        if (proposal.after.equals(code)) return APPLIED;
        return proposal.before.equals(code) ? NOT_APPLIED : CHANGED;
    }

    /**
     * Stores the repaired code in the block (in the open project's data; saving the project
     * writes it to disk as with any edit).
     *
     * @param historyDirectory data/&lt;sc_id&gt;, where what was replaced is noted for undo
     * @return null when stored; otherwise why not, with the block left as it was
     */
    public static String apply(eC data, Proposal proposal, Generator generator, File historyDirectory) {
        BlockBean bean = find(data, proposal);
        if (bean == null || bean.parameters.isEmpty()) return "That block isn't in the project any more.";
        String code = bean.parameters.get(0);
        if (proposal.after.equals(code)) return null;
        if (!proposal.before.equals(code)) return "The block's code was edited since the build. Build again first.";
        bean.parameters.set(0, proposal.after);
        String problem;
        try {
            problem = prove(proposal, generator);
        } catch (Throwable t) {
            problem = "Generating the code again failed: " + t;
        }
        if (problem != null) {
            bean.parameters.set(0, code);
            return problem;
        }
        note(historyDirectory, proposal);
        return null;
    }

    /** With the new code in the block, the activity generated again must have the repaired lines. */
    private static String prove(Proposal proposal, Generator generator) throws Exception {
        String regenerated = generator.generate(proposal.file);
        if (regenerated == null) return "This activity isn't generated from blocks.";
        File healedFile = new File(proposal.path);
        if (!healedFile.isFile()) return "The build's files are gone. Build again first.";
        String[] fresh = regenerated.split("\n", -1);
        String[] healed = new String(SourceMaps.read(healedFile), StandardCharsets.UTF_8).split("\n", -1);
        if (trimmedLength(fresh) != trimmedLength(healed)) {
            return "The project changed since the build (the generated file has a different length). Build again first.";
        }
        for (int line = proposal.startLine; line <= proposal.endLine; line++) {
            if (line > fresh.length || line > healed.length
                    || !fresh[line - 1].trim().equals(healed[line - 1].trim())) {
                return "The regenerated code isn't the repaired code that compiled (line " + line + "), so nothing was stored.";
            }
        }
        return null;
    }

    /**
     * Puts the block's previous code back.
     *
     * @return null when done, otherwise why not
     */
    public static String undo(eC data, Proposal proposal) {
        BlockBean bean = find(data, proposal);
        if (bean == null || bean.parameters.isEmpty()) return "That block isn't in the project any more.";
        String code = bean.parameters.get(0);
        if (proposal.before.equals(code)) return null;
        if (!proposal.after.equals(code)) return "The block's code was edited after the fix was stored; it was left alone.";
        bean.parameters.set(0, proposal.before);
        return null;
    }

    private static BlockBean find(eC data, Proposal proposal) {
        SourceMap.EventInfo event = new SourceMap.EventInfo();
        event.targetId = proposal.targetId;
        event.eventName = proposal.eventName;
        return find(data, proposal.file, event, proposal.blockId);
    }

    private static BlockBean find(eC data, String javaFileName, SourceMap.EventInfo event, String blockId) {
        try {
            ArrayList<BlockBean> blocks = data.a(javaFileName, event.targetId + "_" + event.eventName);
            if (blocks == null) return null;
            for (BlockBean bean : blocks) {
                if (blockId.equals(bean.id)) return bean;
            }
        } catch (RuntimeException e) {
            // Not found.
        }
        return null;
    }

    private static void note(File historyDirectory, Proposal proposal) {
        try {
            if (historyDirectory == null) return;
            File file = new File(historyDirectory, HISTORY_FILE);
            List<Proposal> history = new ArrayList<>();
            if (file.isFile()) {
                Proposal[] old = new Gson().fromJson(new String(SourceMaps.read(file), StandardCharsets.UTF_8), Proposal[].class);
                if (old != null) for (Proposal entry : old) if (entry != null) history.add(entry);
            }
            history.add(proposal);
            while (history.size() > 30) history.remove(0);
            writeAtomically(file, new Gson().toJson(history.toArray(new Proposal[0])));
        } catch (Exception e) {
            // The fix is stored; only the note for a later undo is missing.
        }
    }

    private static void writeAtomically(File target, String text) throws java.io.IOException {
        File parent = target.getParentFile();
        if (parent != null) parent.mkdirs();
        File temporary = new File(target.getPath() + ".tmp");
        try (OutputStream out = new FileOutputStream(temporary)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        if (!temporary.renameTo(target)) {
            target.delete();
            if (!temporary.renameTo(target)) throw new java.io.IOException("can't write " + target);
        }
    }

    private static String stripLineEnd(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    /** A line without any whitespace: what has to be equal between a block's line and its generated line. */
    private static String essence(String line) {
        StringBuilder out = new StringBuilder(line.length());
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c != ' ' && c != '\t' && c != '\r' && c != '\n') out.append(c);
        }
        return out.toString();
    }

    private static int trimmedLength(String[] lines) {
        int n = lines.length;
        while (n > 0 && lines[n - 1].trim().isEmpty()) n--;
        return n;
    }

    /** Open braces minus close braces, outside strings, characters and comments. */
    static int braceBalance(String code) {
        int balance = 0;
        int n = code.length();
        for (int i = 0; i < n; i++) {
            char c = code.charAt(i);
            if (c == '/' && i + 1 < n && code.charAt(i + 1) == '/') {
                while (i < n && code.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < n && code.charAt(i + 1) == '*') {
                int end = code.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 1;
            } else if (c == '"' || c == '\'') {
                for (i++; i < n && code.charAt(i) != c && code.charAt(i) != '\n'; i++) {
                    if (code.charAt(i) == '\\') i++;
                }
            } else if (c == '{') {
                balance++;
            } else if (c == '}') {
                balance--;
            }
        }
        return balance;
    }
}
