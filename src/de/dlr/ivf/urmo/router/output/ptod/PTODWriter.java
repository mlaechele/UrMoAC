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
package de.dlr.ivf.urmo.router.output.ptod;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Locale;

import de.dlr.ivf.urmo.router.io.Utils;
import de.dlr.ivf.urmo.router.output.AbstractResultsWriter;

/**
 * @class PTODWriter
 * @brief Writes PTODSingleResult results to a database / file
 * @author Daniel Krajzewicz (c) 2017 German Aerospace Center, Institute of Transport Research
 */
public class PTODWriter extends AbstractResultsWriter<PTODSingleResult> {
	/// @brief Counter of results added to the database / file so far
	private int batchCount = 0;
	
	
	/**
	 * @brief Constructor
	 * 
	 * Opens the connection to a PostGIS database and builds the table
	 * @param format The used format
	 * @param inputParts The definition of the input/output source/destination
	 * @param precision The floating point precision to use
	 * @param dropPrevious Whether a previous table with the name shall be dropped 
	 * @throws IOException When something fails
	 */
	public PTODWriter(Utils.Format format, String[] inputParts, int precision, boolean dropPrevious) throws IOException {
		super(format, inputParts, "pt-output", precision, dropPrevious, 
				"(fid bigint, sid bigint, avg_distance real, avg_tt real, "
				+ "avg_access_distance real, avg_access_tt real, avg_egress_distance real, avg_egress_tt real, "
				+ "avg_interchange_distance real, avg_interchange_tt real, avg_pt_distance real, avg_pt_tt real, "
				+ "avg_num_interchanges real, avg_waiting_time real, avg_init_waiting_time real, avg_num real, avg_value real)");
	}


	/** @brief Get the insert statement string
	 * @param[in] format The used output format
	 * @param[in] rsid The used projection
	 * @return The insert statement string
	 */
	protected String getInsertStatement(Utils.Format format, int rsid) {
		return "VALUES (?, ?,  ?, ?,  ?, ?, ?, ?,  ?, ?, ?, ?,  ?, ?, ?, ?, ?)";
	}

	
	/** 
	 * @brief Writes the results to the open database / file
	 * @param result The result to write
	 * @throws IOException When something fails
	 */
	@Override
	public void writeResult(PTODSingleResult result) throws IOException {
		if (intoDB()) {
			try {
				_ps.setLong(1, result.srcID);
				_ps.setLong(2, result.destID);
				_ps.setFloat(3, (float) result.weightedDistance);
				_ps.setFloat(4, (float) result.weightedTravelTime);
				_ps.setFloat(5, (float) result.weightedAccessDistance);
				_ps.setFloat(6, (float) result.weightedAccessTravelTime);
				_ps.setFloat(7, (float) result.weightedEgressDistance);
				_ps.setFloat(8, (float) result.weightedEgressTravelTime);
				_ps.setFloat(9, (float) result.weightedInterchangeDistance);
				_ps.setFloat(10, (float) result.weightedInterchangeTravelTime);
				_ps.setFloat(11, (float) result.weightedPTDistance);
				_ps.setFloat(12, (float) result.weightedPTTravelTime);
				_ps.setFloat(13, (float) result.weightedInterchangesNum);
				_ps.setFloat(14, (float) result.weightedWaitingTime);
				_ps.setFloat(15, (float) result.weightedInitialWaitingTime);
				_ps.setFloat(16, (float) result.connectionsWeightSum);
				_ps.setFloat(17, (float) result.weightedValue);
				_ps.addBatch();
				++batchCount;
				if(batchCount>10000) {
					_ps.executeBatch();
					batchCount = 0;
				}
			} catch (SQLException ex) {
				throw new IOException(ex);
			}
		} else {
			_fileWriter.append(result.srcID + ";" + result.destID + ";" 
					+ String.format(Locale.US, _FS, result.weightedDistance) + ";" 
					+ String.format(Locale.US, _FS, result.weightedTravelTime) + ";"
					+ String.format(Locale.US, _FS, result.weightedAccessDistance) + ";" 
					+ String.format(Locale.US, _FS, result.weightedAccessTravelTime) + ";"
					+ String.format(Locale.US, _FS, result.weightedEgressDistance) + ";" 
					+ String.format(Locale.US, _FS, result.weightedEgressTravelTime) + ";"
					+ String.format(Locale.US, _FS, result.weightedInterchangeDistance) + ";" 
					+ String.format(Locale.US, _FS, result.weightedInterchangeTravelTime) + ";"
					+ String.format(Locale.US, _FS, result.weightedPTDistance) + ";" 
					+ String.format(Locale.US, _FS, result.weightedPTTravelTime) + ";" 
					+ String.format(Locale.US, _FS, result.weightedInterchangesNum) + ";" 
					+ String.format(Locale.US, _FS, result.weightedWaitingTime) + ";" 
					+ String.format(Locale.US, _FS, result.weightedInitialWaitingTime) + ";"
					+ String.format(Locale.US, _FS, result.connectionsWeightSum) + ";" 
					+ String.format(Locale.US, _FS, result.weightedValue) + "\n");
		}
	}

}
