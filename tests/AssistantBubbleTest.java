package os.aios.agent;

public final class AssistantBubbleTest {
    private static int checks;
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        checks++;
    }
    public static void main(String[] args) {
        AssistantBubble b = new AssistantBubble();
        b.reset("completed", "old", "old", "", 0);
        check(b.update("completed", "old", "old", "old answer", "", 0, 100).isEmpty(), "open hides saved answer");
        check(!b.newReply, "open must not replay old speech");
        check(b.update("queued", "next", "old", "old answer", "", 0, 200).equals("正在发送给小助手…"), "queued suppresses stale answer");
        check(b.update("running", "next", "old", "old answer", "", 0, 300).equals("小助手正在处理…"), "running suppresses stale answer");
        check(b.update("completed", "next", "next", "fresh answer", "", 0, 400).equals("fresh answer"), "fresh reply visible");
        check(b.newReply, "fresh reply eligible for speech");
        b.update("completed", "next", "next", "fresh answer", "", 0, 500);
        check(!b.newReply, "repeat render must not replay speech");
        check(b.remaining(500)==11900, "repeat render must not extend expiry");
        check(b.update("completed", "next", "next", "fresh answer", "", 0, 12400).isEmpty(), "reply expires");
        check(b.update("completed", "next", "next", "fresh answer", "", 0, 15000).isEmpty(), "expired reply stays hidden");
        b.reset("completed", "next", "next", "", 0);
        check(b.update("completed", "next", "next", "fresh answer", "", 0, 16000).isEmpty(), "reopening does not revive reply");
        check(b.update("completed", "next", "next", "fresh answer", "正在听", 10, 17000).equals("正在听"), "new notice visible");
        check(!b.newReply, "notice must not speak saved answer");
        check(b.update("completed", "next", "next", "fresh answer", "正在听", 10, 29000).isEmpty(), "notice expires without falling back to saved answer");
        check(b.update("completed", "next", "next", "fresh answer", "正在听", 11, 30000).equals("正在听"), "repeated new notice visible");
        b.reset("failed", "next", "next", "old error", 12);
        check(b.update("failed", "next", "next", "fresh answer", "old error", 12, 31000).isEmpty(), "reopening suppresses old error");
        check(b.update("completed", "third", "next", "fresh answer", "", 0, 32000).isEmpty(), "mismatched reply never shown");
        b.reset("running", "third", "", "", 0);
        check(b.update("running", "third", "", "", "", 0, 33000).equals("小助手正在处理…"), "reopening retains active progress");
        check(b.update("completed", "third", "third", "done", "", 0, 34000).equals("done"), "running request can finish after reopening");
        System.out.println("PASS: " + checks + " bubble lifecycle assertions");
    }
}
