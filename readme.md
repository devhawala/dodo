## Dodo Services - XNS with Java

Dodo Services is an attempt to implement Xerox Network Services (XNS) in Java. It is
currently much more work in progress than useful to provide services to existing emulated
(or real) Xerox client environments like XDE or GlobalView. The work is focused up to now
on building up the necessary infrastructure to provide XNS services (see section
**Functionality**).

In fact, it is not a single Java program, but a set of programs creating a
simple virtual network infrastructure, a kind of XNS-over-TCP/IP. This virtual
network allows different programs to inter-connect, among them the Dodo server proper,
a gateway to a (real) network device and of course the Xerox client machines wanting
to access the server component (see section **Topology**).

However support for Xerox (and other) client environments is somewhat restricted (see
section **Environments known (not) to work**). Besides a large heap of missing XNS
functionality it seems that Pilot-based Xerox environments were not prepared to run
on fast "hardware" available as emulations on contemporary computers.

As for the maybe exotic name: although Dodo is not based on the Mesa-architecgure but
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
    both responder (server-side) and requestor (client-side)
    - SPP (Sequenced Packet Protocol)    
    both server-sockets and client-connections, including the connection close
	protocol (based on sub-sequence types 254 and 255)
    - Echo    
    responder for echo protocol packets
    - Error    
    incoming error packets are dispatched to the local component reported by the sender as
    having produced the problem

- Level 3
    - Time of Day protocol   
    responder for time request broadcasts
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
	currently a small procedure subset is implemented, mainly to allow users
	to login to XDE (disk from the Dawn emulator)
    - Authentication (Auth)
	(Courier program 14, version 2) 
	currently a small procedure subset is implemented, mainly to allow users
	to login to XDE

In the current implementation, the configuration of the provided functionality
is very simplified in the sense that all parameters are hard-coded:
- xerox network number: 0x0401 = 1025
- server hardware-id: 10-00-FF-12-34-01
- time service: GMT derived from local time, CET time zone without daylight savings
- clearinghouse:    
no clearinghouse database, so no users or services are defined; however the
server responds to the Courier "list domains served" request with:
    - organization: home.o
	- domain: dev.d
- authentication:    
all user names are accepted, the password is the user name (case-insensitive);
only simple authentication (16 bit hashed password) is supported, strong authentication
is denied by claiming that the user's strong password is not available.
	
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
packet type specific structural data at the differend layers (ethernet, IDP, PEX,
SPP etc. headers, type specific payload). 


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

This program has no command line parameters and connects to port 3333 on `localhost`.

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


### Environments known (not) to work

First the environments that do work with Dodo:

- Don Woodward's Dawn-emulator with Dawn's XNS driver and XDE disk    
with a very special (i.e. slow) setup, it is possible to log in on this system (i.e.
the user name is displayed in the top left corner of the *Herald Window* without a
"wrong password" or the like message).    
The following configuration was required for this to work:
    - real hardware: Laptop with 2.4 GHz Core2Duo processor
	- ... running Windows-Vista 32 bit as host OS
    - Windows 2000 installed in a MS VirtualPC virtual machine
	- ... with the Dawn XNS protocol driver installed
	- ... running the Dawn emulator with the Dawn XDE disk
	- ... the virtual machine connected to the network through the MS Loopback adapter
	- NetHub with NetHubGateway and Dodo server running on the host OS

	With this configuration, XDE started to open SPP connections to the Courier port
	on the machine it received the address with the BfS requests, and issued Courier
	calls to the Authentication and Clearinghouse services.
	
    BUT: this setup results in an extremely slow XDE environment; keyboard and
    mouse actions are easily lost, screen operations are visible (e.g. the copyright
    banner takes 2-3 seconds to build up). XDE probably runs at half the speed compared
    to a Dandelion...  

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
request. This program works with Dodo, issuing "dev.d:home.o" as result (the only domain served
by Dodo).    
Other XNS programs in BSD 4.3 were not tested, as requiring "higher" XNS functionality than
provided by Dodo.

As one XDE instance is able to fully use the XNS protocols (meaning PEX *and* SPP) and to
invoke the few available Dodo Courier services, it was expected that other setups of the
same XDE environment would also be able to use Dodo.

This is sadly not the case: in any other - faster - setup, the *same* XDE environment
(always the Dawn XDE disk) does not connect to the Dodo Courier services, no matter which emulator
was used. More precisely:
- instead of actively opening SPP connections to the Courier port on the Dodo machine,
the Xerox OS continues to issue BfS broadcasts, apparently ignoring the answers received for these
requests
- the only packet received by the Xerox OS that has any effect is the *first* time request
response at boot time (maintenance panel code 0937); but any subsequent time request responses
are ignored. This was tested by subtracting one day to the timestamp returned on each query: the
time at the XDE machine was taken from the first response, although time requests are routinely
repeated during a boot session (and received their response by Dodo).

The key difference between the first broadcast (querying for network time during MP 0937) and any
other transmissions is that the first query is issued and the response is received by the germ
at boot time: as the germ runs without the Mesa processes machinery, it must poll (and
it does) for the status of the packet sent resp. the receive buffer. Any subsequent network I/O
is controlled by the Pilot OS kernel, using interrupt-driven I/O event handling.

As the same XDE environment does work in a slow and does not in a faster environment, a timing
dependency in the Pilot OS is the obvious candidate for the problem. However artificially slowing
down the Dwarf emulation did not help, either by delaying interrupts or packets or adding
cpu-spinlocks for slower instruction execution: even when the performance was worse than the
only working setup described above, there was no attempt to open an SPP connection when doing the
login in XDE. So maybe the obvious is still not the relevant reason...

In summary, the following variants were tried without XDE trying to open a Courier SSP connection:
- Dwarf-Emulator with an experimental NetworkAgent connecting to NetHub    
    - both with native speed and with various artifial slow down
    - on different fast (or slow) laptop hardware and OS environments    
    (Core2Duo 2.4 GHz with Windows-Vista or Linux Mint, Pentium-M 1,6 GHz with Windows-XP)
- Dawn-Emulator    
on a Pentium-M 1,6 GHz with Windows-XP and Dawn XNS protocol driver installed
- Guam-Emulator with an modified NetworkAgent connecting to a Nethub    
on a Core2Duo 2.4 GHz with Linux Mint

Further investigation is necessary to find out why the same software (meaning the Pilot-based XDE OS)
behaves differently if booted in differently fast environments. But chances are low to find the real
root cause for this behaviour (it's not even sure that whichever "speed" is the reason) and get the
problem fixed, as the internals of the Pilot OS are effectively hidden and obfuscated (no sources,
no Mesa development environment etc.).

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

### Development history

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
source files for the Dwarf program are the property of their respective owners.

