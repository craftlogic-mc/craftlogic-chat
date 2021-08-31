package ru.craftlogic.chat.common.commands;

import net.minecraft.command.CommandException;
import ru.craftlogic.api.command.CommandBase;
import ru.craftlogic.api.command.CommandContext;
import ru.craftlogic.api.text.Text;
import ru.craftlogic.api.world.Player;
import ru.craftlogic.chat.ChatManager;

public class CommandTwinks extends CommandBase {
    public CommandTwinks() {
        super("twinks", 4, "<target:Player>");
        aliases.add("twink");
    }

    @Override
    protected void execute(CommandContext ctx) throws CommandException {
        ChatManager chatManager = ctx.server().getManager(ChatManager.class);
        if (!chatManager.isEnabled()) {
            throw new CommandException("commands.chat.disabled");
        }
        Player target = ctx.get("target").asPlayer();

        Text<?, ?> lines = chatManager.getTwinksInfo(target.getProfile());
        if (lines != null) {
            Text<?, ?> warning = Text.translation("chat.twinks").yellow()
                .arg(target.getName(), Text::gold);

            ctx.sendMessage(warning);
            ctx.sendMessage(lines);
        } else {
            ctx.sendMessage(Text.translation("chat.no_twinks").green()
                .arg(target.getName(), Text::darkGreen));
        }
    }
}
