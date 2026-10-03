package com.sketchware.ai.sourcemap;

import com.besome.sketch.beans.BlockBean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How the source map learns where each block's code ends up without touching the code
 * generator: while a second, throw-away generation of the file runs, every statement block of
 * every event gets a marker block in front of it and one behind it (plain "add source directly"
 * blocks whose code is a comment). The generator emits them like any other block; afterwards
 * {@link SourceMap#extract} finds the comments, notes their lines and takes them out again.
 * <p>
 * The project's own block lists are never modified: marked copies are handed to the generator.
 */
public final class SourceMapMarkers {

    static final String PREFIX = "/*@SWMAP";
    static final String SUFFIX = "@*/";

    /** One event/MoreBlock whose blocks were marked during the current generation. */
    public static final class Event {
        /** "event", "moreBlock", "onCreate" or "adapter". */
        public String kind;
        /** What the logic editor is opened with: view/component id, "onCreate", or the MoreBlock name. */
        public String targetId;
        /** "onClick", "initializeLogic", "moreBlock", "onBindCustomView", ... */
        public String eventName;
        /** Block id -> {opCode, position in the event (1-based, top to bottom), nesting depth}. */
        public final Map<String, String[]> blocks = new HashMap<>();
    }

    private static final ThreadLocal<List<Event>> SESSION = new ThreadLocal<>();

    private SourceMapMarkers() {
    }

    /** Starts marking on this thread; {@link #wrap} returns its input unchanged outside a session. */
    public static void begin() {
        SESSION.set(new ArrayList<Event>());
    }

    /** Ends the session and returns the events that were marked. */
    public static List<Event> end() {
        List<Event> events = SESSION.get();
        SESSION.remove();
        return events == null ? new ArrayList<Event>() : events;
    }

    public static boolean active() {
        return SESSION.get() != null;
    }

    /**
     * @return {@code blocks} itself outside a session (or if anything about the list is
     * unexpected); inside one, a marked copy for the code generator
     */
    public static ArrayList<BlockBean> wrap(ArrayList<BlockBean> blocks, String kind, String targetId, String eventName) {
        List<Event> session = SESSION.get();
        if (session == null || blocks == null || blocks.isEmpty()) return blocks;
        try {
            Event event = new Event();
            event.kind = kind;
            event.targetId = targetId;
            event.eventName = eventName;
            ArrayList<BlockBean> marked = mark(blocks, session.size(), event);
            if (marked == null) return blocks;
            session.add(event);
            return marked;
        } catch (RuntimeException e) {
            return blocks;
        }
    }

    private static ArrayList<BlockBean> mark(ArrayList<BlockBean> blocks, int eventIndex, Event event) {
        Map<Integer, BlockBean> copies = new HashMap<>();
        ArrayList<BlockBean> ordered = new ArrayList<>(blocks.size() * 3);
        int maxId = 0;
        for (BlockBean block : blocks) {
            int id;
            try {
                id = Integer.parseInt(block.id);
            } catch (NumberFormatException e) {
                return null;
            }
            if (copies.containsKey(id)) return null;
            copies.put(id, block.clone());
            maxId = Math.max(maxId, id);
        }
        int[] nextId = {maxId + 1};
        int[] position = {0};
        Set<Integer> visited = new HashSet<>();
        BlockBean first = copies.get(Integer.parseInt(blocks.get(0).id));
        // The generator starts at the first block of the list: the first marker has to be there.
        ArrayList<BlockBean> markers = new ArrayList<>();
        int head = chain(Integer.parseInt(first.id), copies, visited, markers, nextId, position, eventIndex, event, 0);
        if (head < 0) return null;
        BlockBean headMarker = null;
        for (BlockBean marker : markers) {
            if (Integer.parseInt(marker.id) == head) headMarker = marker;
        }
        if (headMarker == null) return null;
        ordered.add(headMarker);
        for (BlockBean block : blocks) ordered.add(copies.get(Integer.parseInt(block.id)));
        for (BlockBean marker : markers) {
            if (marker != headMarker) ordered.add(marker);
        }
        return ordered;
    }

    /**
     * Marks the chain starting at block {@code id}.
     *
     * @return the id the chain now starts with (the first block's begin marker), or -1 if the
     * blocks don't form a clean chain
     */
    private static int chain(int id, Map<Integer, BlockBean> copies, Set<Integer> visited, List<BlockBean> markers,
                             int[] nextId, int[] position, int eventIndex, Event event, int depth) {
        int chainHead = -1;
        BlockBean previousEnd = null;
        int current = id;
        while (current >= 0) {
            BlockBean block = copies.get(current);
            if (block == null || !visited.add(current) || depth > 200) return -1;
            position[0]++;
            event.blocks.put(block.id, new String[]{block.opCode, String.valueOf(position[0]), String.valueOf(depth)});
            BlockBean begin = marker(nextId[0]++, PREFIX + "+" + eventIndex + ":" + block.id + SUFFIX);
            BlockBean end = marker(nextId[0]++, PREFIX + "-" + eventIndex + ":" + block.id + SUFFIX);
            markers.add(begin);
            markers.add(end);
            begin.nextBlock = current;
            if (previousEnd != null) previousEnd.nextBlock = Integer.parseInt(begin.id);
            else chainHead = Integer.parseInt(begin.id);
            if (block.subStack1 >= 0) {
                int head = chain(block.subStack1, copies, visited, markers, nextId, position, eventIndex, event, depth + 1);
                if (head < 0) return -1;
                block.subStack1 = head;
            }
            if (block.subStack2 >= 0) {
                int head = chain(block.subStack2, copies, visited, markers, nextId, position, eventIndex, event, depth + 1);
                if (head < 0) return -1;
                block.subStack2 = head;
            }
            int next = block.nextBlock;
            block.nextBlock = Integer.parseInt(end.id);
            previousEnd = end;
            current = next;
        }
        return chainHead;
    }

    private static BlockBean marker(int id, String comment) {
        BlockBean marker = new BlockBean();
        marker.id = String.valueOf(id);
        marker.spec = "add source directly %s.inputOnly";
        marker.type = " ";
        marker.typeName = "";
        marker.opCode = "addSourceDirectly";
        marker.color = 0xff5cb722;
        marker.parameters.add(comment);
        return marker;
    }
}
