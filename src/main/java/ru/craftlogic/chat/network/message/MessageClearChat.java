package ru.craftlogic.chat.network.message;

import ru.craftlogic.api.network.AdvancedBuffer;
import ru.craftlogic.api.network.AdvancedMessage;
import ru.craftlogic.api.network.AdvancedNetwork;
import ru.craftlogic.chat.CraftChat;
import ru.craftlogic.util.ReflectiveUsage;

public class MessageClearChat extends AdvancedMessage {
    public boolean sent;

    @ReflectiveUsage
    public MessageClearChat() {}

    public MessageClearChat(boolean sent) {
        this.sent = sent;
    }

    @Override
    public AdvancedNetwork getNetwork() {
        return CraftChat.NETWORK;
    }

    @Override
    protected void read(AdvancedBuffer buf) {
        this.sent = buf.readBoolean();
    }

    @Override
    protected void write(AdvancedBuffer buf) {
        buf.writeBoolean(this.sent);
    }
}
