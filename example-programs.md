## Example programs

The Java programs described in the following sections demonstrate
the usage of the XNS-API provided by the Dodo classes. The corresponding
main program classes are available in the Java package:

    dev.hawala.xns.examples 

These programs are not directly part of Dodo and are therefore unsupported,
but may be useful for some use-cases and may serve as starting point for
own developments.

### XnsResponder

This program is a responder for several broadcast packet types sent by clients to
inquiry informations about the local network. It provides responder for the
following service protocols:
- Time protocol
- Routing Information protocol
- Broadcast for Services (BfS) protocol for Clearinghouse and Authentication servers

The program runs a restricted XNS server providing the above responder services
(but no further XNS services) and is started with:

    java -cp dodoserver-and-nethub.jar dev.hawala.xns.examples.XnsResponder
    
and the following command line parameters:
- `help`    
  print the help info and exit (i.e. no services are started)
- `+v`    
  dump requested configuration to `stdout` before starting the responders
- `hubhost:`_name_    
  connect to NetHub on host _name_
- `hubport:`_port_    
  connect to NetHub at port _port_
- `net:`_netno_    
  (required) the (decimal) local network number to which the XnsResponder server belongs to,
  this network number is also published by the Routing Information protocol
  responses
- `host:`_xx-xx-xx-xx-xx-xx_    
  (required) the given host-id (machine-id) for this XnsResponder machine (the
  _xx_ components of the address are given as 2-digit hex numbers)
- `-time`    
  disable the Time server, i.e. do not start the Time service responder
- `+time`    
  `+time:`_gmt-offset-minutes_    
  enable the time server, optionally with the time zone at the given offset
  in minutes relative to GMT
- `daysbackintime:`_days_    
  shift the local time by the given days into past
- `-rip`    
  disable the Routing Information server, i.e. do not start the RIP responder
- `+rip`    
  enable the Routine Information server
- `+bfs:`_nnn_`/`_xx-xx-xx-xx-xx-xx_    
  add a machine at the given network and host ids to BfS responses, i.e.
  start the BfS responders for Clearinghouse and Authentication and add
  the given network address to the BfS response list (at most 40 addresses
  can be specified)
  
The following defaults apply:
- `hubhost:localhost`
- `hubport:3333`
- `+time:0`
- `daysbackintime:0`
- `+rip`

No Bfs addresses are defined by default, i.e. at least one `+bfs:` command line
argument must be added to produce BfS responses.

Invocation example (must be given in one line, splitted here for readability):

    java -cp dodoserver-and-nethub.jar dev.hawala.xns.examples.XnsResponder
      net:2050 host:10-00-AA-12-34-56
      +time:60
      +bfs:2050/10-00-BB-11-22-33
      +bfs:2050/10-00-CC-22-33-44

The above command starts a XNS responder server
- with host-id 10-00-AA-12-34-56
- at network 2050
- starting a Time service broadcasting the local time as +60 minutes from GMT (i.e. west-european time without DST)
- starting a Routing Information service with one route for the local network 2050 (decimal) at 1 hop away
- starting 2 BfS responders (one for Clearinghouse and one for Authentication servers) telling that
  there are 2 servers (for both Clearinghouse and Authentication) at addresses 10-00-BB-11-22-33
  and 10-00-CC-22-33-44, both being on the network 2050

### XnsTestRequestor

This simple program does first a Time service request broadcast, then a Bfs for Clearinghouse
broadcast. In both cases, the packet content received as response is dumped with the
interpreted IDP and PEX structure and the raw PEX payload.

It is invoked with

	  java -cp dodoserver-and-nethub.jar dev.hawala.xns.examples.XnsTestRequestor

The program takes no parameters and uses the Nethub at _localhost_ and port _3333_.

