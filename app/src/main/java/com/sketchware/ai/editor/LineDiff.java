package com.sketchware.ai.editor;

import java.util.ArrayList;
import java.util.List;

/** Line diff (Myers, O((N+M)·D)) for the Java editor: which runs of lines changed between two versions. */
public final class LineDiff {

    /** Lines a[aStart, aEnd) were replaced by b[bStart, bEnd) (0-based, end exclusive; an empty range = insertion/deletion). */
    public static final class Hunk {
        public int aStart, aEnd, bStart, bEnd;

        Hunk(int aStart, int aEnd, int bStart, int bEnd) {
            this.aStart = aStart;
            this.aEnd = aEnd;
            this.bStart = bStart;
            this.bEnd = bEnd;
        }

        @Override
        public String toString() {
            return "a[" + aStart + "," + aEnd + ") -> b[" + bStart + "," + bEnd + ")";
        }
    }

    private LineDiff() {
    }

    /** Whether a hunk is where the caller can use it. */
    public interface Judge {
        boolean good(Hunk hunk);
    }

    /**
     * A run of only deleted (or only inserted) lines can often sit in several places with the same result: a deleted
     * "}" may be either of two equal lines. Moves each such run the caller can't use to the nearest place it can, then
     * joins runs that became neighbours.
     */
    public static List<Hunk> slide(List<Hunk> hunks, List<String> a, List<String> b, Judge judge) {
        List<Hunk> out = new ArrayList<>();
        for (int i = 0; i < hunks.size(); i++) {
            Hunk h = hunks.get(i);
            boolean pure = (h.aStart == h.aEnd) != (h.bStart == h.bEnd);
            if (!pure || judge.good(h)) {
                out.add(h);
                continue;
            }
            int lowA = out.isEmpty() ? 0 : out.get(out.size() - 1).aEnd;
            int lowB = out.isEmpty() ? 0 : out.get(out.size() - 1).bEnd;
            int highA = i + 1 < hunks.size() ? hunks.get(i + 1).aStart : a.size();
            int highB = i + 1 < hunks.size() ? hunks.get(i + 1).bStart : b.size();
            boolean deletion = h.bStart == h.bEnd;
            List<String> side = deletion ? a : b;
            Hunk found = null;
            // up: the context line in front of the run is equal to the run's last line
            Hunk t = new Hunk(h.aStart, h.aEnd, h.bStart, h.bEnd);
            while (found == null && t.aStart - 1 >= lowA && t.bStart - 1 >= lowB) {
                int s = deletion ? t.aStart : t.bStart, e = deletion ? t.aEnd : t.bEnd;
                if (!side.get(s - 1).equals(side.get(e - 1))) break;
                t = new Hunk(t.aStart - 1, t.aEnd - 1, t.bStart - 1, t.bEnd - 1);
                if (judge.good(t)) found = t;
            }
            // down: the context line behind the run is equal to the run's first line
            t = new Hunk(h.aStart, h.aEnd, h.bStart, h.bEnd);
            while (found == null && t.aEnd < highA && t.bEnd < highB) {
                int s = deletion ? t.aStart : t.bStart, e = deletion ? t.aEnd : t.bEnd;
                if (!side.get(s).equals(side.get(e))) break;
                t = new Hunk(t.aStart + 1, t.aEnd + 1, t.bStart + 1, t.bEnd + 1);
                if (judge.good(t)) found = t;
            }
            out.add(found != null ? found : h);
        }
        // join neighbours
        List<Hunk> joined = new ArrayList<>();
        for (Hunk h : out) {
            Hunk last = joined.isEmpty() ? null : joined.get(joined.size() - 1);
            if (last != null && last.aEnd == h.aStart && last.bEnd == h.bStart) {
                last.aEnd = h.aEnd;
                last.bEnd = h.bEnd;
            } else {
                joined.add(new Hunk(h.aStart, h.aEnd, h.bStart, h.bEnd));
            }
        }
        return joined;
    }

    public static List<Hunk> diff(List<String> a, List<String> b) {
        int n = a.size(), m = b.size();
        int max = n + m;
        int offset = max + 1;
        int[] v = new int[2 * max + 3];
        List<int[]> trace = new ArrayList<>();
        int found = -1;
        for (int d = 0; d <= max; d++) {
            if (d > 1500) {
                // Too different to be worth tracing: one change over everything.
                List<Hunk> all = new ArrayList<>();
                all.add(new Hunk(0, n, 0, m));
                return all;
            }
            // only the diagonals this round can read: memory O(D²), not O(D·(N+M))
            trace.add(java.util.Arrays.copyOfRange(v, offset - d, offset + d + 1));
            for (int k = -d; k <= d; k += 2) {
                int x;
                if (k == -d || (k != d && v[offset + k - 1] < v[offset + k + 1])) {
                    x = v[offset + k + 1];
                } else {
                    x = v[offset + k - 1] + 1;
                }
                int y = x - k;
                while (x < n && y < m && a.get(x).equals(b.get(y))) {
                    x++;
                    y++;
                }
                v[offset + k] = x;
                if (x >= n && y >= m) {
                    found = d;
                    break;
                }
            }
            if (found >= 0) break;
        }
        // Walk back: matched pairs.
        boolean[] keptA = new boolean[n];
        int[] pairOfA = new int[n];
        int x = n, y = m;
        for (int d = found; d > 0; d--) {
            int[] vv = trace.get(d);   // diagonals -d..d at index k + d
            int k = x - y;
            int prevK;
            if (k == -d || (k != d && vv[k - 1 + d] < vv[k + 1 + d])) {
                prevK = k + 1;
            } else {
                prevK = k - 1;
            }
            int prevX = vv[prevK + d];
            int prevY = prevX - prevK;
            while (x > prevX && y > prevY) {
                x--;
                y--;
                keptA[x] = true;
                pairOfA[x] = y;
            }
            x = prevX;
            y = prevY;
        }
        while (x > 0 && y > 0) {
            x--;
            y--;
            keptA[x] = true;
            pairOfA[x] = y;
        }
        // Hunks between matched pairs.
        List<Hunk> hunks = new ArrayList<>();
        int ai = 0, bi = 0;
        for (int i = 0; i <= n; i++) {
            if (i == n || keptA[i]) {
                int bj = i == n ? m : pairOfA[i];
                if (ai < i || bi < bj) hunks.add(new Hunk(ai, i, bi, bj));
                ai = i + 1;
                bi = bj + 1;
            }
        }
        return hunks;
    }
}
