package io.github.electroheavenvn.zaloxposed.hooks.custommenu;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;

public class SendAsStickerHook {

    private static final String TAG_STICKER_BTN = "zxp_btn_send_as_sticker";
    public static final AtomicBoolean sendAsStickerEnabled = new AtomicBoolean(false);

    public static class WebpResult {
        public final File file;
        public final int width;
        public final int height;

        public WebpResult(File file, int width, int height) {
            this.file = file;
            this.width = width;
            this.height = height;
        }
    }

    /**
     * Gọi hàm này trong ZaloXposedLoader sau khi load package com.zing.zalo
     */
    public static void hook(XposedInterface xposed) {
        try {
            Method onCreate = Activity.class.getDeclaredMethod("onCreate", Bundle.class);
            xposed.hook(onCreate, ActivityOnCreateHooker.class);
        } catch (Throwable ignored) {
        }
    }

    public static class ActivityOnCreateHooker implements XposedInterface.Hooker {
        public static void after(XposedInterface.AfterHookCallback callback) {
            Object thisObj = callback.getThisObject();
            if (!(thisObj instanceof Activity)) return;

            Activity activity = (Activity) thisObj;
            String name = activity.getClass().getName().toLowerCase();

            // Chỉ hiện nút trong các màn hình chọn / xem trước ảnh
            if (name.contains("photo") || name.contains("gallery")
                    || name.contains("picker") || name.contains("media")
                    || name.contains("image")) {
                activity.getWindow().getDecorView().post(() -> attachCompactButton(activity));
            }
        }
    }

    /**
     * Tạo nút "Gửi thành Sticker" nhỏ gọn dạng pill
     */
    private static void attachCompactButton(Activity activity) {
        try {
            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root == null || root.findViewWithTag(TAG_STICKER_BTN) != null) return;

            TextView btn = new TextView(activity);
            btn.setTag(TAG_STICKER_BTN);
            btn.setText("Gửi thành Sticker");
            btn.setTextColor(Color.WHITE);
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            btn.setGravity(Gravity.CENTER);

            int padH = dp(activity, 12);
            int padV = dp(activity, 6);
            btn.setPadding(padH, padV, padH, padV);
            btn.setElevation(dp(activity, 4));
            updateButtonStyle(btn, activity, sendAsStickerEnabled.get());

            btn.setOnClickListener(v -> {
                boolean next = !sendAsStickerEnabled.get();
                sendAsStickerEnabled.set(next);
                updateButtonStyle(btn, activity, next);
                Toast.makeText(
                        activity,
                        next ? "Đã bật: Ảnh tiếp theo sẽ gửi thành Sticker" : "Đã tắt gửi thành Sticker",
                        Toast.LENGTH_SHORT
                ).show();
            });

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.gravity = Gravity.TOP | Gravity.END;
            lp.topMargin = dp(activity, 56);
            lp.rightMargin = dp(activity, 12);

            root.addView(btn, lp);
        } catch (Throwable ignored) {
        }
    }

    private static void updateButtonStyle(TextView btn, Context ctx, boolean active) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(ctx, 18));
        bg.setColor(active ? Color.parseColor("#0068FF") : Color.parseColor("#B31E1E1E"));
        bg.setStroke(dp(ctx, 1), active ? Color.parseColor("#66B2FF") : Color.parseColor("#66FFFFFF"));
        btn.setBackground(bg);
        btn.setText(active ? "✓ Gửi thành Sticker" : "Gửi thành Sticker");
    }

    private static int dp(Context ctx, int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, ctx.getResources().getDisplayMetrics()
        ));
    }

    /**
     * Convert ảnh JPG/PNG sang file .webp chuẩn sticker (tối đa 512x512)
     */
    public static WebpResult convertToWebp(File inputFile, File cacheDir) {
        try {
            Bitmap original = BitmapFactory.decodeFile(inputFile.getAbsolutePath());
            if (original == null) return null;

            int maxSize = 512;
            float scale = Math.min(
                    Math.min((float) maxSize / original.width(), (float) maxSize / original.height()),
                    1.0f
            );
            int targetW = Math.max(1, Math.round(original.getWidth() * scale));
            int targetH = Math.max(1, Math.round(original.getHeight() * scale));

            Bitmap resized = original;
            if (scale < 1.0f) {
                resized = Bitmap.createScaledBitmap(original, targetW, targetH, true);
                original.recycle();
            }

            File outFile = new File(cacheDir, "zsticker_" + System.currentTimeMillis() + ".webp");
            Bitmap.CompressFormat format = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    ? Bitmap.CompressFormat.WEBP_LOSSY
                    : Bitmap.CompressFormat.WEBP;

            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                resized.compress(format, 85, fos);
            }
            resized.recycle();

            return new WebpResult(outFile, targetW, targetH);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Vá JSON payload gửi ảnh thành Custom Sticker (port từ zca-js)
     */
    public static void patchStickerPayload(JSONObject json, String webpUrl, int width, int height) {
        try {
            JSONObject webpObj = new JSONObject();
            webpObj.put("width", width);
            webpObj.put("height", height);
            webpObj.put("url", webpUrl);

            JSONObject jcpObj = new JSONObject();
            jcpObj.put("pStickerType", 1);

            JSONObject extObj = new JSONObject();
            extObj.put("sSrcStr", "");
            extObj.put("sSrcType", -1);

            JSONObject propsObj = new JSONObject();
            propsObj.put("subType", 0);
            propsObj.put("color", -1);
            propsObj.put("size", -1);
            propsObj.put("type", 3);
            propsObj.put("ext", extObj.toString());

            json.put("oriUrl", webpUrl);
            json.put("hdUrl", webpUrl);
            json.put("webp", webpObj.toString());
            json.put("jcp", jcpObj.toString());
            json.put("properties", propsObj.toString());

            // Tự tắt cờ sau khi vá xong gói tin
            sendAsStickerEnabled.set(false);
        } catch (Throwable ignored) {
        }
    }
}
