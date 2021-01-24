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

### Font and character set handling for PostScript generation

Mapping Xerox text encoding and fonts to PostScript brings some problems regarding printing the
correct characters and using the correct or similar enough PostScript fonts.

The Xerox Star and successor office systems use a 16 bit encoding scheme for characters and text,
allowing for mix texts in diverse human (and technical) languages in the same document (the 
_Xerox Character Encoding Standard_ can be seen as an ancestor of today's Unicode standard). The 16 bit
character space is subdivided in up to 255 _character sets_ with up to 256 characters each (with
more or less large parts of these ranges unused or unassigned). The character sets group characters
by function or language, e.g. set 0 has the latin alphabet and punctuation characters, set 38 has
the greek characters and set 241 has the accented characters for most european languages.

PostScript represents texts as 8 bit characters. A PostScript font is a collection of image renderings
and a map identifying the image to be produced if a characters code is to be issued with this font (_very_
simplified!). This map can be modified when creating a font instance in a PostScript file, so a special
font variant can be created to render a custom 8 bit character encoding (of course provided that all
necessary image renderings are present in the original PostScript font).

For bringing these 2 worlds together, there are at least 2 strategies:

- provide one PostScript font for each Xerox character set to be used in a Xerox fontt, resulting in a
  set of PostScript fonts for each Xerox font used.    
  Printing an arbitrary Xerox 16 bit encoded text involves selecting the correct PostScript font for each
  single 16 bit character by extracting the character set, which identifies the specific PostScript font for this
  8 bit character set, and then issuing the 8 bit character code for rendering it with this font; consecutive
  characters in the same character set should of course be grouped and issued as one single PostScript text
  for reducing the number of font changes in PostScript.
  
- use the "standard" (in the meaning of existing) PostScript fonts, select the most used or useful
  character images in these fonts to define customized PostScript fonts. So each Xerox font is
  substituted by one PostScript font which can render the defined 8 bit subset of the 16 bit Xerox
  characters.    
  Printing an arbitrary Xerox 16 bit encoded text involves mapping each 16 bit character in the text
  to the corresponding 8 bit code used for the custom PostScript font and issuing the transformed text
  with the PostScript font. For all 16 bit characters that do not have an assigned 8 bit code for the
  PostScript fonts, a substitution character should be issued (e.g. a question mark or a bullet) to
  at least signal the lost correct rendering at this text position.

The Interpress-to-PostScript conversion delivered with GlobalView 2.1 for Windows (the file `IPPROC.PS`)
uses the second strategy, using a set of PostScript fonts expected to be present on the target printer
to substitute the standard Xerox fonts used in GlobalView or predecessors.    
Dodo supports this by mapping the 16 bit characters to the custom character set defined in `IPPROC.PS`.
This allows a subset of western european and some graphical characters to be printed. The quality of
the final rendering depends on the PostScript fonts selected in `IPPROC.PS` for substitution as well as
their availability on the target system (e.g. GhostScript for producing PDF vs. a real PostScript printer),
so deviations from the expected result are probable due to different character images or character widths
compared to the original Xerox fonts.

GlobalView 2.1 also provides a set of PostScript fonts (PFB files) for the Xerox "Classic" and "Modern" fonts,
having specific PostScript fonts for a dozen Xerox character sets (roughly for the european languages). But
(strangely enough) these fonts are not used by `IPPROC.PS`.    
Dodo can take advantage of these fonts if they are copied to a subdirectory named `pfb` besides (i.e. in the same
directory as) `IPPROC.PS`. If the font files are present (Dodo only checks for 3 PostScript font files),
Dodo switches to the first strategy (at least tries to) if the Xerox "Classic" or "Modern" fonts is used for
a text: Dodo will then patch the `IPPROC.PS` when included in the generated PS file to use the Xerox 
PFB font files for the "Classic" and "Modern" fonts instead of the default PostScript substitutions and
it will use the character set specific fonts instead of using the character mapping of the 2nd strategy.    
Currently this works only with the post-processing scripts for Windows and Unixoids delivered with Dodo in
the sample environment: these scripts will register the `pfb` directory with the Xerox PostScript fonts when
invoking GhostScript for creating a PDF file.    
When developing own post-processing scripts targeting GhostScript, the sample scripts can be taken as templates
for using the font files in the `pfb` directory.

(Dodo also supports rendering of _Equations_ in VP/GV documents, however the necessary font "Classic-Thin-Bold"
is not available in the Xerox PFB files and is substituted with the normal "Classic" font; printed formulas created
with _Equations_ look similar to the expected rendering, but noticeable deviations like misplacement or wrong
size for formula signs are probable)

Remark:    
Even if the Xerox PFB font files are present in the `pfb` directory, it is still possible to deactivate
them (i.e. using the 2nd strategy only, as intended in `IPPROC.PS`) without removing the files (e.g. renaming
the `pfb` directory) and restarting the Dodo server process. This can be done by setting the _Message_ property
for the printjob to the text `!no-xerox-fonts!` when printing a document, either per printjob by setting the
_Message_ in the property sheet which appears when the document is copied onto the printer icon or in general for
a printer icon by setting the _Message_ value in the icon's property sheet.

### PostScript postprocessing

After conversion of the interpress master for a print job to PostScript, the post-processing script
specified with the parameter `printService.psPostprocessor` is invoked with the following parameters:

- _postscript filename_    
this is the output filename of the PostScript generation, given as the (possibly relative)
directory specified by the `printService.outputDirectory` property and the print
job specific filename of the `.ps` file

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

It is important to remember that a Dodo XNS print service is subject of configuration on
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

The `printservice.outputDirectory` is located in the subdirectory `prt-poseidon`
where the `.ip`, `.interpress`, `.ps` files will be written.

This directory has the subdirectory `res` where the `IPPROC.PS` file should be copied to
and which already has the sample scripts `postprocess-ps.cmd` (Windows) and `postprocess-ps.sh`
(Unixoids) for converting the generated Postscript file to PDF. These scripts use GhostScript
for this and require the directories `bin` and `lib` of the GhostScript installation to be
in the `PATH` environment variable.

If the PFB font files coming with GlobalView 2.1 (usually in `C:\PSFONTS`) are copied to the
subdirectory `pfb` (in `res`), the above sample scripts will automatically add the `pfb`
directory to the FONTPATH for GhostScript, so texts in the "Classic" and "Modern" fonts in
the created PDF should look much more like in the original printouts compared to the
default PostScript substitution fonts when using `IPPROC.PS` alone.

### Limitations

When generating PostScript for the interpress master, not all interpress constructs are
supported, the following constructs will prevent (abort) the generation:

- nested bodies / interpress masters
- compressed bitmap sequence types (CompressedPixelVector, AdaptivePixelVector, Ccitt4PixelVector)
- sequence types InsertMaster, Continued, LargeVector

Furthermore, the support for Xerox character sets is limited to a subset of western european
and some graphical characters for fonts other the "Classic" and "Modern" or if the PostScript PFB font
files provided with GlobalView 2.1 are not used.    
The PFB font files for "Classic" and "Modern" Xerox fonts support 11 Xerox character sets, so
the range of printable human texts is also limited compared to the capabilities of Star and
successors.