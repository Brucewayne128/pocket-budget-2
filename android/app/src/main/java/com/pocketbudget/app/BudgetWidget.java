package com.pocketbudget.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Home-screen widget: this month's bills (tab 1) and everything else (tab 2).
 * View only. The app sends it fresh numbers every time something changes.
 */
public class BudgetWidget extends AppWidgetProvider {

    static final String PREFS = "pocket_widget";
    static final String KEY_DATA = "data";
    static final String ACTION_TAB = "com.pocketbudget.app.WIDGET_TAB";
    static final String EXTRA_TAB = "tab";
    static final String ACTION_THEME = "com.pocketbudget.app.WIDGET_THEME";
    static final String KEY_DARK = "widget_dark";

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) draw(c, m, id);
        m.notifyAppWidgetViewDataChanged(ids, R.id.w_list);
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        super.onReceive(c, intent);
        if (ACTION_TAB.equals(intent.getAction())) {
            int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return;
            prefs(c).edit().putInt("tab_" + id, intent.getIntExtra(EXTRA_TAB, 0)).apply();
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            draw(c, m, id);
            m.notifyAppWidgetViewDataChanged(id, R.id.w_list);
        } else if (ACTION_THEME.equals(intent.getAction())) {
            // The widget's own light/dark button
            prefs(c).edit().putBoolean(KEY_DARK, !palette(c).dark).apply();
            redrawAll(c);
        }
    }

    static void redrawAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, BudgetWidget.class));
        if (ids == null || ids.length == 0) return;
        for (int id : ids) draw(c, m, id);
        m.notifyAppWidgetViewDataChanged(ids, R.id.w_list);
    }

    @Override
    public void onDeleted(Context c, int[] ids) {
        SharedPreferences.Editor e = prefs(c).edit();
        for (int id : ids) e.remove("tab_" + id);
        e.apply();
    }

    /** Called by the app whenever the budget changes. */
    static void saveAndRefresh(Context c, String json) {
        prefs(c).edit().putString(KEY_DATA, json).apply();
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, BudgetWidget.class));
        if (ids == null || ids.length == 0) return;
        for (int id : ids) draw(c, m, id);
        m.notifyAppWidgetViewDataChanged(ids, R.id.w_list);
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** This month's saved data, or null if there is none yet or it's from an earlier month. */
    static JSONObject currentData(Context c) {
        String raw = prefs(c).getString(KEY_DATA, null);
        if (raw == null) return null;
        try {
            JSONObject d = new JSONObject(raw);
            return monthKey().equals(d.optString("month")) ? d : null;
        } catch (Exception e) {
            return null;
        }
    }

    static boolean hasOldData(Context c) {
        return prefs(c).getString(KEY_DATA, null) != null;
    }

    /** Widget colors follow the app's light/dark choice (sent by the app), else the phone's setting. */
    static final class Pal {
        final boolean dark;
        final int bg, ink, muted, line, accent, onAccent, good, warn, bad;
        Pal(boolean dark) {
            this.dark = dark;
            bg = dark ? 0xFF18212A : 0xFFFFFFFF;
            ink = dark ? 0xFFE6EDF3 : 0xFF15212C;
            muted = dark ? 0xFF93A1AE : 0xFF5D6B78;
            line = dark ? 0xFF2A3642 : 0xFFD9DFE5;
            accent = dark ? 0xFF5FA8DC : 0xFF1F5F8B;
            onAccent = dark ? 0xFF0B1620 : 0xFFFFFFFF;
            good = dark ? 0xFF4CC38A : 0xFF1D7A4F;
            warn = dark ? 0xFFE2A336 : 0xFFB26A00;
            bad = dark ? 0xFFF2786D : 0xFFB3261E;
        }
    }

    static Pal palette(Context c) {
        SharedPreferences p = prefs(c);
        if (p.contains(KEY_DARK)) return new Pal(p.getBoolean(KEY_DARK, false));
        int night = c.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return new Pal(night == android.content.res.Configuration.UI_MODE_NIGHT_YES);
    }

    /** The widget now has its own light/dark button, so the app's theme no longer changes it. */
    static void setDark(Context c, boolean dark) { }

    static String monthKey() {
        Calendar cal = Calendar.getInstance();
        return String.format(Locale.US, "%d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1);
    }

    static String money(double v) {
        return NumberFormat.getCurrencyInstance(Locale.US).format(v);
    }

    static void draw(Context c, AppWidgetManager m, int id) {
        Pal pal = palette(c);
        RemoteViews v = new RemoteViews(c.getPackageName(), pal.dark ? R.layout.widget_dark : R.layout.widget);
        int tab = prefs(c).getInt("tab_" + id, 0);
        JSONObject d = currentData(c);
        int on = pal.dark ? R.drawable.widget_tab_on_dark : R.drawable.widget_tab_on_light;
        int off = pal.dark ? R.drawable.widget_tab_off_dark : R.drawable.widget_tab_off_light;

        // Colors
        v.setInt(R.id.w_root, "setBackgroundResource", pal.dark ? R.drawable.widget_bg_dark : R.drawable.widget_bg_light);
        v.setTextColor(R.id.w_title, pal.ink);
        v.setTextColor(R.id.w_month, pal.muted);
        v.setTextColor(R.id.w_summary, pal.ink);
        v.setTextColor(R.id.w_empty, pal.muted);

        // Tabs
        v.setInt(R.id.w_tab_bills, "setBackgroundResource", tab == 0 ? on : off);
        v.setInt(R.id.w_tab_else, "setBackgroundResource", tab == 1 ? on : off);
        v.setTextColor(R.id.w_tab_bills, tab == 0 ? pal.onAccent : pal.muted);
        v.setTextColor(R.id.w_tab_else, tab == 1 ? pal.onAccent : pal.muted);
        Intent themeI = new Intent(c, BudgetWidget.class).setAction(ACTION_THEME);
        v.setOnClickPendingIntent(R.id.w_theme, PendingIntent.getBroadcast(c, 7, themeI,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        v.setOnClickPendingIntent(R.id.w_tab_bills, tabIntent(c, id, 0));
        v.setOnClickPendingIntent(R.id.w_tab_else, tabIntent(c, id, 1));

        // Month and summary line
        if (d == null) {
            v.setTextViewText(R.id.w_month, "");
            v.setTextViewText(R.id.w_summary, "");
            v.setTextViewText(R.id.w_empty, hasOldData(c)
                ? "Open Pocket Budget to load this month."
                : "Open Pocket Budget once to show your budget here.");
        } else {
            v.setTextViewText(R.id.w_month, d.optString("monthName"));
            if (tab == 0) {
                v.setTextViewText(R.id.w_summary, money(d.optDouble("billsPaid")) + " paid of "
                    + money(d.optDouble("billsBudget")) + " · " + d.optInt("nPaid") + " of " + d.optInt("nBills") + " paid");
                v.setTextViewText(R.id.w_empty, "No bills this month.");
            } else {
                double budget = d.optDouble("spendBudget");
                v.setTextViewText(R.id.w_summary, money(d.optDouble("spent")) + " spent"
                    + (budget > 0 ? " of " + money(budget) : ""));
                v.setTextViewText(R.id.w_empty, "No categories yet.");
            }
        }

        // Scrollable list of bills or categories
        Intent svc = new Intent(c, BudgetWidgetService.class);
        svc.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        svc.setData(Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME)));
        v.setRemoteAdapter(R.id.w_list, svc);
        v.setEmptyView(R.id.w_list, R.id.w_empty);

        // Tapping anywhere else opens the app
        Intent openApp = new Intent(c, MainActivity.class);
        PendingIntent open = PendingIntent.getActivity(c, 0, openApp,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.w_head, open);
        v.setOnClickPendingIntent(R.id.w_summary, open);
        v.setOnClickPendingIntent(R.id.w_empty, open);
        int mutable = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0;
        PendingIntent template = PendingIntent.getActivity(c, 1, new Intent(c, MainActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT | mutable);
        v.setPendingIntentTemplate(R.id.w_list, template);

        m.updateAppWidget(id, v);
    }

    static PendingIntent tabIntent(Context c, int id, int tab) {
        Intent i = new Intent(c, BudgetWidget.class);
        i.setAction(ACTION_TAB);
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        i.putExtra(EXTRA_TAB, tab);
        return PendingIntent.getBroadcast(c, id * 10 + tab, i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
