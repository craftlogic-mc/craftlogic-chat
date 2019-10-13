package ru.craftlogic.chat.common.commands;

import net.minecraft.command.CommandException;
import ru.craftlogic.api.CraftMessages;
import ru.craftlogic.api.command.CommandBase;
import ru.craftlogic.api.command.CommandContext;
import ru.craftlogic.api.text.Text;
import ru.craftlogic.api.world.Player;
import ru.craftlogic.chat.ChatManager;

import java.util.List;

public class CommandUnmute extends CommandBase {
    public CommandUnmute() {
        super("unmute", 3, "<target:Player>");
    }

    @Override
    protected void execute(CommandContext ctx) throws CommandException {
        ChatManager chatManager = ctx.server().getManager(ChatManager.class);
        if (!chatManager.isEnabled()) {
            throw new CommandException("commands.chat.disabled");
        }

        Player player = ctx.get("target").asPlayer();

        if (chatManager.removeMute(player)) {
            ctx.sendNotification(
                Text.translation("commands.unmute.success").gray()
                    .arg(player.getName(), Text::darkGray)
            );
        } else {
            throw new CommandException("commands.unmute.already", player.getName());
        }
    }
}
