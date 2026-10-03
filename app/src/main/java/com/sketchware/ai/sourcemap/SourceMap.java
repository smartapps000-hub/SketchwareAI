package com.sketchware.ai.sourcemap;

import java.util.ArrayList;
import java.util.List;

/**
 * Where each block of one generated Java file ended up: for every statement block of every
 * event and MoreBlock, the lines its code occupies. Lines are 1-based and refer to the file as
 * it is written to disk (auto-fix never adds or removes lines, so they stay valid after it).
 */
public final class SourceMap {

    public static final int VERSION = 1;

    public static final class Entry {
        /** Index into {@link #events}. */
        public int event;
        public String blockId;
        public String opCode;
        /** Position of the block in its event, counted top to bottom from 1. */
        public int position;
        public int depth;
        public int start;
        public int end;
    }

    public static final class EventInfo {
        public String kind;
        public String targetId;
        public String eventName;
        public int start;
        public int end;
    }

    /** What a line of the generated file comes from. */
    public static final class Origin {
        public String file;
        public int line;
        public EventInfo event;
        /** Null when the line belongs to the event's frame (its method header), not to a block. */
        public Entry block;
        /** Line inside the block's own code, from 1 (useful for long "add source directly" code). */
        public int lineInBlock;
    }

    public int version = VERSION;
    public String file;
    /** False when command blocks rewrote the file after the lines were noted: lookups are refused. */
    public boolean exact = true;
    /** SHA-1 of the generated file the lines belong to. */
    public String sourceHash;
    /** Which generator produced it. */
    public String generator;
    public List<EventInfo> events = new ArrayList<>();
    public List<Entry> entries = new ArrayList<>();

    /** The generated code without the markers, and the map made from them. */
    public static final class Extracted {
        public String code;
        public SourceMap map;
    }

    /**
     * Takes the markers out of marked generated code and notes where they were. The result has to
     * be the text the generator produces without markers; the caller checks that before trusting
     * the map.
     */
    public static Extracted extract(String marked, List<SourceMapMarkers.Event> session) {
        StringBuilder out = new StringBuilder(marked.length());
        SourceMap map = new SourceMap();
        for (SourceMapMarkers.Event event : session) {
            EventInfo info = new EventInfo();
            info.kind = event.kind;
            info.targetId = event.targetId;
            info.eventName = event.eventName;
            map.events.add(info);
        }
        java.util.Map<String, Entry> open = new java.util.HashMap<>();
        int line = 1;
        int i = 0;
        int n = marked.length();
        while (i < n) {
            int at = marked.indexOf(SourceMapMarkers.PREFIX, i);
            if (at < 0) {
                line += count(marked, i, n);
                out.append(marked, i, n);
                break;
            }
            int close = marked.indexOf(SourceMapMarkers.SUFFIX, at);
            if (close < 0) {
                line += count(marked, i, n);
                out.append(marked, i, n);
                break;
            }
            line += count(marked, i, at);
            out.append(marked, i, at);
            String body = marked.substring(at + SourceMapMarkers.PREFIX.length(), close);
            int after = close + SourceMapMarkers.SUFFIX.length();
            boolean begin = body.startsWith("+");
            int colon = body.indexOf(':');
            int eventIndex = -1;
            String blockId = null;
            try {
                eventIndex = Integer.parseInt(body.substring(1, colon));
                blockId = body.substring(colon + 1);
            } catch (RuntimeException e) {
                // Not one of ours: leave the text alone.
                out.append(marked, at, after);
                i = after;
                continue;
            }
            String key = eventIndex + ":" + blockId;
            // The generator joins blocks with line breaks (not always the same kind), so a begin
            // marker goes together with the line break behind it and an end marker with the one
            // in front of it. The indentation in front of a marker is its own and goes too.
            int indent = trailingIndent(out);
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
            } else if (indent >= 0 && rest > 0) {
                // An end marker on the very first line.
                out.setLength(out.length() - indent);
                i = after + rest;
                first = line;
                last = line - 1;
            } else {
                i = after;
                first = line;
                last = line;
            }
            if (begin) {
                Entry entry = new Entry();
                entry.event = eventIndex;
                entry.blockId = blockId;
                entry.start = first;
                if (eventIndex < session.size()) {
                    String[] info = session.get(eventIndex).blocks.get(blockId);
                    if (info != null) {
                        entry.opCode = info[0];
                        entry.position = Integer.parseInt(info[1]);
                        entry.depth = Integer.parseInt(info[2]);
                    }
                }
                open.put(key, entry);
            } else {
                Entry entry = open.remove(key);
                if (entry != null) {
                    entry.end = last;
                    // A block that produced no line of its own (empty code) has nothing to point at.
                    if (entry.end >= entry.start) map.entries.add(entry);
                }
            }
        }
        for (Entry entry : map.entries) {
            if (entry.event < 0 || entry.event >= map.events.size()) continue;
            EventInfo info = map.events.get(entry.event);
            if (info.start == 0 || entry.start < info.start) info.start = entry.start;
            if (entry.end > info.end) info.end = entry.end;
        }
        java.util.Collections.sort(map.entries, (a, b) -> a.start != b.start ? Integer.compare(a.start, b.start) : Integer.compare(b.end, a.end));
        Extracted extracted = new Extracted();
        extracted.code = out.toString();
        extracted.map = map;
        return extracted;
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

    /**
     * @return length of the spaces/tabs and the line break at {@code at}; 0 if the text ends there
     * after only spaces/tabs; -1 if something else follows on the line
     */
    private static int restOfLine(String s, int at) {
        int i = at;
        while (i < s.length() && (s.charAt(i) == '\t' || s.charAt(i) == ' ')) i++;
        if (i >= s.length()) return 0;
        if (s.charAt(i) == '\r' && i + 1 < s.length() && s.charAt(i + 1) == '\n') return i + 2 - at;
        if (s.charAt(i) == '\n') return i + 1 - at;
        return -1;
    }

    /** @return what {@code line} comes from, or null if it is not block code (or the map isn't exact) */
    public Origin resolve(int line) {
        if (!exact) return null;
        Entry best = null;
        for (Entry entry : entries) {
            if (line < entry.start || line > entry.end) continue;
            if (best == null || entry.end - entry.start <= best.end - best.start) best = entry;
        }
        if (best == null || best.event < 0 || best.event >= events.size()) return null;
        Origin origin = new Origin();
        origin.file = file;
        origin.line = line;
        origin.event = events.get(best.event);
        origin.block = best;
        origin.lineInBlock = line - best.start + 1;
        return origin;
    }
}
