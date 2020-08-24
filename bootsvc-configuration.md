## Boot service configuration

Setting up the Dodo boot service is very similar to setting up an original
Xerox Boot service as described in the document
[Boot_Service_10.0_1986.pdf](http://bitsavers.informatik.uni-stuttgart.de/pdf/xerox/xns_services/services_10.0/Network_Shared_Services_10.0/610E02850_Boot_Service_10.0_1986.pdf).
This document also describes the network boot and installation procedures only
summarized here.

The setup involves:
- copying the network boot files to deliver,
- setting up an installation drawer for allowing network-based workstation installations,
- configuring Dodo for running the service.

### Boot service files

The microcode, germ and boot files to be delivered by the boot service must be
contained in a boot service directory, along with the configuration file
`BootService.profile` for mapping the numeric boot object identifications
to a bootfile to be delivered. The boot service directory is a directory in the
local file system and is specified in the Dodo configuration file.

When doing a network boot, the initial microcode of a workstations sends a numeric
id specific for the file type (microcode, germ, boot) and specific for the machine
type to the boot service. The boot service chooses
the file based on the id mapping. If the id is not found in the mapping file (or the
mapping file is missing) or the file is not found in the boot service directory, no
file can be delivered and network booting the machine requesting the file fails.

A number of network boot installation file sets can be found at
[Bitsavers](http://bitsavers.org/bits/Xerox/Services/) as floppy disk archives.
Each floppy immage set have all files to be copied to the boot service directory,
including a matching `BootService.profile`.

### File service configuration

Having a boot service allows to perform network based installation of workstations
by booting a network installer, which can then install the relevant files taken from
a file service.

When the network installer is booted, this installer loads the installation scripts for
the menu driven workstation installation also from the network. For this, it opens
the file drawer `Installation Drawer` on the file service with the alias
`Installation Server` and retrieves the installation scripts found in this
drawer. These scripts fetch files for installation on the workstation from the same
file service drawer.

After choosing the Dodo file service that will provide the installation scripts and
files, the following configuration steps are necessary:

- Dodo clearinghouse database    
add the alias `Installation Drawer` to the file service (i.e. extend or add
the property `aliases` with this name to the `fs~name.properties` file of
the file service).

- Dodo file service    
add a line for the file drawer `Installation Server` to the file `root-folder.lst`
of the file service to ensure that the file drawer will exist after the next restart
of the service.

The document [Boot_Service_10.0_1986.pdf](http://bitsavers.informatik.uni-stuttgart.de/pdf/xerox/xns_services/services_10.0/Network_Shared_Services_10.0/610E02850_Boot_Service_10.0_1986.pdf)
describes the files to be copied to the file drawer `Installation Drawer`,
in this case for setting up ViewPoint 2.0 or 1.1.2 workstations.

### Dodo configuration

The following properties in the Dodo configuration file define the boot service:

- `startBootService`    
do start Dodo's boot service?    
_optional_, _default_: `false`

- `bootService.baseDir`    
location in the local file system of the boot service directory containing the boot files to deliver    
_optional_, _default_: `bootsvc`

- `bootService.verbose`    
log the boot service activities?   
_optional_, _default_: `false`

- `bootService.simpleDataSendInterval`    
interval in milliseconds between 2 packets when using the simple protocol,
so this parameter controls the packet transmission rate for microcode and
germ files    
_optional_, _default_: `40` (meaning 25 packets/second)
