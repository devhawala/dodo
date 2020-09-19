/*
Copyright (c) 2019, Dr. Hans-Walter Latz
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * The name of the author may not be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDER "AS IS" AND ANY EXPRESS
OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package dev.hawala.xns.level4.boot;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.hawala.xns.EndpointAddress;
import dev.hawala.xns.level1.IDP;
import dev.hawala.xns.level1.IDP.PacketType;
import dev.hawala.xns.level2.Error;
import dev.hawala.xns.level2.SPP;
import dev.hawala.xns.level2.SppConnection;
import dev.hawala.xns.network.iIDPReceiver;
import dev.hawala.xns.network.iIDPSender;

/**
 * Implementation of the responder for the Boot-Service, supporting both
 * the SimpleRequest/SimpleData and SPP variants of the boot protocol for
 * transmitting boot-files (microcode, germ, boot-file) to a requesting
 * machine.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2019)
 */
public class BootResponder implements iIDPReceiver {
	
	// time interval in milliseconds between 2 'simpleData' packets
	private static int simpleDataSendInterval = 40; // default: ~ 25 packets per second
	
	public static void setSimpleDataSendInterval(int msecs) {
		simpleDataSendInterval = msecs;
	}
	
	// seed for local connection IDs
	private int lastConnId = (int)(System.currentTimeMillis() & 0xFFFF);
	
	// our own address
	private EndpointAddress localEndpoint = null;
	
	// our packet sink
	private iIDPSender sender = null;
	
	// base directory for the boot files
	private final File baseDir;
	
	// map Boot-File-Number => filename of the boot file 
	private final Map<Long,File> bootFiles = new HashMap<>();
	
	// boot requests in progress
	private final Map<Integer,Request> requests = new HashMap<>();
	
	// verbose logging?
	private final boolean verbose;
	
	// SubSequenceTypes (SST types)
	private static final byte SST_DATA = 0;
	
	
	public BootResponder(String baseDirName, boolean verbose) {
		this.baseDir = (baseDirName != null) ? new File(baseDirName) : new File(".");
		if (!this.baseDir.exists() || !this.baseDir.isDirectory() || !this.baseDir.canRead()) {
			throw new IllegalArgumentException("Invalid base directory name: " + baseDirName);
		}
		this.verbose = verbose;
		
		// load the definition of the boot files (with their IDs) present in the base directory
		this.loadBootServiceProfile();
	}
	
	// interpret the BootService.profile file in the original Xerox format,
	// providing the boot files mentioned there and found in the base directory
	private void loadBootServiceProfile() {
		Path path = Paths.get(this.baseDir.getAbsolutePath(), "BootService.profile");
		try {
			List<String> lines = Files.readAllLines(path);
			for (String line : lines) {
				line = line.trim();
				if (!line.startsWith("Key:")) {
					continue;
				}
				String[] parts = line.split(" ");
				if (parts.length == 5) {
					try {
						long bootFileId = Long.parseLong(parts[3], 8);
						File bootFile = new File(this.baseDir, parts[4]);
						if (bootFile.exists() && bootFile.canRead() && !bootFile.isDirectory()) {
							this.bootFiles.put(bootFileId, bootFile);
							this.logf("bootSvc: bootFileId 0x%012X -> %s\n", bootFileId, parts[4]);
						}
					} catch (NumberFormatException nfe) {
						continue;
					}
				}
			}
		} catch (IOException e) {
			return;
		}
	}
	
	@Override
	public void start(EndpointAddress localEndpoint, iIDPSender sender) {
		this.localEndpoint = localEndpoint;
		this.sender = sender;
	}

	@Override
	public void accept(IDP rcvIdp) {
		if (this.sender == null) { return; } // service currently stopped...
		
		PacketType packetType = rcvIdp.getPacketType();
		if (packetType == PacketType.BOOT_SERVER_PACKET) {
			this.logf("boot-server-packet %s\ndata: 0x %04X %04X %04X %04X %04X %04X\n",
					rcvIdp.toString(),
					rcvIdp.rdCardinal(0), rcvIdp.rdCardinal(2), rcvIdp.rdCardinal(4),
					rcvIdp.rdCardinal(6), rcvIdp.rdCardinal(8), rcvIdp.rdCardinal(10));
			int reqType = rcvIdp.rdCardinal(0);
			if (reqType == 1) { // simpleRequest
				// get request data
				long bfn0 = rcvIdp.rdCardinal(2);
				long bfn1 = rcvIdp.rdCardinal(4);
				long bfn2 = rcvIdp.rdCardinal(6);
				long bfn = (bfn0 << 32) | (bfn1 << 16) | bfn2;
				this.logf(
						"## bootSvc: processing simple-request from 0x%012X for boot-file-number 0x%012X\n",
						rcvIdp.getSrcHost(), bfn);
				
				// start new download session
				if (!this.bootFiles.containsKey(bfn)) {
					this.logf("## bootSvc : ERROR -> no bootfile defined for boot-file-number 0x%012X\n", bfn);
					return;
				}
				File bootfile = this.bootFiles.get(bfn);
				this.logf(
						"## bootSvc: sending file '%s' via simpleFile for boot-file-number 0x%012X\n",
						bootfile.getName(), bfn);
				
				// prepare idp packet for sending the boot file
				IDP idp = new IDP().asReplyTo(rcvIdp).withSource(this.localEndpoint);
				idp.wrCardinal(0, 2); // simpleData (response to simpleRequest)
				idp.wrCardinal(2, (int)bfn0);
				idp.wrCardinal(4, (int)bfn1);
				idp.wrCardinal(6, (int)bfn2);
				
				// start sending thread
				try {
					BufferedInputStream bis = new BufferedInputStream(new FileInputStream(bootfile));
					Thread thr = new Thread(new SimpleDataSender(bis, idp, this.sender, this.verbose));
					thr.setDaemon(true);
					thr.start();
				} catch (FileNotFoundException e) {
					this.logf(
						"## bootSvc : ERROR -> failed to access bootfile boot-file-number 0x%012X: %s\n",
						bfn, e.getMessage());
					return;
				}
			} else if (reqType == 3) { // sppRequest
				// get request data
				long bfn0 = rcvIdp.rdCardinal(2);
				long bfn1 = rcvIdp.rdCardinal(4);
				long bfn2 = rcvIdp.rdCardinal(6);
				long bfn = (bfn0 << 32) | (bfn1 << 16) | bfn2;
				int otherConnectionID = rcvIdp.rdCardinal(8);
				this.logf(
					"## bootSvc: processing SPP-request from 0x%012X connID 0x%04X for boot-file-number 0x%012X\n",
					rcvIdp.getSrcHost(), otherConnectionID, bfn);
				
				// start new download session
				if (!this.bootFiles.containsKey(bfn)) {
					this.logf("## bootSvc : ERROR -> no bootfile defined for boot-file-number 0x%012X\n", bfn);
					return;
				}
				int myConnectionID = (this.lastConnId + 3) & 0xFFFF;
				this.lastConnId = myConnectionID;
				try {
					File bootfile = this.bootFiles.get(bfn);
					BufferedInputStream bis = new BufferedInputStream(new FileInputStream(bootfile));
					int key = (otherConnectionID << 16) | myConnectionID;
					this.requests.put(key,  new Request(bis));
					this.logf("## bootSvc: created session key 0x%08X for request, providing boot file '%s'\n", key, bootfile.getName());
				} catch (FileNotFoundException e) {
					this.logf(
						"## bootSvc : ERROR -> failed to access bootfile boot-file-number 0x%012X: %s\n",
						bfn, e.getMessage());
					return;
				}
				
				// send SPP "connection opened" packet response
				// with request to acknowledge for triggering the send mechanism 
				SPP spp = new SPP();
				spp.idp.asReplyTo(rcvIdp).withSource(this.localEndpoint);
				spp.asSystemPacket()
					.asSendAcknowledge()
					.setDstConnectionId(otherConnectionID)
					.setSrcConnectionId(myConnectionID)
					.setSequenceNumber(0)
					.setAllocationNumber(0)
					.setAcknowledgeNumber(0)
					.setDatastreamType(SST_DATA);
				this.dumpSpp("## bootSvc - reply SPP:", spp);
				this.sender.send(spp.idp);
			} else {
				this.logf("## bootSvc: unsupported request type %d, ignoring request\n", reqType);
			}
		} else if (packetType == PacketType.SPP) {
			// packet in a SPP-flavor boot file transfer: check the session
			SPP rcvSpp = new SPP(rcvIdp);
			this.dumpSpp("## bootSvc - ingone SPP:", rcvSpp);
			int key = (rcvSpp.getSrcConnectionId() << 16) | rcvSpp.getDstConnectionId();
			Request request = this.requests.get(key);
			this.logf(
				"## bootSvc: resulting session key 0x%08X -> request found: %s\n",
				key, Boolean.toString(request != null));
			if (request == null) {
				return; // no session => no response possible
			}
			
			// check state in the packet ping-pong
			int sst = rcvSpp.getDatastreamType() & 0xFF;
			if (sst == SST_DATA) {
				// ongoing transfer: prepare response packet common fields
				SPP rplySpp = new SPP();
				rplySpp.idp.asReplyTo(rcvIdp);
				rplySpp
					.setDstConnectionId(rcvSpp.getSrcConnectionId())
					.setSrcConnectionId(rcvSpp.getDstConnectionId())
					.setAllocationNumber(0)
					.setAcknowledgeNumber(0)
					.setDatastreamType((byte)SppConnection.SST_CLOSE_CONFIRM);
				
				// check which page to send this time
				int nextPageNo = request.nextPageToSend();
				this.logf("## bootSvc: next bootfile page = %d\n", nextPageNo);
				int ackNo = rcvSpp.getAcknowledgeNumber();
				if (ackNo == nextPageNo) {
					// last page sent was acknowledged, so send next page
					this.logln("## bootSvc: request.fillNextPacket(rplySpp)");
					if (request.fillNextPacket(rplySpp)) {
						rplySpp
							.asSendAcknowledge()
							.setDatastreamType(SST_DATA)
							.setSequenceNumber(nextPageNo);
					} else {
						// the boot file is complete, so signal this to booting machine
						this.logln("## bootSvc: ... reached end-of-bootfile"); 
						rplySpp
							.setDatastreamType((byte)SppConnection.SST_CLOSE_REQUEST)
							.setSequenceNumber(nextPageNo /*- 1*/);
					}
				} else {
					// last page sent is not acknowledged, so resend it
					this.logln("## bootSvc: request.resendLastPacket(rplySpp)");
					request.resendLastPacket(rplySpp);
					rplySpp
						.asSendAcknowledge()
						.setDatastreamType(SST_DATA)
						.setSequenceNumber(nextPageNo - 1);
				}
				
				// finally send the packet
				this.sendIDP(rplySpp);
			} else if (sst == SppConnection.SST_CLOSE_REQUEST || sst == SppConnection.SST_CLOSE_CONFIRM) {
				// this is either:
				// -> a request from the booting machine to close: reply with close confirmation
				// or
				// -> the response to our close request: we have to reply with (also) a confirmation
				SPP rplySpp = new SPP();
				rplySpp.idp.asReplyTo(rcvIdp).withSource(this.localEndpoint);
				rplySpp
					.setDstConnectionId(rcvSpp.getSrcConnectionId())
					.setSrcConnectionId(rcvSpp.getDstConnectionId())
					.setSequenceNumber(rcvSpp.getAcknowledgeNumber())
					.setAllocationNumber(0)
					.setAcknowledgeNumber(0)
					.setDatastreamType((byte)SppConnection.SST_CLOSE_CONFIRM);
				this.sendIDP(rplySpp);
				
				// if drop request => this will silently ignore the SST_CLOSE_CONFIRM reply to our confirmation packet
				request.close();
				this.requests.remove(key);
				this.logf("## bootSvc: closed session 0x%08X\n", key);
			}
		}
		
	}
	
	private void sendIDP(SPP spp) {
		try { Thread.sleep(simpleDataSendInterval); } catch (InterruptedException ie) { /* ignored */ };
		this.dumpSpp("## bootSvc - reply SPP:", spp);
		this.sender.send(spp.idp);
	}

	@Override
	public void acceptError(Error err) {
		// error notifications currently ignored
	}

	@Override
	public void stopped() {
		this.sender = null;
	}
	
	/*
	 * logging
	 */
	
	private void dumpSpp(String prefix, SPP spp) {
		this.logf(
				"%s srcAdr=%012X dstAdr=%012X srcId=%04X dstId=%04X%s%s sst=%d seqNo=%d ackNo=%d allocNo=%d dataLen=%d\n",
				prefix,
				spp.idp.getSrcHost(),
				spp.idp.getDstHost(),
				spp.getSrcConnectionId(),
				spp.getDstConnectionId(),
				(spp.isSystemPacket()) ? " system" : "",
				(spp.isSendAcknowledge()) ? " sendAck" : "",
				spp.getDatastreamType(),
				spp.getSequenceNumber(),
				spp.getAcknowledgeNumber(),
				spp.getAllocationNumber(),
				spp.getPayloadLength()
				);
	}
	
	private void logf(String pattern, Object... args) {
		if (this.verbose) { System.out.printf(pattern, args); }
	}
	
	private void logln(String s) {
		if (this.verbose) { System.out.println(s); }
	}
	
	/*
	 * helper classes
	 */
	
	// implementation of the thread sending a boot file
	// as sequence of simpleData packets
	private static class SimpleDataSender implements Runnable {
		
		private final IDP idp;
		private InputStream src;
		private final iIDPSender sender;
		private final boolean verbose;
		
		private int nextPageNo = 1;
		
		private SimpleDataSender(InputStream src, IDP idp, iIDPSender sender, boolean verbose) {
			this.idp = idp;
			this.sender = sender;
			this.src = src;
			this.verbose = verbose;
		}

		@Override
		public void run() {
			boolean done = false;
			long startMs = System.currentTimeMillis();
			try {
				while(!done) {
					Thread.sleep(simpleDataSendInterval);
					int pageNo = this.nextPageNo++;
					this.idp.wrCardinal(8, pageNo);
					int packetLength = 10;
					int bootfileBytes = this.fillPage(this.idp);
					done = (bootfileBytes < 1);
					packetLength += bootfileBytes;
					this.idp.setPayloadLength(packetLength);
					this.sender.send(this.idp);
					if (this.verbose) {
						System.out.printf("## bootSvc - simpleData: at +%d sent %d bootfile bytes%s\n",
								System.currentTimeMillis() - startMs,
								bootfileBytes,
								done ? " - transfer done" : "");
					}
				}
			} catch (InterruptedException e) {
				this.close();
			}
			
		}
		
		private int fillPage(IDP idp) {
			if (this.src == null) {
				return 0;
			}
			int byteCount = 0;
			try {
				for (int i = 0; i < 512; i++) {
					int b = this.src.read();
					if (b < 0) {
						this.close();
						break;
					}
					idp.wrByte(10 + i, (byte)b);
					byteCount++;
				}
			} catch (IOException e) {
				this.close();
			}
			return byteCount;
		}
		
		private void close() {
			if (this.src == null) { return; }
			try {
				this.src.close();
			} catch (IOException e) {
				// ignored
			}
			this.src = null;
		}
	}
	
	// representation of a running SPP transfer request, providing
	// the packet sequence
	private static class Request {
		private InputStream src;
		
		private int lastPageSent = -1;
		private byte[] page = new byte[512];
		
		private Request(InputStream s) {
			this.src = s;
		}
		
		public int nextPageToSend() {
			return this.lastPageSent + 1;
		}
		
		// returns true if the next packet was available, i.e. if not EOF
		public boolean fillNextPacket(SPP spp) {
			if (this.src == null) {
				return false;
			}
			int count = 0;
			try {
				count = this.src.read(this.page);
				if (count <= 0) {
					this.close();
					return false;
				}
			} catch (IOException e) {
				this.close();
				return false;
			}
			spp.wrBytes(0, count, this.page, 0, count);
			spp.setPayloadLength(count);
			this.lastPageSent++;
			return true;
		}
		
		public void resendLastPacket(SPP spp) {
			spp.wrBytes(0, this.page.length, this.page, 0, this.page.length);
			spp.setPayloadLength(this.page.length);
		}
		
		public void close() {
			if (this.src == null) { return; }
			try {
				this.src.close();
			} catch (IOException e) {
				// ignored
			}
			this.src = null;
		}
	}
	
}
