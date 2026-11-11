package com.k4811.launcher.pip;

import android.util.Log;

import java.lang.reflect.Method;

/**
 * Acesso às APIs internas do Android 10 que os PIPs usam (as mesmas do android.app.ActivityView).
 *
 * <p>O app não é assinado com a chave de plataforma, então as APIs ocultas ficam bloqueadas. No
 * Android 10 a reflexão feita "de dentro" do java.lang.Class passa pelo bloqueio: com ela pedimos
 * ao VMRuntime a isenção de tudo ("L") uma vez, e daí em diante a reflexão comum funciona.
 */
public final class Hidden {
    private static final String TAG = "GdmPip";
    private static boolean unlocked;

    private Hidden() {
    }

    public static synchronized void unlock() {
        if (unlocked) return;
        unlocked = true;
        try {
            Method forName = Class.class.getDeclaredMethod("forName", String.class);
            Method getDeclaredMethod = Class.class.getDeclaredMethod(
                "getDeclaredMethod", String.class, Class[].class);
            Class<?> runtime = (Class<?>) forName.invoke(null, "dalvik.system.VMRuntime");
            Method getRuntime = (Method) getDeclaredMethod.invoke(runtime, "getRuntime", null);
            Method exempt = (Method) getDeclaredMethod.invoke(runtime, "setHiddenApiExemptions",
                (Object) new Class<?>[] {String[].class});
            exempt.invoke(getRuntime.invoke(null), new Object[] {new String[] {"L"}});
        } catch (Throwable t) {
            Log.w(TAG, "isenção das APIs internas falhou", t);
        }
    }

    static Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = find(target.getClass(), name, types);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    static Object callStatic(String className, String name) throws Exception {
        Method m = Class.forName(className).getDeclaredMethod(name);
        m.setAccessible(true);
        return m.invoke(null);
    }

    /** Procura também nas superclasses e interfaces (os stubs do binder implementam a interface). */
    private static Method find(Class<?> c, String name, Class<?>[] types) throws NoSuchMethodException {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                return k.getDeclaredMethod(name, types);
            } catch (NoSuchMethodException ignored) {
            }
            for (Class<?> i : k.getInterfaces()) {
                try {
                    return i.getMethod(name, types);
                } catch (NoSuchMethodException ignored) {
                }
            }
        }
        throw new NoSuchMethodException(c.getName() + "." + name);
    }
}
