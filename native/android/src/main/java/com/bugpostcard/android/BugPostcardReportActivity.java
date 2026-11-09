package com.bugpostcard.android;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Review and export the report using the Android document picker. */
public final class BugPostcardReportActivity extends Activity {
    private static final int PICK_SCREENSHOT = 41;
    private static final int CREATE_REPORT = 42;
    private static final String API_LEVEL = Integer.toString(Build.VERSION.SDK_INT);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Draft draft;
    private EditText title;
    private EditText description;
    private LinearLayout details;
    private TextView status;
    private Button export;

    /** Call from a visible user action on the main thread. */
    public static void open(Activity host) {
        host.startActivity(new Intent(host, BugPostcardReportActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        draft = (Draft) getLastNonConfigurationInstance();
        if (draft == null && savedInstanceState != null) {
            // A process restart intentionally discards the draft and any pending export.
            finish();
            return;
        }
        if (draft == null) {
            String appName = getApplicationInfo().loadLabel(getPackageManager()).toString();
            String version;
            try {
                version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (PackageManager.NameNotFoundException e) {
                version = null;
            }
            draft = new Draft(bounded(appName, 120, "App"), bounded(version, 80, "Unknown"));
        }

        setTitle("BugPostcard");
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setFitsSystemWindows(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(16), dp(20), dp(16));
        scroll.addView(content);
        setContentView(scroll);

        heading(content, "Review your bug report");
        paragraph(content, "Choose what to include before saving a JSON file.");
        paragraph(content, draft.appName + " · " + draft.version + " · Android\n"
            + ReportJson.timestamp(draft.createdAt));
        title = editor(content, "Issue title", draft.title, 160, false);
        description = editor(content, "What happened?", draft.description, 8000, true);
        details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        content.addView(details);

        status = paragraph(content, "");
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        export = button(content, "Save JSON", this::chooseDestination);
        button(content, "Close report", this::finish);
        content.setFocusableInTouchMode(true);
        content.requestFocus();
        render();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (draft != null) draft.refresh = this::render;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (draft != null) render();
    }

    @Override
    protected void onStop() {
        if (draft != null) draft.refresh = null;
        super.onStop();
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        if (draft != null) storeText();
        return draft;
    }

    private void render() {
        if (draft.capture != null && !BugPostcard.isCurrent(draft.capture)) {
            draft.capture = null;
            draft.selected = new boolean[0];
        }
        details.removeAllViews();
        CheckBox api = checkbox(details, "Include Android API level " + API_LEVEL, draft.includeApi);
        api.setOnCheckedChangeListener((view, checked) -> draft.includeApi = checked);

        heading(details, "Recent events");
        if (draft.capture == null || draft.capture.events.isEmpty()) {
            paragraph(details, "There are no recent events in this report.");
        } else {
            paragraph(details, "Uncheck any events you want to leave out.");
            for (int index = 0; index < draft.capture.events.size(); index++) {
                final int selectedIndex = index;
                BugPostcard.Event event = draft.capture.events.get(index);
                CheckBox checkbox = checkbox(details, event.label + "\n" + event.kind + " · "
                    + ReportJson.timestamp(event.timeMillis), draft.selected[index]);
                checkbox.setOnCheckedChangeListener((view, checked) -> draft.selected[selectedIndex] = checked);
            }
        }

        heading(details, "Screenshot");
        boolean available = !draft.busy && draft.picker == 0;
        if (draft.screenshot != null) {
            ImageView preview = new ImageView(this);
            preview.setContentDescription("Screenshot included in this report");
            preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
            preview.setImageBitmap(draft.screenshot.preview);
            details.addView(preview, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(240)));
            button(details, "Remove screenshot", () -> {
                draft.screenshot = null;
                draft.status = "Screenshot removed.";
                render();
            }).setEnabled(available);
        }
        button(details, draft.screenshot == null ? "Choose PNG screenshot" : "Replace screenshot",
            this::chooseScreenshot).setEnabled(available);
        status.setText(draft.status);
        export.setEnabled(available);
    }

    private void chooseScreenshot() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/png");
        launchPicker(intent, PICK_SCREENSHOT);
    }

    private void chooseDestination() {
        storeText();
        if (draft.title.isEmpty()) {
            title.setError("Enter a title for this report.");
            title.requestFocus();
            return;
        }
        List<BugPostcard.Event> chosen = new ArrayList<>();
        if (draft.capture != null && BugPostcard.isCurrent(draft.capture)) {
            for (int index = 0; index < draft.selected.length; index++) {
                if (draft.selected[index]) chosen.add(draft.capture.events.get(index));
            }
        } else if (draft.capture != null && !draft.capture.events.isEmpty()) {
            draft.status = "Recent events were cleared. Review the updated report.";
            render();
            return;
        }
        draft.pending = new Export(draft, chosen);
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "bugpostcard.json");
        launchPicker(intent, CREATE_REPORT);
    }

    private void launchPicker(Intent intent, int request) {
        draft.picker = request;
        draft.status = "";
        try {
            startActivityForResult(intent, request);
        } catch (ActivityNotFoundException | SecurityException e) {
            draft.picker = 0;
            draft.pending = null;
            draft.status = "The Android document picker is unavailable.";
        }
        render();
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (draft == null || request != draft.picker || request == 0) return;
        draft.picker = 0;
        Export pending = draft.pending;
        draft.pending = null;
        Uri uri = result == RESULT_OK && data != null ? data.getData() : null;
        if (uri == null || !"content".equals(uri.getScheme()) || (request == CREATE_REPORT && pending == null)) {
            draft.status = result == RESULT_OK ? "The selected document is unavailable." : "";
            render();
            return;
        }
        draft.busy = true;
        draft.status = request == PICK_SCREENSHOT ? "Loading screenshot…" : "Saving report…";
        render();
        ContentResolver resolver = getApplicationContext().getContentResolver();
        Draft active = draft;
        if (request == PICK_SCREENSHOT) {
            new Thread(() -> readScreenshot(active, resolver, uri), "BugPostcard image").start();
        } else {
            new Thread(() -> writeReport(active, resolver, uri, pending), "BugPostcard export").start();
        }
    }

    private static void readScreenshot(Draft draft, ContentResolver resolver, Uri uri) {
        try {
            Screenshot screenshot = Screenshot.read(resolver, uri);
            MAIN.post(() -> {
                draft.screenshot = screenshot;
                draft.complete("Screenshot ready for review.");
            });
        } catch (IOException | RuntimeException e) {
            MAIN.post(() -> draft.complete("Could not use this PNG. Choose a smaller PNG and try again."));
        }
    }

    private static void writeReport(Draft draft, ContentResolver resolver, Uri uri, Export report) {
        String message;
        try {
            String screenshot = report.screenshot == null ? null : report.screenshot.dataUrl();
            byte[] json = ReportJson.encode(report.appName, report.version, report.createdAt,
                report.title, report.description, report.apiLevel, report.events, screenshot)
                .getBytes(StandardCharsets.UTF_8);
            if (report.revoked()) {
                message = "Recent events were cleared. Review the report and save again.";
            } else {
                try (OutputStream stream = resolver.openOutputStream(uri, "w")) {
                    if (stream == null) throw new IOException("Document unavailable");
                    if (report.revoked()) {
                        message = "Recent events were cleared. Review the report and save again.";
                    } else {
                        stream.write(json);
                        message = "Report saved.";
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            message = "The report could not be saved. Choose a destination and try again.";
        }
        String status = message;
        MAIN.post(() -> draft.complete(status));
    }

    private void storeText() {
        draft.title = title.getText().toString().trim();
        draft.description = description.getText().toString();
    }

    private static String bounded(String value, int limit, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        return value.substring(0, Math.min(limit, value.length())).trim();
    }

    private EditText editor(LinearLayout parent, String hint, String value, int limit, boolean multiline) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setSaveEnabled(false);
        if (Build.VERSION.SDK_INT >= 26) {
            field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
            field.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        }
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        if (multiline) {
            field.setGravity(Gravity.TOP | Gravity.START);
            field.setMinLines(3);
        } else {
            field.setSingleLine(true);
        }
        field.setFilters(new InputFilter[] {new InputFilter.LengthFilter(limit)});
        field.setText(value);
        parent.addView(field);
        return field;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView paragraph(LinearLayout parent, String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setPadding(0, dp(5), 0, dp(5));
        parent.addView(label);
        return label;
    }

    private void heading(LinearLayout parent, String text) {
        TextView label = paragraph(parent, text);
        label.setTextSize(19);
        if (Build.VERSION.SDK_INT >= 28) label.setAccessibilityHeading(true);
    }

    private Button button(LinearLayout parent, String text, Runnable action) {
        Button button = new Button(this);
        button.setText(text);
        button.setOnClickListener(view -> action.run());
        parent.addView(button);
        return button;
    }

    private CheckBox checkbox(LinearLayout parent, String text, boolean checked) {
        CheckBox box = new CheckBox(this);
        box.setText(text);
        box.setChecked(checked);
        box.setMinHeight(dp(48));
        parent.addView(box);
        return box;
    }

    private static final class Draft {
        final String appName;
        final String version;
        final long createdAt = System.currentTimeMillis();
        BugPostcard.Capture capture = BugPostcard.capture();
        boolean[] selected = new boolean[capture.events.size()];
        boolean includeApi = true;
        String title = "";
        String description = "";
        String status = "";
        Screenshot screenshot;
        Export pending;
        boolean busy;
        int picker;
        Runnable refresh;

        Draft(String appName, String version) {
            this.appName = appName;
            this.version = version;
            java.util.Arrays.fill(selected, true);
        }

        void complete(String status) {
            busy = false;
            this.status = status;
            if (refresh != null) refresh.run();
        }
    }

    private static final class Export {
        final String appName;
        final String version;
        final long createdAt;
        final String title;
        final String description;
        final String apiLevel;
        final List<BugPostcard.Event> events;
        final BugPostcard.Capture capture;
        final Screenshot screenshot;

        Export(Draft draft, List<BugPostcard.Event> events) {
            appName = draft.appName;
            version = draft.version;
            createdAt = draft.createdAt;
            title = draft.title;
            description = draft.description;
            apiLevel = draft.includeApi ? API_LEVEL : null;
            this.events = events;
            capture = events.isEmpty() ? null : draft.capture;
            screenshot = draft.screenshot;
        }

        boolean revoked() {
            return capture != null && !BugPostcard.isCurrent(capture);
        }
    }
}
