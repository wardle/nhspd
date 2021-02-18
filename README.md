# NHSPD
Support for the UK NHS Postcode Directory, linking all UK postcodes to health data.

The NHSPD is published by the Office of National Statistics in the UK.

There are several issues which make automating the management of postcode data
problematic:

1. There is no easy way to automate the recognition and downloading of the 
   quarterly issues of the dataset.
2. The naming of the files within the archive changes, not only because they
include the date of the release in the filename, but there doesn't appear to be
   a standard for the filename structure.
3. The archive file contains duplicate files, with one large file and then
several smaller files each with parts of the main file presumably to make
   processing easier. Paradoxically, this simply increases bandwidth use and
   makes it more complicated to process. 
   
Currently, this library provides a way around these issues. 

Firstly, it pretends it knows about the releases and the URLs from which the
archive files can be fetched.

Secondly, it uses some heuristics to try to identify the right data file from
the archive.

Thirdly, it ignores the duplicate files.

In an ideal world, government agencies would recognise the value of 
open data and metadata and ensure information is machine-readable and so
permit automation for users of their services.

