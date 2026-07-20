package uk.co.pluckier.discordbot;

import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

@Name("uk.co.pluckier.discordbot.MessageReceivedEvent")
@Label("Discord Message Received")
public class BotMessageEvent extends Event {
    @Label("Channel Name")
    public String channelName;

    @Label("Author")
    public String author;

    @Label("Message")
    public String message;

}
