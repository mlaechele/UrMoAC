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
package de.dlr.ivf.urmo.router.output.od;

import de.dlr.ivf.urmo.router.algorithms.edgemapper.MapResult;
import de.dlr.ivf.urmo.router.algorithms.routing.DijkstraResult;
import de.dlr.ivf.urmo.router.output.MeasurementGenerator;
import de.dlr.ivf.urmo.router.shapes.LayerObject;

/**
 * @class ODMeasuresGenerator
 * @brief Interprets a path to build an ODSingleResult
 * @author Daniel Krajzewicz (c) 2017 German Aerospace Center, Institute of Transport Research
 * @param <T>
 */
public class ODMeasuresGenerator extends MeasurementGenerator<ODSingleResult> {
	/**
	 * @brief Interprets the path to build an ODSingleResult
	 * @param beginTime The start time of the path
	 * @param from The origin the path started at
	 * @param to The destination accessed by this path
	 * @param dr The routing result
	 * @return An ODSingleResult computed using the given path
	 */
	public ODSingleResult buildResult(int beginTime, MapResult from, MapResult to, DijkstraResult dr) {
		ODSingleResult e = new ODSingleResult(from.em.getOuterID(), to.em.getOuterID(), from, to, dr);
		e.weightedDistance = e.dist * e.val;
		e.weightedTravelTime = e.tt * e.val;
		e.weightedValue = ((LayerObject) to.em).getAttachedValue() * e.val;
		e.connectionsWeightSum = e.val;
		return e;
	}
	
	
	/**
	 * @brief Builds an empty entry of type ODSingleResult
	 * @param srcID The id of the origin the path started at
	 * @param destID The id of the destination accessed by this path
	 * @return An empty entry type ODSingleResult
	 */
	public ODSingleResult buildEmptyEntry(long srcID, long destID) {
		return new ODSingleResult(srcID, destID);
	}

	
}