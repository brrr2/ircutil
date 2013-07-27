package ircutil;

import java.util.*;
import org.pircbotx.*;

public class Utilities{
	private long startTime;
    Random randGen;
	public Utilities(){
		startTime = System.currentTimeMillis();
        randGen = new Random();
	}
	
	public void processCommand(PircBotX bot, Channel channel, User user, String command, String[] params){    	
        // Join a specified channel
        if (command.equals("joinchannel")){
            if (!channel.isOp(user)){
                bot.sendNotice(user, "You do not have Op privileges in this channel.");
            } else {
                if (params.length > 0){
                    bot.joinChannel(params[0]);
                } else {
                    bot.sendNotice(user,"Missing channel parameter.");
                }
            }
        // Leave a specified channel or channel of command origin
        } else if (command.equals("partchannel") || command.equals("leavechannel")){
            if (!channel.isOp(user)){
                bot.sendNotice(user, "You do not have Op privileges in this channel.");
            } else {
                if (bot.getChannels().size() > 1){
                    if (params.length > 0){
                        bot.partChannel(bot.getChannel(params[0]));
                    } else {
                        bot.partChannel(channel);
                    }
                } else {
                    bot.sendNotice(user, bot.getNick()+" must be joined to at least 1 channel.");
                }
            }
        // Display current host time
        } else if (command.equals("time")){
            bot.sendMessage(channel, "Time: " + new Date().toString());
        // Display bot uptime
        } else if (command.equals("uptime")){
        	long d = (System.currentTimeMillis() - startTime)/1000;
        	long seconds = d % 60;
        	long minutes = (d / 60) % 60;
        	long hours = (d / 3600) % 24;
        	long days = d / 86400;
        	bot.sendMessage(channel, "Uptime: "+String.format("%02d:%02d:%02d:%02d", days, hours, minutes, seconds));
        // Display channels to which the bot is connected
        } else if (command.equals("channels")){
            Set<Channel> channels = bot.getChannels();
            String outStr = "Channels: ";
            Iterator<Channel> it = channels.iterator();
            while(it.hasNext()){
                outStr += ((Channel)it.next()).getName()+", ";
            }
            bot.sendMessage(channel, outStr.substring(0, outStr.length()-2));
        // Display a list of users in a channel excluding ChanServ and the bot
        } else if (command.equals("ping")){
            Set<User> users = bot.getUsers(channel);
            String tNick;
            String outStr = "Ping: ";
            Iterator<User> it = users.iterator();
            while(it.hasNext()){
                tNick= ((User)it.next()).getNick();
                if (!tNick.equals("ChanServ") && !tNick.equals(bot.getNick())){
                    outStr += tNick+", ";
                }
            }
            bot.sendMessage(channel, outStr.substring(0, outStr.length()-2));
        // Display the results of a coin flip
        } else if (command.equals("coin")){
            int n = randGen.nextInt(2);
            String outStr = Colors.BOLD+user.getNick()+Colors.BOLD+" flips a coin... and it lands on ";
            if (n == 0){
                outStr += Colors.BOLD+"tails"+Colors.BOLD+".";
            } else {
                outStr += Colors.BOLD+"heads"+Colors.BOLD+".";
            }
            bot.sendMessage(channel, outStr);
        // Displays greetings to user in channel
        } else if (command.equals("hi")){
            bot.sendMessage(channel, "Hi "+user.getNick()+"!");
        // Displays a list of commands
        } else if (command.equals("commands")){
            bot.sendMessage(channel, "Commands: channels, time, uptime, ping, coin, hi, help");
        // Displays a help message
        } else if (command.equals("help")){
            bot.sendMessage(channel, user.getNick()+": Type .ghelp");
        }
	}
}
