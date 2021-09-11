# NHSPD

[![Scc Count Badge](https://sloc.xyz/github/wardle/nhspd)](https://github.com/wardle/nhspd/)
[![Scc Cocomo Badge](https://sloc.xyz/github/wardle/nhspd?category=cocomo&avg-wage=100000)](https://github.com/wardle/nhspd/)

Support for the UK NHS Postcode Directory, linking all UK postcodes to health data.

The NHSPD is published by the Office of National Statistics in the UK.

## Getting started

You will need to [install clojure](https://clojure.org/guides/getting_started), if you don't already have it installed.

For example, on Mac OS X:

```shell
brew install clojure/tools/clojure
```


1. Create the postcode index
2. Run a web service
3. Use as a library

### 1. Create the postcode index

To download and create a searchable index, run the following command:

```shell
clj -M:download 
```

This will download the latest NHSPD release and index it in a file-based directory
in the current directory.

Or: 
```shell
clj -M:download /tmp/nhspd-2021-02
```

This will download the latest NHSPD release and create an index in the directory `/tmp/nhspd-2021-02`:

The process of downloading and indexing takes 2-3 minutes on my laptop.

### 2. Run a web service

This code is designed to be used as a library in a larger server application. 

However, if you wish, you can run a simple web service on any given port.
Here we start using the index `/tmp/nhspd-2021-02` created above, publishing on port 8080.

```shell
clj -M:serve /tmp/nhspd-2021-02 8080
```

Once running, a simple REST server will provide data on a UK postcode:

```shell
http -j localhost:8080/v1/nhspd/CF144XW
```

Entered postcodes will be normalized to the PCD2 standard. 
This means the following are equivalent:

```shell
http -j localhost:8080/v1/nhspd/CF14%204XW
http -j localhost:8080/v1/nhspd/CF14%20%204xw
http -j localhost:8080/v1/nhspd/cf144x%20w
```


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

### 3. Use as a library

This is the main intended use of this code as part of my wider PatientCare electronic health and care record system.

Include NHSPD in your deps.edn file (remember to use the latest SHA):

```clojure
[com.eldrix.nhspd {:git/url "https://github.com/wardle/nhspd.git"
                    :sha     "8de18b7038a9b4b8cbf6d829a128b8a71f19d978"}
```

And then in your code:

```clojure
(with-open [nhspd (open-index "/tmp/nhspd-2021-02")]
    (let [pc1 (fetch-postcode nhspd "CF14 4XW")    ;; University Hospital of Wales
          pc2 (fetch-postcode nhdpd "CF47 9DT")]   ;; Prince Charles Hospital
        (postcode/distance-between pc1 pc2))
```

`pc1` and `pc2` will contain the full NHSPD data for each postcode including
mappings to different health and care data. In this example, we use those data to calculate
a crude distance between those two postcodes.

When used a library, the code to run as a web service is not included, by design.

