package ru.craftlogic.chat.common.commands;

import net.minecraft.command.CommandException;
import ru.craftlogic.api.CraftMessages;
import ru.craftlogic.api.command.CommandBase;
import ru.craftlogic.api.command.CommandContext;
import ru.craftlogic.api.text.Text;
import ru.craftlogic.api.world.Player;
import ru.craftlogic.chat.ChatManager;

public class CommandMute extends CommandBase {
    public CommandMute() {
        super("mute", 3,
            "<target:Player> <duration>",
            "<target:Player> <duration> <reason>"
        );
    }

    @Override
    protected void execute(CommandContext ctx) throws CommandException {
        ChatManager chatManager = ctx.server().getManager(ChatManager.class);
        if (!chatManager.isEnabled()) {
            throw new CommandException("commands.chat.disabled");
        }

        Player target = ctx.get("target").asPlayer();
        long duration = CommandContext.parseDuration(ctx.get("duration").asString());
        String reason = ctx.getIfPresent("reason", CommandContext.Argument::asString).orElse(null);

        if (!chatManager.addMute(target, System.currentTimeMillis() + duration, reason)) {
            throw new CommandException("commands.mute.already", target.getName());
        }

        Text<?, ?> d = CraftMessages.parseDuration(duration).darkRed();

        if (reason != null) {
            ctx.server().broadcast(Text.translation("commands.mute.target.reason").gray()
                .arg(target.getName(), Text::darkGray)
                .arg(d.darkGray())
                .arg(reason, Text::darkGray)
            );
            target.sendMessage(
                Text.translation("commands.mute.you.reason").red()
                    .arg(d.darkRed())
                    .arg(reason, Text::red)
            );
        } else {
            ctx.sendNotification(
                Text.translation("commands.mute.target").gray()
                    .arg(target.getName(), Text::darkGray)
                    .arg(d.darkGray())
            );
            target.sendMessage(
                Text.translation("commands.mute.you").red()
                    .arg(d.darkRed())
            );
        }
    }
}
