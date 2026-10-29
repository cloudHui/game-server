package lobby.client.handle.role;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import lobby.Lobby;
import lobby.db.InviteRepository;
import lobby.db.UserEntity;
import lobby.db.UserRepository;
import lobby.manager.User;
import lobby.manager.UserManager;
import msg.registor.message.LMsg;
import net.client.Sender;
import net.handler.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.LobbyProto;
import utils.crypto.MD5Utils;
import utils.registry.annotation.ProcessType;
import utils.trace.TraceContext;

import java.util.Collections;
import java.util.List;

/**
 * 玩家注册请求处理器
 * <p>
 * 负责校验用户名、密码、邀请码有效性并创建玩家实体，初始化登录态。
 */
@ProcessType(LMsg.REQ_REGISTER_MSG)
public class ReqRegisterHandler implements Handler {

    private static final Logger logger = LoggerFactory.getLogger(ReqRegisterHandler.class);

    public static final int CODE_OK = 0;
    public static final int CODE_FAIL = 1;
    public static final int CODE_USERNAME_EXISTS = 2;
    public static final int CODE_INVITE_REQUIRED = 3;
    public static final int CODE_INVITE_INVALID = 4;

    @Override
    public boolean handler(Sender sender, int clientId, Message message, long mapId, int sequence) {
        try {
            LobbyProto.ReqUserRegister request = (LobbyProto.ReqUserRegister) message;
            String username = request.getUsername().toStringUtf8().trim();
            String password = request.getPassword().toStringUtf8();
            String nickname = request.getNickName().toStringUtf8().trim();
            String invite = request.getInvite().toStringUtf8().trim();

            if (username.isEmpty() || password.isEmpty()) {
                sendAck(sender, clientId, sequence, CODE_FAIL, 0, "", "", "");
                return true;
            }
            if (nickname.isEmpty()) {
                nickname = username;
            }

            Lobby lobby = Lobby.getInstance();
            UserRepository userRepo = lobby.getUserRepository();
            InviteRepository inviteRepo = lobby.getInviteRepository();

            if (userRepo.findByUsername(username).isPresent()) {
                sendAck(sender, clientId, sequence, CODE_USERNAME_EXISTS, 0, "", "", "");
                return true;
            }

            boolean needInvite = !lobby.isOpenRegister();
            if (needInvite) {
                if (invite.isEmpty() || !inviteRepo.peekValid(invite).isPresent()) {
                    sendAck(sender, clientId, sequence, invite.isEmpty() ? CODE_INVITE_REQUIRED : CODE_INVITE_INVALID, 0, "", "", "");
                    return true;
                }
            }

            UserEntity entity = new UserEntity();
            entity.setUsername(username);
            entity.setNickname(nickname);
            entity.setPasswordHash(MD5Utils.MD5(password));
            entity.setEnabled(true);
            entity.setCreatedAt(System.currentTimeMillis());
            long userId = userRepo.insert(entity);
            if (userId <= 0) {
                sendAck(sender, clientId, sequence, CODE_FAIL, 0, "", "", "");
                return true;
            }

            if (needInvite && !inviteRepo.consume(invite)) {
                logger.warn("邀请码消费失败(可能并发), username={}", username);
                sendAck(sender, clientId, sequence, CODE_INVITE_INVALID, 0, "", "", "");
                return true;
            }

            String token = Lobby.newToken();
            userRepo.updateLogin(userId, token, System.currentTimeMillis());

            // clientId = gate 玩家连接 id
            int gateConnId = clientId;
            User user = new User((int) userId, username, nickname, gateConnId);
            user.setOffline(false);
            user.setPendingToken(token);
            UserManager.getInstance().putOrUpdate(user);

            TraceContext.setUserId((int) userId);
            sendAck(sender, clientId, sequence, CODE_OK, (int) userId, username, nickname, token);
            logger.info("注册成功, userId: {}, username: {}", userId, username);
        } catch (Exception e) {
            logger.error("处理注册失败, gateClientId: {}", clientId, e);
            sendAck(sender, clientId, sequence, CODE_FAIL, 0, "", "", "");
        }
        return true;
    }

    private void sendAck(Sender sender, int clientId, int sequence, int code,
                         int userId, String username, String nick, String token) {
        try {
            LobbyProto.AckLogin ack = LobbyProto.AckLogin.newBuilder()
                    .setCode(code)
                    .setUserId(userId)
                    .setUsername(ByteString.copyFromUtf8(username == null ? "" : username))
                    .setNickName(ByteString.copyFromUtf8(nick == null ? "" : nick))
                    .setToken(ByteString.copyFromUtf8(token == null ? "" : token))
                    .build();
            sender.sendMessage(clientId, LMsg.ACK_REGISTER_MSG, 0L, ack, sequence);
        } catch (Exception e) {
            logger.error("发送 AckRegister 失败", e);
        }
    }
}
