## Print service configuration

### Dodo configuration

The following properties define the characteristics of a Print Service in the
Dodo configuration file:

- `printService.name`    
the full qualified clearinghouse name of the print service that this Dodo
machine should provide. A single Dodo machine can only provide at most one
print service.    
If this and the `printService.outputDirectory` parameter are given, the
print service is started. The `machineId` of this Dodo machine must then match
the `machineId` field of the chs definition file for the print service  (and
similar for `networkNo`).    
_optional_ (no default)

- `printService.outputDirectory`    
the directory where the print service will save the ip master files delivered
by the printing client. The interpress master files are saved with extension `.ip`
and named with the request id.    
_optional_ (no default)

- `printService.paperSizes`    
the comma-separated list of known paper sizes (defined by the Printing courier program) that
this printing service should report in "get printer properties" request, valid paper values
are (others are ignored): usLetter, usLegal, a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10,
isoB0, isoB1, isoB2, isoB3, isoB4, isoB5, isoB6, isoB7, isoB8, isoB9, isoB10, jisB0, jisB1,
jisB2, jisB3, jisB4, jisB5, jisB6, jisB7, jisB8, jisB9, jisB10    
_optional_, _default_: `a4, usLetter, usLegal`

- `printService.disassembleIp`    
this boolean parameter controls if the print service will produce a human readable version
in `printService.outputDirectory` with the extension `.interpress`    
_optional_, _default_: `false`

- `printService.ip2PsProcFilename`    
the location (path and name) of the file containing the IP-to-PS conversion resource
of GVWin 2.1.    
If this property is given and the specified file is found, the Dodo print service will
generate a PostScript file for the print job interpress master.     
_optional_ (no default)

- `printService.psPostprocessor`    
the location (path and name) of a script for post processing the PostScript file
generated for the print job (hence requiring that the property `printService.ip2PsProcFilename`
is correctly provided).      
This script can for example send the PostScript file to a printer or convert it to PDF.     
_optional_ (no default)

### PostScript generation

The PostScript generation relies on the IP-to-PS conversion PostScript resource
found in GlobalView 2.1 for Windows.

This file is not provided with the Dodo distribution, but has to be copied from the
local GVWin installation to the location specified in the `printService.ip2PsProcFilename`
property. This file can be found in a standard GVWin installation in directory `C:\GVWIN` and is
named `IPPROC.PS`.

### PostScript postprocessing

After conversion of the interpress master for a print job to PostScript, the specified
post-processing script is invoked with the following parameters:

- _postscript filename_    
this is the output filename of the PostScript generation, given as the (possibly relative)
directory specified by the `printService.outputDirectory` property and the print
job specific filename of the `.ps ` file

- _paper size_    
the known paper size as it was given by the printing client for the print job, defaulted
to `a4` if a non-standard size was specified

- _job name_    
the internal job identifying name

- _print object name_    
the name for the printout specified by the printing client, usually the name of the printed file

- _sender name_    
usually the name of the logged in user who printed the document

The script can do arbitrary processing with the print job file, there is no limitation to
printing or PDF conversion.

The `stdout` and `stderr` outputs of the script are merged and collected in a log file
that will be located in the outputDirectory and named after the postscript file with the
additional `.ps` extension.

### Setting up a Dodo print service

It is important to remember that an XNS print service is subject of configuration on
2 related items:

- the Dodo machine providing the print service proper
- the Clearinghouse service informing XNS clients of the existence and location
of the print service.

The relation between the 2 configuration items is the machine id: the Clearinghouse
entry for the print service (the `p~name.properties` file of the CHS database)
must have the `machineId` property matching the `machineId` property of the
configuration for the Dodo machine providing the print service.

One single Dodo machine can provide at most one XNS print service. This can be the same
"central" machine where other XNS services (Clearinghouse, Time, ... services) are provided
or a separate "stand-alone" Dodo machine dedicated for the printing service.

In case of a "stand-alone" machine, the Dodo configuration should disable (not start)
all other services except for the print service.
   
### Sample configuration

In the distribution directory, a sample configuration for the print service _poseidon:dev:hawala_
in the "central" Dodo machine is provided, however the configuration parameters are commented out
in the Dodo configuration, so the print service is not started by default.

The `printservice.outputDirectory` is located in the subdirectory `poseidon`
where the `.ip`, `.interpress`, `.ps` files will be written.

This directory has the subdirectory `res` where the `IPPROC.PS` file should be copied to
and which already has a sample (Linux) script `postprocess-ps.sh` for converting the
generated Postscript file to PDF.

### Limitations

When generating PostScript for the interpress master, not all interpress constructs are
supported, the following constructs will prevent (abort) the generation:

- nested bodies / interpress masters
- compressed bitmap sequence types (CompressedPixelVector, AdaptivePixelVector, Ccitt4PixelVector)
- sequence types InsertMaster, Continued, LargeVector

Furthermore, the support for Xerox character sets is limited to a subset of western european
and some graphical characters.
