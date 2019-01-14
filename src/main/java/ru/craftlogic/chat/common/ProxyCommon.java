package ru.craftlogic.chat.common;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import ru.craftlogic.api.event.server.ServerAddManagersEvent;
import ru.craftlogic.api.network.AdvancedMessage;
import ru.craftlogic.api.network.AdvancedMessageHandler;
import ru.craftlogic.chat.ChatManager;
import ru.craftlogic.chat.network.message.MessageClearChat;
import ru.craftlogic.util.ReflectiveUsage;

import static ru.craftlogic.chat.CraftChat.NETWORK;

@ReflectiveUsage
public class ProxyCommon extends AdvancedMessageHandler {
    public void preInit() {

    }

    public void init() {
        NETWORK.registerMessage(this::handleClearChat, MessageClearChat.class, Side.CLIENT);
    }

    public void postInit() {

    }

    protected AdvancedMessage handleClearChat(MessageClearChat message, MessageContext context) {
        return null;
    }

    @SubscribeEvent
    public void onServerAddManagers(ServerAddManagersEvent event) {
        event.addManager(ChatManager.class, ChatManager::new);
    }
}
