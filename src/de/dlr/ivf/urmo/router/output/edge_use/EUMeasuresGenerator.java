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
package de.dlr.ivf.urmo.router.output.edge_use;

import de.dlr.ivf.urmo.router.algorithms.edgemapper.MapResult;
import de.dlr.ivf.urmo.router.algorithms.routing.DijkstraEntry;
import de.dlr.ivf.urmo.router.algorithms.routing.DijkstraResult;
import de.dlr.ivf.urmo.router.output.MeasurementGenerator;

/**
 * @class EUMeasuresGenerator
 * @brief Interprets a path to build an EUSingleResult
 * @author Daniel Krajzewicz (c) 2017 German Aerospace Center, Institute of Transport Research
 * @param <T>
 */
public class EUMeasuresGenerator extends MeasurementGenerator<EUSingleResult> {
	/**
	 * @brief Interprets the path to build an EUSingleResult
	 * @param beginTime The start time of the path
	 * @param from The origin the path started at
	 * @param to The destination accessed by this path
	 * @param dr The routing result
	 * @return An EUSingleResult computed using the given path
	 */
	public EUSingleResult buildResult(int beginTime, MapResult from, MapResult to, DijkstraResult dr) {
		DijkstraEntry current = dr.getEdgeInfo(to.edge);
		EUSingleResult e = new EUSingleResult(from.em.getOuterID(), to.em.getOuterID());
		do {
			DijkstraEntry next = current;
			e.addSingle(next.e, next.usedMode);
			current = current.prev;
		} while(current!=null);
		return e;
	}	
	
	
	/**
	 * @brief Builds an empty entry of type EUSingleResult
	 * @param srcID The id of the origin the path started at
	 * @param destID The id of the destination accessed by this path
	 * @return An empty entry type EUSingleResult
	 */
	public EUSingleResult buildEmptyEntry(long srcID, long destID) {
		return new EUSingleResult(srcID, destID);
	}

	
}