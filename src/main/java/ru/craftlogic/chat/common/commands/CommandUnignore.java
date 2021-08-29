package ru.craftlogic.chat.common.commands;

import net.minecraft.command.CommandException;
import ru.craftlogic.api.command.CommandBase;
import ru.craftlogic.api.command.CommandContext;
import ru.craftlogic.api.text.Text;
import ru.craftlogic.api.world.Player;
import ru.craftlogic.chat.ChatManager;

public class CommandUnignore extends CommandBase {
    public CommandUnignore() {
        super("unignore", 0, "<target:Player>");
    }

    @Override
    protected void execute(CommandContext ctx) throws CommandException {
        Player sender = ctx.senderAsPlayer();
        ChatManager chatManager = ctx.server().getManager(ChatManager.class);
        if (!chatManager.isEnabled()) {
            throw new CommandException("commands.chat.disabled");
        }

        Player target = ctx.get("target").asPlayer();

        if (!chatManager.removeIgnore(sender, target)) {
            throw new CommandException("commands.unignore.already", target.getName());
        }

        ctx.sendNotification(
            Text.translation("commands.unignore.target").gray()
                .arg(target.getName(), Text::darkGray)
        );
    }
}
