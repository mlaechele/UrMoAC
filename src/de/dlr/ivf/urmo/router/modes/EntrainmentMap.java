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
package de.dlr.ivf.urmo.router.modes;

import java.util.HashMap;

/**
 * @class EntrainmentMap
 * @brief Represents which carrier may carry which vehicle
 * @author Daniel Krajzewicz (c) 2016 German Aerospace Center, Institute of Transport Research
 */
public class EntrainmentMap {
	/// @brief Map from carrier to carried modes
	public HashMap<String, Long> carrier2carried = new HashMap<>();
	
	
	/**
	 * @brief Adds a mode to carry to a given carrier
	 * @param carrier_full The name of the carrier
	 * @param carried The mode that may be carried
	 */
	public void add(String carrier_full, long carried) {
		if(carrier2carried.containsKey(carrier_full)) {
			carrier2carried.put(carrier_full, carrier2carried.get(carrier_full).longValue()|carried);
		} else {
			carrier2carried.put(carrier_full, carried);
		}
	}
}