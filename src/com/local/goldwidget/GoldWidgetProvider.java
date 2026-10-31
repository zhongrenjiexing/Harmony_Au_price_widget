package com.local.goldwidget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.widget.RemoteViews;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class GoldWidgetProvider extends AppWidgetProvider {
    static final String ACTION_REFRESH = "com.local.goldwidget.REFRESH";
    private static final long INTERVAL_MS = 10L * 60L * 1000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent == null ? null : intent.getAction();
        if (ACTION_REFRESH.equals(action)
                || AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(action)
                || Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            final PendingResult pending = goAsync();
            final Context app = context.getApplicationContext();
            new Thread(() -> {
                try {
                    updateAll(app);
                    schedule(app);
                } finally {
                    pending.finish();
                }
            }).start();
        }
    }

    @Override
    public void onEnabled(Context context) {
        schedule(context);
    }

    @Override
    public void onDisabled(Context context) {
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarm.cancel(refreshIntent(context));
    }

    static void schedule(Context context) {
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        long when = SystemClock.elapsedRealtime() + INTERVAL_MS;
        alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME, when, refreshIntent(context));
    }

    private static PendingIntent refreshIntent(Context context) {
        Intent intent = new Intent(context, GoldWidgetProvider.class);
        intent.setAction(ACTION_REFRESH);
        return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName name = new ComponentName(context, GoldWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(name);
        if (ids == null || ids.length == 0) {
            return;
        }
        Quote quote = fetch();
        for (int id : ids) {
            manager.updateAppWidget(id, render(context, quote));
        }
    }

    private static RemoteViews render(Context context, Quote quote) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_gold);
        views.setTextViewText(R.id.cny, quote.cny);
        views.setTextViewText(R.id.cny_unit, quote.cnyMeta);
        views.setTextViewText(R.id.cny_time, quote.cnyTime);
        views.setTextViewText(R.id.usd, quote.usd);
        views.setTextViewText(R.id.usd_meta, quote.usdMeta);
        views.setTextViewText(R.id.usd_time, quote.usdTime);
        views.setOnClickPendingIntent(R.id.root, refreshIntent(context));
        return views;
    }

    private static Quote fetch() {
        try {
            String body = http("https://hq.sinajs.cn/list=gds_AUTD,hf_XAU");
            String autd = field(body, "hq_str_gds_AUTD", 0);
            String xau = field(body, "hq_str_hf_XAU", 0);
            if (autd == null && xau == null) {
                return Quote.error();
            }
            String cnyWhen = shortStamp(field(body, "hq_str_gds_AUTD", 12), field(body, "hq_str_gds_AUTD", 6));
            String xauWhen = shortStamp(field(body, "hq_str_hf_XAU", 12), field(body, "hq_str_hf_XAU", 6));
            String cny = autd == null ? "—" : trimPrice(autd);
            String cnyMeta = autd == null ? "" : "元/克 - 上金所";
            String usd = xau == null ? "—" : trimPrice(xau);
            String usdMeta = xau == null ? "" : "美元/盎司 - 伦敦金";
            return new Quote(cny, cnyMeta, cnyWhen == null ? "" : cnyWhen, usd, usdMeta, xauWhen == null ? "" : xauWhen);
        } catch (Exception e) {
            return Quote.error();
        }
    }

    private static String trimPrice(String raw) {
        try {
            double value = Double.parseDouble(raw);
            if (Math.abs(value - Math.rint(value)) < 0.001) {
                return String.format(Locale.US, "%.0f", value);
            }
            return String.format(Locale.US, "%.2f", value);
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    private static String field(String body, String key, int index) {
        int start = body.indexOf(key + "=\"");
        if (start < 0) {
            return null;
        }
        start += key.length() + 2;
        int end = body.indexOf('"', start);
        if (end < 0) {
            return null;
        }
        String[] parts = body.substring(start, end).split(",", -1);
        if (index >= parts.length) {
            return null;
        }
        String value = parts[index].trim();
        return value.isEmpty() ? null : value;
    }

    private static String http(String address) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestProperty("Referer", "https://finance.sina.com.cn");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line);
            }
            return out.toString();
        } finally {
            conn.disconnect();
        }
    }

    private static String shortStamp(String date, String clock) {
        String day = date;
        if (date != null && date.length() >= 10 && date.charAt(4) == '-') {
            day = date.substring(5, 10);
        }
        String hm = null;
        if (clock != null && clock.length() >= 5 && clock.charAt(2) == ':') {
            hm = clock.substring(0, 5);
        }
        if (day == null) {
            return hm;
        }
        if (hm == null) {
            return day;
        }
        return day + " " + hm;
    }

    private static final class Quote {
        final String cny;
        final String cnyMeta;
        final String cnyTime;
        final String usd;
        final String usdMeta;
        final String usdTime;

        Quote(String cny, String cnyMeta, String cnyTime, String usd, String usdMeta, String usdTime) {
            this.cny = cny;
            this.cnyMeta = cnyMeta;
            this.cnyTime = cnyTime;
            this.usd = usd;
            this.usdMeta = usdMeta;
            this.usdTime = usdTime;
        }

        static Quote error() {
            return new Quote("—", "", "", "—", "暂时无法获取", "点击重试");
        }
    }
}
