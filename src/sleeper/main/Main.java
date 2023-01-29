package sleeper.main;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin{
	public final Logger logger = getLogger();
	public static Main plugin;
	Commands commands;
	EventHandlers eventhandlers;
	
	//Setting values
	boolean useAnimation = true;
	int skipPercentage = 25;
	int skipSpeed = 100;
	
	//Variables
	ArrayList<String> skipping = new ArrayList<>();
	ArrayList<String> recentlySkipped = new ArrayList<>();
	HashMap<String, Integer> skipWorlds = new HashMap<>();
	ArrayList<Player> ignorePlayers = new ArrayList<Player>();
	ArrayList<Player> debugPlayers = new ArrayList<Player>();
	HashMap<String, Integer> sleepingWorlds = new HashMap<>();
	HashMap<String, Integer> playersOnline = new HashMap<>();
	
	//Strings
	String sleepInfo = "&aSleep > &7 %percent% (%count%) out of a minimum of 25% sleeping.";
	String nightSkip = "&aSleep > &7At least 25% of online users sleeping (%count%), skipping the night.";
	String ignored  = "&cSleep > &7You are still being ignored for sleep calculations!";

	public void onDisable() {
		PluginDescriptionFile pdfFile = this.getDescription();
		this.logger.info(pdfFile.getName() + " Has Been Disabled!");
	}

	public void onEnable() {
		plugin = this;
		PluginDescriptionFile pdfFile = this.getDescription();
		this.logger.info(pdfFile.getName() + " Version " + pdfFile.getVersion()
		+ " Has Been Enabled!");
		int pluginId = 15317;
        Metrics metrics = new Metrics(this, pluginId);
		PluginManager pm = getServer().getPluginManager();
		eventhandlers = new EventHandlers(this);
		commands = new Commands(this, eventhandlers);
		getCommand("ignoresleep").setExecutor(commands);
		getCommand("sleepdata").setExecutor(commands);
		getCommand("sleepreload").setExecutor(commands);
		pm.registerEvents(eventhandlers, this);
		setConfig();
		loadConfig();
		plugin.getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
			public void run() {
				ArrayList<String> remove = new ArrayList<>();
				for(String worldName : skipping) {
					if(useAnimation) {
						World world = Bukkit.getWorld(worldName);
						long time = world.getTime();
						world.setTime(time+skipSpeed);
						world.setStorm(false); 
						if(time >= 24000) {
							world.setTime(0);
							time = 0;
						}
						if(time < 2000) {
							remove.add(worldName);
							Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
								public void run() { //Force sleeping count to 0 in case it has become wrong
									sleepingWorlds.put(worldName, 0);
									recentlySkipped.remove(worldName);
								} 
							}, 20L);
						}
					}
				}
				remove.forEach(name -> skipping.remove(name));
			}
		}, 1L, 1L);
	}
	
	public void loadConfig() {
		reloadConfig();
		FileConfiguration config = this.getConfig();
		useAnimation = config.getBoolean("UseAnimation");
		skipPercentage = config.getInt("SkipPercentage");
		skipSpeed = config.getInt("SkipSpeed");
		sleepInfo = config.getString("SleepInfo");
		nightSkip = config.getString("NightSkip");
		ignored = config.getString("Ignored");
	}
	
	public void setConfig() {
		File f = new File(this.getDataFolder() + File.separator + "config.yml");
		if (f.exists()) {
			return;
		}
		FileConfiguration config = YamlConfiguration.loadConfiguration(f);
		try (InputStream defConfigStream = this.getResource("config.yml");
				InputStreamReader reader = new InputStreamReader(defConfigStream,StandardCharsets.UTF_8)){
			FileConfiguration defconf = YamlConfiguration.loadConfiguration(reader);
			config.addDefaults(defconf);
			config.setDefaults(defconf);
			this.saveDefaultConfig();
		} catch(Exception e){
			e.printStackTrace();
		}
	}

	public void sleep(Player player){
		String pWorld = player.getWorld().getName();
		World world = Bukkit.getWorld(pWorld);
		if(ignorePlayers.contains(player)) return;
		Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() { public void run() {
			if (player.isSleeping() == true){
				int onlineIgnored = 0;
				for(Player p : Bukkit.getOnlinePlayers()) {
					if(ignorePlayers.contains(p) || p.getWorld() != world) {
						onlineIgnored++;
					}
				}
				playersOnline.put(pWorld, Bukkit.getOnlinePlayers().size()-onlineIgnored);
				int wonline = plugin.playersOnline.get(player.getWorld().getName());
				int wsleeping = sleepingWorlds.getOrDefault(player.getWorld().getName(), 0); //Increase sleeper count
				wsleeping++;
				sleepingWorlds.put(pWorld, wsleeping);
				DecimalFormat dfrmt = new DecimalFormat();
				dfrmt.setMaximumFractionDigits(2);
				if(debugPlayers.contains(player)) {
					player.sendMessage(ChatColor.YELLOW + "DEBUG: " + ChatColor.GRAY + "eventhandlers.sleeping: ");
					plugin.sleepingWorlds.keySet().forEach(world -> player.sendMessage(ChatColor.GRAY + plugin.sleepingWorlds.get(world).toString()));
					player.sendMessage(ChatColor.YELLOW + "DEBUG: " + ChatColor.GRAY + "eventhandlers.playersOnline: ");
					plugin.playersOnline.keySet().forEach(world -> player.sendMessage(ChatColor.GRAY + plugin.playersOnline.get(world).toString()));
					player.sendMessage(ChatColor.YELLOW + "DEBUG: " + ChatColor.GRAY + "skipping: " + skipping.toString());
				}
				player.sendMessage(ChatColor.translateAlternateColorCodes('&',
						sleepInfo.replace("%percent%", dfrmt.format((wsleeping/wonline)*100) + "%")
						.replace("%count%", Integer.toString(wsleeping))));
				if(debugPlayers.contains(player)) {
					player.sendMessage(ChatColor.YELLOW + "DEBUG: " + ChatColor.GRAY + "Checking if should skip....");
					player.sendMessage(ChatColor.YELLOW + "DEBUG: sleeping/onlineplayers : " + ChatColor.GRAY + (wsleeping/wonline));
				}
				if((wsleeping/wonline)*100 >= skipPercentage && !skipping.contains(pWorld)) { //Skip
					if(debugPlayers.contains(player)) {
						player.sendMessage(ChatColor.YELLOW + "DEBUG: " + ChatColor.GRAY + "Skipping...");
					}
					for (Player players : Bukkit.getOnlinePlayers()) {
						players.sendMessage(ChatColor.translateAlternateColorCodes('&', nightSkip.replace("%percent%", dfrmt.format((wsleeping/wonline)*100) + "%").replace("%count%", Integer.toString(wsleeping)).replace("%player%", player.getName())));
					}
					skipping.add(pWorld);
					recentlySkipped.add(pWorld);
					if(!useAnimation) {
						Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() { 
							public void run() {
								if(debugPlayers.contains(player)) {
									player.sendMessage(ChatColor.YELLOW + "DEBUG: " + ChatColor.GRAY + "Skipping after delay");
								}
								world.setTime(0);
								world.setStorm(false); 
								skipping.remove(pWorld);
								Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
									public void run() {
										sleepingWorlds.put(pWorld, 0);
										recentlySkipped.remove(pWorld);
										if(debugPlayers.contains(player)) {
											player.sendMessage(ChatColor.YELLOW + "DEBUG: " + ChatColor.GRAY + "sleeping set to 0");
										}
									} 
								}, 20L);
							} 
						}, 120L);
					}else {
						if(debugPlayers.contains(player)) {
							player.sendMessage(ChatColor.YELLOW + "DEBUG: " + ChatColor.GRAY + "Skipping with animation");
						}
					}
				}	
			}
		} }, 1L);
	}
}
