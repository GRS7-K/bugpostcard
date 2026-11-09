package com.bugpostcard.android;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

final class ReportJson {
    private static final String HEX = "0123456789abcdef";

    private ReportJson() {}

    static String encode(String appName, String version, long createdAt, String title,
                         String description, String apiLevel, List<BugPostcard.Event> events,
                         String screenshot) {
        StringBuilder json = new StringBuilder("{\"schemaVersion\":1,\"app\":{\"name\":");
        quote(json, appName);
        json.append(",\"version\":");
        quote(json, version);
        json.append(",\"platform\":\"android\"},\"createdAt\":");
        quote(json, timestamp(createdAt));
        json.append(",\"title\":");
        quote(json, title);
        json.append(",\"description\":");
        quote(json, description);
        json.append(",\"context\":{");
        if (apiLevel != null) {
            json.append("\"androidApiLevel\":");
            quote(json, apiLevel);
        }
        json.append("},\"events\":[");
        boolean first = true;
        for (BugPostcard.Event event : events) {
            if (!first) json.append(',');
            first = false;
            json.append("{\"time\":");
            quote(json, timestamp(event.timeMillis));
            json.append(",\"kind\":");
            quote(json, event.kind);
            json.append(",\"label\":");
            quote(json, event.label);
            json.append('}');
        }
        json.append("],\"screenshot\":");
        if (screenshot == null) {
            json.append("null");
        } else {
            quote(json, screenshot);
        }
        return json.append('}').toString();
    }

    static String timestamp(long timeMillis) {
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        return iso.format(new Date(timeMillis));
    }

    private static void quote(StringBuilder json, String value) {
        json.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '"' || ch == '\\') {
                json.append('\\').append(ch);
            } else if (ch == '\n') {
                json.append("\\n");
            } else if (ch < 0x20) {
                json.append("\\u00").append(HEX.charAt(ch >> 4)).append(HEX.charAt(ch & 15));
            } else if (Character.isHighSurrogate(ch) && i + 1 < value.length()
                    && Character.isLowSurrogate(value.charAt(i + 1))) {
                json.append(ch).append(value.charAt(++i));
            } else if (Character.isHighSurrogate(ch) || Character.isLowSurrogate(ch)) {
                json.append('\ufffd');
            } else {
                json.append(ch);
            }
        }
        json.append('"');
    }
}
