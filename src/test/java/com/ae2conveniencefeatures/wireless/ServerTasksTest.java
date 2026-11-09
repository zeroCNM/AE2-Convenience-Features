package com.ae2conveniencefeatures.wireless;

public final class ServerTasksTest {

    public static void main(String[] args) {
        int[] calls = { 0 };
        ServerTasks.execute(() -> calls[0]++);
        if (calls[0] != 1) throw new AssertionError("normal request must execute");
        ServerTasks.execute(() -> { throw new NoSuchMethodError("simulated client-only method"); });
        ServerTasks.execute(() -> { throw new IllegalStateException("simulated invalid request"); });
        ServerTasks.execute(() -> calls[0]++);
        if (calls[0] != 2) throw new AssertionError("next request must execute after failure");
        try {
            ServerTasks.execute(() -> { throw new AssertionError("non-linkage errors must propagate"); });
            throw new IllegalStateException("non-linkage Error was swallowed");
        } catch (AssertionError expected) {
            // Do not turn a request boundary into an indiscriminate Throwable catch.
        }
        System.out.println("PASS: 5 server request failure-boundary regression checks");
    }
}
