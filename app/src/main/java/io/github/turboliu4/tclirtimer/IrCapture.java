package io.github.turboliu4.tclirtimer;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class IrCapture implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam app) {
        if (app.packageName.equals("android")
                || app.packageName.equals("io.github.turboliu4.tclirtimer")) {
            return;
        }
        XposedHelpers.findAndHookMethod("android.hardware.ConsumerIrManager", null,
                "transmit", int.class, int[].class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam call) {
                        if (call.hasThrowable() || call.args[1] == null) {
                            return;
                        }
                        int[] pattern = (int[]) call.args[1];
                        StringBuilder command = new StringBuilder("TCLIR:")
                                .append(call.args[0]);
                        for (int duration : pattern) {
                            command.append(',').append(duration);
                        }
                        XposedBridge.log(command.toString());
                    }
                });
    }
}
