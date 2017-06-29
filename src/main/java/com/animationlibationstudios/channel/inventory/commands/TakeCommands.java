package com.animationlibationstudios.channel.inventory.commands;

import com.animationlibationstudios.channel.inventory.commands.utility.CommandArgumentParserUtil;
import com.animationlibationstudios.channel.inventory.model.Room;
import com.animationlibationstudios.channel.inventory.model.Thing;
import com.animationlibationstudios.channel.inventory.persist.RoomStore;
import com.animationlibationstudios.channel.inventory.persist.RoomStorePersister;
import de.btobastian.javacord.DiscordAPI;
import de.btobastian.javacord.entities.Channel;
import de.btobastian.javacord.entities.Server;
import de.btobastian.javacord.entities.User;
import de.btobastian.javacord.entities.message.Message;
import de.btobastian.javacord.entities.message.MessageBuilder;
import de.btobastian.javacord.entities.message.MessageDecoration;
import de.btobastian.sdcf4j.Command;
import de.btobastian.sdcf4j.CommandExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Process !!take commands - they work pretty much like !!buy commands, but don't require any pricing checks.
 */
@Service
public class TakeCommands implements CommandExecutor {

    @Autowired
    private RoomStorePersister storage;

    @Autowired
    private CommandArgumentParserUtil commandArgumentParserUtil;

    @Command(aliases = {"!!take"},
            description = "!!take <item> - Take one of the named item for the price shown.\n" +
                    "Arguments (must appear after the item name but otherwise can be in any order):\n" +
                    "  -q # - Buy quantity # items named at # times the price.\n")
    public String onCommand(DiscordAPI api, String command, String[] args, Message message) {
        Server server = message.getChannelReceiver().getServer();
        String serverName = server.getName();
        Channel channel = message.getChannelReceiver();
        Room room = RoomStore.DataStore.get(serverName, channel.getName());
        String returnMessage = String.format("There is no room associated with channel #%s.  To create one, type !!room add <name>", channel.getName());

        User requestor = message.getAuthor();
        String item;

        int quantity = 1;

        // Start by loading the server file if we need to, and if we can.
        commandArgumentParserUtil.checkAndRead(serverName);

        if (room != null) {
            if (room.getThings() == null || room.getThings().isEmpty()) {
                returnMessage = "There's nothing to take here.  Type '!!look' to see what's available.";
            } else if ("-q".equalsIgnoreCase(args[0])) {
                // invalid; you need to specify an item first.
                returnMessage = "Invalid command.  An item name must appear before any arguments (like '-q').";
            } else {
                item = commandArgumentParserUtil.parseItemName(args);

                try {
                    quantity = commandArgumentParserUtil.parseQuantity(args);
                    if (quantity == 0) {
                        throw new NumberFormatException();
                    }
                } catch (NumberFormatException e) {
                    returnMessage = "Invalid quantity specified.  '-q' must be followed by a valid, non-negative " +
                            "integer number (0 will remove all of the specified item).";
                    quantity = 0;
                }

                // If we have an item and a non-zero quantity, we've got a good command.  Now check and make sure that
                // what the requestor wants to buy is actually in the room...
                boolean weAreGood = false;
                Thing theThing = null;

                if (item != null && quantity > 0) {
                    for (Thing thing: room.getThings()) {
                        if (item.equalsIgnoreCase(thing.getName())) {
                            theThing = thing;

                            if (theThing.getQuantity() >= quantity) {
                                if (theThing.getPrice() == null || theThing.getPrice().isEmpty()) {
                                    weAreGood = true;
                                } else {
                                    returnMessage = String.format("You asked to take %d %s(s) but those have a price of %s - use !!buy instead.", quantity, item, thing.getPrice());
                                }
                            } else {
                                returnMessage = String.format("You asked to take %d %s(s) but there is/are only %d in the room.", quantity, item, thing.getQuantity());
                            }

                            break;
                        }
                    }

                    if (theThing != null) {
                        if (weAreGood) {
                            if (quantity == theThing.getQuantity()) {
                                // that's all of them!
                                room.getThings().remove(theThing);
                            } else {
                                theThing.setQuantity(theThing.getQuantity() - quantity);
                            }

                            returnMessage = "Transaction complete. The seller has been private messaged the outcome of the transaction.  Thank you for shopping!";

                            requestor.sendMessage(String.format("You have successfully taken %d %s(s) from the shop in %s.",
                                    quantity, item, room.getName()));

                            if (room.getRoomAdmin() != null) {
                                room.getRoomAdmin().sendMessage(String.format("%s just took %d %s(s) from the shop in %s.",
                                        requestor.getName(), quantity, item, room.getName()));
                                returnMessage = "Transaction complete. Both buyer and seller have been private messaged " +
                                        "the outcome of the transaction.  Thank you for shopping!";
                            }
                        }
                    } else {
                        returnMessage = String.format("There are no %s(s) in the room.", item);
                    }
                }
            }
        }

        return new MessageBuilder().appendDecoration(returnMessage, MessageDecoration.CODE_LONG).toString();
    }
}
