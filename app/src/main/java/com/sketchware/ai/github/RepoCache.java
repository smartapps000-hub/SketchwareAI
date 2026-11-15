package com.sketchware.ai.github;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * v2.0.4 (owner's 16 Nov directive "multi-device zero-hang discovery"): the account's project repositories and each
 * one's versions, kept on the phone (names, numbers, visibility, times; no token). Restore projects shows them at once
 * and refreshes them from GitHub in the background; a sign-in starts that discovery by itself ({@link #discover}), so
 * a fresh phone lists the projects of another one without the screen ever waiting for the network.
 */
public final class RepoCache {

    private static final Object LOCK = new Object();

    private RepoCache() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(CloudSync.PREFS, Context.MODE_PRIVATE);
    }

    /** The kept list of {@code login}'s project repositories, or null; {@code at[0]} = when it was read. */
    static List<ProjectRepo.Info> repos(Context c, String login, long[] at) {
        SharedPreferences p = prefs(c);
        if (login == null || !login.equalsIgnoreCase(p.getString("repos_login", ""))) return null;
        try {
            ProjectRepo.Info[] list = new Gson().fromJson(p.getString("repos", null), ProjectRepo.Info[].class);
            if (list == null) return null;
            if (at != null) at[0] = p.getLong("repos_at", 0);
            return new ArrayList<>(Arrays.asList(list));
        } catch (RuntimeException e) {
            return null;
        }
    }

    static void saveRepos(Context c, String login, List<ProjectRepo.Info> list) {
        synchronized (LOCK) {
            prefs(c).edit().putString("repos_login", login).putString("repos", new Gson().toJson(list))
                    .putLong("repos_at", System.currentTimeMillis()).apply();
        }
    }

    /** The kept versions of one repository (by number), or null. */
    static List<ProjectRepo.Version> versions(Context c, String login, long number) {
        SharedPreferences p = prefs(c);
        if (login == null || !login.equalsIgnoreCase(p.getString("repos_login", ""))) return null;
        try {
            ProjectRepo.Version[] list = new Gson().fromJson(p.getString("versions_" + number, null),
                    ProjectRepo.Version[].class);
            return list == null ? null : new ArrayList<>(Arrays.asList(list));
        } catch (RuntimeException e) {
            return null;
        }
    }

    static void saveVersions(Context c, long number, List<ProjectRepo.Version> list) {
        synchronized (LOCK) {
            prefs(c).edit().putString("versions_" + number, new Gson().toJson(list)).apply();
        }
    }

    /** After a sign-in: the account's project repositories into the cache, on a background thread. */
    public static void discover(Context context) {
        Context app = context.getApplicationContext();
        Thread t = new Thread(() -> {
            try {
                TokenStore store = TokenStore.get(app);
                String login = store.login();
                String token = store.token();
                if (login == null || token == null) return;
                saveRepos(app, login, ProjectRepo.listOwn(token));
            } catch (Exception ignored) {
                // offline or refused: the screen asks again when it opens
            }
        }, "RepoDiscovery");
        t.setDaemon(true);
        t.start();
    }
}
