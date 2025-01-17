#!/usr/bin/env python
# =========================================================
# parse_gtfs_ref.py
# @author Daniel Krajzewicz
# @date 08.08.2022
# @copyright Institut fuer Verkehrsforschung, 
#            Deutsches Zentrum fuer Luft- und Raumfahrt
# @brief Parses the GTFS definition and builds import information
# =========================================================


# --- imported modules ------------------------------------
from bs4 import BeautifulSoup
import bs4
import psycopg2, sys, os.path, io


# --- enum definitions ------------------------------------
"""! @brief A map of file type MML to their enum"""
fieldTypeMap = {
    "Color" : "FieldType.COLOR",
    "Currency code" : "FieldType.CURRENCY_CODE",
    "Currency amount" : "FieldType.CURRENCY_AMOUNT",
    "Date" : "FieldType.DATE",
    "Email" : "FieldType.EMAIL",
    "Enum" : "FieldType.ENUM",
    "ID" : "FieldType.ID",
    "Language code" : "FieldType.LANGUAGE_CODE",
    "Latitude" : "FieldType.LATITUDE",
    "Longitude" : "FieldType.LONGITUDE",
    "Float" : "FieldType.FLOAT",
    "Integer" : "FieldType.INTEGER",
    "Phone number" : "FieldType.PHONE_NUMBER",
    "Time" : "FieldType.TIME",
    "Text" : "FieldType.TEXT",
    "Timezone" : "FieldType.TIMEZONE",
    "URL" : "FieldType.URL",

    "Unique ID" : "FieldType.ID",
    "Foreign ID referencing" : "FieldType.ID",
    "Non-negative integer" : "FieldType.INTEGER",
    "Non-negative float" : "FieldType.FLOAT",
    "Non-zero integer" : "FieldType.INTEGER",
    "Positive integer" : "FieldType.INTEGER",
    "Non-null integer" : "FieldType.INTEGER",
    "Positive float" : "FieldType.FLOAT",
    "Text or URL or Email or Phone number" : "FieldType.TEXT",
    "Foreign ID" : "FieldType.ID",
}


"""! @brief A map of presence MML to their enum"""
fieldPresenceMap = {
    "Required" : "Presence.REQUIRED",
    "Optional" : "Presence.OPTIONAL",
    "Conditionally Required" : "Presence.CONDITIONALLY_REQUIRED",
    "Conditionally Forbidden" : "Presence.CONDITIONALLY_FORBIDDEN"

}


# --- method definitions ----------------------------------
def parseTable(info, fdo, optional):
    """! @brief Parses a single 'field-definitions' entry
                consisting of the file name and the entry 
                definitions.
                
                The contents are written to the given file.
                
    @param info The definitions page as parsed HTML
    @param fdo The file to write the parsed definitions to
    @param optional A list to append the GTFS file name to if it's optional
    """
    tabName = info.contents[0].strip()
    print (tabName)
    tabName = tabName.replace(".txt", "")
    fdo.write("    \"" + tabName + "\" : [\n")
    n = info.next_sibling.next_sibling
    #print (">%s<" % n.contents[1].contents[0])
    if n.contents[1].contents[0]=="Optional" or n.contents[1].contents[0]=="Conditionally Required":
        optional.append(tabName)
    n = n.next_sibling # primary key
    while type(n)!=bs4.element.Tag or n.contents[0].name!='div':
        n = n.next_sibling # skip text
    tab = n.find("table")
    trs = tab.find_all("tr")
    vals = []
    for i,tr in enumerate(trs):
        if i==0:
            continue
        if tr.parent.parent!=tab:
            continue
        fieldName = tr.contents[1].contents[0].contents[0].strip()
        fieldType = tr.contents[3].contents[0].strip()
        fieldPresence = tr.contents[5].contents[0]
        if type(fieldPresence)==bs4.element.Tag:
            fieldPresence = fieldPresence.contents[0].strip()

        fieldType = fieldTypeMap[fieldType]
        fieldPresence = fieldPresenceMap[fieldPresence]
        str = "        [ \"%s\", %s, %s ]" % (fieldName, fieldType, fieldPresence)
        vals.append(str)
    fdo.write(",\n".join(vals))
    fdo.write("\n    ]")


def writeHeader(fdo):
    fdo.write("""#!/usr/bin/env python
# =========================================================
# gtfs_defs.py
# @author Daniel Krajzewicz
# @date 08.08.2022
# @copyright Institut fuer Verkehrsforschung, 
#            Deutsches Zentrum fuer Luft- und Raumfahrt
# @brief GTFS file definitions parsed from the original documentation
#        Generated by 'parse_reference.py'
# =========================================================


# --- imported modules ------------------------------------
from enum import IntEnum


# --- enum definitions ------------------------------------
class Presence(IntEnum):
    \"\"\"! @brief An enumeration of known presence conditions\"\"\"
    REQUIRED = 0
    OPTIONAL = 1
    CONDITIONALLY_REQUIRED = 2
    CONDITIONALLY_FORBIDDEN = 3


class FieldType(IntEnum):
    \"\"\"! @brief An enumeration of known field types\"\"\"
    COLOR = 0
    CURRENCY_CODE = 1
    CURRENCY_AMOUNT = 2
    DATE = 3
    EMAIL = 4
    ENUM = 5
    ID = 6
    LANGUAGE_CODE = 7
    LATITUDE = 8
    LONGITUDE = 9
    FLOAT = 10
    INTEGER = 11
    PHONE_NUMBER = 12
    TIME = 13
    TEXT = 14
    TIMEZONE = 15
    URL = 16


# --- GTFS definitions ------------------------------------
""")



def main(argv):
    """! @brief Parses a copy of the GTFS definitions page
    
                Reads the page and parses it using BeautifulSoup.
                Searches for the "field-definitions" part and parses
                the file definitions from the children. Writes the 
                definitions to a file.
    """
    fd = io.open("Reference - General Transit Feed Specification.htm", 'r', encoding='utf-8-sig')
    html = fd.read()
    fd.close()

    fdo = open("gtfs_defs.py", "w")
    writeHeader(fdo)
    fdo.write("tableDefinitions = {\n")
    optional = []
    soup = BeautifulSoup(html, features="html.parser")
    fieldDefs = soup.find("h2", id="field-definitions")
    nextSibling = fieldDefs
    first = True
    while nextSibling:
        if type(nextSibling)==bs4.element.Tag:
            if len(nextSibling.contents)>0 and len(nextSibling.contents[0])>0 and str(nextSibling.contents[0]).endswith(".txt"):
                if not first: fdo.write(",\n")
                first = False
                parseTable(nextSibling, fdo, optional)
        nextSibling = nextSibling.next_sibling
    fdo.write("\n}\n\n\n")
    optional = ['"' + o + '"' for o in optional]
    fdo.write("optionalTables = [ %s ]\n\n" % ", ".join(optional))
    fdo.close()


# -- main check
if __name__ == '__main__':
  main(sys.argv)
  
  