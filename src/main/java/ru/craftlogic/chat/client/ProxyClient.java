package ru.craftlogic.chat.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import ru.craftlogic.api.network.AdvancedMessage;
import ru.craftlogic.chat.common.ProxyCommon;
import ru.craftlogic.chat.network.message.MessageClearChat;
import ru.craftlogic.util.ReflectiveUsage;

@ReflectiveUsage
public class ProxyClient extends ProxyCommon {
    private final Minecraft client = FMLClientHandler.instance().getClient();

    @Override
    public void preInit() {
        super.preInit();
    }

    @Override
    public void init() {
        super.init();
    }

    @Override
    public void postInit() {
        super.postInit();
    }

    @Override
    protected AdvancedMessage handleClearChat(MessageClearChat message, MessageContext context) {
        syncTask(context, () -> this.client.ingameGUI.getChatGUI().clearChatMessages(message.sent));
        return null;
    }
}
