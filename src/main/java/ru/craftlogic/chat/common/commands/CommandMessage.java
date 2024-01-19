package ru.craftlogic.chat.common.commands;

import net.minecraft.command.CommandException;
import net.minecraftforge.event.ServerChatEvent;
import ru.craftlogic.api.command.CommandBase;
import ru.craftlogic.api.command.CommandContext;
import ru.craftlogic.api.text.Text;
import ru.craftlogic.api.text.TextTranslation;
import ru.craftlogic.api.world.CommandSender;
import ru.craftlogic.api.world.Player;
import ru.craftlogic.chat.ChatManager;
import ru.craftlogic.chat.MuteManager;

import java.util.Collections;
import java.util.UUID;

public class CommandMessage extends CommandBase {
    public CommandMessage() {
        super("message", 0, "<target:Player> <message>...");
        Collections.addAll(aliases, "msg", "m", "w", "tell");
    }

    @Override
    protected void execute(CommandContext ctx) throws CommandException {
        ChatManager chatManager = ctx.server().getManager(ChatManager.class);
        CommandSender sender = ctx.sender();
        CommandSender target = ctx.get("target").asString().equals("Server") ? ctx.server() : ctx.get("target").asPlayer();
        if (target instanceof Player && sender instanceof Player && chatManager.isIgnored((Player) target, (Player) sender)) {
            throw new CommandException("chat.personal.ignored");
        }
        MuteManager.Mute mute = chatManager.getMute(ctx.senderAsPlayer().getId());
        if (mute != null) {
            throw new CommandException("chat.muted");
        }
        String message = ctx.get("message").asString();
        TextTranslation y = Text.translation("tooltip.you");
        Text<?, ?> s = formatMessage(y, Text.string(target.getName()), message).suggestCommand("/w " + target.getName() + " ");
        Text<?, ?> r = formatMessage(Text.string(sender.getName()), y, message).suggestCommand("/w " + sender.getName() + " ");
        ctx.sendMessage(s);
        target.sendMessage(r);
        UUID playerId = ctx.senderAsPlayer().getId();
        UUID targetId = ctx.get("target").asPlayer().getId();
        chatManager.setLastSender(targetId, playerId);
        chatManager.setLastSender(playerId, targetId);
    }

    private Text<?, ?> formatMessage(Text<?, ?> sender, Text<?, ?> receiver, String message) {
        return Text.string("[").gold()
            .append(sender)
            .appendText(" -> ", Text::gold)
            .append(receiver)
            .appendText("] ", Text::gold)
            .appendText(message, Text::yellow);
    }
}
