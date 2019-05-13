## XNS File services configuration

Using Dodo XNS file services requires configuration at 3 levels:

- in the Clearinghouse    
where file services are defined by associating a network address to a file service name

- for the Dodo server instance(s)
by defining which file services are provided by a server instance and where the filing volume
for each file service is located (in the file system of the hosting computer); as it is intended
in XNS, a single server can provide several file services

- in each filing volume
for defining which root folders (*File drawers* in Star/ViewPoint/GlobalView jargon) are to be
created if not present

The redundant configuration of a file service both in the clearinghouse and the service instance is
due to the fact that Dodo's clearinghouse database is read-only.    
It must be manually ensured that both configurations are consistent, i.e. that the addresses given
in the clearinghouse match the server address and the file services configured for a Dodo server
conversely correspond to the names defined in the clearinghouse.

### Clearinghouse configuration

A file service is defined in Dodo's Clearinghouse configuration with a properties file with the
name pattern f~*name*.properties (see [Clearinghouse configuration](./chs-config-files.md)).

The important configuration value in the properties file is the `machineId` given for the file service.
This value must match the `machineId` parameter in the configuration file for the Dodo server where
this file service will be provided.

### Dodo configuration

A Dodo server instance can provide one or more file services, each defined by a pair of properties
in the configuration file for the Dodo server:

- `fileService.NN.name`    
this entry identifies the name of a file service defined in the Clearinghouse that this Dodo server
instance will provide; the name must be given as CHS three-part-name.

- `fileService.NN.volumePath`    
this entry specifies the location (in the local OS where Dodo runs) for the volume directory to
be used for the file service identified by the matching `fileService.NN.name` entry.

The `NN` part in the above property names denotes an integer counting the configuration entries for
file services. This numbering must start with the value 0 and incremented by 1 for each additional
file service. Scanning for file service entries will stop at the first gap in this number sequence.

### Example configuration

Assuming that a Dodo XNS network should have a file service called "fs1" in domain "dev" in organisation
"hawala", the following file named `f~fs1.properties` in the clearinghouse configuration directory
defines this file service:

	# description of the service
	description = First file service
	
	# network address of the service
	machineId = 10-00-BB-10-11-0F
	
	# authentication level
	authLevel = both

The relevant entries in the Dodo server configuration are as follows:

	# network number
	networkNo = 1050
	
	# machine id
	machineId = 10-00-BB-10-11-0F
	
	# clearinghouse database
	chsDatabaseRoot = ./chs-database
	organizationName = hawala
	domainName = dev
	
	# file service(s)
	fileService.0.name = fs1:dev:hawala
	fileService.0.volumePath = ./fs1

Incidentally the above describes the configuration for the Clearinghouse and file service
found in the `dist.zip` distribution for Dodo.

### Volumes for File Services

Dodo manages all data for items (metadata and file contents) of a single  XNS File service
below a directory in the local OS file system (where Dodo runs). This OS directory is called
"the volume" and is given in the `fileService.NN.volumePath` configuration entry for
the file service.

When a volume is first used, the volume's OS directory is *initialized* by filling it
with the required subdirectories and files. An important part of the initialization
is setting up the root of the XNS File service by creating the the root folders of
type *file drawer*. The drawer "Desktops" is always created, this drawer will store
the desktops of GV user having configured this file service as (home) file service.

Additional drawers to be created are defined in a file named `root-folder.lst`
in the volume directory (this should be the only file for a not yet initialized
volume directory). This file is loaded each time the Dodo server is started and opens
the volume: Dodo ensures that all folders defined in `root-folder.lst` will be
present in the XNS File service volume. So this file can also be used to add additional
root drawers after the volume has been initialized, e.g. when adding new users in the
clearinghouse which will need private drawers in the XNS File service. (there are currently
no means to *remove* file drawers)

As the root of a file service volume is always read/only, the file `root-folder.lst`
is the only means for changing the top-level of the folder hierarchy of a (Dodo) file service. 

The file `root-folder.lst` must have one line for each file drawer with the
following fields separated by (real) tabs:

- name of the file drawer
- name of the owning user for the drawer (3-part CHS name)    
this user will also be added to the accessList and defaultAccessList attributes
with full access permissions
- tab-separated list of users to be added to the accessList attribute of the drawer    
each entry must have the format: `accesscode!3-part-chs-username`    
the *accesscode* is a single character identifying the permissions for the user
*3-part-chs-username*, one of `O` for full access (owner), `R` for read access
or `W` for write access. The *3-part-chs-username* can specify a single user or
a user-group defined in the clearinghouse, or a pattern for an existing domain and organization.

Example:    
the following content for the `root-folders.lst` will initialize the volume
with 2 file drawers, both owned by user `admin:dev:hawala`, granting write access
to all users in the `:dev:hawala` domain:

	hans    admin:dev:hawala
	test    admin:dev:hawala    w!*:dev:hawala

### Volume backup and restore

As the content of a XNS File service volume is completely contained inside a single
OS directory, doing a backup of a volume simply means to archive the complete base-directory
of the volume with the favorite archive and compress tools.

Restoring a volume is obviously the simple unarchiving operation with these tools. However,
it must be ensured that restoring is **not** made into an existing volume, but creates the
volume base-directory from scratch. As Dodo's metadata persistence machinery creates intermediate
files that are interpreted and discarded when opening the volume, still having such files from
Dodo's last run in a freshly restored volume will very probably corrupt the restored volume.

So always delete (or rename away) an old version of the volume base-directory before restoring
a volume backup.

### Capabilities and restrictions

#### XDE functionality supported

All usual interactions with a file service using the FileTool are supported
and work as expected for single files and sets of files:
- Store!
- Retrieve!
- Remote-List!
- Remote-Delete! 

#### GVWin functionality supported

Copying and moving files and folders between a file service and the local
desktop works as expected, as well as opening the properties window
for items on the file service.

Logging off with "move desktop to file service" and later logging on again
with the restored desktop also works.

Currently not supported are copy or moves between folder windows for the same
file service (see below: filing courier protocol procedures not implemented)

#### Unimplemented functionality

- controls (see section 3.3 of Filing Protocol)    
this means that there is no coordination among parallel sessions (of different or the same
user) accessing the same file or directory

- interpretation of access lists    
this means that all users can access all files and directories in a file service; although
the file attributes `accessList` and `defaultAccessList` are stored in the metadata,
they are currently not interpreted and checked against the session's user identity

- filing courier protocol procedures not implemented:
    - procedure 6 : GetControls()
    - procedure 7 : ChangeControls()
    - procedure 10: Copy()
    - procedure 11: Move()
    - procedure 14: Replace()
    - procedure 17: Find()
    - procedure 20: UnifyAccessLists()
    - procedure 23: ReplaceBytes()
    - procedure 22: RetrieveBytes()

#### Incomplete implementation

- procedure 18 List(): matching for the name attribute will probably produce mismatches (false or missing hits)
if the pattern or the file name contain non-ascii characters

- procedure 18 List(): matching for the pathname attribute is unimplemented and will never match

- procedure 18 List(): enumeration in backward direction is unsupported and rejected with an error

#### Known problems

(currently none so far)
