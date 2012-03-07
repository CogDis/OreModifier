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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;

import com.precipicegames.utilities.misc.ChunkSQL;
import com.precipicegames.utilities.misc.LocationSQL;

public class ChunkWorkerThread implements Runnable {

	//The caches for different things
	private HashMap<ChunkSQL, ArrayList<LocationSQL>> possibleLocations;
	private ArrayList<ChunkSQL> markedChunks;
	private ConcurrentLinkedQueue<ChunkSnapshot> queue;
	private HashMap<Material, ArrayList<LocationSQL>> optimalMaterialLocations;
	private HashMap<Material, HashMap<String, Integer>> materialProperties;
	private HashSet<Material> materialTypes = new HashSet<Material>();
	//The Data Directory
	private File dataDir;
	//Database Stuff
	private Connection conn;
	private String locAdd = "INSERT INTO locations(locx, locy, locz, worldname, lochash) VALUES(?, ?, ?, ?, ?);";
	private String locGetAll = "SELECT * FROM locations;";
	private String chunkAdd = "INSERT INTO chunk(chunkx, chunkz, worldname, csqlhash) VALUES(?, ?, ?, ?);";
	private String chunkGetAll = "SELECT * FROM chunk;";
	private String markChunkAdd = "INSERT INTO marked_chunk(chunkx, chunkz, worldname, csqlhash) VALUES(?, ?, ?, ?);";
	private String markChunkGetAll = "SELECT * FROM marked_chunk;";
	//booleans to prevent issues with thread shutdown
	private boolean enabled = false;
	private boolean running = false;

	public ChunkWorkerThread(File dataDir, OreModifier instance) {
		Bukkit.getLogger().info("[OreModifier] Initizaliting ChunkWorkerThread...");
		this.dataDir = new File(dataDir+"/Chunk/");
		try {
			this.conn = DriverManager.getConnection("jdbc:sqlite:"+this.dataDir+"data.db");
			Statement statement = conn.createStatement();
			statement.executeUpdate("CREATE TABLE IF NOT EXISTS chunk (id INTEGER PRIMARY KEY, chunkx INTEGER, chunkz INTEGER, worldname TEXT, cciphash INTEGER UNIQUE);");
			statement.executeUpdate("CREATE TABLE IF NOT EXISTS locations (id INTEGER PRIMARY KEY, locx INTEGER, locy INTEGER, locz INTEGER, worldname TEXT, lochash INTEGER UNIQUE);");
			statement.executeUpdate("CREATE TABLE IF NOT EXISTS marked_chunk (id INTEGER PRIMARY KEY, chunkx INTEGER, chunkz INTEGER, worldname TEXT, cciphash INTEGER UNIQUE);");

			//Load up the data, if available
			this.queue = new ConcurrentLinkedQueue<ChunkSnapshot>(this.loadQueue());
			this.markedChunks = new ArrayList<ChunkSQL>(this.loadMarkedChunks());
			this.possibleLocations = new HashMap<ChunkSQL, ArrayList<LocationSQL>>(this.loadPossibleLocations());
			this.optimalMaterialLocations = new HashMap<Material, ArrayList<LocationSQL>>();

			this.materialProperties = new HashMap<Material, HashMap<String, Integer>>(instance.materialProperties);
			//Populate the HashSet with the vanilla ores
			this.materialTypes.add(Material.COAL_ORE);
			this.materialTypes.add(Material.IRON_ORE);
			this.materialTypes.add(Material.GOLD_ORE);
			this.materialTypes.add(Material.LAPIS_ORE);
			this.materialTypes.add(Material.REDSTONE_ORE);
			this.materialTypes.add(Material.DIAMOND_ORE);
		} catch (Exception e) {
			Bukkit.getLogger().warning("[OreModifier] SQLite errors on ChunkWorkerThread initizalition! Shutting down the plugin.");
			Bukkit.getLogger().warning("[OreModifier] "+e.getCause().getMessage());
			Bukkit.getServer().getPluginManager().disablePlugin(instance);
		}
		this.enabled = true;
	}

	public void run() {
		Bukkit.getLogger().info("[OreModifier] Starting ChunkWorkerThread...");
		boolean regenerate;
		this.running = true;
		while(this.enabled) {
			regenerate = false; //reset this


			//regenerate the optimal material locations
			if(regenerate) this.regenerate();
		}
		Bukkit.getLogger().info("[OreModifier] Stopping ChunkWorkerThread...");
		this.running = false;
	}

	public synchronized boolean hasInQueue(ChunkSnapshot snapshot) {
		return this.queue.contains(snapshot);
	}

	public synchronized boolean addToQueue(ChunkSnapshot snapshot) {
		return this.queue.offer(snapshot);
	}

	public synchronized boolean isChunkMarked(ChunkSQL chunk) {
		if(this.markedChunks.contains(chunk)) {
			return true;
		} else {
			this.markedChunks.add(chunk);
			return false;
		}
		
	}

	public synchronized void addToPossibleLocations(Location loc) {
		int chunkx, chunkz;
		LocationSQL location = new LocationSQL(loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getName());

		chunkx = loc.getBlockX() >> 4;
		chunkz = loc.getBlockZ() >> 4;

		ChunkSQL chunk = new ChunkSQL(chunkx, chunkz, loc.getWorld().getName());

		ArrayList<LocationSQL> temp;

		if(this.possibleLocations.containsKey(chunk)) {
			temp = this.possibleLocations.get(chunk); //pull the arraylist out
			temp.add(location); //add the location
			this.possibleLocations.put(chunk, temp); //put it all back in
		} else {
			temp = new ArrayList<LocationSQL>(); //init the ArrayList
			temp.add(location); //add the location
			this.possibleLocations.put(chunk, temp); //put it in the HashMap
		}
	}

	public synchronized void regenerate() {
		Iterator<Map.Entry<ChunkSQL, ArrayList<LocationSQL>>> it = this.possibleLocations.entrySet().iterator();
		HashMap<String, Integer> properties;

		while(it.hasNext()) {
			Map.Entry<ChunkSQL, ArrayList<LocationSQL>> set = it.next();
			ChunkSQL csql = set.getKey();
			Iterator<LocationSQL> it2 = set.getValue().iterator();
			int highestBlock = 0;
			int chunkBlockX, chunkBlockZ;

			chunkBlockX = csql.getX() << 4;
			chunkBlockZ = csql.getZ() << 4;

			//Get the highest block in the chunk
			for(int x = chunkBlockX; x < chunkBlockX+16; x++) {
				for(int z = chunkBlockZ; z < chunkBlockZ+16; z++) {
					int temp = Bukkit.getWorld(csql.getWorldName()).getHighestBlockYAt(x, z);
					if(temp > highestBlock) highestBlock = temp;
				}
			}

			//Iterate through the locations and place them in the optimal places
			while(it2.hasNext()) {
				LocationSQL location = it2.next();
				int y = location.getY();

				//Thank you Muddr for this, would of never thought about it
				for (Material ore : this.materialTypes) {
					properties = this.materialProperties.get(ore);
					int maxy = properties.get("maxy");
					int miny = properties.get("miny");
					int chance = properties.get("chance");

					if(y < maxy && y > miny && chance > 0) {
						ArrayList<LocationSQL> temploc = this.optimalMaterialLocations.get(ore);
						temploc.add(location);
						this.optimalMaterialLocations.put(ore, temploc);
					}

				}
			}
		}
	}

	public void shutdown() {
		this.enabled = false;
		while(this.running) { ; } //wait for the thread to shutdown

		try {
			this.saveSQL();
		} catch (Exception e) {
			Bukkit.getLogger().warning("[OreModifier] SQLite errors on ChunkWorkerThread shutdown! I will recover what I can next boot...");
			Bukkit.getLogger().warning("[OreModifier] "+e.getCause().getMessage());
		}

		this.possibleLocations = null;
		this.queue = null;
	}

	private void saveSQL() throws Exception {
		//remove the old data, if any exists
		this.conn.createStatement().execute("DELETE FROM locations;");
		this.conn.createStatement().execute("DELETE FROM chunks;");

		Iterator<Map.Entry<ChunkSQL, ArrayList<LocationSQL>>> it1 = possibleLocations.entrySet().iterator();

		PreparedStatement statement = this.conn.prepareStatement(this.locAdd);

		while(it1.hasNext()) {
			Map.Entry<ChunkSQL, ArrayList<LocationSQL>> set = it1.next();
			Iterator<LocationSQL> it2 = set.getValue().iterator();

			while(it2.hasNext()) {
				LocationSQL location = it2.next();
				statement.setInt(1, location.getX());
				statement.setInt(2, location.getY());
				statement.setInt(3, location.getZ());
				statement.setString(4, location.getWorldName());
				statement.setInt(5, location.hashCode());
				statement.addBatch();
			}
		}
		statement.executeBatch();

		Iterator<ChunkSnapshot> it2 = this.queue.iterator();
		statement = this.conn.prepareStatement(this.chunkAdd);

		while(it2.hasNext()) {
			ChunkSnapshot cs = it2.next();
			ChunkSQL csql = new ChunkSQL(cs.getX(), cs.getZ(), cs.getWorldName());
			statement.setInt(1, csql.getX());
			statement.setInt(2, csql.getZ());
			statement.setString(3, csql.getWorldName());
			statement.setInt(4, csql.hashCode());
			statement.addBatch();
		}
		statement.executeBatch();

		it1 = null;
		it2 = null;
		statement = null;

		this.conn.commit();
		this.conn.close();
		this.conn = null;
	}

	private Collection<ChunkSQL> loadMarkedChunks() throws Exception {
		Collection<ChunkSQL> temp = new ArrayList<ChunkSQL>();

		PreparedStatement statement = conn.prepareStatement(this.markChunkGetAll);
		ResultSet set = statement.executeQuery();

		if(set.first()) {
			set.beforeFirst(); //Position the pointer before the first row
			while(set.next()) {
				int chunkx = set.getInt("chunkx");
				int chunkz = set.getInt("chunkz");
				String worldName = set.getString("worldname");

				ChunkSQL chunk = new ChunkSQL(chunkx, chunkz, worldName);
				temp.add(chunk);
			}
		}

		return temp;

	}

	private Collection<ChunkSnapshot> loadQueue() throws Exception {
		Collection<ChunkSnapshot> temp = new ConcurrentLinkedQueue<ChunkSnapshot>();

		PreparedStatement statement = conn.prepareStatement(this.chunkGetAll);
		ResultSet set = statement.executeQuery();

		if(set.first()) {
			set.beforeFirst(); //Position the pointer before the first row
			while(set.next()) {
				int chunkx = set.getInt("chunkx");
				int chunkz = set.getInt("chunkz");
				String worldName = set.getString("worldname");

				ChunkSnapshot snapshot = Bukkit.getWorld(worldName).getChunkAt(chunkx, chunkz).getChunkSnapshot();
				temp.add(snapshot);
			}
		} //If the table is empty, this if statement should fail

		return temp;
	}

	private HashMap<ChunkSQL, ArrayList<LocationSQL>> loadPossibleLocations() throws Exception {
		HashMap<ChunkSQL, ArrayList<LocationSQL>> temp = new HashMap<ChunkSQL, ArrayList<LocationSQL>>();

		PreparedStatement statement = this.conn.prepareStatement(this.locGetAll);
		ResultSet set = statement.executeQuery();

		if(set.first()) {
			set.beforeFirst(); //Position the pointer before the first row
			while(set.next()) {
				int chunkx, chunkz, x, y, z;
				String worldName;

				x = set.getInt("locx");
				y = set.getInt("locy");
				z = set.getInt("locz");
				worldName = set.getString("worldname");

				LocationSQL location = new LocationSQL(x, y, z, worldName);

				chunkx = x >> 4; chunkz = z >> 4;

				ChunkSQL chunk = new ChunkSQL(chunkx, chunkz, worldName);
	
				ArrayList<LocationSQL> temp2;
	
				if(temp.containsKey(chunk)) {
					temp2 = temp.get(chunk); //pull the arraylist out
					temp2.add(location); //add the location
					temp.put(chunk, temp2); //put it all back in
				} else {
					temp2 = new ArrayList<LocationSQL>(); //init the ArrayList
					temp2.add(location); //add the location
					temp.put(chunk, temp2); //put it in the HashMap
				}
			}
		}
		return temp;
	}

}
