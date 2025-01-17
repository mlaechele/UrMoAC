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
package de.dlr.ivf.urmo.router.algorithms.routing;

import java.util.HashMap;
import java.util.HashSet;

/**
 * @class RouteWeightFunction_Price_TT
 * @brief Compares paths by price, then by the travel time
 */
public class RouteWeightFunction_Price_TT extends AbstractRouteWeightFunction {
	/** @brief Returns the number of required parameters
	 * @return The number of required parameters
	 */
	public int getParameterNumber() {
		return 0;
	}

	
	/**
	 * @brief Comparing function
	 * @param c1 First entry 
	 * @param c2 Second entry 
	 * @return Comparison 
	 */
	@Override
	public int compare(DijkstraEntry c1, DijkstraEntry c2) {
		double pc1 = (Double) c1.measures.get("price");
		double pc2 = (Double) c2.measures.get("price");
		if(pc1<pc2) {
			return -1;
		} else if(pc1>pc2) {
			return 1;
		}
		if(c1.tt<c2.tt) {
			return -1;
		} else if(c1.tt>c2.tt) {
			return 1;
		}
		return 0;
	}
	

	/**
	 * @brief Builds the measures used for weighting
	 * @param prev The prior path element
	 * @param current The current path element
	 * @return A map with build measures
	 */
	public HashMap<String, Object> buildMeasures(DijkstraEntry prev, DijkstraEntry current) {
		HashMap<String, Object> ret = new HashMap<>();
		HashSet<String> lines = new HashSet<String>();
		if(prev!=null) {
			lines.addAll((HashSet<String>) prev.measures.get("lines"));
		}
		ret.put("lines", lines);
		ret.put("price", current.e.getPrice(current.usedMode, lines));
		return ret;
	}
	
};
