package net.msg;

import com.google.protobuf.Message;
import net.client.Sender;
import net.message.TCPMessage;
import net.proto.CommonProto;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class MsgRouterTest {

    static class MockSender implements Sender {
        public int lastClientId;
        public int lastMsgId;
        public long lastMapId;
        public Message lastMessage;
        public int lastSequence;

        @Override
        public void sendMessage(int clientId, int msgId, long mapId, Message msg, int sequence) {
            this.lastClientId = clientId;
            this.lastMsgId = msgId;
            this.lastMapId = mapId;
            this.lastMessage = msg;
            this.lastSequence = sequence;
        }

        @Override
        public void sendMessage(int msgId, Message msg, int sequence) {
            sendMessage(0, msgId, 0, msg, sequence);
        }

        @Override
        public void sendMessage(TCPMessage tcpMessage) {
        }

        @Override
        public CompletableFuture<TCPMessage> sendMessageBackTcp(Message msg, int msgId, int timeout) {
            return null;
        }

        @Override
        public CompletableFuture<TCPMessage> sendTcpMessage(TCPMessage msg, int timeout) {
            return null;
        }
    }

    public static class TestController {
        final AtomicBoolean handled = new AtomicBoolean(false);
        final AtomicLong serverIdReceived = new AtomicLong();

        @Msg(id = 1001, ack = 1002, desc = "测试注册并自动回包")
        public CommonProto.AckRegister onRegister(MsgContext<CommonProto.ReqRegister> ctx) {
            handled.set(true);
            serverIdReceived.set(ctx.getMsg().getServerId());
            return CommonProto.AckRegister.newBuilder()
                    .setResult(0)
                    .setServerId(888L)
                    .build();
        }

        @Msg(id = 2001, desc = "测试手动reply")
        public void onManualReply(MsgContext<CommonProto.ReqRegister> ctx) {
            ctx.reply(2002, CommonProto.AckRegister.newBuilder()
                    .setResult(0)
                    .setServerId(999L)
                    .build());
        }
    }

    @Test
    public void testRouterDispatchAndAutoReply() {
        MsgRouter router = new MsgRouter();
        TestController controller = new TestController();
        router.register(controller);

        Assert.assertEquals(2, router.getRouteCount());

        MockSender sender = new MockSender();
        CommonProto.ReqRegister req = CommonProto.ReqRegister.newBuilder()
                .setServerId(12345L)
                .setServerType(1)
                .build();

        TCPMessage tcpMsg = TCPMessage.newInstance(1001, req.toByteArray());
        tcpMsg.setClientId(12345);
        tcpMsg.setSequence(99);
        tcpMsg.setMapId(777L);

        boolean success = router.dispatch(sender, tcpMsg);
        Assert.assertTrue(success);
        Assert.assertTrue(controller.handled.get());
        Assert.assertEquals(12345L, controller.serverIdReceived.get());

        // 验证自动根据 ack = 1002 回包
        Assert.assertEquals(1002, sender.lastMsgId);
        Assert.assertEquals(12345, sender.lastClientId);
        Assert.assertEquals(99, sender.lastSequence);
        Assert.assertEquals(777L, sender.lastMapId);
        Assert.assertTrue(sender.lastMessage instanceof CommonProto.AckRegister);
        Assert.assertEquals(888L, ((CommonProto.AckRegister) sender.lastMessage).getServerId());
    }

    @Test
    public void testManualReply() {
        MsgRouter router = new MsgRouter();
        TestController controller = new TestController();
        router.register(controller);

        MockSender sender = new MockSender();
        CommonProto.ReqRegister req = CommonProto.ReqRegister.newBuilder()
                .setServerId(54321L)
                .setServerType(2)
                .build();
        TCPMessage tcpMsg = TCPMessage.newInstance(2001, req.toByteArray());
        tcpMsg.setClientId(54321);
        tcpMsg.setSequence(66);

        router.dispatch(sender, tcpMsg);

        Assert.assertEquals(2002, sender.lastMsgId);
        Assert.assertEquals(54321, sender.lastClientId);
        Assert.assertEquals(66, sender.lastSequence);
        Assert.assertEquals(999L, ((CommonProto.AckRegister) sender.lastMessage).getServerId());
    }
}
