## XNS Mail service configuration

Dodo supports exactly one Mail service instance, which must be co-located with the
(single) Clearinghouse service instance. This single mail service hosts the mailboxes
for all users defined in the Clearinghouse database.

Using the Dodo XNS mail service requires configuration at 2 levels:

- in the Clearinghouse    
where the mail service is defined by associating a network address with the mail service name

- for the Dodo server instance    
by defining the Filing volume to be used as mail storage

The network address of the Mail service and the Clearinghouse service defined in the Clearinghouse
database and the Dodo server instance providing both services must be identical, so using a machine-id
defined in a `machines.cfg` is recommended to ensure that all 3 values are same.

### Clearinghouse configuration
The Clearinghouse database must define exactly one Mail service with a `m~`_name_`.properties`file,
see [Clearinghouse configuration](./chs-config-files.md).    
If more than one Mail service is defined, the first definition file will be used (usually the alphabetically first
file, but the OS where Dodo runs may have a different opinion about ordering) and the other mail service definitions
are ignored. If no Mail service is defined in the Clearinghouse database, Dodo will abort startup.

The single Mail service manages the mailboxes for all users and is therefore automatically associated to
all users defined in the Clearinghouse.

### Dodo configuration
The Mail service is not available to the XNS network by default. The Mail service is started on the same
Dodo server instance as the Clearinghouse service by defining the Filing volume to be used for storing mails
with the following option in the Dodo configuration file:

- `mailService.volumePath`    
the location (in the local OS where Dodo runs) for the volume directory to be used for storing mails
and user mailboxes.    
_optional_, _default_: (none, the Mail service will not start if not specified) 


### Usage notes and restrictions

#### General  

Mails can be sent and received with the standard functionality of the Xerox operating systems used, i.e.
the MailTool on XDE and the Outbasket resp. Inbasket icons on Star or ViewPoint.

Mails can be sent to one or more users as primary ("To:") or secondary ("Copies:") recipients. Recipents
can also be specified as user-groups defined in the Clearinghouse, in this case the group is (recursively)
expanded to users and the mail is delivered to each of these users; however the mail envelope itself stays
unchanged, meaning that the delivered mail will still have the user-group name as the name of the primary or
secondary recipient. 

When sending mails, the Mail service honors the flags "Only if all names valid/To all valid names" resp.
"Allow Distributionlists recipients" by possibly rejecting mails having invalid names or user-groups as
recipients resp. delivering mail only to valid recipient names, depending on the flags given.

Dodo supports both sending mail notes and file-icons (files or folders), i.e. mails without or with attachments.
The file-object sent with or as mail will be delivered unmodified to the recipient(s), the Mail service
ignores the content type of the attachment and only extracts the envelope data prepended by the sending
system.

While the mail itself as the textual mail notice can be received by any of XDE, StarOS ViewPoint or GlobalView,
this may not be true for the attachment of the mail. Whereas folders and simple text files can also be
used on any target, special considerations apply to Documents sent by mail, as the functionality and the
internal file format for Documents evolved over the versions:
- older operating systems cannot open Documents created on newer systems, e.g. StarOS cannot open a
ViewPoint or GlobalView document received by mail
- newer operating systems _may_ open Document received from an older system, provided the necessary
"Document Upgrader" application is installed and running; this allows ViewPoint 2.0 to open Documents from
StarOS 5.0 onwards resp. GlobalView to open ViewPoint 2.0 documents.

#### GlobalView Restrictions

GlobalView uses newer versions of the Courier mail protocols (MailTransport version 5 resp. Inbasket version 2),
for which no documentation or Courier definitions could be found in the vast internet (unlike the older protocols
used by XDE/Star/ViewPoint, for which hints are available through a programmer's manual and Mesa files at Bitsavers).
So most structures and procedures defined in the implementation of the newer mail protocols used by GlobalView (and
possibly by ViewPoint 3.0) are highly speculative.

Furthermore the implementation of the newer mailing protocols uses the internal mail service used for the initial
(older) mailing protocol implementation, so mails are always stored by the mail service with the old (VP 2.0 / XDE)
compatible feature set, so newer mail features (like "importance" or all the mail properties available when switching
to "Show fields: ALL" in GlobalView) are discarded when a mail is sent from GlobalView and will not be present (i.e.
have default values) when receiving mails in GlobalView.

### Mail volume organization
The Filing volume used to store mails and mailboxes is fully managed by the Mail service. There is no need
to create a `root-folder.lst` in the specified volume directory, as the Mail service creates one at each
startup (manual changes will therefore be lost!).

A single mail accepted by the service results in 2 types of files:
- one mail file holding the raw mail content (as transferred as bulk-data transfer when the mail is created
  by a client) as well as some metadata in a file property
- one mail reference file for each recipient, having the mail envelope for this recipient as file content

The Mail service uses 2 file drawers in the volume root to store these different kinds of files:
- the file drawer `Mailfiles` stores the raw mail files
- the file drawer `Mailboxes` has one folder for each user defined in the Clearinghouse, each folder
representing the mailbox for the corresponding user; the name of each mailbox folder is the 3-part name
of the user owning the mailbox.

As required by the Mail service definition, each mail has a 5-word identification. The first 3 of the 5 words
of a mail identification are the same for all mails and generated more or less randomly when the filing volume
is initialized by the mail service. The last 2 words of the identification are the linear numbering of the mails.
The last mail-id used is stored as attribute of the `Mailfiles` file drawer.

The raw mail file in `Mailfiles` is named with the millisecond timestamp of the mail posting and the 2
words of the linear mail number (i.e. last 2 words of the mail id), as hexadecimal values separated by dashes.

For each recipient of the mail, a reference file is created in the mailbox folder of the recipient. The name of
the mail reference file is composed of the name of the referenced raw mail file and an additional suffix specifying
the status of the mail in the mailbox, the suffix is one of _:new_ , _:known_ or _:received_.

The metadata stored in a file attribute of the raw mail file contains a reference counter holding the number of the
(still) existing references from mailbox files. When a mail is deleted from a mailbox, the mailbox reference file
in this mailbox folder is deleted and the reference counter on the raw mail file is decremented. When this count
becomes 0, the raw mail file is also deleted.