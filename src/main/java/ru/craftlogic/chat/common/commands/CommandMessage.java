package ru.craftlogic.chat.common.commands;

import net.minecraft.command.CommandException;
import ru.craftlogic.api.command.CommandBase;
import ru.craftlogic.api.command.CommandContext;
import ru.craftlogic.api.text.Text;
import ru.craftlogic.api.text.TextTranslation;
import ru.craftlogic.api.world.CommandSender;
import ru.craftlogic.chat.ChatManager;
import ru.craftlogic.chat.network.message.MessageClearChat;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class CommandMessage extends CommandBase {
    public CommandMessage() {
        super("message", 0, "<target:Player> <message>...");
        Collections.addAll(aliases, "msg", "m", "w", "tell");
    }

    @Override
    protected void execute(CommandContext ctx) throws CommandException {
        CommandSender sender = ctx.sender();
        CommandSender target = ctx.get("target").asString().equals("Server") ? ctx.server() : ctx.get("target").asPlayer();
        String message = ctx.get("message").asString();
        TextTranslation y = Text.translation("tooltip.you");
        Text<?, ?> s = formatMessage(y, Text.string(target.getName()), message).suggestCommand("/w " + target.getName() + " ");
        Text<?, ?> r = formatMessage(Text.string(sender.getName()), y, message).suggestCommand("/w " + sender.getName() + " ");
        ctx.sendMessage(s);
        target.sendMessage(r);
    }

    private Text<?, ?> formatMessage(Text<?, ?> sender, Text<?, ?> receiver, String message) {
        return Text.string("[").gold().italic()
            .append(sender)
            .appendText(" -> ", t-> t.gold().italic())
            .append(receiver)
            .appendText("] ", t-> t.gold().italic())
            .appendText(message, Text::yellow);
    }
}
