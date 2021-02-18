# NHSPD
Support for the UK NHS Postcode Directory, linking all UK postcodes to health data.

The NHSPD is published by the Office of National Statistics in the UK.

## Getting started

1. Create the postcode index
2. Use as a library

### 1. Create the postcode index

To download and create a searchable index, run the following command:

This will download the latest NHSPD release and create an index in the directory `/tmp/nhspd-2021-02`:

```shell
clj -M:download /tmp/nhspd-2021-02
```

### 2. Use as a library

Include NHSPD in your deps.edn file (remember to use the latest SHA):

```clojure
[com.eldrix.nhspd {:git/url "https://github.com/wardle/nhspd.git"
                    :sha     "4300f330841f58c0980412b225e1a397349e6522"}
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



