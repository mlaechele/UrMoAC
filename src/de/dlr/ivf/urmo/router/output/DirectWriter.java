/*
 * Copyright (c) 2016-2022 DLR Institute of Transport Research
 * All rights reserved.
 * 
 * This file is part of the "UrMoAC" accessibility tool
 * http://github.com/DLR-VF/UrMoAC
 * Licensed under the GNU General Public License v3.0
 * 
 * German Aerospace Center (DLR)
 * Institute of Transport Research (VF)
 * Rutherfordstraße 2
 * 12489 Berlin
 * Germany
 * http://www.dlr.de/vf
 */
package de.dlr.ivf.urmo.router.output;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Vector;

import de.dlr.ivf.urmo.router.algorithms.edgemapper.MapResult;
import de.dlr.ivf.urmo.router.algorithms.routing.DijkstraEntry;
import de.dlr.ivf.urmo.router.algorithms.routing.DijkstraResult;
import de.dlr.ivf.urmo.router.gtfs.GTFSStop;
import de.dlr.ivf.urmo.router.io.Utils;
import de.dlr.ivf.urmo.router.shapes.DBEdge;

/**
 * @class DirectWriter
 * @brief Writes PTODSingleResult results to a database / file
 * @author Daniel Krajzewicz (c) 2017 German Aerospace Center, Institute of Transport Research
 */
public class DirectWriter extends BasicCombinedWriter {
	/// @brief Counter of results added to the database / file so far
	private int batchCount = 0;
	/// @brief A map of edges to assigned destinations
	HashMap<DBEdge, Vector<MapResult>> nearestToEdges;

	
	/**
	 * @brief Constructor
	 * 
	 * Opens the connection to a PostGIS database and builds the table
	 * @param format The used format
	 * @param inputParts The definition of the input/output source/destination
	 * @param precision The floating point precision to use
	 * @param dropPrevious Whether a previous table with the name shall be dropped 
	 * @param rsid The RSID to use
	 * @param _nearestToEdges A map of edges to assigned destinations
	 * @throws IOException When something fails
	 */
	public DirectWriter(Utils.Format format, String[] inputParts, int precision, boolean dropPrevious, 
			int rsid, HashMap<DBEdge, Vector<MapResult>> _nearestToEdges) throws IOException {
		super(format, inputParts, "direct-output", precision, dropPrevious,
				"(fid bigint, sid bigint, edge text, line text, mode text, tt real, node text, idx integer)");
		addGeometryColumn("geom", rsid, "LINESTRING", 2);
		nearestToEdges = _nearestToEdges;
	}
	
	
	/** @brief Get the insert statement string
	 * @param[in] format The used output format
	 * @param[in] rsid The used projection
	 * @return The insert statement string
	 */
	protected String getInsertStatement(Utils.Format format, int rsid) {
		if(format==Utils.Format.FORMAT_POSTGRES) {
			return "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ST_GeomFromText(?, " + rsid + "))";
		}
		return "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
	}


	/**
	 * @brief Writes the "direct" representation of the result
	 * @param result The result to write
	 * @param from The origin
	 * @param needsPT Whether only results that contain a public transport trip shall be written
	 * @param singleDestination If >0 only this destination shall be regarded
	 * @throws SQLException When something fails
	 * @throws IOException When something fails
	 */
	public synchronized void writeResult(DijkstraResult result, MapResult from, boolean needsPT, long singleDestination) throws IOException {
		for(DBEdge e : result.edgeMap.keySet()) {
			DijkstraEntry toEdgeEntry = result.getEdgeInfo(e);
			if(!toEdgeEntry.matchesRequirements(needsPT)) {
				continue;
			}
			Vector<MapResult> toObjects = nearestToEdges.get(e);
			for(MapResult toObject : toObjects) {
				if(singleDestination>=0 && toObject.em.getOuterID()!=singleDestination) {
					continue;
				}
				// revert order
				Vector<DijkstraEntry> entries = new Vector<>();
				DijkstraEntry c = toEdgeEntry;
				do {
					entries.add(c);
					c = c.prev;
				} while(c!=null);
				Collections.reverse(entries);
				// go through entries
				int index = 0;
				for(DijkstraEntry current : entries) {
					String id = Long.toString(current.n.id);
					if(current.n instanceof GTFSStop) {
						id = ((GTFSStop) current.n).mid;
					}
					String routeID = getLineID(current.line);
					if (intoDB()) {
						try {
							_ps.setLong(1, from.em.getOuterID());
							_ps.setLong(2, toObject.em.getOuterID());
							_ps.setString(3, current.e.id);
							_ps.setString(4, routeID);
							_ps.setString(5, current.usedMode.mml);
							_ps.setDouble(6, current.ttt);
							_ps.setString(7, id);
							_ps.setInt(8, index);
							_ps.setString(9, current.e.geom.toText());
							_ps.addBatch();
							++batchCount;
							if(batchCount>100) {
								_ps.executeBatch();
								_connection.commit();
								batchCount = 0;
							}
						} catch (SQLException ex) {
							throw new IOException(ex);
						}
					} else {
						_fileWriter.append(from.em.getOuterID() + ";" + toObject.em.getOuterID() + ";" 
								+ current.e.id + ";" + routeID + ";"
								+ current.usedMode.mml + ";"  
								+ String.format(Locale.US, _FS, current.ttt) + ";" + id + ";" + index + ";"
								+ current.e.geom.toText() 
								+ "\n");
					}
					++index;
				}
			}
		}
		if (intoDB()) {
			try {
				_ps.executeBatch();
				_connection.commit();
			} catch (SQLException ex) {
				throw new IOException(ex);
			}	
		}
	}


}
