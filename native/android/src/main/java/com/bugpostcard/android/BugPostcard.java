package com.bugpostcard.android;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Process-local, opt-in diagnostics. Callers supply static, non-sensitive event codes. */
public final class BugPostcard {
    private static final Recorder RECORDER = new Recorder(new TimeSource() {
        @Override public long elapsedMillis() {
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
        }

        @Override public long wallMillis() {
            return System.currentTimeMillis();
        }
    });

    private BugPostcard() {}

    public static void setCollectionEnabled(boolean enabled) {
        RECORDER.setEnabled(enabled);
    }

    public static boolean isCollectionEnabled() {
        return RECORDER.isEnabled();
    }

    public static void clearEvents() {
        RECORDER.clear();
    }

    public static void breadcrumb(Enum<?> code) {
        RECORDER.breadcrumb(code);
    }

    public static void httpStatus(Enum<?> operation, int status) {
        RECORDER.httpStatus(operation, status);
    }

    public static void permissionResult(Enum<?> permission, boolean granted) {
        RECORDER.permissionResult(permission, granted);
    }

    public static void appError(Enum<?> code) {
        RECORDER.appError(code);
    }

    static Capture capture() {
        return RECORDER.capture();
    }

    static boolean isCurrent(Capture capture) {
        return RECORDER.isCurrent(capture);
    }

    interface TimeSource {
        long elapsedMillis();
        long wallMillis();
    }

    static final class Recorder {
        private static final int MAX_EVENTS = 100;
        private static final long MAX_AGE_MILLIS = TimeUnit.MINUTES.toMillis(15);
        private static final Pattern SAFE_CODE = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");

        private final TimeSource clock;
        private final ArrayDeque<Event> events = new ArrayDeque<>();
        private boolean enabled;
        private long generation;

        Recorder(TimeSource clock) {
            this.clock = clock;
        }

        synchronized void setEnabled(boolean enabled) {
            if (this.enabled != enabled) {
                this.enabled = enabled;
                clear();
            }
        }

        synchronized boolean isEnabled() {
            return enabled;
        }

        synchronized void clear() {
            events.clear();
            generation++;
        }

        void breadcrumb(Enum<?> code) {
            add("breadcrumb", code, "");
        }

        void httpStatus(Enum<?> operation, int status) {
            if (status >= 100 && status <= 599) {
                add("http", operation, " HTTP " + status);
            }
        }

        void permissionResult(Enum<?> permission, boolean granted) {
            add("permission", permission, granted ? " granted" : " denied");
        }

        void appError(Enum<?> code) {
            add("error", code, "");
        }

        private synchronized void add(String kind, Enum<?> code, String detail) {
            if (!enabled || code == null || !SAFE_CODE.matcher(code.name()).matches()) return;
            long elapsed = clock.elapsedMillis();
            long wall = clock.wallMillis();
            expire(elapsed, wall);
            if (events.size() == MAX_EVENTS) events.removeFirst();
            events.addLast(new Event(wall, elapsed, kind, code.name() + detail));
        }

        synchronized Capture capture() {
            expire(clock.elapsedMillis(), clock.wallMillis());
            return new Capture(generation, new ArrayList<>(events));
        }

        synchronized boolean isCurrent(Capture capture) {
            return capture.generation == generation;
        }

        private void expire(long elapsed, long wall) {
            // Wall time covers device sleep. Elapsed time covers backwards clock changes.
            Iterator<Event> iterator = events.iterator();
            while (iterator.hasNext()) {
                Event event = iterator.next();
                if (elapsed - event.elapsedMillis >= MAX_AGE_MILLIS
                        || wall - event.timeMillis >= MAX_AGE_MILLIS) {
                    iterator.remove();
                }
            }
        }
    }

    static final class Capture {
        private final long generation;
        final List<Event> events;

        private Capture(long generation, List<Event> events) {
            this.generation = generation;
            this.events = Collections.unmodifiableList(events);
        }
    }

    static final class Event {
        final long timeMillis;
        private final long elapsedMillis;
        final String kind;
        final String label;

        private Event(long timeMillis, long elapsedMillis, String kind, String label) {
            this.timeMillis = timeMillis;
            this.elapsedMillis = elapsedMillis;
            this.kind = kind;
            this.label = label;
        }
    }
}
