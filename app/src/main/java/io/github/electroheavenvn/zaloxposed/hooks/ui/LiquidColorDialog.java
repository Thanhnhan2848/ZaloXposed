package io.github.electroheavenvn.zaloxposed.hooks.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import io.github.electroheavenvn.zaloxposed.utilities.Config;
import io.github.electroheavenvn.zaloxposed.utilities.Utils;

/** Simple hex color picker for the Liquid Glass tab bar. */
public final class LiquidColorDialog
{
    private LiquidColorDialog() { }

    public static void show(Activity activity)
    {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint("#007AFF");
        input.setText(Config.getLiquidGlassColor());
        int pad = Math.round(16f * activity.getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad / 2, pad, pad / 2);

        new AlertDialog.Builder(activity)
            .setTitle(Utils.IsEnglish() ? "Liquid Glass color" : "Màu thanh Liquid Glass")
            .setMessage(Utils.IsEnglish()
                ? "Enter a hex color, or leave empty to use the theme default."
                : "Nhập mã màu hex, hoặc để trống để dùng màu mặc định.")
            .setView(input)
            .setPositiveButton(android.R.string.ok, (dialog, which) ->
            {
                String value = input.getText().toString().trim();
                if (!value.isEmpty())
                {
                    try
                    {
                        if (!value.startsWith("#"))
                            value = "#" + value;
                        Color.parseColor(value);
                    }
                    catch (Throwable ignored)
                    {
                        Toast.makeText(activity,
                            Utils.IsEnglish() ? "Invalid color" : "Màu không hợp lệ",
                            Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                Config.setLiquidGlassColor(value.toUpperCase());
                Toast.makeText(activity,
                    Utils.IsEnglish() ? "Liquid Glass color saved" : "Đã lưu màu thanh Liquid Glass",
                    Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }
}
