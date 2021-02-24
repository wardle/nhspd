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
clj -M:download /tmp/nhspd-2021-02
```

This will download the latest NHSPD release and create an index in the directory `/tmp/nhspd-2021-02`:

### 2. Run a web service

This code is designed to be used as a library in a larger server application. 

However, if you wish, you can run a simple web service on any given port.
Here we start using the index `/tmp/nhspd-2021-02` created above, publishing on port 8080.

```shell
clj -M:serve /tmp/nhspd-2021-02 8080
```

Once running, a simple REST server will be running that will provide data on a UK postcode:

```shell
http localhost:8080/v1/nhspd/CF144XW
```

Result:
```
HTTP/1.1 200 OK

{"CANNET" "N95", "PCDS" "CF14 4XW", "NHSER" "W92", "SCN" "N95", "PSED" "62UBFL16", "CTRY" "W92000004", "OA01" "W00009154", "HRO" "W00", "OLDHA" "QW2", "RGN" "W99999999", "OSWARD" "W05000864", "LSOA01" "W01001770", "OSNRTH1M" 179319, "CANREG" "Y1101", "OSHLTHAU" "7A4", "CALNCV" "W99999999", "OSGRDIND" "1", "MSOA11" "W02000384", "MSOA01" "W02000384", "WARD98" "00PTMM", "OLDHRO" "W00", "CENED" "TNFL16", "OLDPCT" "6A8", "USERTYPE" "0", "OSEAST1M" 317551, "PCT" "7A4", "PCD2" "CF14 4XW", "NHSRLO" "W92", "OSNRTH100M" 1793, "DOTERM" "", "STP" "W92", "OSLAUA" "W06000015", "OSHAPREV" "Q99", "EDIND" "1", "LSOA11" "W01001770", "UR01IND" "5", "CCG" "7A4", "OSEAST100M" 3175, "DOINTR" "199906", "PCON" "W07000051", "ODSLAUA" "052", "OA11" "W00009154", "OSCTY" "W99999999"}
```

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

