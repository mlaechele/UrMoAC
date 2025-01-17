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

import de.dlr.ivf.urmo.router.algorithms.edgemapper.MapResult;
import de.dlr.ivf.urmo.router.algorithms.routing.DijkstraResult;

/**
 * @class MeasurementGenerator
 * @brief Something that interprets a path to build a specific result 
 * @author Daniel Krajzewicz (c) 2017 German Aerospace Center, Institute of Transport Research
 * @param <T>
 */
public abstract class MeasurementGenerator<T extends AbstractSingleResult> {
	/**
	 * @brief Interprets the path to build a result
	 * @param beginTime The start time of the path
	 * @param from The origin the path started at
	 * @param to The destination accessed by this path
	 * @param dr The routing result
	 * @return T - an abstract result type
	 */
	public abstract T buildResult(int beginTime, MapResult from, MapResult to, DijkstraResult dr);
	
	
	/**
	 * @brief Builds an empty entry of type T
	 * @param srcID The id of the origin the path started at
	 * @param destID The id of the destination accessed by this path
	 * @return An empty entry type T
	 */
	public abstract T buildEmptyEntry(long srcID, long destID);

}
