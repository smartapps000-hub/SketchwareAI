package com.sketchware.ai.editor;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

import a.a.a.eC;
import a.a.a.hC;
import a.a.a.iC;

/**
 * v1.9.0: one event's code in the logic editor's "&lt;/&gt;" sheet, editable in place. The sheet shows the body of
 * the event as the activity's generated code has it ({@link JavaEditSession}, the Java editor's session on the
 * design editor's live project data); Apply puts the edited body back into the activity's code and saves that the way
 * the Java editor does ({@link JavaToBlocks}: statements a block writes become that block, variables are registered,
 * the rest add-source blocks, proof by generating again; nothing changes when the save is refused).
 */
public final class EventCode {

    /** Event key: button1_onClick, onCreate_initializeLogic, greet_moreBlock, listview1_onBindCustomView ... */
    public final String key;
    /** The body as shown: its lines without the indentation the generator gives them. */
    public String body = "";
    /** Why this event's code can't be changed here (it is shown read-only), or null. */
    public String problem;

    private final JavaEditSession session;
    private EditMap.Event event;
    private String indent = "";
    private int editedStart;
    private int editedCount;

    private EventCode(JavaEditSession session, String key) {
        this.session = session;
        this.key = key;
    }

    /**
     * Opens the activity in a Java editor session and finds the event. The event's blocks must be in {@code data}
     * already (the logic editor stores its pane first).
     */
    public static EventCode open(Context context, String scId, String javaName, String key, hC files, eC data, iC libraries) {
        EventCode code = new EventCode(JavaEditSession.open(context, scId, javaName, files, data, libraries), key);
        code.find();
        return code;
    }

    private void find() {
        if (session.ownFile != null) {
            problem = "this activity uses your own " + session.javaName + " (Java Manager) instead of the blocks";
            return;
        }
        if (!session.convertible()) {
            problem = session.problem == null ? "this activity's code can't be turned back into blocks" : session.problem;
            return;
        }
        event = session.event(key);
        if (event == null) {
            problem = "this event isn't written into the activity's code (open the Java editor for the whole activity)";
            return;
        }
        String[] lines = lines(session.code);
        if (event.empty) {
            String header = event.start - 2 >= 0 && event.start - 2 < lines.length ? lines[event.start - 2] : "";
            indent = leading(header) + "\t";
            body = "";
            return;
        }
        int common = Integer.MAX_VALUE;
        for (int k = event.start; k <= event.end; k++) {
            String line = lines[k - 1];
            if (line.trim().isEmpty()) continue;
            if (indent.isEmpty()) indent = leading(line);
            common = Math.min(common, leading(line).length());
        }
        if (common == Integer.MAX_VALUE) common = 0;
        StringBuilder out = new StringBuilder();
        for (int k = event.start; k <= event.end; k++) {
            String line = lines[k - 1];
            if (out.length() > 0 || k > event.start) out.append('\n');
            out.append(line.trim().isEmpty() ? "" : line.substring(Math.min(common, leading(line).length())));
        }
        body = out.toString();
    }

    public boolean editable() {
        return problem == null;
    }

    /**
     * Saves {@code edited} as the event's body. On success the event's blocks (and any variables, imports ... the
     * save registered) are in the project data the session was opened with.
     */
    public JavaToBlocks.Result apply(String edited) {
        if (!editable()) {
            JavaToBlocks.Result result = new JavaToBlocks.Result();
            result.problems.add(new JavaToBlocks.Problem(0, "This event's code can't be changed here: " + problem + "."));
            return result;
        }
        String[] lines = lines(session.code);
        int from = event.start;
        int to = event.empty ? (event.ownLine ? event.start : event.start - 1) : event.end;
        List<String> out = new ArrayList<>();
        for (int k = 1; k < from; k++) out.add(lines[k - 1]);
        String[] typed = lines(edited);
        for (String line : typed) out.add(line.trim().isEmpty() ? "" : indent + line);
        for (int k = to + 1; k <= lines.length; k++) out.add(lines[k - 1]);
        editedStart = from;
        editedCount = typed.length;
        return session.save(String.join("\n", out));
    }

    /** The sheet's line (1-based) for a line of the activity's code a problem names, or 0 when it isn't one of them. */
    public int sheetLine(int activityLine) {
        return activityLine >= editedStart && activityLine < editedStart + editedCount ? activityLine - editedStart + 1 : 0;
    }

    /** The activity's code after the last successful apply (generated from the blocks again). */
    public String activityCode() {
        return session.code;
    }

    private static String[] lines(String text) {
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].endsWith("\r")) lines[i] = lines[i].substring(0, lines[i].length() - 1);
        }
        return lines;
    }

    private static String leading(String line) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) i++;
        return line.substring(0, i);
    }
}
