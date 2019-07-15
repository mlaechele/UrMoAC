/**
 * German Aerospace Center (DLR)
 * Institute of Transport Research (VF)
 * Rutherfordstra�e 2
 * 12489 Berlin
 * Germany
 * 
 * Copyright � 2016-2019 German Aerospace Center 
 * All rights reserved
 */
package de.dlr.ivf.urmo.router.algorithms.routing;

import java.util.HashMap;

import de.dlr.ivf.urmo.router.modes.Mode;
import de.dlr.ivf.urmo.router.shapes.DBEdge;
import de.dlr.ivf.urmo.router.shapes.DBNode;

/**
 * @class Measurements
 * @brief A storage for dijkstra results (1-to-many)
 * @author Daniel Krajzewicz (c) 2016 German Aerospace Center, Institute of Transport Research
 *         
 * - Weight is always at least the number of starting points         
 */
public class DijkstraEntry {
	/// @brief Previous entry
	public DijkstraEntry prev;
	/// @brief Reached node
	public DBNode n;
	/// @brief Used edge
	public DBEdge e;
	/// @brief The modes available at this step
	public long availableModes;
	/// @brief The modes used as last
	public Mode usedMode;
	/// @brief Used line
	public String line;
	/// @brief The traveled distance from the starting point
	public double distance;
	/// @brief The travel time since the begin of the route
	public double tt;
	/// @brief The travel time needed to pass this edge
	public double ttt;
	// @brief The interchange time at this node
	public double interchangeTT;
	/// @brief Whether the edge was approached using the opposite direction edge
	public boolean wasOpposite;
	/// @brief Additional measures for weighting the route
	public HashMap<String, Object> measures;

	public DijkstraEntry first = null;
	

	/**
	 * @brief Constructor
	 * @param !!!
	 */
	public DijkstraEntry(AbstractRoutingMeasure measure, DijkstraEntry _prev, DBNode _n, DBEdge _e, long _availableModes, Mode _usedMode, 
			double _distance, double _tt, String _line, double _ttt, double _interchangeTT, boolean _wasOpposite) {
		prev = _prev;
		n = _n;
		e = _e;
		distance = _distance;
		tt = _tt;
		ttt = _ttt;
		availableModes = _availableModes;
		usedMode = _usedMode;
		line = _line;
		interchangeTT = _interchangeTT;
		wasOpposite = _wasOpposite;
		measures = measure.buildMeasures(_prev, this);
		if(prev==null) {
			first = this;
		} else {
			first = prev.first;
		}
	}


	/** 
	 * @brief Returns the string representation
	 * @return The string representation
	 */
	@Override
	public String toString() {
		return n.id + "(tt=" + tt + "; modes=" + availableModes + ")";
	}


	/**
	 * @brief Returns whether the given requirements are fulfilled
	 * @param needsPT Whether the path must contain a PT element
	 * @return Whether the given requirements are fulfilled
	 */
	public boolean matchesRequirements(boolean needsPT) {
		if(!needsPT) {
			return true;
		}
		DijkstraEntry current = this;
		do {
			if(current.e.isGTFSEdge()) {
				return true;
			}
			current = current.prev;
		} while(current!=null);
		return false;
	}


	
	/** 
	 * @brief Returns the name for the used line
	 * @return The name of the used line
	 */
	public String buildLineModeID() {
		if(line.length()==0) {
			return usedMode.mml;
		}
		return line;
	}



}