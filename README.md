# NHSPD
Support for the UK NHS Postcode Directory, linking all UK postcodes to health data.

The NHSPD is published by the Office of National Statistics in the UK.

## Getting started

1. Install clojure
2. Clone the repository
3. Create the postcode index
4. Use as a library

To download and create a searchable index, run the following command:

This will create an index in the directory /tmp/nhspd-2021-02

```shell
clj -M -m com.eldrix.nhspd.core /tmp/nhspd-2021-02
```

