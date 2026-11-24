package io.github.electroheavenvn.zaloxposed.hooks.permanent;

import android.content.Context;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.regex.Pattern;

import io.github.electroheavenvn.zaloxposed.hooks.BaseHook;
import io.github.electroheavenvn.zaloxposed.utilities.Logger;
import io.github.libxposed.api.XposedInterface;

/**
 * Overrides branding shown in stock Zalo UI (text views and toasts).
 */
public class BrandOverrideHook extends BaseHook
{
    private static final Pattern AUTHOR_PATTERN = Pattern.compile("ElectroHeavenVN", Pattern.CASE_INSENSITIVE);
    private static final Pattern MORPHE_PATTERN = Pattern.compile("ZaloMorphe", Pattern.CASE_INSENSITIVE);

    private static final String TARGET_AUTHOR = "ZaloXposed";
    private static final String TARGET_MODULE = "ZaloXposed";

    private static CharSequence replaceBrand(CharSequence input)
    {
        if (input == null || input.length() == 0)
            return input;

        String text = input.toString();
        String lower = text.toLowerCase();
        boolean hasAuthor = lower.contains("electroheavenvn");
        boolean hasMorphe = lower.contains("zalomorphe");
        if (!hasAuthor && !hasMorphe)
            return input;

        if (hasMorphe)
            text = MORPHE_PATTERN.matcher(text).replaceAll(TARGET_MODULE);
        if (hasAuthor)
            text = AUTHOR_PATTERN.matcher(text).replaceAll(TARGET_AUTHOR);
        return text;
    }

    @Override
    public void installHooks() throws Throwable
    {
        try
        {
            Method setText = TextView.class.getDeclaredMethod("setText", CharSequence.class, TextView.BufferType.class);
            doHook(setText, 100, chain ->
            {
                Object text = chain.getArg(0);
                if (!(text instanceof CharSequence))
                    return chain.proceed();
                CharSequence replaced = replaceBrand((CharSequence)text);
                if (replaced == text)
                    return chain.proceed();
                return chain.proceed(new Object[]{replaced, chain.getArg(1)});
            });
        }
        catch (Throwable t) { Logger.e(t); }

        try
        {
            Method setHint = TextView.class.getDeclaredMethod("setHint", CharSequence.class);
            doHook(setHint, 100, chain ->
            {
                Object arg = chain.getArg(0);
                if (!(arg instanceof CharSequence))
                    return chain.proceed();
                CharSequence replaced = replaceBrand((CharSequence)arg);
                if (replaced == arg)
                    return chain.proceed();
                return chain.proceed(new Object[]{replaced});
            });
        }
        catch (Throwable t) { Logger.e(t); }

        try
        {
            Method makeText = Toast.class.getDeclaredMethod("makeText", Context.class, CharSequence.class, int.class);
            doHook(makeText, 100, chain ->
            {
                Object text = chain.getArg(1);
                if (!(text instanceof CharSequence))
                    return chain.proceed();
                CharSequence replaced = replaceBrand((CharSequence)text);
                if (replaced == text)
                    return chain.proceed();
                return chain.proceed(new Object[]{chain.getArg(0), replaced, chain.getArg(2)});
            });
        }
        catch (Throwable t) { Logger.e(t); }

        try
        {
            Method toastSetText = Toast.class.getDeclaredMethod("setText", CharSequence.class);
            doHook(toastSetText, 100, chain ->
            {
                Object arg = chain.getArg(0);
                if (!(arg instanceof CharSequence))
                    return chain.proceed();
                CharSequence replaced = replaceBrand((CharSequence)arg);
                if (replaced == arg)
                    return chain.proceed();
                return chain.proceed(new Object[]{replaced});
            });
        }
        catch (Throwable t) { Logger.e(t); }
    }
}
