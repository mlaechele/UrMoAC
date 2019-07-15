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
package de.dlr.ivf.urmo.router.shapes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import com.infomatiq.jsi.Rectangle;
import com.infomatiq.jsi.SpatialIndex;
import com.infomatiq.jsi.rtree.RTree;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.PrecisionModel;

/**
 * @class DBNet
 * @brief A transportation network
 * @author Daniel Krajzewicz (c) 2016 German Aerospace Center, Institute of Transport Research
 */
public class DBNet {
	/// @brief Map from node ids to nodes
	public HashMap<Long, DBNode> nodes = new HashMap<>();
	/// @brief A list of edges
	private Vector<DBEdge> edges = new Vector<DBEdge>();
	/// @brief Map of edge names to edges
	private HashMap<String, DBEdge> name2edge = new HashMap<String, DBEdge>();
	/// @brief The network's minimum coordinates (left top)
	public Coordinate minCorner = null;
	/// @brief The network's maximum coordinates (right bottom)
	public Coordinate maxCorner = null;
	/// @brief The network's size
	public Coordinate size = new Coordinate(0, 0);
	/// @brief A spatial index for speeding up some computations
	public SpatialIndex rtree = new RTree();
	/// @brief The used precision model
	public PrecisionModel precisionModel = null;
	/// @brief The used srid
	public int srid = 0;


	/**
	 * @brief Constructor
	 */
	public DBNet() {
		rtree.init(null);
	}


	/**
	 * @brief Adds an edge to the road network
	 * @param e The edge to add
	 */
	public void addEdge(DBEdge e) {
		edges.add(e);
		name2edge.put(e.id, e);
		Rectangle r = new Rectangle();
		Coordinate[] cs = e.getGeometry().getCoordinates();
		for (int i = 0; i < cs.length; ++i) {
			Coordinate c = cs[i];
			if (minCorner == null) {
				minCorner = new Coordinate(c.x, c.y);
				maxCorner = new Coordinate(c.x, c.y);
			}
			minCorner.x = Math.min(minCorner.x, c.x);
			minCorner.y = Math.min(minCorner.y, c.y);
			maxCorner.x = Math.max(maxCorner.x, c.x);
			maxCorner.y = Math.max(maxCorner.y, c.y);
			size.x = maxCorner.x - minCorner.x;
			size.y = maxCorner.y - minCorner.y;
			r.add(new com.infomatiq.jsi.Point((float) c.x, (float) c.y));
		}
		rtree.add(r, (int) e.numID);
		precisionModel = e.geom.getPrecisionModel();
		srid = e.geom.getSRID();
	}


	// TODO: This is dumb; we first hide edges to properly compute the
	// boundaries, now we return them
	/**
	 * @brief Returns the list of edges this network is compound of
	 * @return The list of this network's edges
	 */
	public Vector<DBEdge> getEdges() {
		return edges;
	}


	/**
	 * @brief Adds a node to this road network
	 * @param node The node to add
	 */
	public void addNode(DBNode node) {
		nodes.put(node.id, node);
	}


	/**
	 * @brief Returns the named node or builds it if not existing
	 * @param id The id of the node to return
	 * @param pos The node's position
	 * @return The node
	 */
	public DBNode getNode(long id, Coordinate pos) {
		if (nodes.containsKey(id)) {
			return nodes.get(id);
		}
		DBNode n = new DBNode(id, pos);
		nodes.put(id, n);
		return n;
	}


	/**
	 * @brief Returns the named edge (by name)
	 * @param name The name of the edge
	 * @return The edge (if known, otherwise null)
	 */
	public DBEdge getEdgeByName(String name) {
		return name2edge.get(name);
	}


	/**
	 * @brief Returns the named edge (by id)
	 * @param name The id of the edge
	 * @return The edge (if known, otherwise null)
	 */
	public DBEdge getEdgeByID(int id) {
		for (DBEdge e : edges) {
			if (e.numID == id) {
				return e;
			}
		}
		return null;
	}

	
	/**
	 * @brief Removes this edge from the network
	 * 
	 * The references to this edge are removed from the start/end node.
	 */
	public void removeEdge(DBEdge edge) {
		edges.remove(edge);
		name2edge.remove(edge.id);
		edge.getFromNode().removeOutgoing(edge);
		edge.getToNode().removeIncoming(edge);
	}
	

	/**
	 * @brief Builds and return a spatial index for the parts of the road
	 *        network that may be travelled by the given transport modes
	 * @param modes The available modes of transport
	 * @return The network subparts compound of edges that may be traveled by the given modes
	 */
	public SpatialIndex getModedSpatialIndex(long modes) {
		SpatialIndex rtree = new RTree();
		rtree.init(null);
		for (DBEdge e : edges) {
			if (!e.allowsAny(modes)) {
				continue;
			}
			Rectangle r = new Rectangle();
			Coordinate[] cs = e.getGeometry().getCoordinates();
			for (int i = 0; i < cs.length; ++i) {
				Coordinate c = cs[i];
				r.add(new com.infomatiq.jsi.Point((float) c.x, (float) c.y));
			}
			rtree.add(r, (int) e.numID);
		}
		return rtree;
	}


	/**
	 * @brief Returns the map of edges that allow the given modes
	 * @param modes The used modes
	 * @return The edge that allow this transport mode !!! check usage
	 */
	public HashMap<Integer, DBEdge> getID2EdgeForMode(long modes) {
		HashMap<Integer, DBEdge> ret = new HashMap<>();
		for (DBEdge e : edges) {
			if (!e.allowsAny(modes)) {
				continue;
			}
			ret.put((int) e.numID, e);
		}
		return ret;
	}


	/**
	 * @brief Returns the nodes of this road network
	 * @return This road network's nodes
	 */
	public HashMap<Long, DBNode> getNodes() {
		return nodes;
	}


	/**
	 * @brief Returns the maximum if used within this network
	 * @return The maximum id used in this road network
	 */
	public long getMaxID() {
		long maxID = 0;
		for (Long id : nodes.keySet()) {
			maxID = Math.max(maxID, id);
		}
		for (DBEdge e : edges) {
			maxID = Math.max(maxID, e.numID);
		}
		return maxID;
	}


	/**
	 * @brief Prunes this road network to the named mode !!! not implemented
	 * @param mode The mode for which edges shall be kept
	 */
	public void pruneForModes(long modes) {
		Vector<DBEdge> toRemove = new Vector<>();
		for(DBEdge e : edges) {
			if(!e.allows(modes)) {
				toRemove.add(e);
			}
		}
		for(DBEdge e : toRemove) {
			removeEdge(e);
		}
	}


	/**
	 * @brief Checks which edges are not connected to the major part of the network and removes them
	 */
	public void dismissUnconnectedEdges() {
		Set<DBEdge> seen = new HashSet<>();
		Set<Set<DBEdge>> clusters = new HashSet<>();
		for (DBEdge e : edges) {
			if (seen.contains(e)) {
				continue;
			}
			Vector<DBEdge> next = new Vector<>();
			next.add(e);
			Set<DBEdge> cluster = new HashSet<>();
			DBEdge hadSeen = null;
			while (!next.isEmpty()) {
				DBEdge e2 = next.get(next.size() - 1);
				next.remove(next.size() - 1);
				cluster.add(e2);
				if (seen.contains(e2)&&!cluster.contains(e2)) {
					hadSeen = e2;
				}
				if(!seen.contains(e2)) {
					seen.add(e2);
					next.addAll(e2.getToNode().getOutgoing());
				}
			}
			if (clusters.size()!=0 && hadSeen != null) {
				for (Set<DBEdge> c : clusters) {
					if (c.contains(hadSeen)) {
						c.addAll(cluster);
					}
				}
			} else {
				clusters.add(cluster);
			}
		}
		//
		Set<DBEdge> major = null;
		for (Set<DBEdge> c : clusters) {
			if (major == null || major.size() < c.size()) {
				major = c;
			}
		}

		edges = new Vector<>();
		edges.addAll(major);
		for (Set<DBEdge> c : clusters) {
			if (c == major) {
				continue;
			}
			for (DBEdge e2 : c) {
				removeEdge(e2);
			}
		}

	}


	/**
	 * @brief Returns the precision model (for building GIS structures)
	 * @return The precision model
	 */
	public PrecisionModel getPrecisionModel() {
		return precisionModel;
	}


	/**
	 * @brief Returns the SRID (projection, for building GIS structures)
	 * @return The SRID
	 */
	public int getSRID() {
		return srid;
	}

}