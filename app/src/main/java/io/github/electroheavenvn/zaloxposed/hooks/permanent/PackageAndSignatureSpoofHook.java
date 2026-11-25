package io.github.electroheavenvn.zaloxposed.hooks.permanent;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.content.pm.VersionedPackage;
import android.os.Build;
import android.os.Process;
import android.util.ArraySet;
import android.util.Base64;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import io.github.electroheavenvn.zaloxposed.Common;
import io.github.electroheavenvn.zaloxposed.hooks.BaseHook;
import io.github.electroheavenvn.zaloxposed.utilities.Logger;

/**
 * Makes the module look like the original Zalo package/signature to Zalo and
 * to Google Play Services. Pure Java - no native patching library required.
 */
public class PackageAndSignatureSpoofHook extends BaseHook
{
    private static final String ORIG_PKG = "com.zing.zalo";
    private static final String VNG_CERT_B64 =
        "MIIBnTCCAQagAwIBAgIETxeJcTANBgkqhkiG9w0BAQUFADATMREwDwYDVQQDEwh6aW5ndGFsazAeFw0xMjAxMTkwMzA5MzdaFw0zNzAxMTIwMzA5MzdaMBMxETAPBgNVBAMTCHppbmd0YWxrMIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDY3IburM2Nf+ciORo6GuA0CCskrwymMkTS/xLMn9pNapwb3/XFh8ZIrD6Z5UhSylLO4BIDy5n1lFk6seAjvNimvpseBWw95zYxxW+F9e2FduhQ9n3bygALUzhIHfI4oNJ8KTueKLaaziTJySYwYyI4MglMhSAQAbe+fyEHpFKDXwIDAQABMA0GCSqGSIb3DQEBBQUAA4GBAK69ivJ/wxeLYILR23pfZqrR21XIIxRcXdIf5yHiKfkLdwJzhlRDK1xehmf0mV6dIGrbfSbD23DyyXFjjUS3YkFt9dUQoIUm7ZH90cHo5nUdiDLsMhVMEWgOZH5gXA6GEnAscFJDJKYRQkxpxNPkOidWVR7sX05N6WYzEZTHRISh";

    private static byte[] vngCertBytes;
    private static byte[] vngCertSha256;
    private static PublicKey vngPublicKey;
    private static Signature vngSignature;

    private static void initCert()
    {
        if (vngSignature != null)
            return;
        try
        {
            vngCertBytes = Base64.decode(VNG_CERT_B64, 0);
            vngCertSha256 = MessageDigest.getInstance("SHA-256").digest(vngCertBytes);
            vngSignature = new Signature(vngCertBytes);

            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            X509Certificate certificate = (X509Certificate)factory.generateCertificate(
                new ByteArrayInputStream(vngCertBytes));
            vngPublicKey = certificate.getPublicKey();
        }
        catch (Throwable t)
        {
            Logger.e(t);
        }
    }

    /** hashAlg: 0 = raw DER certificate, 1 = SHA-256 of the certificate. */
    private static boolean matchesVngCert(byte[] certificate, int hashAlg)
    {
        if (certificate == null)
            return false;
        if (hashAlg == 0)
            return vngCertBytes != null && Arrays.equals(certificate, vngCertBytes);
        if (hashAlg == 1)
            return vngCertSha256 != null && Arrays.equals(certificate, vngCertSha256);
        return false;
    }

    private static void spoofSigningInfo(SigningInfo signingInfo)
    {
        try
        {
            Field detailsField = SigningInfo.class.getDeclaredField("mSigningDetails");
            detailsField.setAccessible(true);
            Object details = detailsField.get(signingInfo);
            if (details == null)
                return;

            Signature[] signatures = new Signature[]{vngSignature};
            try
            {
                Field f = details.getClass().getDeclaredField("mSignatures");
                f.setAccessible(true);
                f.set(details, signatures);
            }
            catch (Throwable ignored) { }
            try
            {
                Field f = details.getClass().getDeclaredField("mPastSigningCertificates");
                f.setAccessible(true);
                f.set(details, signatures);
            }
            catch (Throwable ignored) { }
            if (vngPublicKey != null)
            {
                try
                {
                    Field f = details.getClass().getDeclaredField("mPublicKeys");
                    f.setAccessible(true);
                    ArraySet<PublicKey> keys = new ArraySet<>();
                    keys.add(vngPublicKey);
                    f.set(details, keys);
                }
                catch (Throwable ignored) { }
            }
        }
        catch (Throwable t)
        {
            Logger.e(t);
        }
    }

    private static void spoofPackageInfoSignatures(PackageInfo info, int flags)
    {
        if (info == null || vngSignature == null)
            return;
        if ((flags & 0x40) == 0 || info.signatures == null)
            info.signatures = new Signature[]{vngSignature};
        if (Build.VERSION.SDK_INT >= 28 && info.signingInfo != null)
            spoofSigningInfo(info.signingInfo);
    }

    private static Object fixPackageInfo(Object result, String requested, int flags,
                                         String actualPackage, boolean cloned)
    {
        if (result instanceof PackageInfo)
        {
            PackageInfo info = (PackageInfo)result;
            if (cloned && ORIG_PKG.equals(requested))
                info.packageName = ORIG_PKG;
            spoofPackageInfoSignatures(info, flags);
        }
        return result;
    }

    @Override
    public void installHooks() throws Throwable
    {
        initCert();
        final String actualPackage = Common.getPackageName();
        final boolean cloned = actualPackage != null && !ORIG_PKG.equals(actualPackage);

        try
        {
            Class<?> pm = Class.forName("android.app.ApplicationPackageManager", false, classLoader);
            final int priority = 10;

            try
            {
                java.lang.reflect.Method method = pm.getDeclaredMethod("getPackageInfo", String.class, int.class);
                doHook(method, priority, chain ->
                {
                    String requested = (String)chain.getArg(0);
                    int flags = (Integer)chain.getArg(1);
                    if (!ORIG_PKG.equals(requested)
                        && (actualPackage == null || !actualPackage.equals(requested)))
                        return chain.proceed();
                    Object result = (cloned && ORIG_PKG.equals(requested))
                        ? chain.proceed(new Object[]{actualPackage, flags})
                        : chain.proceed();
                    return fixPackageInfo(result, requested, flags, actualPackage, cloned);
                });
            }
            catch (Throwable t) { Logger.e(t); }

            try
            {
                java.lang.reflect.Method method = pm.getDeclaredMethod("getPackageInfo", VersionedPackage.class, int.class);
                doHook(method, priority, chain ->
                {
                    Object arg = chain.getArg(0);
                    if (!(arg instanceof VersionedPackage))
                        return chain.proceed();
                    VersionedPackage versioned = (VersionedPackage)arg;
                    String requested = versioned.getPackageName();
                    int flags = (Integer)chain.getArg(1);
                    if (!ORIG_PKG.equals(requested)
                        && (actualPackage == null || !actualPackage.equals(requested)))
                        return chain.proceed();
                    Object result = (cloned && ORIG_PKG.equals(requested))
                        ? chain.proceed(new Object[]{
                            new VersionedPackage(actualPackage, versioned.getLongVersionCode()), flags})
                        : chain.proceed();
                    return fixPackageInfo(result, requested, flags, actualPackage, cloned);
                });
            }
            catch (Throwable t) { Logger.e(t); }

            try
            {
                java.lang.reflect.Method method = pm.getDeclaredMethod("hasSigningCertificate",
                    String.class, byte[].class, int.class);
                doHook(method, priority, chain ->
                {
                    String requested = (String)chain.getArg(0);
                    byte[] cert = (byte[])chain.getArg(1);
                    int hashAlg = (Integer)chain.getArg(2);
                    if ((ORIG_PKG.equals(requested)
                        || (actualPackage != null && actualPackage.equals(requested)))
                        && matchesVngCert(cert, hashAlg))
                        return true;
                    return chain.proceed();
                });
            }
            catch (Throwable t) { Logger.e(t); }

            try
            {
                java.lang.reflect.Method method = pm.getDeclaredMethod("hasSigningCertificate",
                    int.class, byte[].class, int.class);
                doHook(method, priority, chain ->
                {
                    int uid = (Integer)chain.getArg(0);
                    byte[] cert = (byte[])chain.getArg(1);
                    int hashAlg = (Integer)chain.getArg(2);
                    if (uid == Process.myUid() && matchesVngCert(cert, hashAlg))
                        return true;
                    return chain.proceed();
                });
            }
            catch (Throwable t) { Logger.e(t); }

            if (cloned)
            {
                try
                {
                    java.lang.reflect.Method method = pm.getDeclaredMethod("getApplicationInfo", String.class, int.class);
                    doHook(method, priority, chain ->
                    {
                        String requested = (String)chain.getArg(0);
                        if (!ORIG_PKG.equals(requested))
                            return chain.proceed();
                        Object result = chain.proceed(new Object[]{actualPackage, chain.getArg(1)});
                        if (result instanceof ApplicationInfo)
                            ((ApplicationInfo)result).packageName = ORIG_PKG;
                        return result;
                    });
                }
                catch (Throwable t) { Logger.e(t); }
            }

            try
            {
                java.lang.reflect.Method method = pm.getDeclaredMethod("getInstallerPackageName", String.class);
                doHook(method, priority, chain ->
                {
                    String requested = (String)chain.getArg(0);
                    if (ORIG_PKG.equals(requested)
                        || (actualPackage != null && actualPackage.equals(requested)))
                        return "com.android.vending";
                    return chain.proceed();
                });
            }
            catch (Throwable t) { Logger.e(t); }
        }
        catch (Throwable t)
        {
            Logger.e(t);
        }
    }
}
