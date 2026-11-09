package os.aios.agent;

/** Transient presentation state, separate from durable conversation history. */
final class AssistantBubble {
    static final long DISPLAY_MS = 12000;
    private String seen = "";
    private long expiresAt;
    boolean newReply;

    private static String event(String status, String requestId, String replyId,
            String notice, long noticeAt) {
        if (!notice.isEmpty()) return "notice:" + noticeAt + ":" + notice;
        if (status.equals("completed") && !replyId.isEmpty() && replyId.equals(requestId))
            return "reply:" + replyId;
        return "";
    }

    // Opening the orb establishes a baseline; saved answers are not new events.
    void reset(String status, String requestId, String replyId, String notice, long noticeAt) {
        seen = event(status, requestId, replyId, notice, noticeAt);
        expiresAt = 0;
        newReply = false;
    }

    String update(String status, String requestId, String replyId, String sentence,
            String notice, long noticeAt, long now) {
        newReply = false;
        String key = event(status, requestId, replyId, notice, noticeAt);
        if (!key.equals(seen)) {
            seen = key;
            expiresAt = key.isEmpty() ? 0 : now + DISPLAY_MS;
            newReply = key.startsWith("reply:");
        }
        if (!key.isEmpty() && now < expiresAt) return notice.isEmpty() ? sentence : notice;
        if (status.equals("queued")) return "正在发送给小助手…";
        if (status.equals("running")) return "小助手正在处理…";
        return "";
    }

    long remaining(long now) { return Math.max(0, expiresAt - now); }
}
