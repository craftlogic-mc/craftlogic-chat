package ru.craftlogic.chat;

import net.minecraft.command.CommandException;
import ru.craftlogic.api.CraftMessages;
import ru.craftlogic.api.command.Command;
import ru.craftlogic.api.command.CommandContext;
import ru.craftlogic.api.command.CommandContext.Argument;
import ru.craftlogic.api.command.CommandRegistrar;
import ru.craftlogic.api.text.Text;
import ru.craftlogic.api.text.TextTranslation;
import ru.craftlogic.api.world.CommandSender;
import ru.craftlogic.api.world.Player;
import ru.craftlogic.chat.network.message.MessageClearChat;

import java.io.IOException;


public class ChatCommands implements CommandRegistrar {
    @Command(name = "mute", syntax = {
        "<target:Player> <duration>",
        "<target:Player> <duration> <reason>"
    }, serverOnly = true, opLevel = 2)
    public static void commandMute(CommandContext ctx) throws CommandException {
        ChatManager chatManager = ctx.server().getManager(ChatManager.class);
        if (!chatManager.isEnabled()) {
            throw new CommandException("commands.chat.disabled");
        }

        Player target = ctx.get("target").asPlayer();
        long duration = CommandContext.parseDuration(ctx.get("duration").asString());
        String reason = ctx.getIfPresent("reason", Argument::asString).orElse(null);

        if (chatManager.getMute(target) != null) {
            throw new CommandException("commands.mute.already", target.getName());
        }
        chatManager.addMute(target, System.currentTimeMillis() + duration, reason);

        Text<?, ?> d = CraftMessages.parseDuration(duration).darkRed();

        if (reason != null) {
            ctx.sendNotification(
                Text.translation("commands.mute.target.reason").gray()
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

    @Command(name = "unmute", syntax = "<target:Player>", serverOnly = true, opLevel = 2)
    public static void commandUnmute(CommandContext ctx) throws CommandException {
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

    @Command(name = "chat", syntax = {
        "clear all",
        "clear",
        "reload"
    })
    public static void commandChat(CommandContext ctx) throws CommandException {
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

    @Command(name = "message", syntax = {
        "<target:Player> <message>..."
    }, aliases = {
        "msg", "m", "w", "tell"
    }, opLevel = 0)
    public static void commandMessage(CommandContext ctx) throws CommandException {
        CommandSender sender = ctx.sender();
        CommandSender target = ctx.get("target").asString().equals("Server") ? ctx.server() : ctx.get("target").asPlayer();
        String message = ctx.get("message").asString();
        TextTranslation y = Text.translation("tooltip.you");
        Text<?, ?> s = formatMessage(y, Text.string(target.getName()), message).suggestCommand("/w " + target.getName() + " ");
        Text<?, ?> r = formatMessage(Text.string(sender.getName()), y, message).suggestCommand("/w " + sender.getName() + " ");
        ctx.sendMessage(s);
        target.sendMessage(r);
    }

    private static Text<?, ?> formatMessage(Text<?, ?> sender, Text<?, ?> receiver, String message) {
        return Text.string("[").gold().italic()
                .append(sender)
                .appendText(" -> ", t-> t.gold().italic())
                .append(receiver)
                .appendText("] ", t-> t.gold().italic())
                .appendText(message, Text::yellow);
    }
}
