/**
 * This file is licensed under Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported (CC BY-NC-SA 3.0)
 * Copyright (C) 2012 PrecipiceGames.com <hackhalo2@precipicegames.com>
 *
 * You are free to:
 *     - Remix this work (adapt it to your needs under this license scope)
 *     - Share this work (copy, distrubute, and transmit it under this license scope)
 * Under the following Conditions:
 *     - You may not use this work for commercial purposes.
 *     - You must attribute the work in the manner specified by the author or licensor (but not in any way that
 *           suggests that they endorse you or your use of the work).
 *     -  If you alter, transform, or build upon this work, you may distribute the resulting work only under
 *           the same or similar license to this one.
 * With the understanding that:
 *     - Any of the above conditions can be waived if you get permission from the copyright holder. (See Note #1)
 *     - Where the work or any of its elements is in the public domain under applicable law, that status is in no
 *           way affected by the license. (See Note #2)
 *     - In no way are any of the following rights affected by the license:
 *         - Your fair dealing or fair use rights, or other applicable copyright exceptions and limitations; (See Note #3)
 *         - The author's moral rights; (See Note #4)
 *         - Rights other persons may have either in the work itself or in how the work is used, such as publicity
 *               or privacy rights. (See Note #5)
 *
 * Note #1: CC licenses anticipate that a licensor may want to waive compliance with a specific condition, such as attribution.
 * Note #2: A work is in the public domain when it is free for use by anyone for any purpose without restriction under copyright.
 * Note #3: All jurisdictions allow some limited uses of copyrighted material without permission. CC licenses do not affect the
 *       rights of users under those copyright limitations and exceptions, such as fair use and fair dealing where applicable.
 * Note #4: In addition to the right of licensors to request removal of their name from the work when used in a derivative or
 *       collective they don't like, copyright laws in most jurisdictions around the world (with the notable exception of the US
 *       except in very limited circumstances) grant creators "moral rights" which may provide some redress if a derivative work
 *       represents a "derogatory treatment" of the licensor's work.
 * Note #5: Publicity rights allow individuals to control how their voice, image or likeness is used for commercial purposes in
 *       public. If a CC-licensed work includes the voice or image of anyone other than the licensor, a user of the work may need
 *       to get permission from those individuals before using the work for commercial purposes.
 *
 * Please see http://creativecommons.org/licenses/by-nc-sa/3.0/ for more information.
 */

package com.precipicegames.utilities;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import com.precipicegames.utilities.misc.ChunkSQL;
import com.precipicegames.utilities.misc.LocationSQL;
import com.precipicegames.utilities.misc.OreChangeResult;

public class OreModifier extends JavaPlugin implements Runnable {
	
	public LinkedList<Integer> blockID = new LinkedList<Integer>();
	public int chance;
	public HashMap<String, Integer> loadedChunks;
	public WorldChunkListener listener = new WorldChunkListener(this);
	public ChunkWorkerThread thread;
	public File dataFile;
	private HashMap<String, Integer> tID = new HashMap<String, Integer>();
	public HashMap<Material, HashMap<String, Integer>> materialProperties;
	public HashMap<String, ConcurrentLinkedQueue<OreChangeResult>> queue;
	
	private boolean running = false;

	public void onLoad() {
		YamlConfiguration config = new YamlConfiguration();
		this.dataFile = new File(this.getDataFolder(), "config.yml");

		if(this.dataFile != null && this.dataFile.exists()) {
			try {
				config.load(this.dataFile);
			} catch (Exception e) {
				Bukkit.getLogger().warning("[OreModifier] Error loading config.yml! Using default values...");
			}
		} else {
			try {
				config.load(this.getResource("default.yml"));
			} catch (Exception e) {
				Bukkit.getLogger().warning("[OreModifier] Error loading the default config.yml from the jar! Defaulting values...");
			}
		}
		
		this.materialProperties = new HashMap<Material, HashMap<String, Integer>>(this.loadMaterialProperties());
		this.thread = new ChunkWorkerThread(this.getDataFolder(), this); //Load this last!
	}

	public void onEnable() {
		int id1, id2; //Thread process ID's
		this.getServer().getPluginManager().registerEvents(listener, this);
		this.loadedChunks = new HashMap<String, Integer>(this.getLoadedChunks()); 
		Bukkit.getLogger().info("[OreModifier] Starting up Threads...");
		id1 = this.getServer().getScheduler().scheduleSyncRepeatingTask(this, this, 1000L, 100L);
		id2 = this.getServer().getScheduler().scheduleAsyncDelayedTask(this, this.thread, 500L);
		if(id1 == -1 || id2 == -1) {
			Bukkit.getLogger().warning("[OreModifier] There was an issue starting the threads! Shutting down...");
			Bukkit.getPluginManager().disablePlugin(this);
		} else { //Add thread process ID's to the HashMap
			this.tID.put("MAIN", id1);
			this.tID.put("ASYNC", id2);
			Bukkit.getLogger().info("[OreModifier] I will now check for visible ores when a chunk is loaded");
		}
		
	}

	public void onDisable() {
		this.thread.shutdown();
		while(this.running) { ; } //Wait for the thread to stop, if it's running
		//TODO: save and nullify stuffs
	}
	
	private HashMap<String, Integer> getLoadedChunks() {
		HashMap<String, Integer> temp = new HashMap<String, Integer>();
		List<World> worlds = Bukkit.getWorlds();
		
		for(World world : worlds) {
			int loaded = 0;
			Chunk[] chunks = world.getLoadedChunks();
			
			for(Chunk chunk : chunks) {
				ChunkSnapshot snapshot = chunk.getChunkSnapshot();
				ChunkSQL chunksql = new ChunkSQL(snapshot.getX(), snapshot.getZ(), snapshot.getWorldName());
				
				//Check to make sure we are not re-adding a marked chunk back into the queue
				if(!this.thread.hasInQueue(snapshot) && !this.thread.isChunkMarked(chunksql)) thread.addToQueue(snapshot); 
				loaded++;
			}
			
			temp.put(world.getName(), loaded);
		}
		
		return temp;
	}
	
	private HashMap<Material, HashMap<String, Integer>> loadMaterialProperties() {
		HashMap<Material, HashMap<String, Integer>> temp = new HashMap<Material, HashMap<String, Integer>>();
		YamlConfiguration config = new YamlConfiguration();
		//TODO: this
		return temp;
	}
	
	/* This is run in the main thread so the server doesn't bitch about the tick list becoming out of sync */
	public void run() {
		this.running = true;
		List<World> worlds = Bukkit.getWorlds();
		//Iterate through all loaded worlds
		for(World world : worlds) {
			//Make sure there are enough loaded chunks to get a nice spread on block placements
			int loadedChunks = this.loadedChunks.get(world.getName());
			if(loadedChunks <= 19) continue; //if there are less then 20 chunks loaded in the world, continue to the next world
			
			Queue<OreChangeResult> worldQueue = this.queue.get(world.getName());
			Iterator<OreChangeResult> it = worldQueue.iterator();
			
			while(it.hasNext()) { //cycle through the OreChangeResults and make them live
				OreChangeResult ocr = it.next();
				LocationSQL loc = ocr.getLocation();
				Bukkit.getWorld(ocr.getWorldName()).getBlockAt(loc.getX(), loc.getY(), loc.getZ()).setTypeId(ocr.getToTypeID());
			}
		}
		
		//Sanity Check the async Thread
		int asyncThread = this.tID.get("ASYNC");
		if(!Bukkit.getServer().getScheduler().isCurrentlyRunning(asyncThread)) {
			this.running = false;
			Bukkit.getLogger().warning("[OreModifier] The async thread isn't running! Shutting down...");
			Bukkit.getPluginManager().disablePlugin(this);
			return;
		}
		this.running = false;
	}
}
