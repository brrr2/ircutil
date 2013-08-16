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
    private ArrayList<String> awayList;
    private ArrayList<String> adminList;
    Random randGen;
    
	public Utilities(){
		startTime = System.currentTimeMillis();
        randGen = new Random();
        awayList = loadHostmaskList("away.txt");
        adminList = loadHostmaskList("admins.txt");
	}
	
    /**
     * Processes commands given to the the bot through PM. These commands
     * should be accessible only by admins.
     * 
     * @param bot the bot that caught the command
     * @param user the User that sent the command
     * @param command the command
     * @param params the command parameters
     */
    public void processPrivateCommand(PircBotX bot, User user, String command, String[] params){
        // Check if the user is an admin
        if (!isAdmin(user)){
            bot.sendNotice(user, "You are not authorized to make this command.");
        
        // Join a specified channel
        } else if (command.equals("join")){
            // Check if we have enough parameters
            if (params.length < 1) {
                bot.sendNotice(user, "Missing channel parameter.");
            } else {
                if (!params[0].startsWith("#")){
                    bot.joinChannel("#" + params[0]);
                } else {
                    bot.joinChannel(params[0]);
                }
            }
            
        // Leave a specified channel
        } else if (command.equals("part") || command.equals("leave")){
            // Check if we have enough parameters
            if (params.length < 1) {
                bot.sendNotice(user, "Missing channel parameter.");
            } else {
                if (bot.channelExists(params[0])){
                    bot.partChannel(bot.getChannel(params[0]));
                } else {
                    bot.sendNotice(user, bot.getNick() + " is not in " + params[0] + ".");
                }
            }
        
        // Ops/Deops the specified user in the specified channel
        } else if (command.equals("op") || command.equals("deop")){
            // Check if we have enough parameters
            if (params.length < 1){
                bot.sendNotice(user, "Missing channel parameter.");
            } else {
                // Check if the bot is in the specified channel
                if (!bot.channelExists(params[0])){
                    bot.sendNotice(user, bot.getNick() + " is not in " + params[0] + ".");
                } else {
                    Channel channel = bot.getChannel(params[0]);
                    // Check if the bot has Ops in that channel
                    if (!channel.isOp(bot.getUserBot())){
                        bot.sendNotice(user, bot.getNick()+" is not authorized to do this in " + params[0] + ".");
                    } else {
                        // Check if we have a specified user
                        if (params.length > 1){
                            if (!isUserInChannel(channel, params[1])){
                                bot.sendNotice(user, params[1] + " is not in " + params[0] + ".");
                            } else {
                                User newuser = bot.getUser(params[1]);
                                if (command.equals("op")){
                                    bot.op(channel, newuser);
                                } else {
                                    bot.deOp(channel, newuser);
                                }
                            }
                        } else {
                            if (!channel.getUsers().contains(user)){
                                bot.sendNotice(user, "You are not in " + params[0] + ".");
                            } else {
                                if (command.equals("op")){
                                    bot.op(channel, user);
                                } else {
                                    bot.deOp(channel, user);
                                }
                            }
                        }
                    }
                }
            }
        
        // Erases all hostmasks from away.txt
        } else if (command.equals("clearaway")){
            awayList.clear();
            saveHostmaskList("away.txt", awayList);
            bot.sendNotice(user, "The away list has been emptied.");
        }
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
        // Display current host time
        if (command.equals("time")){
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
                outStr += it.next().getName()+", ";
            }
            bot.sendMessage(channel, outStr.substring(0, outStr.length()-2));
            
        // Remove the user from the away list
        } else if (command.equals("back")){
            if (isUserAway(user)){
                setUserBack(user);
                bot.sendNotice(user, "You are no longer marked as away.");
            } else {
                bot.sendNotice(user, "You are not marked as away!");
            }
            
        // Add the user to the away list
        } else if (command.equals("away")){
            if (isUserAway(user)){
                bot.sendNotice(user, "You are already marked as away!");
            } else {
                setUserAway(user);
                bot.sendNotice(user, "You are now marked as away.");
            }
            
        // Display a list of users in a channel excluding ChanServ, the bot
        } else if (command.equals("ping")){
            // Grab the users in the channel
            Set<User> users = bot.getUsers(channel);
            User tUser;
            String tNick;
            String outStr = "Ping: ";
            Iterator<User> it = users.iterator();
            while(it.hasNext()){
                tUser = it.next();
                tNick = tUser.getNick();
                if (!tNick.equalsIgnoreCase("ChanServ") && 
                        !tNick.equalsIgnoreCase(bot.getNick()) &&
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
    
    // Checks if the user is in the channel
    private boolean isUserInChannel(Channel channel, String nick){
        Set<User> users = channel.getUsers();
        Iterator<User> it = users.iterator();
        while(it.hasNext()){
            if (it.next().getNick().equalsIgnoreCase(nick)){
                return true;
            }
        }
        return false;
    }
    
    // Determines if the user is on the away list
    private boolean isUserAway(User user){
        return awayList.contains(user.getHostmask());
    }
    
    // Adds a user to the away list
    private void setUserAway(User user){
        awayList.add(user.getHostmask());
        saveHostmaskList("away.txt", awayList);
    }
    
    // Removes a user from the away list
    private void setUserBack(User user){
        awayList.remove(user.getHostmask());
        saveHostmaskList("away.txt", awayList);
    }
    
    // Adds a user to the admin list
    private void addAdmin(User user){
        adminList.add(user.getHostmask());
        saveHostmaskList("admins.txt", adminList);
    }
    
    // Removes a user from the admin list
    private void removeAdmin(User user){
        adminList.remove(user.getHostmask());
        saveHostmaskList("admins.txt", adminList);
    }
    
    // Determines if a user is an admin
    private boolean isAdmin (User user){
        return adminList.contains(user.getHostmask());
    }
    
    // Saves a list of hostmasks to the specified file
    private static void saveHostmaskList(String file, ArrayList<String> hostList){
        try {
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(file)));
            for (int ctr = 0; ctr < hostList.size(); ctr++){
                out.println(hostList.get(ctr));
            }
            out.close();
        } catch (IOException e){
            // do nothing if unable to write file
        }      
    }
    
    // Loads a list of hostmasks from the specified file
    private static ArrayList<String> loadHostmaskList(String file){
        try {
            BufferedReader in = new BufferedReader(new FileReader(file));
            ArrayList hostList = new ArrayList<String>();
            while (in.ready()) {
                hostList.add(in.readLine());
            }
            in.close();
            return hostList;
        } catch (IOException e){
            return new ArrayList<String>(); // return empty list if unable to read file
        }      
    }
}
