package io.github.turboliu4.tclirtimer;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.format.DateFormat;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Date;

public final class TimerActivity extends Activity {
    private EditText command;
    private EditText minutes;
    private TextView status;
    private SharedPreferences preferences;
    private AlarmManager alarms;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        preferences = getSharedPreferences("timer", MODE_PRIVATE);
        alarms = getSystemService(AlarmManager.class);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            }
            return insets;
        });
        scroll.addView(content);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText("TCL 空调定时");
        title.setTextSize(26);
        content.addView(title);
        TextView help = new TextView(this);
        help.setText("从 LSPosed 日志复制空调遥控指令，粘贴后先试发，再设置延时。");
        content.addView(help);

        command = new EditText(this);
        command.setHint("TCLIR:频率,脉冲…");
        command.setMinLines(3);
        command.setText(preferences.getString("command", ""));
        content.addView(command);

        minutes = new EditText(this);
        minutes.setHint("延时分钟数（1–1440）");
        minutes.setInputType(InputType.TYPE_CLASS_NUMBER);
        minutes.setText(preferences.getString("minutes", "30"));
        content.addView(minutes);

        addButton(content, "立即试发", () -> {
            TimerReceiver.parse(command.getText().toString()).transmit(this);
            Toast.makeText(this, "已调用系统红外发射，请确认空调响应", Toast.LENGTH_SHORT)
                    .show();
        });
        addButton(content, "设置定时", this::schedule);
        addButton(content, "取消定时", () -> {
            PendingIntent pending = pendingIntent("", PendingIntent.FLAG_NO_CREATE);
            if (pending != null) {
                alarms.cancel(pending);
                pending.cancel();
            }
            preferences.edit().remove("deadline").putString("result", "定时已取消")
                    .apply();
            updateStatus();
        });
        status = new TextView(this);
        content.addView(status);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void addButton(LinearLayout content, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(view -> {
            try {
                action.run();
            } catch (NumberFormatException error) {
                Toast.makeText(this, "请检查指令和分钟数的数字格式", Toast.LENGTH_LONG)
                        .show();
            } catch (RuntimeException error) {
                Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        content.addView(button);
    }

    private PendingIntent pendingIntent(String text, int flag) {
        Intent intent = new Intent(this, TimerReceiver.class).putExtra("command", text);
        return PendingIntent.getBroadcast(this, 1, intent, flag | PendingIntent.FLAG_IMMUTABLE);
    }

    private void schedule() {
        String text = command.getText().toString();
        TimerReceiver.parse(text);
        android.hardware.ConsumerIrManager ir =
                getSystemService(android.hardware.ConsumerIrManager.class);
        if (ir == null || !ir.hasIrEmitter()) {
            throw new IllegalStateException("Android 未检测到可用的红外发射器");
        }
        int delay = Integer.parseInt(minutes.getText().toString().trim());
        if (delay < 1 || delay > 1440) {
            throw new IllegalArgumentException("延时范围为 1–1440 分钟");
        }
        if (Build.VERSION.SDK_INT >= 31 && !alarms.canScheduleExactAlarms()) {
            startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName())));
            Toast.makeText(this, "允许精确闹钟后，请回来再点一次设置定时", Toast.LENGTH_LONG)
                    .show();
            return;
        }
        long deadline = System.currentTimeMillis() + delay * 60_000L;
        PendingIntent showTimer = PendingIntent.getActivity(this, 0,
                new Intent(this, TimerActivity.class), PendingIntent.FLAG_IMMUTABLE);
        alarms.setAlarmClock(new AlarmManager.AlarmClockInfo(deadline, showTimer),
                pendingIntent(text, PendingIntent.FLAG_UPDATE_CURRENT));
        preferences.edit().putString("command", text).putString("minutes", Integer.toString(delay))
                .putLong("deadline", deadline)
                .remove("result").apply();
        updateStatus();
    }

    private void updateStatus() {
        long deadline = preferences.getLong("deadline", 0);
        if (deadline != 0) {
            String time = DateFormat.getMediumDateFormat(this).format(new Date(deadline))
                    + " " + DateFormat.getTimeFormat(this).format(new Date(deadline));
            if (Build.VERSION.SDK_INT >= 31 && !alarms.canScheduleExactAlarms()) {
                status.setText("精确闹钟权限已关闭，请授权后重新设置定时");
            } else if (pendingIntent("", PendingIntent.FLAG_NO_CREATE) == null) {
                status.setText("定时已失效，请重新设置");
            } else if (System.currentTimeMillis() >= deadline) {
                status.setText("定时已逾期，尚无发射结果。原定时间：" + time);
            } else {
                status.setText("预计发射时间：" + time);
            }
        } else {
            status.setText(preferences.getString("result", "当前没有定时"));
        }
    }
}
