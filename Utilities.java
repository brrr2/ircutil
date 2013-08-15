/*
	Copyright (C) 2013 Yizhe Shen <brrr@live.ca>
	
	This file is part of ircutil.

    ircutil is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    ircutil is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with irccasino.  If not, see <http://www.gnu.org/licenses/>.
*/

package ircutil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import org.pircbotx.*;

/**
 * A set of useful functions for an IRC bot.
 * @author Yizhe Shen
 */
public class Utilities{
	private long startTime;
    Random randGen;
	public Utilities(){
		startTime = System.currentTimeMillis();
        randGen = new Random();
	}
	
	/**
     * Process a command.
     * 
     * @param bot the bot that caught a command
     * @param channel the originating channel of the command
     * @param user the user who made the command
     * @param command the command
     * @param params the parameters after the command
     */
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
            
        // Erases all hostmasks from away.txt
        } else if (command.equals("clearaway")){
            if (!channel.isOp(user)){
                bot.sendNotice(user, "You do not have Op privileges in this channel.");
            } else {
                clearAwayList();
                bot.sendNotice(user, "The away list as been emptied.");
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
            
        // Remove the user from the away list
        } else if (command.equals("back")){
            ArrayList<String> awayList = getAwayList();
            if (awayList.contains(user.getHostmask())){
                setUserBack(awayList, user.getHostmask());
                bot.sendNotice(user, "You are no longer marked as away.");
            } else {
                bot.sendNotice(user, "You are not marked as away!");
            }
            
        // Add the user to the away list
        } else if (command.equals("away")){
            ArrayList<String> awayList = getAwayList();
            if (awayList.contains(user.getHostmask())){
                bot.sendNotice(user, "You are already marked as away!");
            } else {
                setUserAway(user.getHostmask());
                bot.sendNotice(user, "You are now marked as away.");
            }
            
        // Display a list of users in a channel excluding ChanServ, the bot
        } else if (command.equals("ping")){
            ArrayList<String> awayList = getAwayList();
            Set<User> users = bot.getUsers(channel);
            User tUser;
            String tNick;
            String outStr = "Ping: ";
            Iterator<User> it = users.iterator();
            while(it.hasNext()){
                tUser = it.next();
                tNick = tUser.getNick();
                if (!tNick.equals("ChanServ") && !tNick.equals(bot.getNick()) &&
                        !awayList.contains(tUser.getHostmask())){
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
            
        // Gives the user a cup of hot chocolate
        } else if (command.equals("cocoa")){
            bot.sendAction(channel, "hands " + user.getNick() +
                                    " a cup of hot chocolate. Cheers!");
            
        // Stokes the fireplace
        } else if (command.equals("stoke")){
            bot.sendAction(channel, "stokes the glowing ambers of the fire.");
            
        // Displays a list of commands
        } else if (command.equals("commands")){
            bot.sendMessage(channel, "Commands: channels, time, uptime, cocoa, stoke, away, back, ping, coin, hi, help");
            
        // Displays a help message
        } else if (command.equals("help")){
            bot.sendMessage(channel, user.getNick()+": Read the topic. For a list of non-game commands, type .commands.");
        }
	}
    
    // Adds a user to the away list by writing to file
    private static void setUserAway(String hostmask){
        try {
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("away.txt",true)));
            out.println(hostmask);
            out.close();
        } catch (IOException e) {
            // do nothing
        }
    }
    
    // Removes a user from the away list by removing entry from file
    private static void setUserBack(ArrayList<String> awayList, String hostmask){
        try {
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("away.txt")));
            awayList.remove(hostmask);
            for (int ctr = 0; ctr < awayList.size(); ctr++){
                out.println(awayList.get(ctr));
            }
            out.close();
        } catch (IOException e) {
            // do nothing
        }
    }
    
    // Clears the away list file of all entries
    private static void clearAwayList(){
        try {
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("away.txt")));
            out.close();
        } catch (IOException e) {
            // do nothing
        }
    }
    
    // Returns the away list
    private static ArrayList<String> getAwayList(){
        try {
            ArrayList<String> hostmasks = new ArrayList<String>();
            BufferedReader in = new BufferedReader(new FileReader("away.txt"));
            while (in.ready()) {
                hostmasks.add(in.readLine());
            }
            in.close();
            return hostmasks;
        } catch (IOException e){
            return new ArrayList<String>(); // return empty list if unable to read file
        }      
    }
}
