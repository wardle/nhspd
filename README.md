# NHSPD

[![Scc Count Badge](https://sloc.xyz/github/wardle/nhspd)](https://github.com/wardle/nhspd/)
[![Scc Cocomo Badge](https://sloc.xyz/github/wardle/nhspd?category=cocomo&avg-wage=100000)](https://github.com/wardle/nhspd/)
[![Tests](https://github.com/wardle/nhspd/actions/workflows/test.yml/badge.svg)](https://github.com/wardle/nhspd/actions/workflows/test.yml)
[![Clojars Project](https://img.shields.io/clojars/v/com.eldrix/nhspd.svg)](https://clojars.org/com.eldrix/nhspd)

Support for the UK NHS Postcode Directory, linking all UK postcodes to health data.

The NHSPD is published by the Office of National Statistics in the UK.

## Getting started

You can use NHSPD in three ways:
1. **As a command-line tool** - Download the prebuilt JAR or use Clojure
2. **As a library** - Include in your Clojure project
3. **As a SQLite index** - simply use your favourite programming language and SQLite bindings and directly use the index

### Option 1: Command-line tool

**Using prebuilt JAR (recommended):**

Download the latest release from [GitHub releases](https://github.com/wardle/nhspd/releases):

```shell
# Get help
java -jar nhspd.jar --help

# Create NHSPD database from latest release
java -jar nhspd.jar create --db nhspd.db

# Run web service
java -jar nhspd.jar serve --db nhspd.db --port 8080
```

**Using Clojure CLI:**

If you have [Clojure installed](https://clojure.org/guides/getting_started):

```shell
# Get help
clj -M:run --help

# Create NHSPD database from latest release
clj -M:run create --db nhspd.db

# Run web service  
clj -M:run serve --db nhspd.db --port 8080
```

**Using SQLite**

Once you have created an index, you can simply use your programming language's 
SQLite bindings (e.g. from Python) to make use of NHSPD data. You can continue
to use the command line tools here to create new indexes, or update an existing
index in place.

#### Available Commands

- **`create`** - Download latest NHSPD release and create SQLite database
- **`update`** - Update existing database with latest release
- **`import`** - Import specific NHSPD files
- **`serve`** - Run HTTP web service
- **`status`** - Show database information

#### Creating the database

To download and create a searchable SQLite database:

```shell
# Creates nhspd.db with core columns (fastest)
java -jar nhspd.jar create --db nhspd.db --profile core

# Creates nhspd.db with all available columns (slower, larger)
java -jar nhspd.jar create --db nhspd.db --profile all
```

The process takes 2-3 minutes depending on your connection and selected profile.

### Running a web service

Start a REST API server:

```shell
# Using JAR
java -jar nhspd.jar serve --db nhspd.db --port 8080

# Using Clojure CLI
clj -M:run serve --db nhspd.db --port 8080
```

#### HTTP API

Once running, the REST server provides data on UK postcodes:

```shell
# Basic postcode lookup
http localhost:8080/v1/nhspd/CF144XW

# Request JSON explicitly  
http -j localhost:8080/v1/nhspd/CF144XW
```

Postcodes are automatically normalised to PCD2 standard, so these are all equivalent:

```shell
http localhost:8080/v1/nhspd/CF14%204XW
http localhost:8080/v1/nhspd/CF14%20%204xw  
http localhost:8080/v1/nhspd/cf144x%20w
```

The API supports content negotiation - request `application/json`, `application/edn`, or `text/plain`.


Result:
```json
{
    "CALNCV": "W99999999",
    "CANNET": "N95",
    "CANREG": "Y1101",
    "CCG": "7A4",
    "CENED": "TNFL16",
    "CTRY": "W92000004",
    "DOINTR": "199906",
    "DOTERM": "",
    "EDIND": "1",
    "HRO": "W00",
    "LSOA01": "W01001770",
    "LSOA11": "W01001770",
    "MSOA01": "W02000384",
    "MSOA11": "W02000384",
    "NHSER": "W92",
    "NHSRLO": "W92",
    "OA01": "W00009154",
    "OA11": "W00009154",
    "ODSLAUA": "052",
    "OLDHA": "QW2",
    "OLDHRO": "W00",
    "OLDPCT": "6A8",
    "OSCTY": "W99999999",
    "OSEAST100M": 3175,
    "OSEAST1M": 317551,
    "OSGRDIND": "1",
    "OSHAPREV": "Q99",
    "OSHLTHAU": "7A4",
    "OSLAUA": "W06000015",
    "OSNRTH100M": 1793,
    "OSNRTH1M": 179319,
    "OSWARD": "W05000864",
    "PCD2": "CF14 4XW",
    "PCDS": "CF14 4XW",
    "PCON": "W07000051",
    "PCT": "7A4",
    "PSED": "62UBFL16",
    "RGN": "W99999999",
    "SCN": "N95",
    "STP": "W92",
    "UR01IND": "5",
    "USERTYPE": "0",
    "WARD98": "00PTMM"
}
```

If a postcode cannot be found, or an invalid postcode is entered, the server
will respond with a 404 response ('not found').

### Option 2: Use as a library

**From Clojars (recommended):**

```clojure
;; In deps.edn
{:deps {com.eldrix/nhspd {:mvn/version "2.0.LATEST"}}}
```

**From Git (development):**

```clojure
;; In deps.edn
{:deps {com.eldrix/nhspd {:git/url "https://github.com/wardle/nhspd.git"
                          :sha     "LATEST_SHA"}}}
```

**Usage:**

```clojure
(require '[com.eldrix.nhspd.api :as nhspd])

;; Open database
(with-open [svc (nhspd/open "nhspd.db")]
  ;; Look up postcodes
  (let [pc1 (nhspd/postcode svc "CF14 4XW")    ;; University Hospital of Wales
        pc2 (nhspd/postcode svc "CF47 9DT")]   ;; Prince Charles Hospital
    
    ;; Calculate distance between postcodes
    (nhspd/distance-between svc "CF14 4XW" "CF47 9DT")
    
    ;; Get OS grid reference with WGS84 coordinates
    (nhspd/with-wgs84 (nhspd/os-grid-reference svc "CF14 4XW"))))
```

**API Functions:**

- `(nhspd/open "path/to/nhspd.db")` - Open database connection
- `(nhspd/close svc)` - Close opened service
- `(nhspd/postcode svc "CF14 4XW")` - Get postcode data (keyword keys)
- `(nhspd/fetch-postcode svc "CF14 4XW")` - Get postcode data (string keys)
- `(nhspd/distance-between svc "PC1" "PC2")` - Calculate distance in metres
- `(nhspd/os-grid-reference svc "CF14 4XW")` - Get OS grid coordinates
- `(nhspd/with-wgs84 coords)` - Convert to WGS84 latitude/longitude
- `(nhspd/status svc)` - Get database statistics

