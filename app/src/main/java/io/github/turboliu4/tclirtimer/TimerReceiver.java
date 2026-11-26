package io.github.turboliu4.tclirtimer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.ConsumerIrManager;

public final class TimerReceiver extends BroadcastReceiver {
    static IrCommand parse(String text) {
        String command = text.trim();
        int marker = command.indexOf("TCLIR:");
        if (marker >= 0) {
            command = command.substring(marker + "TCLIR:".length()).split("\\R", 2)[0];
        }
        String[] values = command.split(",", -1);
        if (values.length < 2 || values.length > 4097) {
            throw new IllegalArgumentException("请输入一条完整的 TCLIR 指令");
        }
        int frequency = Integer.parseInt(values[0].trim());
        if (frequency <= 0) {
            throw new IllegalArgumentException("载波频率必须为正数");
        }
        int[] pattern = new int[values.length - 1];
        long duration = 0;
        for (int index = 0; index < pattern.length; index++) {
            int pulse = Integer.parseInt(values[index + 1].trim());
            if (pulse <= 0 || (duration += pulse) > 2_000_000) {
                throw new IllegalArgumentException("红外脉冲无效或超过 2 秒");
            }
            pattern[index] = pulse;
        }
        return new IrCommand(frequency, pattern);
    }

    static final class IrCommand {
        final int frequency;
        final int[] pattern;

        IrCommand(int frequency, int[] pattern) {
            this.frequency = frequency;
            this.pattern = pattern;
        }

        void transmit(Context context) {
            ConsumerIrManager manager = context.getSystemService(ConsumerIrManager.class);
            if (manager == null || !manager.hasIrEmitter()) {
                throw new IllegalStateException("Android 未检测到可用的红外发射器");
            }
            manager.transmit(frequency, pattern);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String result;
        try {
            parse(intent.getStringExtra("command")).transmit(context);
            result = "已调用系统红外发射";
        } catch (RuntimeException error) {
            result = "定时发射失败：" + error.getMessage();
        }
        context.getSharedPreferences("timer", Context.MODE_PRIVATE).edit()
                .remove("deadline").putString("result", result).apply();
    }
}
