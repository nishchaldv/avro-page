---
title: "Trevni"
linkTitle: "Trevni"
weight: 5
date: 2021-10-25
aliases:
- spec.html
---

<!--

 Licensed to the Apache Software Foundation (ASF) under one
 or more contributor license agreements.  See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership.  The ASF licenses this file
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License.  You may obtain a copy of the License at

   https://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied.  See the License for the
 specific language governing permissions and limitations
 under the License.

-->

## Trevni: A Column File Format

Version 0.1

DRAFT

This document is the authoritative specification of a file format. Its intent is to permit compatible, independent implementations that read and/or write files in this format.

## Introduction
Data sets are often described as a table composed of rows and columns. Each record in the dataset is considered a row, with each field of the record occupying a different column. Writing records to a file one-by-one as they are created results in a row-major format, like Hadoop’s SequenceFile or Avro data files.

In many cases higher query performance may be achieved if the data is instead organized in a column-major format, where multiple values of a given column are stored adjacently. This document defines such a column-major file format for datasets.

To permit scalable, distributed query evaluation, datasets are partitioned into row groups, containing distinct collections of rows. Each row group is organized in column-major order, while row groups form a row-major partitioning of the entire dataset.

## Rationale
### Goals
The format is meant satisfy the following goals:

Maximize the size of row groups. Disc drives are used most efficiently when sequentially accessing data. Consider a drive that takes 10ms to seek and transfers at 100MB/second. If a 10-column dataset whose values are all the same size is split into 10MB row groups, then accessing a single column will require a sequence of seek+1MB reads, for a cost of 20ms/MB processed. If the same dataset is split into 100MB row groups then this drops to 11ms/MB processed. This effect is exaggerated for datasets with larger numbers of columns and with columns whose values are smaller than average. So we’d prefer row groups that are 100MB or greater.

Permit random access within a row group. Some queries will first examine one column, and, only when certain relatively rare criteria are met, examine other columns. Rather than iterating through selected columns of the row-group in parallel, one might iterate through one column and randomly access another. This is called support for WHERE clauses, after the SQL operator of that name.
Minimize the number of files per dataset. HDFS is a primary intended deployment platform for these files. The HDFS Namenode requires memory for each file in the filesystem, thus for a format to be HDFS-friendly it should strive to require the minimum number of distinct files.
Support co-location of columns within row-groups. Row groups are the unit of parallel operation on a column dataset. For efficient file i/o, the entirety of a row-group should ideally reside on the host that is evaluating the query in order to avoid network latencies and bottlenecks.
Data integrity. The format should permit applications to detect data corruption. Many file systems may prevent corruption, but files may be moved between filesystems and be subject to corruption at points in that process. It is best if the data in a file can be validated independently.
Extensibility. The format should permit applications to store additional annotations about a datasets in the files, such as type information, origin, etc. Some environments may have metadata stores for such information, but not all do, and files might be moved among systems with different metadata systems. The ability to keep such information within the file simplifies the coordination of such information.
Minimal overhead. The column format should not make datasets appreciably larger. Storage is a primary cost and a choice to use this format should not require additional storage.
Primary format. The column format should be usable as a primary format for datasets, not as an auxiliary, accelerated format. Applications that process a dataset in row-major order should be able to easily consume column files and applications that produce datasets in row-major order should be able to easily generate column files.

### Design
To meet these goals we propose the following design.

Each row group is a separate file. All values of a column in a file are written contiguously. This maximizes the row group size, optimizing performance when querying few and small columns.

Each file occupies a single HDFS block. A larger than normal block size may be specified, e.g., ~1GB instead of the typical ~100MB. This guarantees co-location and eliminates network use when query processing can be co-located with the file. This also moderates the memory impact on the HDFS Namenode since no small files are written.
Each column in a file is written as a sequence of ~64kB compressed blocks. The sequence is prefixed by a table describing all of the blocks in the column to permit random access within the column.
Application-specific metadata may be added at the file, column, and block levels.
Checksums are included with each block, providing data integrity.

### Discussion
The use of a single block per file achieves the same effect as the custom block placement policy described in the CIF paper, but while still permitting HDFS rebalancing and not increasing the number of files in the namespace.

## Format Specification
This section formally describes the proposed column file format.

### Data Model
We assume a simple data model, where a record is a set of named fields, and the value of each field is a sequence of untyped bytes. A type system may be layered on top of this, as specified in the Type Mapping section below.

### Primitive Values
We define the following primitive value types:

* Signed 64-bit long values are written using a variable-length zig-zag coding, where the high-order bit in each byte determines whether subsequent bytes are present. For example:
decimal value	hex bytes

 | decimal value | hex bytes | 
--- | --- | --- |  
 | 0 | 00 |  
 | -1 | 01 |   
 | 1 | 02 | 
 | ... |  |  
 | -64 | 7f | 
 | 64 | 80 01 | 
 | ... |  | 


* bytes are encoded as a long followed by that many bytes of data.
* a string is encoded as a long followed by that many bytes of UTF-8 encoded character data.

For example, the three-character string "foo" would be encoded as the long value 3 (encoded as hex 06) followed by the UTF-8 encoding of 'f', 'o', and 'o' (the hex bytes 66 6f 6f): 06 66 6f 6f

## Type Names
The following type names are used to describe column values:

* null, requires zero bytes. Sometimes used in array columns.
* boolean, one bit, packed into bytes, little-endian;
* int, like long, but restricted to 32-bit signed values
* long 64-bit signed values, represented as above
* fixed32 32-bit values stored as four bytes, little-endian.
* fixed64 64-bit values stored as eight bytes, little-endian.
* float 32-bit IEEE floating point value, little-endian
* double 64-bit IEEE floating point value, little-endian
* string as above
* bytes as above, may be used to encapsulate more complex objects

Type names are represented as strings (UTF-8 encoded, length-prefixed).

## Metadata
**Metadata** consists of:
* A long indicating the number of metadata key/value pairs.
* For each pair, a string key and bytes value.

All metadata properties that start with "trevni." are reserved.

### File Metadata
The following file metadata properties are defined:

* **trevni.codec** the name of the default compression codec used to compress blocks, as a string. Implementations are required to support the "null" codec. Optional. If absent, it is assumed to be "null". Codecs are described in more detail below.
* **trevni.checksum** the name of the checksum algorithm used in this file, as a string. Implementations are required to support the "crc-32” checksum. Optional. If absent, it is assumed to be "null". Checksums are described in more detail below.

### Column Metadata
The following column metadata properties are defined:

* trevni.codec the name of the compression codec used to compress the blocks of this column, as a string. Implementations are required to support the "null" codec. Optional. If absent, it is assumed to be "null". Codecs are described in more detail below.
* trevni.name the name of the column, as a string. Required.
* trevni.type the type of data in the column. One of the type names above. Required.
* trevni.values if present, indicates that the initial value of each block in this column will be stored in the block’s descriptor. Not permitted for array columns or columns that specify a parent.
* trevni.array if present, indicates that each row in this column contains a sequence of values of the named type rather than just a single value. An integer length precedes each sequence of values indicating the count of values in the sequence. If the length is negative then it indicates a sequence of zero or one lengths, where -1 indicates two zeros, -2 two ones, -3 three zeros, -4 three ones, etc.
* trevni.parent if present, the name of an array column whose lengths are also used by this column. Thus values of this column are sequences but no lengths are stored in this column.

For example, consider the following row, as JSON, where all values are primitive types, but one has multiple values.

```json
{"id"=566, "date"=23423234234
 "from"="foo@bar.com",
 "to"=["bar@baz.com", "bang@foo.com"],
 "content"="Hi!"}
 ```

The columns for this might be specified as:

```json
name=id       type=int
name=date     type=long
name=from     type=string
name=to       type=string  array=true
name=content  type=string 
```

If a row contains an array of records, e.g. "received" in the following:

```json
{"id"=566, "date"=23423234234
 "from"="foo@bar.com",
 "to"=["bar@baz.com", "bang@foo.com"],
 "content"="Hi!"
 "received"=[{"date"=234234234234, "host"="192.168.0.0.1"},
             {"date"=234234545645, "host"="192.168.0.0.2"}]
}
```

Then one can define a parent column followed by a column for each field in the record, adding the following columns:

```json
name=received  type=null    array=true
name=date      type=long    parent=received
name=host      type=string  parent=received
```

If an array value itself contains an array, e.g. the "sigs" below:

```json
{"id"=566, "date"=23423234234
 "from"="foo@bar.com",
 "to"=["bar@baz.com", "bang@foo.com"],
 "content"="Hi!"
 "received"=[{"date"=234234234234, "host"="192.168.0.0.1",
              "sigs"=[{"algo"="weak", "value"="0af345de"}]},
             {"date"=234234545645, "host"="192.168.0.0.2",
              "sigs"=[]}]
}
```

Then a parent column may be defined that itself has a parent column.

```json
name=sigs   type=null    array=true  parent=received
name=algo   type=string              parent=sigs
name=value  type=string              parent=sigs
```

### Block Metadata

No block metadata properties are currently defined.

## File Format

A **file** consists of:
* A file header, followed by
* one or more columns.

A **file header** consists of:

* Four bytes, ASCII 'T', 'r', 'v', followed by 0x02.
* a fixed64 indicating the number of rows in the file
* a fixed32 indicating the number of columns in the file
* file metadata.
* for each column, its column metadata
* for each column, its starting position in the file as a fixed64.

A **column** consists of:

* A fixed32 indicating the number of blocks in this column.
* For each block, a block descriptor
* One or more blocks.


A **block descriptor** consists of:

* A fixed32 indicating the number of rows in the block
* A fixed32 indicating the size in bytes of the block before the codec is applied (excluding checksum).
* A fixed32 indicating the size in bytes of the block after the codec is applied (excluding checksum).
* If this column’s metadata declares it to include values, the first value in the column, serialized according to this column's type.

A **block** consists of:

* The serialized column values. If a column is an array column then value sequences are preceded by their length, as an int. If a codec is specified, the values and lengths are compressed by that codec.
* The checksum, as determined by the file metadata.

## Codecs
### null
The "null" codec simply passes data through uncompressed.
### deflate
The "deflate" codec writes the data block using the deflate algorithm as specified in RFC 1951.
### snappy
The "snappy" codec uses Google's Snappy compression library.

## Checksum algorithms
### null
The "null" checksum contains zero bytes.
### crc-32
Each "crc-32" checksum contains the four bytes of an ISO 3309 CRC-32 checksum of the uncompressed block data as a fixed32.

## Type Mappings
We define a standard mapping for how types defined in various serialization systems are represented in a column file. Records from these systems are shredded into columns. When records are nested, a depth-first recursive walk can assign a separate column for each primitive value.

**Avro**

**Protocol Buffers**

**Thrift**

## Implementation Notes
Some possible techniques for writing column files include:

1. Use a standard ~100MB block, buffer in memory up to the block size, then flush the file directly to HDFS. A single reduce task might create multiple output files. The namenode requires memory proportional to the number of names and blocks*replication. This would increase the number of names but not blocks, so this should still be much better than a file per column.
2. Spill each column to a separate local, temporary file then, when the file is closed, append these files, writing a single file to HDFS whose block size is set to be that of the entire file. This would be a bit slower than and may have trouble when the local disk is full, but it would better use HDFS namespace and further reduce seeks when processing columns whose values are small.
3. Use a separate mapreduce job to convert row-major files to column-major. The map output would output a by (row#, column#, value) tuple, partitioned by row# but sorted by column# then row#. The reducer could directly write the column file. But the column file format would need to be changed to write counts, descriptors, etc. at the end of files rather than at the front.

(1) is the simplest to implement and most implementations should start with it.

## References
CIF [Column-Oriented Storage Techniques for MapReduce](https://arxiv.org/pdf/1105.4252.pdf), Floratou, Patel, Shekita, & Tata, VLDB 2011.

DREMEL Dremel: [Interactive Analysis of Web-Scale Datasets](https://static.googleusercontent.com/media/research.google.com/en//pubs/archive/36632.pdf), Melnik, Gubarev, Long, Romer, Shivakumar, & Tolton, VLDB 2010.
