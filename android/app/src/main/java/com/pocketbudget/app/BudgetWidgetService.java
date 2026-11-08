package com.pocketbudget.app;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Supplies the widget's scrolling list: bills (tab 1) or spending categories (tab 2). */
public class BudgetWidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Factory(getApplicationContext(),
            intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID));
    }

    private static class Row {
        String name, amount, sub;
        String subColor = "muted";
        int barColor = 0;   // ARGB, 0 = no bar
        int barProgress = -1;
        boolean barOver = false;
    }

    private static class Factory implements RemoteViewsFactory {
        private final Context c;
        private final int widgetId;
        private final List<Row> rows = new ArrayList<>();

        Factory(Context c, int widgetId) {
            this.c = c;
            this.widgetId = widgetId;
        }

        @Override public void onCreate() { }
        @Override public void onDestroy() { rows.clear(); }

        @Override
        public void onDataSetChanged() {
            rows.clear();
            JSONObject d = BudgetWidget.currentData(c);
            if (d == null) return;
            int tab = BudgetWidget.prefs(c).getInt("tab_" + widgetId, 0);
            try {
                if (tab == 0) loadBills(d); else loadCategories(d);
            } catch (Exception ignored) { }
        }

        private void loadBills(JSONObject d) throws Exception {
            JSONArray bills = d.optJSONArray("bills");
            if (bills == null) return;
            Calendar now = Calendar.getInstance();
            int today = now.get(Calendar.DAY_OF_MONTH);
            int lastDay = now.getActualMaximum(Calendar.DAY_OF_MONTH);
            int month = now.get(Calendar.MONTH) + 1;
            for (int i = 0; i < bills.length(); i++) {
                JSONObject b = bills.getJSONObject(i);
                Row r = new Row();
                r.name = b.optString("name");
                boolean paid = b.optBoolean("paid");
                r.amount = BudgetWidget.money(paid ? b.optDouble("paidAmount") : b.optDouble("amount"));
                int due = b.optInt("due", 0);
                int dueDay = due > 0 ? Math.min(due, lastDay) : 0;
                String dueText = dueDay > 0 ? "Due " + month + "/" + dueDay : "";
                if (paid) {
                    r.sub = "Paid" + (dueText.isEmpty() ? "" : " · " + dueText);
                    r.subColor = "good";
                } else if (dueDay == 0) {
                    r.sub = "Not paid yet";
                    r.subColor = "muted";
                } else {
                    int days = dueDay - today;
                    String when;
                    if (days > 1) when = days + " days until due";
                    else if (days == 1) when = "Due tomorrow";
                    else if (days == 0) when = "Due today";
                    else when = (-days) + (days == -1 ? " day" : " days") + " overdue";
                    r.sub = dueText + " · " + when;
                    r.subColor = days < 0 ? "bad" : (days <= 3 ? "warn" : "muted");
                }
                rows.add(r);
            }
        }

        private void loadCategories(JSONObject d) throws Exception {
            JSONArray cats = d.optJSONArray("cats");
            if (cats == null) return;
            for (int i = 0; i < cats.length(); i++) {
                JSONObject k = cats.getJSONObject(i);
                Row r = new Row();
                r.name = k.optString("name");
                double spent = k.optDouble("spent", 0), limit = k.optDouble("limit", 0);
                if (limit > 0) {
                    r.amount = BudgetWidget.money(spent) + " of " + BudgetWidget.money(limit);
                    double rem = limit - spent;
                    r.sub = rem < 0 ? BudgetWidget.money(-rem) + " over budget" : BudgetWidget.money(rem) + " left";
                    r.subColor = rem < 0 ? "bad" : "muted";
                    r.barProgress = (int) Math.min(1000, Math.round(spent / limit * 1000));
                    r.barOver = rem < 0;
                    try { r.barColor = Color.parseColor(k.optString("color", "#7A8792")); }
                    catch (Exception e) { r.barColor = Color.parseColor("#7A8792"); }
                } else {
                    r.amount = BudgetWidget.money(spent);
                    r.sub = "No set budget";
                    r.subColor = "muted";
                }
                rows.add(r);
            }
        }

        @Override public int getCount() { return rows.size(); }

        @Override
        public RemoteViews getViewAt(int position) {
            BudgetWidget.Pal pal = BudgetWidget.palette(c);
            RemoteViews v = new RemoteViews(c.getPackageName(), pal.dark ? R.layout.widget_row_dark : R.layout.widget_row);
            if (position >= rows.size()) return v;
            Row r = rows.get(position);
            v.setTextColor(R.id.row_name, pal.ink);
            v.setTextColor(R.id.row_amount, pal.ink);
            v.setTextViewText(R.id.row_name, r.name);
            v.setTextViewText(R.id.row_amount, r.amount);
            v.setTextViewText(R.id.row_sub, r.sub);
            int sc = "good".equals(r.subColor) ? pal.good : "bad".equals(r.subColor) ? pal.bad
                : "warn".equals(r.subColor) ? pal.warn : pal.muted;
            v.setTextColor(R.id.row_sub, sc);
            if (r.barProgress >= 0) {
                v.setViewVisibility(R.id.row_bar, View.VISIBLE);
                v.setProgressBar(R.id.row_bar, 1000, r.barProgress, false);
                if (Build.VERSION.SDK_INT >= 31) {
                    int color = r.barOver ? pal.bad : r.barColor;
                    v.setColorStateList(R.id.row_bar, "setProgressTintList", ColorStateList.valueOf(color));
                    v.setColorStateList(R.id.row_bar, "setProgressBackgroundTintList", ColorStateList.valueOf(pal.line));
                }
            } else {
                v.setViewVisibility(R.id.row_bar, View.GONE);
            }
            v.setOnClickFillInIntent(R.id.row_root, new Intent());
            return v;
        }

        @Override public RemoteViews getLoadingView() { return null; }
        @Override public int getViewTypeCount() { return 2; }  // light and dark row designs
        @Override public long getItemId(int position) { return position; }
        @Override public boolean hasStableIds() { return false; }
    }
}
