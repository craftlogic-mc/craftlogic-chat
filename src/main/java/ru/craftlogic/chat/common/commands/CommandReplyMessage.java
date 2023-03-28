package ru.craftlogic.chat.common.commands;

import net.minecraft.command.CommandException;
import net.minecraft.world.GameType;
import ru.craftlogic.api.command.CommandBase;
import ru.craftlogic.api.command.CommandContext;
import ru.craftlogic.api.server.PlayerManager;
import ru.craftlogic.api.text.Text;
import ru.craftlogic.api.text.TextTranslation;
import ru.craftlogic.api.world.Player;
import ru.craftlogic.chat.ChatManager;

import java.util.UUID;

public class CommandReplyMessage extends CommandBase {
    public CommandReplyMessage() {
        super("reply", 0,"<message>...");
        aliases.add("r");
    }

    @Override
    protected void execute(CommandContext ctx) throws CommandException {
        ChatManager chatManager = ctx.server().getManager(ChatManager.class);
        PlayerManager manager = ctx.server().getManager(PlayerManager.class);
        Player player = ctx.senderAsPlayer();
        UUID sender = chatManager.getLastSender(player.getId());
        if (sender == null) {
            throw new CommandException("chat.reply_message");
        }
        Player target = manager.getOnline(sender);
        if (target == null || target.getGameMode() == GameType.SPECTATOR) {
            throw new CommandException("chat.reply_message.offline");
        }
        String message = ctx.get("message").asString();
        TextTranslation y = Text.translation("tooltip.you");
        Text<?, ?> s = formatMessage(y, Text.string(target.getName()), message).suggestCommand("/w " + target.getName() + " ");
        Text<?, ?> r = formatMessage(Text.string(player.getName()), y, message).suggestCommand("/w " + player.getName() + " ");
        target.sendMessage(r);
        player.sendMessage(s);
        chatManager.setLastSender(target.getId(), player.getId());
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
