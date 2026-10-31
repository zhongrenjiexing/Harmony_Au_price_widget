package com.local.goldwidget;

import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

public class PinActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        TextView text = new TextView(this);
        text.setTextSize(18);
        layout.addView(text);
        setContentView(layout);

        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        ComponentName provider = new ComponentName(this, GoldWidgetProvider.class);
        if (manager.isRequestPinAppWidgetSupported()) {
            Intent callback = new Intent(this, GoldWidgetProvider.class);
            callback.setAction(GoldWidgetProvider.ACTION_REFRESH);
            PendingIntent success = PendingIntent.getBroadcast(
                    this,
                    1,
                    callback,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            boolean asked = manager.requestPinAppWidget(provider, null, success);
            text.setText(asked ? "请在弹出的窗口里确认，把黄金价格加到桌面。" : "这台平板不允许自动添加小组件。请在桌面长按空白处，选择窗口小工具，找到“金价”。");
        } else {
            text.setText("请在桌面长按空白处，选择窗口小工具，找到“金价”。");
        }
    }
}
