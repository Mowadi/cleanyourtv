package com.gathin.tvhome;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Fork presentation only. MainActivity keeps its existing identity, input routing and lifecycle. */
final class CinematicHome extends FrameLayout {
    private final Activity activity;
    private final Config config;
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final RecyclerView[] shelves = new RecyclerView[2];
    private final List<View> menu = new ArrayList<>();
    private final List<List<App>> apps = new ArrayList<>();
    private final int[] remembered = {0, 0};
    private final TextView title, subtitle, capacity;
    final View inputs;
    private boolean active, closed;
    private int generation;
    private String selected = "";
    private int selectedRow;
    private final Runnable readings = new Runnable() {
        @Override public void run() {
            if (!active) return;
            ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
            ActivityManager manager = activity.getSystemService(ActivityManager.class);
            if (manager != null) manager.getMemoryInfo(memory);
            StatFs disk = new StatFs(Environment.getDataDirectory().getPath());
            capacity.setText(getResources().getString(R.string.cinema_capacity,
                    gb(memory.totalMem - memory.availMem), gb(memory.totalMem),
                    gb(disk.getTotalBytes() - disk.getAvailableBytes()), gb(disk.getTotalBytes())));
            postDelayed(this, 5000);
        }
    };

    CinematicHome(Activity activity, Config config, Runnable showInputs) {
        super(activity);
        this.activity = activity;
        this.config = config;
        setClipChildren(false);
        setFocusable(false);
        View scrim = new View(activity);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x40080E13, 0x10080E13, 0xEF080E13}));
        addView(scrim, new LayoutParams(-1, -1));
        View side = new View(activity);
        side.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xB0080E13, 0x00080E13}));
        addView(side, new LayoutParams(-1, -1));

        LinearLayout navigation = new LinearLayout(activity);
        navigation.setPadding(dp(5), dp(4), dp(5), dp(4));
        navigation.setGravity(Gravity.CENTER);
        navigation.setBackground(panel(0xCC20252A, 30, 0x25FFFFFF));
        LayoutParams navParams = new LayoutParams(-2, dp(44), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        navParams.topMargin = dp(22);
        addView(navigation, navParams);
        button(navigation, R.string.cinema_home, () -> focusShelf(0, remembered[0]));
        button(navigation, R.string.app_library, () -> focusShelf(1, remembered[1]));
        inputs = button(navigation, R.string.inputs, showInputs);
        button(navigation, R.string.wifi, () -> open(SystemSettings.intent(activity, Settings.ACTION_WIFI_SETTINGS)));
        button(navigation, R.string.settings, () -> open(SystemSettings.intent(activity, Settings.ACTION_SETTINGS)));

        LinearLayout time = new LinearLayout(activity);
        time.setOrientation(LinearLayout.VERTICAL);
        time.setGravity(Gravity.END);
        TextClock clock = new TextClock(activity);
        clock.setFormat12Hour("h:mm a");
        clock.setFormat24Hour("HH:mm");
        clock.setTextSize(21);
        clock.setTextColor(Color.WHITE);
        time.addView(clock);
        TextClock date = new TextClock(activity);
        date.setFormat12Hour("EEE, d MMM");
        date.setFormat24Hour("EEE, d MMM");
        date.setTextSize(11);
        date.setTextColor(0xCCFFFFFF);
        time.addView(date);
        LayoutParams timeParams = new LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        timeParams.topMargin = dp(24);
        timeParams.rightMargin = dp(34);
        addView(time, timeParams);

        LinearLayout hero = new LinearLayout(activity);
        hero.setOrientation(LinearLayout.VERTICAL);
        TextView eyebrow = text(getResources().getString(R.string.app_name), 11, 0xCCFFFFFF);
        eyebrow.setLetterSpacing(.18f);
        hero.addView(eyebrow);
        title = text(getResources().getString(R.string.cinema_welcome), 52, Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        title.setLetterSpacing(-.035f);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        hero.addView(title);
        subtitle = text(getResources().getString(R.string.cinema_hint), 14, 0xCCFFFFFF);
        hero.addView(subtitle);
        LayoutParams heroParams = new LayoutParams(dp(560), -2, Gravity.TOP | Gravity.START);
        heroParams.leftMargin = dp(48);
        heroParams.topMargin = dp(116);
        addView(hero, heroParams);

        LinearLayout rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setClipChildren(false);
        rows.setClipToPadding(false);
        LayoutParams rowParams = new LayoutParams(-1, dp(272), Gravity.BOTTOM);
        rowParams.bottomMargin = dp(38);
        addView(rows, rowParams);
        for (int row = 0; row < 2; row++) {
            apps.add(new ArrayList<>());
            TextView heading = text(getResources().getString(row == 0 ? R.string.cinema_featured : R.string.cinema_all_apps), 18, Color.WHITE);
            heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            heading.setPadding(dp(48), 0, 0, 0);
            rows.addView(heading, new LinearLayout.LayoutParams(-1, dp(25)));
            RecyclerView shelf = new RecyclerView(activity);
            shelf.setId(View.generateViewId());
            shelf.setLayoutManager(new LinearLayoutManager(activity, RecyclerView.HORIZONTAL, false));
            shelf.setPadding(dp(42), dp(7), dp(42), dp(7));
            shelf.setClipToPadding(false);
            shelf.setClipChildren(false);
            shelf.setItemAnimator(null);
            shelf.setAdapter(new ShelfAdapter(row));
            shelves[row] = shelf;
            rows.addView(shelf, new LinearLayout.LayoutParams(-1, dp(111)));
        }
        capacity = text("", 11, 0xBFFFFFFF);
        LayoutParams statParams = new LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        statParams.leftMargin = dp(48);
        statParams.bottomMargin = dp(14);
        addView(capacity, statParams);
        for (int i = 0; i < menu.size(); i++) {
            final int index = i;
            menu.get(i).setOnKeyListener((v, key, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (key == KeyEvent.KEYCODE_DPAD_DOWN) { focusShelf(0, remembered[0]); return true; }
                if (key == KeyEvent.KEYCODE_DPAD_UP) return true;
                if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    menu.get(ShelfNavigation.horizontal(index, key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1, menu.size())).requestFocus();
                    return true;
                }
                return false;
            });
        }
    }

    private String gb(long bytes) { return String.format(Locale.getDefault(), "%.1f", bytes / 1073741824d); }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String label, int size, int color) {
        TextView view = new TextView(activity);
        view.setText(label); view.setTextSize(size); view.setTextColor(color);
        view.setFontFeatureSettings("tnum");
        return view;
    }
    private GradientDrawable panel(int color, int radius, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color); shape.setCornerRadius(dp(radius)); shape.setStroke(dp(1), stroke);
        return shape;
    }
    private View button(LinearLayout parent, int label, Runnable action) {
        TextView view = text(getResources().getString(label), 14, 0xDDFFFFFF);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(16), 0, dp(16), 0);
        view.setFocusable(true);
        view.setOnClickListener(v -> action.run());
        view.setOnFocusChangeListener((v, focused) -> {
            view.setBackground(focused ? panel(0xFFF2F3F5, 24, 0) : null);
            view.setTextColor(focused ? 0xFF11161B : 0xDDFFFFFF);
        });
        parent.addView(view, new LinearLayout.LayoutParams(-2, -1));
        menu.add(view);
        return view;
    }
    void resume() {
        active = true;
        removeCallbacks(readings); readings.run();
        final int request = ++generation;
        loader.execute(() -> {
            List<App> found = discover();
            post(() -> {
                if (closed || !active || request != generation) return;
                List<App> featured = new ArrayList<>();
                for (String pkg : config.dockApps) for (App app : found)
                    if (app.component.getPackageName().equals(pkg) && !featured.contains(app)) featured.add(app);
                for (App app : found) if (app.tv && !featured.contains(app)) featured.add(app);
                if (featured.isEmpty()) featured.addAll(found);
                boolean hadTileFocus = shelves[0].hasFocus() || shelves[1].hasFocus();
                apps.set(0, featured); apps.set(1, found);
                for (int row = 0; row < 2; row++) {
                    for (int i = 0; i < apps.get(row).size(); i++) {
                        if (apps.get(row).get(i).component.flattenToString().equals(selected)) {
                            if (row == selectedRow) remembered[row] = i;
                            break;
                        }
                    }
                }
                for (RecyclerView shelf : shelves) shelf.getAdapter().notifyDataSetChanged();
                // A returned app keeps its selected shelf and position. Package removals clamp safely.
                if (hadTileFocus || !hasFocus()) focusShelf(selectedRow, remembered[selectedRow]);
                if (found.isEmpty()) {
                    title.setText(R.string.cinema_welcome);
                    subtitle.setText(R.string.cinema_empty);
                }
            });
        });
    }
    void pause() { active = false; generation++; removeCallbacks(readings); }
    void close() { closed = true; pause(); loader.shutdownNow(); }
    void home() { selectedRow = 0; remembered[0] = 0; focusShelf(0, 0); }
    private void focusShelf(int row, int position) {
        int target = ShelfNavigation.clamp(position, apps.get(row).size());
        if (target < 0) { menu.get(0).requestFocus(); return; }
        RecyclerView shelf = shelves[row];
        shelf.scrollToPosition(target);
        shelf.post(() -> {
            if (!active) return;
            RecyclerView.ViewHolder holder = shelf.findViewHolderForAdapterPosition(target);
            if (holder != null) holder.itemView.requestFocus();
            else shelf.postOnAnimation(() -> {
                RecyclerView.ViewHolder ready = shelf.findViewHolderForAdapterPosition(target);
                if (active && ready != null) ready.itemView.requestFocus();
            });
        });
    }
    private List<App> discover() {
        PackageManager pm = activity.getPackageManager();
        LinkedHashMap<String, App> found = new LinkedHashMap<>();
        Set<String> hidden = new HashSet<>(config.hiddenApps);
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo info : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                if (Thread.currentThread().isInterrupted()) return new ArrayList<>();
                if (info.activityInfo == null || !info.activityInfo.exported || !info.activityInfo.enabled) continue;
                String pkg = info.activityInfo.packageName;
                if (pkg.equals(activity.getPackageName()) || found.containsKey(pkg)) continue;
                String label = Cards.cleanLabel(config.appLabels.containsKey(pkg) ? config.appLabels.get(pkg) : String.valueOf(info.loadLabel(pm)), pkg);
                found.put(pkg, new App(new ComponentName(pkg, info.activityInfo.name), label,
                        category.equals(Intent.CATEGORY_LEANBACK_LAUNCHER) && !hidden.contains(pkg), info));
            }
        }
        List<App> result = new ArrayList<>(found.values());
        Collator collator = Collator.getInstance();
        result.sort((a, b) -> collator.compare(a.label, b.label));
        return result;
    }
    private void launch(App app) {
        String pkg = app.component.getPackageName();
        Intent fallback = new Intent(Intent.ACTION_MAIN).setComponent(app.component)
                .addCategory(app.tv ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER);
        Intent intent = LaunchActions.select(pkg, config.launchActions, fallback, (p, action) -> {
            Intent custom = new Intent(action).setPackage(p);
            return activity.getPackageManager().resolveActivity(custom, 0) == null ? null : custom;
        });
        open(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
    }
    private void open(Intent intent) {
        try { activity.startActivity(intent); }
        catch (android.content.ActivityNotFoundException | SecurityException e) {
            Toast.makeText(activity, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }
    private static final class App {
        final ComponentName component;
        final String label;
        final boolean tv;
        final ResolveInfo info;
        App(ComponentName component, String label, boolean tv, ResolveInfo info) {
            this.component = component; this.label = label; this.tv = tv; this.info = info;
        }
    }
    private final class Tile extends RecyclerView.ViewHolder {
        final ImageView art;
        final TextView label;
        Tile(LinearLayout card, ImageView art, TextView label) { super(card); this.art = art; this.label = label; }
    }
    private final class ShelfAdapter extends RecyclerView.Adapter<Tile> {
        final int row;
        ShelfAdapter(int row) { this.row = row; }
        @Override public int getItemCount() { return apps.get(row).size(); }
        @Override public Tile onCreateViewHolder(ViewGroup parent, int type) {
            LinearLayout card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(6), dp(5), dp(6), dp(4));
            card.setFocusable(true); card.setClickable(true);
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(dp(174), -1);
            params.rightMargin = dp(8);
            card.setLayoutParams(params);
            card.setBackground(panel(0xB31D252B, 10, 0x20FFFFFF));
            ImageView art = new ImageView(activity);
            art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            card.addView(art, new LinearLayout.LayoutParams(-1, 0, 1));
            TextView label = text("", 12, Color.WHITE);
            label.setSingleLine(true); label.setEllipsize(TextUtils.TruncateAt.END);
            label.setGravity(Gravity.CENTER);
            card.addView(label, new LinearLayout.LayoutParams(-1, dp(20)));
            return new Tile(card, art, label);
        }
        @Override public void onBindViewHolder(Tile holder, int position) {
            App app = apps.get(row).get(position);
            holder.label.setText(app.label);
            holder.itemView.setContentDescription(app.label);
            boolean isFocused = holder.itemView.hasFocus();
            holder.itemView.setBackground(panel(isFocused ? 0xE6404C56 : 0xB31D252B, 10, isFocused ? Color.WHITE : 0x20FFFFFF));
            holder.itemView.setScaleX(isFocused ? 1.035f : 1f);
            holder.itemView.setScaleY(isFocused ? 1.035f : 1f);
            holder.art.setImageDrawable(null);
            // Decode artwork on the same bounded worker; recycled holders discard stale results.
            holder.art.setTag(app.component);
            loader.execute(() -> {
                Drawable art = null;
                try {
                    String custom = config.appBanners.get(app.component.getPackageName());
                    int resource = custom != null && custom.startsWith("banner_")
                            ? getResources().getIdentifier(custom, "drawable", activity.getPackageName()) : 0;
                    if (resource != 0) art = activity.getDrawable(resource);
                    if (art == null) art = app.info.activityInfo.loadBanner(activity.getPackageManager());
                    if (art == null) art = app.info.loadIcon(activity.getPackageManager());
                } catch (RuntimeException ignored) { /* An app can disappear while its artwork is loading. */ }
                final Drawable image = art;
                post(() -> {
                    if (!closed && app.component.equals(holder.art.getTag())) holder.art.setImageDrawable(image);
                });
            });
            holder.itemView.setOnClickListener(v -> launch(app));
            holder.itemView.setOnFocusChangeListener((v, focused) -> {
                v.setBackground(panel(focused ? 0xE6404C56 : 0xB31D252B, 10, focused ? Color.WHITE : 0x20FFFFFF));
                v.setScaleX(focused ? 1.035f : 1f); v.setScaleY(focused ? 1.035f : 1f);
                if (focused) {
                    remembered[row] = holder.getBindingAdapterPosition();
                    selectedRow = row;
                    selected = app.component.flattenToString();
                    title.setText(app.label);
                    subtitle.setText(R.string.cinema_open);
                }
            });
            holder.itemView.setOnKeyListener((v, key, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                int index = holder.getBindingAdapterPosition();
                if (index == RecyclerView.NO_POSITION) return true;
                if (key == KeyEvent.KEYCODE_DPAD_UP) {
                    if (row == 0) menu.get(0).requestFocus(); else focusShelf(0, remembered[0]);
                    return true;
                }
                if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (row == 0) focusShelf(1, remembered[1]);
                    return true;
                }
                if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    focusShelf(row, ShelfNavigation.horizontal(index, key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1, getItemCount()));
                    return true;
                }
                return false;
            });
        }
        @Override public void onViewRecycled(Tile holder) {
            holder.art.setTag(null); holder.art.setImageDrawable(null);
        }
    }
}
