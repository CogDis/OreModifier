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
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;

import com.precipicegames.utilities.misc.ChunkSQL;
import com.precipicegames.utilities.misc.LocationSQL;

public class ChunkWorkerThread implements Runnable {
	
	private static HashMap<ChunkSQL, ArrayList<LocationSQL>> possibleLocations;
	private static ConcurrentLinkedQueue<ChunkSnapshot> queue;
	private File dataDir;
	private Connection conn;
	private String locAdd = "INSERT INTO locations(locx, locy, locz, worldname, lochash) VALUES(?, ?, ?, ?, ?);";
	private String locGet = "SELECT * FROM locations WHERE lochash=?;";
	private String locGetAll = "SELECT * FROM locations;";
	private String locRem = "DELETE FROM locations WHERE locx=? AND locy=? AND locz=?;";
	private String chunkAdd = "INSERT INTO chunk(chunkx, chunkz, worldname, csqlhash) VALUES(?, ?, ?, ?);";
	private String chunkGet = "SELECT * FROM chunk WHERE csqlhash=?;";
	private String chunkGetAll = "SELECT * FROM chunk;";
	private String chunkRem = "DELETE FROM chunk WHERE chunkx=? AND chunkz=?;";
	
	//booleans to prevent issues with thread shutdown
	private boolean enabled = false;
	private boolean running = false;
	
	public ChunkWorkerThread(File dataDir, OreModifier instance) {
		this.dataDir = new File(dataDir+"/Chunk/");
		try {
			this.conn = DriverManager.getConnection("jdbc:sqlite:"+this.dataDir+"data.db");
			Statement statement = conn.createStatement();
			statement.executeUpdate("CREATE TABLE IF NOT EXISTS chunk (id INTEGER PRIMARY KEY, chunkx INTEGER, chunkz INTEGER, worldname TEXT, cciphash INTEGER UNIQUE);");
			statement.executeUpdate("CREATE TABLE IF NOT EXISTS locations (id INTEGER PRIMARY KEY, locx INTEGER, locy INTEGER, locz INTEGER, worldname TEXT, lochash INTEGER UNIQUE);");
			
			//Load up the data, if available
			queue = new ConcurrentLinkedQueue<ChunkSnapshot>(this.loadQueue());
			possibleLocations = new HashMap<ChunkSQL, ArrayList<LocationSQL>>(this.loadPossibleLocations());
		} catch (Exception e) {
			Bukkit.getLogger().warning("[OreModifier] SQLite errors on ChunkWorkerThread init! Shutting down the plugin.");
			Bukkit.getLogger().warning("[OreModifier] "+e.getCause().getMessage());
			Bukkit.getServer().getPluginManager().disablePlugin(instance);
		}
		this.enabled = true;
	}

	public void run() {
		this.running = true;
		while(enabled) {
			
		}
		this.running = false;
	}
	
	public synchronized boolean addToQueue(ChunkSnapshot snapshot) {
		return queue.add(snapshot);
	}
	
	public synchronized void addToPossibleLocations(Location loc) {
		int chunkx, chunkz;
		LocationSQL location = new LocationSQL(loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getName());
		
		chunkx = loc.getBlockX() >> 4;
		chunkz = loc.getBlockZ() >> 4;
		
		ChunkSQL chunk = new ChunkSQL(chunkx, chunkz, loc.getWorld().getName());
		
		ArrayList<LocationSQL> temp;
		
		if(possibleLocations.containsKey(chunk)) {
			temp = possibleLocations.get(chunk); //pull the arraylist out
			temp.add(location); //add the location
			possibleLocations.put(chunk, temp); //put it all back in
		} else {
			temp = new ArrayList<LocationSQL>(); //init the ArrayList
			temp.add(location); //add the location
			possibleLocations.put(chunk, temp); //put it in the HashMap
		}
	}
	
	public void shutdown() {
		this.enabled = false;
		while(running) { ; } //wait for the thread to shutdown
		
		try {
			this.saveSQL();
		} catch (Exception e) {
			Bukkit.getLogger().warning("[OreModifier] SQLite errors on ChunkWorkerThread shutdown! I will recover what I can next boot...");
			Bukkit.getLogger().warning("[OreModifier] "+e.getCause().getMessage());
		}
		
		possibleLocations = null;
		queue = null;
	}
	
	private void saveSQL() throws Exception {
		//remove the old data, if any exists
		conn.createStatement().execute("DELETE FROM locations;");
		conn.createStatement().execute("DELETE FROM chunks;");
		
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
		
		Iterator<ChunkSnapshot> it2 = queue.iterator();
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
	
	private Collection<ChunkSnapshot> loadQueue() throws Exception {
		Collection<ChunkSnapshot> temp = new ConcurrentLinkedQueue<ChunkSnapshot>();
		
		PreparedStatement statement = conn.prepareStatement(chunkGetAll);
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
		
		PreparedStatement statement = conn.prepareStatement(locGetAll);
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
				
				chunkx = x >> 4;
				chunkz = z >> 4;
				
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
