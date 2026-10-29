package utils.registry.fixture;

import com.google.protobuf.Message;
import net.client.Sender;
import net.handler.Handler;
import utils.registry.annotation.ProcessType;

@ProcessType(value = 10001)
public class MockSingleHandler implements Handler {
    @Override
    public boolean handler(Sender sender, int clientId, Message msg, long mapId, int sequence) {
        return true;
    }
}

