package com.github.m5rian.jdaCommandHandler.commandServices;

import com.github.m5rian.jdaCommandHandler.Channel;
import com.github.m5rian.jdaCommandHandler.CommandContext;
import com.github.m5rian.jdaCommandHandler.CommandUtils;
import com.github.m5rian.jdaCommandHandler.Everyone;
import com.github.m5rian.jdaCommandHandler.exceptions.NotRegisteredException;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.function.Function;

/**
 * A command service for default needs.
 */
public class DefaultCommandService implements ICommandService, IPermissionService {
    private final String defaultPrefix;
    private final Function<Guild, String> customPrefix;
    private final boolean allowMention;

    /**
     * Constructor
     * This constructor builds the actual DefaultCommandService
     *
     * @param defaultPrefix The default prefix, which is used when a no variable prefix is set.
     *                      The default prefix is also used when you fire commands in the direct messages.
     * @param customPrefix  A variable prefix, which can depend on each guild.
     * @param allowMention  Should the bot respond on mentions too?
     */
    public DefaultCommandService(String defaultPrefix, Function<Guild, String> customPrefix, boolean allowMention) {
        // No default prefix set
        if (defaultPrefix == null) throw new IllegalArgumentException("You need to specify a default prefix");

        this.defaultPrefix = defaultPrefix;
        this.customPrefix = customPrefix;
        this.allowMention = allowMention;

        this.registerPermission(new Everyone()); // Register default role
    }

    @Override
    public void processCommandExecution(MessageReceivedEvent event) {
        final String rawMsg = event.getMessage().getContentRaw().replace("<@!", "<@"); // Get raw content

        String prefix = defaultPrefix; // Get default prefix
        if (customPrefix != null && event.isFromGuild())
            prefix = this.customPrefix.apply(event.getGuild()); // Get guild specific prefix

        final String m = event.getJDA().getSelfUser().getAsMention(); // Get bot as mention

        String msg;
        if (rawMsg.startsWith(prefix)) msg = rawMsg.substring(prefix.length()); // Fired using prefix
        else if (allowMention && rawMsg.startsWith(m)) msg = rawMsg.substring(m.length()); // Fired using mention
        else return; // No command was fired

        String finalPrefix = prefix;
        this.commandsNew.parallelStream().forEach(command -> {
            try {

                boolean hasPermissions = hasPermissions(event.getMember(), command.getCommand().requires());// Does the member have the required permission?
                boolean rightChannel = isType(command.getCommand().channel(), event); // Was the command executed in the right channel?

                if (rightChannel && hasPermissions) {
                    final List<String> executors = CommandUtils.getCommandExecutors(command.getMethod()); // Get all command executors
                    // Check for every command executor
                    for (String executor : executors) {
                        final String regex = "(?i)" + executor + "($|\s.*)"; // Command regex
                        // Message matches command regex
                        if (msg.matches(regex)) {
                            String commandArguments = msg.substring(executor.length()); // Filter arguments
                            if (!commandArguments.equals("")) commandArguments = commandArguments.substring(1);

                            command.getMethod().invoke(command.getInstance(), new CommandContext(finalPrefix, event, commandArguments, this.getEventWaiter())); // Run command
                            break; // Only run command once
                        }
                    }
                }

            } catch (IllegalAccessException e){
                e.printStackTrace();
            }
            // A required permissions isn't registered
            catch (NotRegisteredException notRegisteredException){
                notRegisteredException.printStackTrace();
            }
            // Error is thrown in the original method
            catch (InvocationTargetException e){
                e.getCause().printStackTrace();
            }
        });

    }

    /**
     * Checks for the right channel type.
     *
     * @param type  JDA's channel type.
     * @param event The msgReceivedEvent.
     * @return Returns if the right channel type was used.
     */
    private boolean isType(Channel type, MessageReceivedEvent event) {
        final ChannelType channel = event.getChannelType();

        switch (type) {
            case DEFAULT -> { // Used for all channels except group
                return channel != ChannelType.GROUP;
            }
            case GUILD -> { // Guild only
                return channel.isGuild();
            }
            case DM -> { // Direct messages
                return channel == ChannelType.PRIVATE;
            }
            default -> {
                return false;
            }
        }
    }
}