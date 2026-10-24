package com.sketchware.ai.editor;

import com.besome.sketch.beans.ProjectFileBean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where every event body and every statement block of one generated activity is, line by line, for the Java editor
 * (v1.8.8). Made from a marked generation ({@link EditMarkers}) and only kept when the marked code, with the markers
 * taken out, is character for character the normal generation; otherwise {@link #generate} says why and the editor
 * converts nothing.
 * <p>
 * Lines are 1-based and count "\n" (the generated text mixes "\r\n" and "\n"; a lone "\r" never occurs in it).
 */
public final class EditMap {

    public static final class Block {
        public String id;
        public String opCode;
        public int start;
        /** start - 1 when the block wrote no line. */
        public int end;
        public Block parent;
        public int stack;
        /** Child chains: index 1 and 2 (index 0 unused). */
        @SuppressWarnings("unchecked")
        public final List<Block>[] stacks = new List[]{null, new ArrayList<Block>(), new ArrayList<Block>()};
    }

    public static final class Event {
        public EditMarkers.Event info;
        /** First and last line of the body; for an empty body both are the line where its code would go. */
        public int start;
        public int end;
        public boolean empty;
        /** An empty body with a (blank) line of its own; false when the generator writes nothing at all for it. */
        public boolean ownLine;
        public final List<Block> top = new ArrayList<>();
        public final Map<String, Block> blocks = new HashMap<>();
    }

    /** The normal generation (the markers' text removed). */
    public String code;
    public final List<Event> events = new ArrayList<>();

    /** Why there is no map, or null. */
    public String problem;

    /**
     * Generates the activity once with markers.
     *
     * @param classic what the normal generation wrote ({@code Jx.formattedCode()})
     */
    public static EditMap generate(a.a.a.jq buildConfig, ProjectFileBean file, a.a.a.eC data, String classic) {
        String marked;
        List<EditMarkers.Event> session;
        EditMarkers.begin();
        try {
            marked = new a.a.a.Jx(buildConfig, file, data).formattedCode();
        } catch (RuntimeException e) {
            EditMap failed = new EditMap();
            failed.problem = "the marked generation failed: " + e;
            return failed;
        } finally {
            session = EditMarkers.end();
        }
        EditMap map = extract(marked, session, classic);
        if (map.problem == null && !map.code.equals(classic)) {
            map.problem = "the marked generation differs from the normal one at line " + firstDifferentLine(map.code, classic);
        }
        return map;
    }

    static int firstDifferentLine(String a, String b) {
        int line = 1;
        int n = Math.min(a.length(), b.length());
        for (int i = 0; i < n; i++) {
            if (a.charAt(i) != b.charAt(i)) return line;
            if (a.charAt(i) == '\n') line++;
        }
        return line;
    }

    /**
     * Takes the markers out and notes where they were.
     *
     * @param classic the normal generation: where an event without blocks is, some templates write an empty line and
     *                some write nothing; this text says which
     */
    static EditMap extract(String marked, List<EditMarkers.Event> session, String classic) {
        EditMap map = new EditMap();
        for (EditMarkers.Event info : session) {
            Event event = new Event();
            event.info = info;
            map.events.add(event);
        }
        StringBuilder out = new StringBuilder(marked.length());
        Map<String, Block> open = new HashMap<>();
        Map<Integer, Integer> openEvents = new HashMap<>();
        int line = 1;
        int i = 0;
        int n = marked.length();
        while (i < n) {
            int at = marked.indexOf(EditMarkers.PREFIX, i);
            int close = at < 0 ? -1 : marked.indexOf(EditMarkers.SUFFIX, at);
            if (at < 0 || close < 0) {
                line += count(marked, i, n);
                out.append(marked, i, n);
                break;
            }
            line += count(marked, i, at);
            out.append(marked, i, at);
            String body = marked.substring(at + EditMarkers.PREFIX.length(), close);
            int after = close + EditMarkers.SUFFIX.length();
            char kind = body.isEmpty() ? '?' : body.charAt(0);
            int eventIndex;
            String blockId = null;
            try {
                if (kind == '[' || kind == ']') {
                    eventIndex = Integer.parseInt(body.substring(1));
                } else if (kind == '+' || kind == '-') {
                    int colon = body.indexOf(':');
                    eventIndex = Integer.parseInt(body.substring(1, colon));
                    blockId = body.substring(colon + 1);
                } else {
                    throw new NumberFormatException(body);
                }
                if (eventIndex < 0 || eventIndex >= map.events.size()) throw new NumberFormatException(body);
            } catch (RuntimeException e) {
                map.problem = "unexpected marker " + body;
                map.code = "";
                return map;
            }
            Event event = map.events.get(eventIndex);
            boolean begin = kind == '[' || kind == '+';
            int indent = trailingIndent(out);
            if (kind == '[') {
                // An empty body: the begin marker, a line break and the end marker. The two go, the line stays
                // (with the indentation in front of the begin marker), as the generator writes "" there.
                String endMarker = EditMarkers.PREFIX + "]" + eventIndex + EditMarkers.SUFFIX;
                int rest = restOfLine(marked, after);
                if (rest > 0) {
                    int next = after + rest;
                    while (next < n && (marked.charAt(next) == ' ' || marked.charAt(next) == '\t')) next++;
                    if (marked.startsWith(endMarker, next)) {
                        int afterEnd = next + endMarker.length();
                        int nextMarker = marked.indexOf(EditMarkers.PREFIX, afterEnd);
                        if (nextMarker < 0) nextMarker = n;
                        // A: the body keeps its line (blank, indented); B: nothing at all is written for it
                        boolean keepLine = classic == null
                                || classic.startsWith(marked.substring(afterEnd, nextMarker), out.length());
                        int restEnd = restOfLine(marked, afterEnd);
                        if (!keepLine && indent >= 0 && restEnd > 0
                                && classic.startsWith(marked.substring(afterEnd + restEnd, Math.max(afterEnd + restEnd, nextMarker)), out.length() - indent)) {
                            out.setLength(out.length() - indent);
                            event.start = line;
                            event.end = line - 1;
                            event.empty = true;
                            event.ownLine = false;
                            i = afterEnd + restEnd;
                            continue;
                        }
                        event.start = line;
                        event.end = line;
                        event.empty = true;
                        event.ownLine = true;
                        i = afterEnd;
                        continue;
                    }
                }
            }
            int rest = restOfLine(marked, after);
            int first;   // first line of what follows the marker
            int last;    // last line of what precedes it
            if (begin && indent >= 0 && rest > 0) {
                out.setLength(out.length() - indent);
                i = after + rest;
                first = line;
                last = line - 1;
            } else if (!begin && indent >= 0 && trailingLineBreak(out, indent) > 0) {
                out.setLength(out.length() - indent - trailingLineBreak(out, indent));
                line--;
                i = after;
                first = line + 1;
                last = line;
            } else {
                map.problem = "a marker shares its line with code (event " + event.info.key() + ")";
                map.code = "";
                return map;
            }
            if (kind == '[') {
                event.start = first;
                openEvents.put(eventIndex, first);
            } else if (kind == ']') {
                if (openEvents.remove(eventIndex) == null) {
                    map.problem = "event markers out of order";
                    map.code = "";
                    return map;
                }
                event.end = last;
            } else if (kind == '+') {
                EditMarkers.BlockInfo info = event.info.blocks.get(blockId);
                if (info == null) {
                    map.problem = "unknown block " + blockId;
                    map.code = "";
                    return map;
                }
                Block block = new Block();
                block.id = blockId;
                block.opCode = info.opCode;
                block.stack = info.stack;
                block.start = first;
                event.blocks.put(blockId, block);
                open.put(eventIndex + ":" + blockId, block);
            } else {
                Block block = open.remove(eventIndex + ":" + blockId);
                if (block == null) {
                    map.problem = "block markers out of order";
                    map.code = "";
                    return map;
                }
                block.end = last;
            }
        }
        if (!open.isEmpty() || !openEvents.isEmpty()) {
            map.problem = "a marker was not closed";
            map.code = "";
            return map;
        }
        // Tree: parents and chains in generation order (the order the begin markers came in = top to bottom).
        for (Event event : map.events) {
            List<Block> ordered = new ArrayList<>(event.blocks.values());
            java.util.Collections.sort(ordered, (a, b) -> a.start != b.start ? Integer.compare(a.start, b.start) : Integer.compare(b.end, a.end));
            for (Block block : ordered) {
                EditMarkers.BlockInfo info = event.info.blocks.get(block.id);
                if (info.parentId == null) {
                    event.top.add(block);
                } else {
                    Block parent = event.blocks.get(info.parentId);
                    if (parent == null) {
                        map.problem = "a block's parent was not generated";
                        map.code = "";
                        return map;
                    }
                    block.parent = parent;
                    parent.stacks[info.stack].add(block);
                }
            }
        }
        map.code = out.toString();
        return map;
    }

    private static int count(String s, int from, int to) {
        int lines = 0;
        for (int i = from; i < to; i++) if (s.charAt(i) == '\n') lines++;
        return lines;
    }

    /** Length of the spaces/tabs the text ends with if they start a line, else -1. */
    private static int trailingIndent(StringBuilder s) {
        int i = s.length();
        while (i > 0 && (s.charAt(i - 1) == '\t' || s.charAt(i - 1) == ' ')) i--;
        return i == 0 || s.charAt(i - 1) == '\n' ? s.length() - i : -1;
    }

    /** Length of the line break in front of the last {@code indent} characters, or 0. */
    private static int trailingLineBreak(StringBuilder s, int indent) {
        int i = s.length() - indent;
        if (i == 0 || s.charAt(i - 1) != '\n') return 0;
        return i > 1 && s.charAt(i - 2) == '\r' ? 2 : 1;
    }

    /** Length of the spaces/tabs and the line break at {@code at}; -1 if anything else follows on the line. */
    private static int restOfLine(String s, int at) {
        int i = at;
        while (i < s.length() && (s.charAt(i) == '\t' || s.charAt(i) == ' ')) i++;
        if (i >= s.length()) return -1;
        if (s.charAt(i) == '\r' && i + 1 < s.length() && s.charAt(i + 1) == '\n') return i + 2 - at;
        if (s.charAt(i) == '\n') return i + 1 - at;
        return -1;
    }

    /** The event whose body holds {@code line} (for an empty body: the body's line), or null. */
    public Event eventAt(int line) {
        Event best = null;
        for (Event event : events) {
            if (!event.info.marked) continue;
            if (line >= event.start && line <= Math.max(event.end, event.start)) {
                if (best == null || event.end - event.start < best.end - best.start) best = event;
            }
        }
        return best;
    }
}
