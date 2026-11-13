package io.github.leekin2.xnotify;

/** Real compatibility cases: the separate registration and unregistration methods on MiLink 18.2.4. */
public final class ReceiveScreenBindingTest {
    interface Listener { }
    static class Legacy { public void o(Listener listener) { } }
    static class Current { private void o(Listener listener) { } public void w(Listener listener) { } }
    static class Wrong { public static void o(Listener listener) { } public boolean p(Listener listener) { return true; } }
    public static void main(String[] args) throws Exception {
        check(ReceiveScreenBinding.register(Legacy.class, Listener.class).getName().equals("o"));
        check(ReceiveScreenBinding.register(Current.class, Listener.class).getName().equals("o"));
        // Fail closed when the register method does not match the verified contract.
        for (Class<?> type : new Class<?>[]{Wrong.class}) {
            try { ReceiveScreenBinding.register(type, Listener.class); throw new AssertionError(type); }
            catch (NoSuchMethodException expected) { }
        }
        System.out.println("Receive screen compatibility cases passed");
    }
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
}
