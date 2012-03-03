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
import java.util.concurrent.ConcurrentLinkedQueue;

import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;

import com.precipicegames.utilities.misc.ChunkSQL;
import com.precipicegames.utilities.misc.LocationSQL;

public class ChunkWorkerThread implements Runnable {
	
	public static HashMap<ChunkSQL, ArrayList<LocationSQL>> possibleLocations;
	public static ConcurrentLinkedQueue<ChunkSnapshot> queue;
	private File dataDir;
	private Connection conn;
	private String locAdd = "INSERT INTO locations(locx, locy, locz, worldname, lochash) VALUES(?, ?, ?, ?, ?);";
	private String locGet = "SELECT * FROM locations WHERE lochash=?;";
	private String locGetAll = "SELECT * FROM locations;";
	private String locRem = "DELETE FROM locations WHERE locx=? AND locy=? AND locz=?;";
	private String chunkAdd = "INSERT INTO chunk(chunkx, chunkz, worldname, cciphash) VALUES(?, ?, ?, ?);";
	private String chunkGet = "SELECT * FROM chunk WHERE cciphash=?;";
	private String chunkGetAll = "SELECT * FROM chunk;";
	private String chunkRem = "DELETE FROM chunk WHERE chunkx=? AND chunky=?;";
	
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
	}

	public void run() {
		// TODO Auto-generated method stub

	}
	
	public static void shutdown() {
		
		
		possibleLocations = null;
		queue = null;
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
		return null;
	}

}
