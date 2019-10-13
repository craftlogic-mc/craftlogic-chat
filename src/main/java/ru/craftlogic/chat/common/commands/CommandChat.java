package ru.craftlogic.chat.common.commands;

import net.minecraft.command.CommandException;
import ru.craftlogic.api.CraftMessages;
import ru.craftlogic.api.command.CommandBase;
import ru.craftlogic.api.command.CommandContext;
import ru.craftlogic.api.text.Text;
import ru.craftlogic.api.world.Player;
import ru.craftlogic.chat.ChatManager;
import ru.craftlogic.chat.network.message.MessageClearChat;

import java.io.IOException;
import java.util.List;

public class CommandChat extends CommandBase {
    public CommandChat() {
        super("chat", 4,
            "clear all",
            "clear",
            "reload"
        );
    }

    @Override
    protected void execute(CommandContext ctx) throws CommandException {
        ChatManager chatManager = ctx.server().getManager(ChatManager.class);
        if (!chatManager.isEnabled()) {
            throw new CommandException("commands.chat.disabled");
        }

        switch (ctx.action(0)) {
            case "clear": {
                ctx.server().broadcastPacket(new MessageClearChat(ctx.hasAction(1)));
                ctx.sendNotification(
                    Text.translation("commands.chat.clear").gray()
                );
                break;
            }
            case "reload": {
                try {
                    chatManager.load();
                    ctx.sendNotification(
                        Text.translation("commands.chat.reload.success").gray()
                    );
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new CommandException("commands.chat.reload.failed", e.getMessage());
                }
                break;
            }
        }
    }
}
