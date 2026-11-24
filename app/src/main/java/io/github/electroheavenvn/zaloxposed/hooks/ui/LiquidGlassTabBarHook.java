package io.github.electroheavenvn.zaloxposed.hooks.ui;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.enums.StringMatchType;
import org.luckypray.dexkit.query.matchers.MethodMatcher;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

import io.github.electroheavenvn.zaloxposed.hooks.BaseHook;
import io.github.electroheavenvn.zaloxposed.utilities.Config;
import io.github.electroheavenvn.zaloxposed.utilities.DexKitCache;
import io.github.electroheavenvn.zaloxposed.utilities.Logger;

/**
 * Replaces Zalo's bottom tab bar with a floating rounded "liquid glass" bar
 * whose highlight follows the touch position.
 */
public class LiquidGlassTabBarHook extends BaseHook
{
    private static final String TAG = "dx_liquid_glass_tabbar_host";
    private static final WeakHashMap<FrameLayout, TabBarReplacement> ATTACHED = new WeakHashMap<>();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static WeakReference<FrameLayout> lastMainRoot = new WeakReference<>(null);
    private static volatile boolean isCapturingBackdrop = false;

    @Override
    public void installHooks() throws Throwable
    {
        Config.addOnConfigChangedListener((key, oldValue, newValue) ->
        {
            if (Config.KEY_ENABLE_LIQUID_GLASS_TABBAR.equals(key))
            {
                MAIN_HANDLER.post(() ->
                {
                    if (Config.getEnableLiquidGlassTabBar())
                    {
                        FrameLayout root = lastMainRoot.get();
                        if (root != null)
                            scheduleAttach(root);
                    }
                    else
                    {
                        for (TabBarReplacement replacement : new ArrayList<>(ATTACHED.values()))
                        {
                            if (replacement != null)
                                replacement.detach();
                        }
                        ATTACHED.clear();
                    }
                });
            }
            else if (Config.KEY_LIQUID_GLASS_THEME.equals(key))
            {
                MAIN_HANDLER.post(() ->
                {
                    for (TabBarReplacement replacement : ATTACHED.values())
                    {
                        if (replacement != null)
                            replacement.refreshTheme();
                    }
                });
            }
        });

        try
        {
            Method dispatchTouchEvent = Activity.class.getDeclaredMethod("dispatchTouchEvent", MotionEvent.class);
            doHook(dispatchTouchEvent, 5, chain ->
            {
                Object arg = chain.getArg(0);
                if (arg instanceof MotionEvent && Config.getEnableLiquidGlassTabBar())
                {
                    MotionEvent event = (MotionEvent)arg;
                    for (TabBarReplacement replacement : ATTACHED.values())
                    {
                        if (replacement != null)
                            replacement.onActivityTouchEvent(event);
                    }
                }
                return chain.proceed();
            });
        }
        catch (Throwable t) { Logger.e(t); }

        try
        {
            Class<?> mainTabView = Class.forName("com.zing.zalo.ui.maintab.MainTabView", false, classLoader);
            List<Method> methods = DexKitCache.GetMethods("LiquidGlassTabBar_MainTabView_OnCreateView", () ->
                bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                    .declaredClass(mainTabView)
                    .modifiers(Modifier.PUBLIC | Modifier.FINAL)
                    .returnType("android.view.View")
                    .paramCount(3)
                    .paramTypes("android.view.LayoutInflater", "android.view.ViewGroup", "android.os.Bundle")
                    .addUsingString("onCreateView", StringMatchType.Equals))), classLoader);

            if (methods.isEmpty())
            {
                Logger.e("LiquidGlassTabBarHook: MainTabView.onCreateView not found");
                return;
            }
            for (Method method : methods)
            {
                doHook(method, chain ->
                {
                    Object result = chain.proceed();
                    if (result instanceof FrameLayout)
                    {
                        FrameLayout root = (FrameLayout)result;
                        lastMainRoot = new WeakReference<>(root);
                        if (Config.getEnableLiquidGlassTabBar())
                            scheduleAttach(root);
                    }
                    return result;
                });
            }
        }
        catch (Throwable t)
        {
            Logger.e(t);
        }
    }

    private static void scheduleAttach(FrameLayout root)
    {
        root.post(() ->
        {
            try
            {
                ViewGroup bar = findBottomTabBar(root);
                if (bar == null || TAG.equals(bar.getTag()))
                    return;
                TabBarReplacement existing = ATTACHED.get(root);
                if (existing != null && existing.original == bar)
                    return;
                if (existing != null)
                    existing.detach();
                ATTACHED.put(root, new TabBarReplacement(root, bar));
            }
            catch (Throwable t)
            {
                Logger.e(t);
            }
        });
    }

    private static List<View> getVisibleChildren(ViewGroup group)
    {
        List<View> result = new ArrayList<>();
        for (int i = 0; i < group.getChildCount(); i++)
        {
            View child = group.getChildAt(i);
            if (child.getVisibility() == View.VISIBLE && child.getWidth() > 0)
                result.add(child);
        }
        return result;
    }

    private static void collectTabBarCandidates(ViewGroup root, ViewGroup current,
                                                int rootWidth, int rootHeight,
                                                int minHeight, int maxHeight,
                                                List<ViewGroup> out)
    {
        if (current.getVisibility() != View.VISIBLE || TAG.equals(current.getTag()))
            return;

        if (current != root && isLikelyTabBar(current, root, rootWidth, rootHeight, minHeight, maxHeight))
            out.add(current);

        for (int i = 0; i < current.getChildCount(); i++)
        {
            View child = current.getChildAt(i);
            if (child instanceof ViewGroup)
                collectTabBarCandidates(root, (ViewGroup)child, rootWidth, rootHeight,
                    minHeight, maxHeight, out);
        }
    }

    private static boolean isLikelyTabBar(ViewGroup candidate, ViewGroup root,
                                          int rootWidth, int rootHeight,
                                          int minHeight, int maxHeight)
    {
        int width = candidate.getWidth();
        int height = candidate.getHeight();
        if (width < rootWidth * 0.78f || height < minHeight || height > maxHeight)
            return false;

        int[] candidateLocation = new int[2];
        int[] rootLocation = new int[2];
        candidate.getLocationInWindow(candidateLocation);
        root.getLocationInWindow(rootLocation);
        if (candidateLocation[1] - rootLocation[1] < rootHeight * 0.73f)
            return false;

        List<View> children = getVisibleChildren(candidate);
        if (children.size() < 2 || children.size() > 7)
            return false;

        int maxCenter = Integer.MIN_VALUE;
        for (View child : children)
        {
            if (child.getWidth() <= 0 || child.getHeight() <= 0)
                return false;
            int center = child.getLeft() + child.getWidth() / 2;
            if (center <= maxCenter)
                return false;
            maxCenter = center;
        }
        return true;
    }

    private static int scoreCandidate(ViewGroup candidate, ViewGroup root, int rootWidth, int rootHeight)
    {
        int score = 40;
        String name = candidate.getClass().getName().toLowerCase();
        if (name.contains("tab") || name.contains("bottom"))
            score += 50;
        if (candidate.getId() != View.NO_ID)
        {
            try
            {
                String entry = candidate.getResources().getResourceEntryName(candidate.getId()).toLowerCase();
                if (entry.contains("tab") || entry.contains("bottom"))
                    score += 50;
            }
            catch (Throwable ignored) { }
        }
        int children = getVisibleChildren(candidate).size();
        if (children >= 3 && children <= 6)
            score += 30;
        for (View child : getVisibleChildren(candidate))
        {
            if (child.isClickable())
                score += 6;
        }
        int[] location = new int[2];
        int[] rootLocation = new int[2];
        candidate.getLocationInWindow(location);
        root.getLocationInWindow(rootLocation);
        int bottom = location[1] - rootLocation[1] + candidate.getHeight();
        if (bottom >= rootHeight - 180)
            score += 20;
        return score;
    }

    private static ViewGroup findBottomTabBar(ViewGroup root)
    {
        int width = root.getWidth();
        int height = root.getHeight();
        if (width <= 0 || height <= 0)
            return null;

        float density = root.getResources().getDisplayMetrics().density;
        int minHeight = Math.round(38f * density);
        int maxHeight = Math.round(112f * density);

        List<ViewGroup> candidates = new ArrayList<>();
        collectTabBarCandidates(root, root, width, height, minHeight, maxHeight, candidates);
        if (candidates.isEmpty())
            return null;

        ViewGroup best = null;
        int bestScore = Integer.MIN_VALUE;
        for (ViewGroup candidate : candidates)
        {
            int score = scoreCandidate(candidate, root, width, height);
            if (score > bestScore)
            {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private static final class TabBarReplacement
    {
        private final FrameLayout host;
        private final ViewGroup original;
        private final LiquidGlassTabBarView glass;
        private final ViewGroup.LayoutParams originalParams;

        TabBarReplacement(FrameLayout root, ViewGroup bar)
        {
            this.host = new FrameLayout(bar.getContext());
            this.host.setId(View.generateViewId());
            this.original = bar;
            this.originalParams = bar.getLayoutParams();

            this.glass = new LiquidGlassTabBarView(bar.getContext());
            ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            this.glass.setLayoutParams(params);
            this.glass.setClickable(false);
            this.glass.setFocusable(false);

            ViewGroup parent = (ViewGroup)bar.getParent();
            int index = parent.indexOfChild(bar);
            parent.removeView(bar);
            host.addView(glass);
            host.addView(bar);
            host.setTag(TAG);
            parent.addView(host, index, originalParams);
        }

        void onActivityTouchEvent(MotionEvent event)
        {
            glass.handleTouchEvent(event);
        }

        void refreshTheme()
        {
            glass.applyTheme();
        }

        void detach()
        {
            try
            {
                ViewGroup parent = (ViewGroup)host.getParent();
                if (parent == null)
                    return;
                int index = parent.indexOfChild(host);
                parent.removeView(host);
                host.removeView(glass);
                host.removeView(original);
                parent.addView(original, index, originalParams);
            }
            catch (Throwable t)
            {
                Logger.e(t);
            }
        }
    }

    private static final class LiquidGlassTabBarView extends View
    {
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF capsule = new RectF();
        private final RectF highlight = new RectF();
        private final float density;
        private float currentTabPos = -1f;
        private float targetTabPos = -1f;
        private float glowX = -1f;
        private float glowAlpha = 0f;

        LiquidGlassTabBarView(android.content.Context context)
        {
            super(context);
            density = getResources().getDisplayMetrics().density;
            borderPaint.setStyle(Paint.Style.STROKE);
            applyTheme();
        }

        void applyTheme()
        {
            int theme = Config.getLiquidGlassTheme();
            boolean dark = (getContext().getResources().getConfiguration().uiMode & 0x30) == 0x20;
            int accent = theme == 1 ? Color.parseColor("#FF4081")
                : theme == 2 ? Color.parseColor("#00BCD4")
                : dark ? Color.parseColor("#0A84FF") : Color.parseColor("#007AFF");
            fillPaint.setColor(dark ? 0x22FFFFFF : 0x2BF8F8FF);
            borderPaint.setColor(dark ? 0x33FFFFFF : 0x55FFFFFF);
            borderPaint.setStrokeWidth(1.2f * density);
            highlightPaint.setColor(colorWithAlpha(accent, dark ? 70 : 55));
            glowPaint.setColor(colorWithAlpha(accent, 45));
            invalidate();
        }

        private static int colorWithAlpha(int color, int alpha)
        {
            return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
        }

        void handleTouchEvent(MotionEvent event)
        {
            float x = event.getX();
            if (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL)
            {
                glowAlpha = 0f;
            }
            else
            {
                targetTabPos = x;
                if (currentTabPos < 0f)
                    currentTabPos = x;
                glowX = x;
                glowAlpha = 1f;
            }
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh)
        {
            super.onSizeChanged(w, h, oldw, oldh);
            float pad = 8f * density;
            float height = Math.min(h - 8f * density, 64f * density);
            float top = h - height - 6f * density;
            capsule.set(pad, top, w - pad, top + height);
        }

        @Override
        protected void onDraw(Canvas canvas)
        {
            super.onDraw(canvas);
            if (capsule.width() <= 0)
                return;

            float radius = capsule.height() / 2f;
            canvas.drawRoundRect(capsule, radius, radius, fillPaint);
            canvas.drawRoundRect(capsule, radius, radius, borderPaint);

            if (currentTabPos >= 0f)
            {
                currentTabPos += (targetTabPos - currentTabPos) * 0.22f;
                float tabWidth = Math.max(48f * density, capsule.width() / 7f);
                float left = Math.max(capsule.left + 2f,
                    Math.min(currentTabPos - tabWidth / 2f, capsule.right - tabWidth - 2f));
                highlight.set(left, capsule.top + 3f * density,
                    left + tabWidth, capsule.bottom - 3f * density);
                canvas.drawRoundRect(highlight, highlight.height() / 2f,
                    highlight.height() / 2f, highlightPaint);
            }

            if (glowAlpha > 0.01f)
            {
                float glowRadius = 26f * density;
                glowPaint.setAlpha((int)(45 * glowAlpha));
                canvas.drawCircle(glowX, capsule.centerY(), glowRadius, glowPaint);
                glowAlpha *= 0.9f;
            }

            if (glowAlpha > 0.01f || (currentTabPos >= 0f && Math.abs(targetTabPos - currentTabPos) > 0.5f))
                invalidate();
        }
    }
}
