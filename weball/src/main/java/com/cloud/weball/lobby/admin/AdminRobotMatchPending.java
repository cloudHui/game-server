package lobby.admin;

import proto.ModelProto;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** 管理后台创建全机器人验收局时，关联 Game 的异步建桌回包。 */
public final class AdminRobotMatchPending {
    private static final ConcurrentHashMap<Integer, CompletableFuture<ModelProto.RoomTableInfo>> PENDING =
            new ConcurrentHashMap<>();

    private AdminRobotMatchPending() {
    }

    public static CompletableFuture<ModelProto.RoomTableInfo> create(int requestId) {
        CompletableFuture<ModelProto.RoomTableInfo> future = new CompletableFuture<>();
        PENDING.put(requestId, future);
        return future;
    }

    public static boolean complete(int requestId, ModelProto.RoomTableInfo table) {
        CompletableFuture<ModelProto.RoomTableInfo> future = PENDING.remove(requestId);
        if (future == null) return false;
        future.complete(table);
        return true;
    }

    public static void remove(int requestId) {
        PENDING.remove(requestId);
    }
}
