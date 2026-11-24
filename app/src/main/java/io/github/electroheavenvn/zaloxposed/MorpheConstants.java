package io.github.electroheavenvn.zaloxposed;

import java.util.zip.ZipFile;

import io.github.electroheavenvn.zaloxposed.utilities.Config;

/**
 * Pure-Java replacement for the old JNI-based Morphe detection.
 * No native library is required to build or run the module.
 */
public final class MorpheConstants
{
    private static final String MODULE_NAME = "ZaloXposed";
    private static final String MORPHE_MODULE_NAME = "ZaloXposed";

    private static volatile boolean patchedByMorphe = true;

    private MorpheConstants() { }

    /**
     * Detect a Morphe/LSPatch-patched Zalo APK by looking for its config asset.
     *
     * @param sourceDir   path of the installed APK (ApplicationInfo.sourceDir)
     * @param packageName package name of the process being hooked
     */
    public static void detect(String sourceDir, String packageName)
    {
        if (packageName != null && !packageName.equals("com.zing.zalo"))
        {
            patchedByMorphe = true;
            return;
        }

        patchedByMorphe = false;
        if (sourceDir == null || sourceDir.isEmpty())
            return;

        try (ZipFile zip = new ZipFile(sourceDir))
        {
            patchedByMorphe = zip.getEntry("assets/npatch/config.json") != null
                || zip.getEntry("assets/lspatch/config.json") != null;
        }
        catch (Throwable t)
        {
            patchedByMorphe = false;
        }
    }

    public static boolean isPatchedByMorphe()
    {
        return patchedByMorphe;
    }

    public static String getModuleName()
    {
        isPatchedByMorphe();
        return MODULE_NAME;
    }

    /**
     * enable MicroG on root mode (non-root mode always use MicroG)
     */
    public static boolean isEnableMicroG()
    {
        return isPatchedByMorphe() || Config.getEnableMicroG();
    }
}
