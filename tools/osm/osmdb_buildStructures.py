#!/usr/bin/env python
# =========================================================
# osmdb_getStructures.py
# @author Daniel Krajzewicz
# @author Simon Nieland
# @date 14.08.2019
# @copyright Institut fuer Verkehrsforschung, 
#            Deutsches Zentrum fuer Luft- und Raumfahrt
# @brief Extracts specific structures (not the network) from an OSM-database representation
# Call with
#  osmdb_buildStructures.py <INPUT_TABLE> <DEF_FILE> <OUTPUT_TABLE> 
# where <INPUT_TABLE> is defined as:
#  <HOST>;<DB>;<SCHEMA>.<PREFIX>;<USER>;<PASSWD>  
# and <OUTPUT_TABLE> is defined as:
#  <HOST>;<DB>;<SCHEMA>.<NAME>;<USER>;<PASSWD>  
# =========================================================


# --- imported modules ------------------------------------
import sys, os
import psycopg2
import datetime
import math
import osm

script_dir = os.path.dirname( __file__ )
mymodule_dir = os.path.join(script_dir, '..', 'helper')
sys.path.append(mymodule_dir)
from wkt import *
from geom_helper import *



# --- global definitions ----------------------------------
"""! @brief A map from a data type to the respective tags"""
subtype2tag = {
  "node": "ntag",
  "way": "wtag",
  "rel": "rtag"
}


# --- class definitions -----------------------------------
class OSMExtractor:
    """! @brief A class for extracting defined structures from OSM
  
    The class reads a set of definitions of the things to extract from a definitions file.
    The definitions are stored as !!!.
    OSM data is retrieved from a database that was imported using "osm2db.py".
    @see osm2db.py  
    """
    
    def __init__(self):
        """! @brief Constructor
        @param self The class instance
        """
        # definitions of what to load
        self._defs = { "node": [], "way": [], "rel": [] }
        self._roles = set()
        # ids of matching objects
        self._objectIDs = { "node": [], "way": [], "rel": [] }
        # matching objects with geometries
        self._objectGeoms = { "node": {}, "way": {}, "rel": {} }
    

    def loadDefinitions(self, fileName):
        """! @brief Loads the definitions about what to extract from a file
        @param self The class instance
        @param fileName The name of the file to read the definitions from
        """
        fd = open(fileName)
        subtype = ""
        for l in fd:
            if len(l.strip())==0: continue # skip empty lines
            elif l[0]=='#': continue # skip comments
            elif l[0]=='[':
                # parse subtype
                subtype = l[1:l.find(']')].strip()
                continue
            elif l[0]=='<':
                # parse roles
                self._roles.add(l[1:l.find('>')].strip())
                continue
            # parse definition
            self._defs[subtype].append(l.strip())
        return self._defs
    

    def _getObjects(self, conn, cursor, schema, prefix, subtype, op, k, v):
        """! @brief Returns the IDs of the objects in the given OSM table that match the given definitions 
        @param self The class instance
        @param conn The database connection to use
        @param cursor The database cursor to use 
        @param schema The database sceham to use
        @param prefix The OSM database prefix to use
        @param subtype The OSM subtype to extract information about
        @param k The key value to match
        @param v The value value to match, "*" if any
        @todo Make database connection an attribute of the class
        """
        ret = set()
        """
        if subtype=="rel":
            ret.add(14183452)
        return ret
        """
        if k=="*": # fetch all
            cursor.execute("SELECT id FROM %s.%s_%s" % (schema, prefix, subtype))
        elif v=='*': # fetch all with a matching key
            cursor.execute("SELECT id FROM %s.%s_%s WHERE k='%s'" % (schema, prefix, subtype2tag[subtype], k))
        else: 
            # fetch all with a key/value pair
            cursor.execute("SELECT id FROM %s.%s_%s WHERE k='%s' AND v%s'%s'" % (schema, prefix, subtype2tag[subtype], k, op, v))
        conn.commit()
        for r in cursor.fetchall():
            ret.add(int(r[0]))
        # !!! add option for extracting (a) certain structure/s by id
        return ret


    def loadObjectIDs(self, conn, cursor, schema, prefix):
        """! @brief Returns the IDs of the objects in the given OSM data that match the given definitions 
        @param self The class instance
        @param conn The database connection to use
        @param cursor The database cursor to use 
        @param schema The database sceham to use
        @param prefix The OSM database prefix to use
        @todo Make database connection an attribute of the class
        """
        for subtype in ["node", "way", "rel"]:
            print (" ... for %s" % subtype)
            for definition in self._defs[subtype]:
                # get objects
                subdefs = definition.split("&")
                collected = None
                for sd in subdefs:
                    sd = sd.strip()
                    if sd=="*":
                        oss = self._getObjects(conn, cursor, schema, prefix, subtype, "*", "*", "*")
                        k = "*"
                        v = "*"
                    else:
                        for op in ["!=", "!~", "=", "~"]:
                            if sd.find(op)>=0:
                                k,v = sd.split(op)
                                oss = self._getObjects(conn, cursor, schema, prefix, subtype, op, k, v)
                                break
                    if collected!=None:
                        collected = collected.intersection(oss)
                    else:
                        collected = oss
                if len(self._objectIDs[subtype])==0:
                    self._objectIDs[subtype] = collected
                else:
                    self._objectIDs[subtype] = self._objectIDs[subtype].union(collected) 
            print (" ... %s found" % len(self._objectIDs[subtype]))
            # !!! make this optional
            #for o in self._objectIDs[subtype]:
            #  print ("%s %s" % (subtype, o))
   
    
    def collectReferencedObjects(self, conn, cursor, schema, prefix):
        """! @brief Collects all needed geometry information 
        @param self The class instance
        @param conn The database connection to use
        @param cursor The database cursor to use 
        @param schema The database sceham to use
        @param prefix The OSM database prefix to use
        @todo Make database connection an attribute of the class
        """
        def divide_chunks(l, n):
            for i in range(0, len(l), n):
                yield l[i:i + n]
        
        missingRELids = list(self._objectIDs["rel"])
        missingWAYids = set(self._objectIDs["way"])
        missingNODEids = set(self._objectIDs["node"])
        area = osm.OSMArea()
    
        # collect relation members, first
        seenRELs = set(missingRELids)
        rel2items = {}
        print (" ... for relations")
        while len(missingRELids)!=0:
            missingRELidsN = set()
            for mRELids in divide_chunks(missingRELids, 10000):
                idstr = ",".join([str(id) for id in mRELids])
                cursor.execute("SELECT rid,elemid,type,role FROM %s.%s_member WHERE rid in (%s) ORDER BY rid,role,idx" % (schema, prefix, idstr))
                conn.commit()
                for r in cursor.fetchall():
                    # close the currently processed relation if a new starts
                    rid = int(r[0])
                    relation = area.getRelation(rid)
                    if not relation:
                        relation = osm.OSMRelation(rid)
                        area.addRelation(relation)
                    role = r[3]
                    #if len(self._roles)>0 and role not in self._roles:
                    #    continue
                    iid = int(r[1])
                    relation.addMember(iid, r[2], r[3])
                    if r[2]=="rel" or r[2]=="relation":
                        if iid==int(r[0]):
                            print ("Self-referencing relation %s" % r[0])
                            continue
                        if iid not in seenRELs:
                            missingRELidsN.add(iid)
                        if True and iid in self._objectIDs["rel"]:
                            self._objectIDs["rel"].remove(iid)
                    elif r[2]=="way":
                        missingWAYids.add(iid)
                        if True and iid in self._objectIDs["way"]:
                            self._objectIDs["way"].remove(iid)
                    elif r[2]=="node":
                        missingNODEids.add(iid)
                        if True and iid in self._objectIDs["node"]:
                            self._objectIDs["node"].remove(iid)
                    else:
                        print ("Check type '%s'" % r[2])
            missingRELids = list(missingRELidsN)
        # collect ways
        print (" ... for ways")
        # https://stackoverflow.com/questions/30773911/union-of-multiple-sets-in-python
        npoints = [missingNODEids]
        if len(missingWAYids)!=0:
            for mWAYids in divide_chunks(list(missingWAYids), 10000):
                idstr = ",".join([str(id) for id in mWAYids])
                cursor.execute("SELECT id,refs FROM %s.%s_way WHERE id in (%s)" % (schema, prefix, idstr))
                conn.commit()
                for r in cursor.fetchall():
                    area.addWay(osm.OSMWay(int(r[0]), r[1]))
                    npoints.append(r[1])
                    for nID in r[1]:
                        if True and nID in self._objectIDs["node"]:
                            self._objectIDs["node"].remove(nID)
        missingNODEids = set.union(*npoints)    
        # collect nodes
        print (" ... for nodes")
        if len(missingNODEids)!=0:
            for mNODEids in divide_chunks(list(missingNODEids), 10000):
                idstr = ",".join([str(id) for id in mNODEids])
                cursor.execute("SELECT id,ST_AsText(pos) FROM %s.%s_node WHERE id in (%s)" % (schema, prefix, idstr))
                conn.commit()
                for r in cursor.fetchall():
                    area.addNode(osm.OSMNode(int(r[0]), parsePOINT2XY(r[1])))
        # clear - no longer used
        missingNODEids = []
        missingWAYids = []
        missingRELids = []
        return area


    def _checkCommit(self, forced, entries, conn, cursor, schema, name):
        """! @brief Inserts read objects if forced or if their number is higher than 10000
        @param self The class instance
        @param entries The objects to insert
        @param conn The database connection to use
        @param cursor The database cursor to use 
        @param schema The database sceham to use
        @param name The name of the database table
        @todo Make database connection an attribute of the class
        """
        if not forced and len(entries)<10000:
            return
        if len(entries)==0:
            return
        args = ','.join(cursor.mogrify("(%s, %s, ST_GeomFromText(%s, 4326), ST_GeomFromText(%s, 4326), ST_Centroid(ST_ConvexHull(ST_GeomFromText(%s, 4326))))", i).decode('utf-8') for i in entries)
        cursor.execute("INSERT INTO %s.%s(gid, type, polygon, geom_collection, centroid) VALUES " % (schema, name) + (args))
        conn.commit()
        del entries[:]
        

    def _addItem(self, entries, item, conn, cursor, schema, name):
        """ @brief Prebuilds the given item's insertion string and checks whether it shall be submitted
        @param self The class instance
        @param entries The objects to insert
        @param item The item to add to the objects
        @param conn The database connection to use
        @param cursor The database cursor to use 
        @param schema The database sceham to use
        @param name The name of the database table
        @todo Make database connection an attribute of the class
        """
        id, type, polys, geom = item.getDescriptionWithPolygons()
        if len(geom)==0:
            print ("Missing geometry for %s %s" % (geom[1], geom[0]))
            return
        geom = "GEOMETRYCOLLECTION(" + geom + ")"
        centroid = geom
        if polys!=None and len(polys)!=0:
            # remove polygons within other
            toRemove = []
            for i,p1 in enumerate(polys):
                for j,p2 in enumerate(polys):
                    if i==j: continue
                    if polygon_in_polygon(p1[0], p2[0]): toRemove.append(i)
                    if polygon_in_polygon(p2[0], p1[0]): toRemove.append(j)
            npolys = []
            for i,p in enumerate(polys):
                if i in toRemove: continue
                npolys.append(p)
            polys = npolys
            #
            npolys = []
            for poly in polys:
                npoly = []
                for polypart in poly:
                    npolypart = "(" + ",".join(["%s %s" % (p[0], p[1]) for p in polypart]) + ")"
                    npoly.append(npolypart)
                npolys.append("(" + ",".join(npoly) + ")")
            polys = "MULTIPOLYGON(" + ",".join(npolys) + ")"
            centroid = polys
        else:
            polys = "MULTIPOLYGON EMPTY"
        entries.append([id, type, polys, geom, centroid])
        self._checkCommit(False, entries, conn, cursor, schema, name)


    def storeObjects(self, area, conn, cursor, schema, name):
        """! @brief Stores objects and their geometries in the given table(s)
        
        @param conn The database connection to use
        @param cursor The database cursor to use 
        @param schema The database sceham to use
        @param name The name of the database to store the objects in
        @param namePS The name of the database to store the objects' outline as points in
        @todo Make database connection an attribute of the class
        """
        geometries = []
        fr = 0
        for rID in self._objectIDs["rel"]:
            if area._relations[rID].buildGeometry(area):
                self._addItem(geometries, area._relations[rID], conn, cursor, schema, name)
            else: fr += 1
        fw = 0
        for wID in self._objectIDs["way"]:
            if area._ways[wID].buildGeometry(area):
                self._addItem(geometries, area._ways[wID], conn, cursor, schema, name)
            else: fw += 1
        for nID in self._objectIDs["node"]:
            self._addItem(geometries, area._nodes[nID], conn, cursor, schema, name)
        self._checkCommit(True, geometries, conn, cursor, schema, name)
        return len(self._objectIDs["node"])+len(self._objectIDs["way"])+len(self._objectIDs["rel"]), fw, fr



# --- main method -----------------------------------------
def main(argv):       
    t1 = datetime.datetime.now()
    # -- open connection
    (host, db, tableFull, user, password) = sys.argv[1].split(";")
    (schema, prefix) = tableFull.split(".")
    conn = psycopg2.connect("dbname='%s' user='%s' host='%s' password='%s'" % (db, user, host, password))
    cursor = conn.cursor()

    # -- load definitions of things to extract
    extractor = OSMExtractor()
    print ("Loading definition of things to extract")
    extractor.loadDefinitions(sys.argv[2])
    print ("Determining object IDs")
    extractor.loadObjectIDs(conn, cursor, schema, prefix)
    print ("Collecting object geometries")
    area = extractor.collectReferencedObjects(conn, cursor, schema, prefix)

    # -- write extracted objects
    # --- open connection
    print ("Building destination databases")
    (host, db, tableFull, user, password) = sys.argv[3].split(";")
    (schema, name) = tableFull.split(".")
    conn2 = psycopg2.connect("dbname='%s' user='%s' host='%s' password='%s'" % (db, user, host, password))
    cursor2 = conn2.cursor()
    # --- build tables
    cursor2.execute("DROP TABLE IF EXISTS %s.%s" % (schema, name))
    cursor2.execute("CREATE TABLE %s.%s ( gid bigint, type varchar(4) );" % (schema, name))
    cursor2.execute("SELECT AddGeometryColumn('%s', '%s', 'centroid', 4326, 'POINT', 2);" % (schema, name))
    cursor2.execute("SELECT AddGeometryColumn('%s', '%s', 'polygon', 4326, 'MULTIPOLYGON', 2);" % (schema, name))
    cursor2.execute("SELECT AddGeometryColumn('%s', '%s', 'geom_collection', 4326, 'GEOMETRYCOLLECTION', 2);" % (schema, name))
    conn2.commit()
    # --- insert objects
    print ("Building and storing objects")
    num, fw, fr = extractor.storeObjects(area, conn2, cursor2, schema, name)

    # --- finish
    t2 = datetime.datetime.now()
    dt = t2-t1
    print ("Built %s objects" % num)
    print (" in %s" % dt)
    if fw>0: print (" %s ways could not be build" % fw)
    if fr>0: print (" %s reletions could not be build" % fr)


# -- main check
if __name__ == '__main__':
    main(sys.argv)
    
