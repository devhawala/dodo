## Dodo Services - XNS with Java

Dodo Services is an attempt to implement Xerox Network Services (XNS) in Java. It is
currently much more work in progress than useful to provide services to existing emulated
(or real) Xerox client environments like XDE or GlobalView. The work is focused up to now
on building up the necessary infrastructure to provide XNS services (see section
**Functionality**).    
Currently the following XNS services are provided by Dodo:
- Time
- Routing Information
- Clearinghouse
- Authentication
- Printing (minimal)

In fact, it is not a single Java program, but a set of programs creating a
simple virtual network infrastructure, a kind of XNS-over-TCP/IP. This virtual
network allows different programs to inter-connect, among them the Dodo server proper,
a gateway to a (real) network device and of course the Xerox client machines wanting
to access the server component (see section **Topology**).

As for the maybe exotic name: although Dodo is not based on the Mesa-architecture but
an almost pure Java implementation, the name had to start with the letter *D* to be in
line with the Xerox tradition; on the other hand, it seemed appropriate to be something
that no longer exists or is not noticeably present in the world; so the extinct species
of a flight incapable bird came in mind: the [Dodo](https://en.wikipedia.org/wiki/Dodo).


### Functionality

The Dodo system implements parts of the Xerox Network Services protocol stack in Java,
providing the functionality to build things like XNS file or print services, at least
some day in future.

In the XNS levels terminology, the Dodo system supports resp. implements the
following protocols:

- Level 0    
Ethernet over TCP/IP as transmission media: raw Ethernet packets are relayed by
the *NetHub* program between machines connected to the NetHub; although any ethernet
packets are transported, only packets with ethertype 0x0600 (XNS IDP) are processed
by the Dodo server and thus relevant here (hence narrowed to *XNS-over-TCP/IP*).

- Level 1    
IDP (Internet Datagram Protocol) packet structure, transporting the basic XNS addressing
information

- Level 2    
    - PEX (Packet EXchange)    
    API for both responder (server-side) and requestor (client-side)
    - SPP (Sequenced Packet Protocol)    
    API both server-sockets and client-connections, including the connection close
	protocol (based on sub-sequence types 254 and 255)
    - Echo    
    responder for echo protocol packets
    - Error    
    incoming error packets are dispatched to the local component reported by the sender as
    having produced the problem

- Level 3
    - Time of Day protocol    
    responder for time request broadcasts
    -  Routing Information protocol    
    broadcaster for routing information as well as responder to
    client requests
    - Courier    
    infrastructure for implementing both Courier server programs and
    Courier clients;    
    however no Courier compiler is currently available, so mapping
    the Courier program definitions (.cr files) to the corresponding
    Java constructs must be done manually. 

- Level 4
    - Broadcast for Servers (BfS)
	responder for Clearinghouse BfS and Authentication BfS requests
    - Clearinghouse (CHS)
	(Courier program 2, versions 2 and 3)    
	all protocol procedures are implemented for a *read-only* clearinghouse
	database, allowing to log in to XDE and GlobalView, to navigate the network
	directory in GlobalView as well to query clearinghouse entries with `Maintain.bcd`
	in XDE; however changes to the	database are rejected
    - Authentication (Auth)
	(Courier program 14, version 2)    
	all protocol procedures are implemented for a *read-only* clearinghouse
	database, allowing to log in to XDE and GVWin; however changes to the
	database are rejected
	- Printing
	(Courier program 4, version 3)    
	all protocol procedures are implemented, allowing to print from XDE and GVWin,
	to query a print job status, the printer properties and the printer status.    
	However interpress files are only received and collected in the output directory,
	but no real printing occurs (the best that can be done is producing a readable
	version of the binary IP file).    
	In summary the print service gives XDE and GVWin the illusion of having
	printed.

The network configuration of a Dodo server instance and the services provided
can be configured through a property file specified when starting the Dodo program.
	
### Topology

Besides the XNS client systems, at least 2 components need to run as independent programs
for using Dodo services:

- NetHub    
this is the backbone of the virtual network, to which all other components connect
with a TCP connection. The NetHub forwards each packet received from one attached
component to the other connected components.    
In a sense, NetHub is the equivalent to the thick yellow coaxial ethernet cable in the 80's
to which each Xerox workstation was connected through a transceiver tapped on the cable.    
NetHub uses a quite simple protocol for communication with connected client components:
the raw ethernet packet to be transmitted is prefixed with a 2 byte big-endian
length information. This protocol is simple enough to be easily implemented in
emulators.    
NetHub listens on port 3333 for client connections (currently hard-coded).

- Dodo server    
this is the program providing the XNS services, currently only a few and some
future day hopefully more services.

2 additional program components of Dodo services can be used if required:
- NetHubGateway    
this is a NetHub client program that connects to a local PCap network device, implementing
a gateway for XNS packets between a (more or less) real network and NetHub, allowing
to attach emulators or potentially real machines that do not directly implement the
NetHub protocol.

- NetSpy    
this is a "read-only program" connecting to the NetHub with the only purpose to
receive all packets traveling in the virtual network and to dump the packet
content to `stdout`; in addition to the raw packet content, it issues the recognized
packet type specific structural data at the different layers (ethernet, IDP, PEX,
SPP etc. headers, type specific payload). 

Additional unsupported examples programs show the usage of the Dodo XNS-API for client
and server applications, see [example-programs](./example-programs.md).

### Invocation and usage

#### Prerequisites

The following prerequisites must be installed and possibly configured to
run Dodo services:
- for NetHub, Dodo server, NetSpy:
    - Java 8 JRE or newer
- for NetHubGateway:    
additionally requires the following platform specific (Windows/Linux/...,
32bit/64bit) libraries:
    - PCap (native packet capture library/driver),
    e.g. [WinPCap](https://www.winpcap.org/) for Windows resp.
    [libpcap](https://www.tcpdump.org/))
    - [jNetPcap](https://sourceforge.net/projects/jnetpcap/)
    Java PCap wrapper library

The current binaries for the Dodo services can be found in the file `dist.zip`,
which can be unpacked to the directory `dist`. This directory contains the jar-file
with the Dodo programs (`dodoserver-and-nethub.jar`) with the programs 
described in the following sections, as well as sample `.cmd` files for the
Windows platform, expecting to be invoked in the `dist` directory.    
For using the NetHubGateway, the `jnetpcap.jar` should be copied there, unless
the script `run-nethubgateway.cmd` is adapted to match the location of this
jar-file. The matching native libray for jNetPCap must be on the `PATH` for
being found at runtime.

#### NetHub

The `main`-class for NetHub backbone for the virtual network is:

	dev.hawala.hub.NetHub

and is started with:

`java -cp dodoserver-and-nethub.jar dev.hawala.hub.NetHub`

The sample Windows batch file is: `run-nethub.cmd`.

This program has not command line parameters and listens on port 3333 for
connections from XNS client systems. 


#### Dodo server

The `main`-class for the Dodo server is:

	dev.hawala.xns.DodoServer

and is started with:

`java -cp dodoserver-and-nethub.jar dev.hawala.xns.DodoServer`

The sample Windows batch file is: `run-dodoserver.cmd`

This program takes as optional parameter the name of a `.properties` file
for configuration of the Dodo server. If no file is given, the program looks
for a file `dodo.properties` in the current directory and uses this file
if found.    
A sample configuration file is available in the directory `dist`.

The following parameters can be specified in the configuration file:

- `networkNo`    
the (decimal) network number that Dodo server belongs to and which is provided in
time and BfS service responses     
_optional_, _default_: 1025

- `machineId`    
the processor or machine id for the Dodo machine (or MAC address in todays wording)  
(it should be ensured that **all** machines on the network have an unique processor id,
or Pilot-based machines will stop by entering 0915 state)    
_optional_, _default_: `10-00-FF-12-34-01`

- `useChecksums`    
specifies if checksums are to be verified resp. generated at IDP level    
_optional_, _default_: `true`

- `netHubHost`    
the name of the NetHub host to connect to    
_optional_, _default_: `localhost`

- `netHubPort`    
the port where the NetHub is listening (must be in range 1..65535)    
_optional_, _default_: `3333`

- `startEchoService`    
do start Dodo's Echo service?    
_optional_, _default_: `true`

- `startTimeService`    
do start Dodo's Time service?    
_optional_, _default_: `true`

- `startRipService`    
do start Dodo's Routing Information Protocol service?    
_optional_, _default_: `true`

- `startChsAndAuthServices`    
do start Dodo's Clearinghouse service and Authentication service? (both can only
be started (or not) together, as they serve the same domain jointly)    
_optional_, _default_: `true`

- `localTimeOffsetMinutes`    
time zone parameter for the time service as
difference between local time and GMT in minutes, with positive values being
to the east and negative to the west (e.g. Germany is 60 without DST and 120
with DST, whereas Alaska should be -560 without DST resp. -480 with DST)    
_optional_, _default_: 0 (i.e. GMT)

- `daysBackInTime`    
number of days to subtract from the current date to get the final timestamp
in the time service    
_optional_, _default_: `0` (i.e. no date change)

- `organization`    
the name of the organization to be handled (served) by the clearinghouse and
authentication services    
_optional_, _default_: `hawala`

- `domain`    
the name of the domain to be handled (served) by the clearinghouse and
authentication services    
_optional_, _default_: `dev`

- `chsDatabaseRoot`    
the name of the directory where the property files defining the objects known
in the clearinghouse database for `domain:organization` are located;
the format of the property files for the different object types is defined
in [clearinghouse configuration](./chs-config-files.md);    
if no value is specified (or it is invalid), then no users or services are
defined, but all user names are accepted, with the password being the user name
(case-insensitive) given for login    
_optional_ (no default)

- `strongKeysAsSpecified`    
how to handle the contradiction in the specification _Authentication Protocol_
(XSIS 098404, April 1984), where the data used in the example does not match the
specification for the strong key generation (section 5.3):    
if `true` encode each 4 char-block with the password to produce the next password
(as specified, but this does match <i>not</i> the data in the example),    
else (if `false`) swap the encryption parameters, i.e. use each 4 char-block to
encrypt the password of the last iteration to produce the new password (this
contradicts the specification, but creates the data in the example...)    
_optional_, _default_: `true`    
Remark: If `startChsAndAuthServices` is `true`, then specifying the optional command
line parameter `-dumpchs` will dump the content of the Clearinghouse database loaded
from the configuration files.

- `printService.name`    
the full qualified clearinghouse name of the print service that this Dodo
machine should provide. A Dodo machine can only provice at most one
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
 

#### NetHubGateway

The `main` class for the NetHubGateway is:

	dev.hawala.hub.NetHubGateway
	
and is started with:

`java -cp dodoserver-and-nethub.jar;jnetpcap.jar dev.hawala.hub.NetHubGateway` [*options*]

The sample Windows batch file is: `run-nethubgateway.cmd`

This program has the following optional parameters:

Parameter|Default|Description
---------|-------|-----------
`-ld`| | Log packet coming from the network device
`-ldd`| | Log packet coming from the network device with packet content
`-lh`| | Log packet coming from the NetHub
`-lhd`| | Log packet coming from the NetHub with packet content
(*1st arg*)| `localhost`| host where the NetHub runs, `=`selects the default
(*2nd arg*)| `3333` | port for the NetHub on the NetHub host, `=`selects the default
(*3rd arg*)| `MS LoopBack Driver` | PCap name of the network device to gateway, `=`selects the default
`-p`| | print the list of PCap device names on the local machine instead of running the gateway
`-h` | | print the command line help and stop 

#### NetSpy

The `main`class for NetSpy is:

	dev.hawala.hub.NetSpy
	
and is started with:

`java -cp dodoserver-and-nethub.jar dev.hawala.hub.NetSpy` 	

The sample Windows batch file is: `run-netspy.cmd`

This program has no command line parameters and connects to port 3333 on `localhost`.


### Environments known to work

Dodo was successfully tested in several environments with different emulators, with
"successful" meaning that it was at least possible to log on to the Xerox operating system
and optionally to verify that Clearinghouse entries can be searched resp. queried,
e.g. under XDE using the `Maintain.bcd` program or by navigating the network
directory under GlobalView (see the examples section in the
[clearinghouse configuration](./chs-config-files.md) document.

The following environments were tested with Dodo server and nethub, using the
default Clearinghouse database found in `dist.zip`:

- Dwarf emulator on Windows-Vista (32 bit) and Linux-Mint (64 bit) on a Core2Duo 2.4 GHz    
both XDE and GlobalView work; all components (Dwarf, Dodo server and nethub)
run on the same (hardware) machine

- Guam-Emulator with an experimental NetworkAgent on a Core2Duo 2.4 GHz with Linux Mint    
the emulator's NetworkAgent was modified for directly connecting to a local Nethub;
connection to Dodo was tested with XDE

- Don Woodward's Dawn-emulator with Dawn's XNS driver and XDE disk    
The following configuration was used:
    - real hardware: Laptop with 2.4 GHz Core2Duo processor
	- ... running Windows-Vista 32 bit as host OS
    - Windows 2000 installed in a MS VirtualPC virtual machine
	- ... with the Dawn XNS protocol driver installed
	- ... running the Dawn emulator with the Dawn XDE disk
	- ... the virtual machine connected to the network through the MS Loopback adapter
	- NetHub with NetHubGateway and Dodo server running on the host OS

- BSD 4.3    
Starting with this vintage OS version, BSD had support for XNS protocols for a while, until
the XNS support was removed due to missing maintainers. Besides the kernel support for XNS
protocols, a set of programs to access XNS services (also to provide some services) is
available, which had to be built manually.    
The test environment was a network-capable SIMH emulation for VAX running the
[Uwisc-4.3BSD](https://sourceforge.net/projects/bsd42/files/4BSD%20under%20Windws/v0.3%20Beta%201/)
system (a meanwhile outdated beta version).   
BSD 4.3 has the program `xnsbfs` allowing to do the Broadcast for Servers request (either for
clearinghouse ot authentication servers) and then invoke the "list domains served" Courier
request.    
Other XNS programs in BSD 4.3 were not tested, as requiring "higher" XNS functionality not yet
provided by Dodo.


### Bibliography

- source code for XNS support in BSD 4.3    
the following are different versions of the XNS implementation for VAX BSD at Cornell University
in the 80's; besides important insights into XNS implementation details, these archives provide
Courier program declarations ('.cr' files) for many XNS services (but some important protocols are
still missing, like Mail protocols) 
    - [https://stuff.mit.edu/afs/athena/astaff/reference/4.3network/xns/](https://stuff.mit.edu/afs/athena/astaff/reference/4.3network/xns/)
    - [courier.4.3d.tar.Z](http://bitsavers.informatik.uni-stuttgart.de/bits/Xerox/XNS/courier.4.3d.tar.Z)

- several documents at [bitsavers](http://bitsavers.informatik.uni-stuttgart.de/pdf/xerox/), e.g.:
    - [Xerox_System_Network_Architecture_General_Information_Manual_Apr85](http://bitsavers.informatik.uni-stuttgart.de/pdf/xerox/xns/XNSG_068504_Xerox_System_Network_Architecture_General_Information_Manual_Apr85.pdf)
    - [Xerox_Office_System_Technology_Jan1984](http://bitsavers.informatik.uni-stuttgart.de/pdf/xerox/8010_dandelion/OSD-R8203A_Xerox_Office_System_Technology_Jan1984.pdf)
    - [Courier_The_Remote_Procedure_Call_Protocol_Dec1981](http://bitsavers.informatik.uni-stuttgart.de/pdf/xerox/xns/standards/XSIS_038112_Courier_The_Remote_Procedure_Call_Protocol_Dec1981.pdf)    
    (Dodo's Courier infrastructure was almost done when this document was uploaded to bitsavers, but it confirmed the implementation concepts derived from the BSD 4.3 source codes)
    - [Authentication_Protocol_Apr1984.pdf](http://bitsavers.informatik.uni-stuttgart.de/pdf/xerox/xns/standards/XSIS_098404_Authentication_Protocol_Apr1984.pdf)
    - [Clearinghouse_Entry_Formats_Apr1984.pdf](http://bitsavers.informatik.uni-stuttgart.de/pdf/xerox/xns/standards/XSIS_168404_Clearinghouse_Entry_Formats_Apr1984.pdf)
    - [Services_8.0_Programmers_Guide_Nov84.pdf](http://bitsavers.informatik.uni-stuttgart.de/pdf/xerox/xns_services/services_8.0/Services_8.0_Programmers_Guide_Nov84.pdf)

### Development history

- 2019-01-22    
added minimal xns print service

- 2019-01-17    
added 2 example programs demonstrating the usage of the XNS-API (see [example-programs](./example-programs.md))

- 2019-01-06    
routing information protocol: extended to broadcast routing data on a regular as well as
event driven base    
clearinghouse service: all courier procedures are now implemented, allowing to query all CHS entries,
however modifications are rejected as the CHS database is read-only    
authentication service: all courier procedures are now implemented, allowing to log in to XDE (simple
credentials) and to GlobalView (strong credentials), however modifications are rejected as the CHS database
is read-only    
bugfix: ethernet packets now have a minimal length of 60 bytes (smaller packets are silently
ignored by Pilot)    
bugfix: SPP now automatically re-sends packets if not acknowledged (even if output queue still
has space)    
bugfix: sending packets by a single SPP connection is throttled to prevent packet losses at
the recipients side (avoid packet resends)    
bugfix: a time shift defined with configuration parameter `daysBackInTime` is applied to
all timestamps issued and used by Dodo server (i.e. also when generating and checking expiration
timestamps for credentials)

- 2018-11-01    
introduced simple read-only clearinghouse database (set of property files, defining users, groups and services), currently only used for authentication    
switch login functionality to use the clearinghouse database    
experimental support for strong credentials creation    
made starting of Dodo services configurable, e.g. for using Dodo only as Time server    
bugfix: overflow when computing password hashes for simple credentials    
bugfix: serialization error for strings at end of courier messages ("authentication service not available")

- 2018-10-12    
added configuration file for Dodo program    
minor code unifications in Courier infrastructure    
(not yet used extensions to Courier authentication and clearinghouse definitions)

- 2018-09-10    
Initial commit to Github    
basic XNS infrastructure    
minimal XNS Clearinghouse/Authentication services to allow login in XDE with simple authentication

- development started about September 2016, but was interrupted in favour of the Dwarf emulator
from January 2017 to about February 2018    
(as all hobby developments it is intermittent and restricted to the free time left over by other important
things like work and private life)

### License

Dodo is released under the BSD license, see the file `License.txt`.

### Disclaimer

All product names, trademarks and registered trademarks mentioned herein and in the
source files for the Dodo programs are the property of their respective owners.

