/*
Copyright (c) 2018, Dr. Hans-Walter Latz
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

package dev.hawala.hub;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.jnetpcap.Pcap;
import org.jnetpcap.PcapIf;
import org.jnetpcap.packet.PcapPacket;
import org.jnetpcap.packet.PcapPacketHandler;

/**
 * Gateway from a real network device to the NetHub.
 * <p>
 * This is both the Gateway class itself forwarding packets
 * from the NetHub to the real device as well as the main
 * program getting packets from the real device forwarding
 * them to the NetHub.
 * </p>
 * <p>
 * Requires: JNetPcap with a configured PCap device.
 * </p>
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2016-2018
 */
public class NetHubGateway implements Runnable {
	
	// log packet going in from NetHub (with packet content?)
	private static boolean logHub = false;
	private static boolean logHubData = false;
	
	// log packet going in from network device (with packet content?)
	private static boolean logDev = false;
	private static boolean logDevData = false;
	
	private static void hubLog(String pattern, Object... values) {
		if (logHub) {
			System.out.printf(pattern, values);
		}
	}
	
	private static void devLog(String pattern, Object... values) {
		if (logDev) {
			System.out.printf(pattern, values);
		}
	}
	
	private final Pcap pcap;
	private final Socket socket;
	
	private InputStream in;
	private OutputStream out;
	
	// list of host addresses located at the NetHub
	// (forwarding their packets to the real network devices will echo
	// these packets as input from the real network, so these packets
	// must be filtered out to prevent confusion at NetHub hosts)
	private static final List<Long> externalHosts = new ArrayList<>();
	
	private static void registerExternalHost(long addr) {
		synchronized(externalHosts) {
			if (!externalHosts.contains(addr)) {
				externalHosts.add(addr);
			}
		}
	}
	
	private static boolean isExternalHost(long addr) {
		synchronized(externalHosts) {
			return externalHosts.contains(addr);
		}
	}
	
	private NetHubGateway(Pcap pcap, Socket socket) throws IOException {
		this.pcap = pcap;
		this.socket = socket;
		
		this.socket.setTcpNoDelay(true);
		
		this.in = socket.getInputStream();
		this.out = socket.getOutputStream(); 
	}
	
	private final byte[] inPacket = new byte[2048];

	private int receivePacket() throws IOException {
		InputStream nis = this.getIn();
		if (nis == null) { throw new IOException(); }
		
		int b0 = (byte)nis.read();
		int b1 = (byte)nis.read();
		int netLen = ((b0 & 0xFF) << 8) | (b1 & 0xFF);
		if (netLen > inPacket.length) { throw new IOException(); }
		int remaining = netLen;
		while(remaining > 0) {
			int chunkSize = nis.read(inPacket, (netLen - remaining), remaining);
			remaining -= chunkSize;
		}
		return netLen;
	}
	
	@Override
	public void run() {
		int pNo = 0;
		int totalSent = 0;
		while(this.in != null) {
			try {
				hubLog("++ in :: awaiting next packet\n");
				int packetLen = this.receivePacket();
				pNo++;
				hubLog("## in[%d]: net packet len: %d\n", pNo, packetLen);
				
				if (packetLen < 1) {
					hubLog("## in[%d]: skipping empty packet!\n", pNo);
					continue;
				}
				
				if (packetLen > 12) {
					long srcAddr = 0;
					for (int i = 6; i < 12; i++) {
						srcAddr = (srcAddr << 8) | (inPacket[i] & 0xFF);
					}
					registerExternalHost(srcAddr);
				}
				
				hubLog("in[%d]: forwarding packet, length = %d:\n", pNo, packetLen);
				if (logHubData) {
					for (int i = 0; i < packetLen; i++) {
						byte b = this.inPacket[i];
						if ((i % 16) == 0) {
							System.out.printf("\n 0x%03X: ", i);
						}
						System.out.printf(" %02X", b);
					}
					System.out.printf("\n\n");
				}
				try {
					this.pcap.sendPacket(this.inPacket, 0, packetLen);
					totalSent += packetLen;
					hubLog("** in[%d]: pCap.sendPacket() transmitted (totalSent: %d)\n", pNo, totalSent);
				} catch(Exception e) {
					hubLog("** in[%d]: pCap.sendPacket() failure: %s\n", pNo, e.getMessage());
				}
			} catch (Exception e) {
				this.closeSocket();
			}
		}
		hubLog("** receiverThread.run(): connection to hub lost\n");
	}
	
	private final byte[] outPacket = new byte[2048];
	private int outNo = 0;
	
	public boolean sendPacket(PcapPacket packet) {
		OutputStream nos = this.getOut();
		if (nos == null) { return false; }
		
		int packetLen = packet.getCaptureHeader().caplen();
		this.outPacket[0] = (byte)((packetLen & 0x0000FF00) >>> 8);
		this.outPacket[1] = (byte)(packetLen & 0x000000FF);
		for (int i = 0; i < packetLen; i++) {
			this.outPacket[i+2] = packet.getByte(i);
		}
		
		outNo++;
		long nanoTs = System.nanoTime();
		devLog("out[%d] => packet length: %d -- at %9d.%06d ms\n", outNo, packetLen, nanoTs / 1000000, nanoTs % 1000000);
		
		try {
			nos.write(this.outPacket, 0, packetLen + 2);
			nos.flush();
			return true;
		} catch (IOException e) {
			System.out.printf("## sendPacket(): connection to hub lost => shutting down gateway\n");
			this.closeSocket();
			return false;
		}
	}
		
	private synchronized OutputStream getOut() {
		return this.out;
	}
	
	private synchronized InputStream getIn() {
		return this.in;
	}
	
	private synchronized void closeSocket() {
		if (this.in != null) {
			try { this.in.close(); } catch (IOException e) {}
			this.in = null;
		}
		if (this.out != null) {
			try { this.out.close(); } catch (IOException e) {}
			this.out = null;
		}
		try { this.socket.close(); } catch (IOException e) {}
	}
	
	private static void help() {
		System.out.printf("\nvalid arguments: [-p] [-lhd] [-ldd] hubHost|= hubPort|= ifName|=\n"
			+ "-p        print available PCap device names and stop\n"
			+ "-lh[d]    log traffic from NetHub (incl. packets content)\n"
			+ "-ld[d]    log traffic from net device (incl. packets content)\n"
			+ "hubHost   hostname for the NetHub or = for default (localhost)\n"
			+ "hubPort   port number for the NetHub or = for default (3333)\n"
			+ "ifName    name of the PCap network device or = for default\n"
			+ "\n"
			);
	}

	/**
	 * Main startup method
	 * 
	 * @param args
	 *          ignored
	 */
	public static void main(String[] args) {
		
		boolean printDevs = false;
		
		String hubHost = "localhost";
		String hubPort = "3333";
		String ifName = "MS LoopBack Driver";
		int argNo = 0;
		
		// scan command line parameters
		for (int i = 0; i < args.length; i++) {
			if ("-p".equalsIgnoreCase(args[i])) {
				printDevs = true;
			} else if ("-ldd".equalsIgnoreCase(args[i])) {
				logDev = true;
				logDevData = true;
			} else if ("-ld".equalsIgnoreCase(args[i])) {
				logDev = true;
			} else if ("-lhd".equalsIgnoreCase(args[i])) {
				logHub = true;
				logHubData = true;
			} else if ("-lh".equalsIgnoreCase(args[i])) {
				logHub = true;
			} else if (argNo == 0) {
				if (!"=".equals(args[i])) { hubHost = args[i]; }
				argNo++;
			} else if (argNo == 1) {
				if (!"=".equals(args[i])) { hubPort = args[i]; }
				argNo++;
			} else if (argNo == 2) {
				if (!"=".equals(args[i])) { ifName = args[i]; }
				argNo++;
			} else if ("-h".equalsIgnoreCase(args[i])) {
				help();
				return;
			} else {
				System.out.printf("** invalid parameter ignored: '%s'\n", args[i]);
			}
		}
		
		// get usable hubPort value
		int port = 3333;
		try {
			port = Integer.parseInt(hubPort);
			if (port < 0 || port > 65535) {
				throw new IllegalArgumentException();
			}
		} catch(Exception e) {
			System.out.printf("** invalid hubPort '%s' specified, aborting...\n", hubPort);
			return;
		}
		
		// PCap data collected
		PcapIf device = null; // the device we shall use
		List<PcapIf> alldevs = new ArrayList<PcapIf>(); // Will be filled with NICs
		StringBuilder errbuf = new StringBuilder(); // For any error msgs

		// go through the list of PCap devices on this system
		int r = Pcap.findAllDevs(alldevs, errbuf);
		if (r == Pcap.NOT_OK || alldevs.isEmpty()) {
			System.err.printf("Can't read list of devices, error is %s", errbuf
			    .toString());
			return;
		}

		if (printDevs) { System.out.println("Network devices found:"); }

		int i = 0;
		for (PcapIf ifDev : alldevs) {
			String description =
			    (ifDev.getDescription() != null) ? ifDev.getDescription()
			        : "No description available";
			if (printDevs) { System.out.printf("#%d: %s [%s]\n", i++, ifDev.getName(), description); }
			if (ifName.equalsIgnoreCase(description)) {
				device = ifDev;
			}
		}
		
		// print device list only?
		if (printDevs) {
			return;
		}
		
		// the PCap device must exist to continue
		if (device == null) {
			System.out.printf("## ifName '%s' not found, aborting\n", ifName);
			return;
		}

		// open the selected device
		int snaplen = 64 * 1024;           // Capture all packets, no truncation
		int flags = Pcap.MODE_PROMISCUOUS; // capture all packets
		int timeout = 5;                   // 5 milliseconds
		final Pcap pcap = Pcap.openLive(device.getName(), snaplen, flags, timeout, errbuf);

		if (pcap == null) {
			System.err.printf("## error while opening device for capture: " + errbuf.toString());
			return;
		}
		
		// connect to the NetHub and start receiver thread
		NetHubGateway worker;
		try {
			Socket hubSocket = new Socket(hubHost, port);
			worker = new NetHubGateway(pcap, hubSocket);
			
			Thread thr = new Thread(worker);
			thr.setDaemon(true);
			thr.start();
			
		} catch(Exception e) {
			System.err.printf("Unable to open connection to hub (host='%s', port=%d): %d\n",
					hubHost, port, e.getMessage());
			pcap.close();
			return;
		}

		// packet handler which will receive packets from the libpcap loop forwarding to the NetHub
		PcapPacketHandler<NetHubGateway> jpacketHandler = new PcapPacketHandler<NetHubGateway>() {

			public void nextPacket(PcapPacket packet, NetHubGateway sender) {
				
				if (packet.getCaptureHeader().wirelen() < 14) {
					return; // wot? not even the ethernet header...?
				}
				
				if (packet.getByte(12) != (byte)0x06 || packet.getByte(13) != (byte)0x00) {
					//devLog("non-XNS packet skipped...\n");
					return;
				}

				long srcAddr = 0;
				for (int i = 6; i < 12; i++) {
					srcAddr = (srcAddr << 8) | (packet.getByte(i) & 0xFF);
				}
				if (isExternalHost(srcAddr)) {
					// ignore NetHub packets forwarded through PCap echoed by PCap 
					return;
				}

				devLog("\nReceived packet at %s caplen=%-4d len=%-4d size=%d\n",
				    new Date(packet.getCaptureHeader().timestampInMillis()), 
				    packet.getCaptureHeader().caplen(),  // Length actually captured
				    packet.getCaptureHeader().wirelen(), // Original length 
				    packet.size()
				    );
				
				devLog("%s\n\n", packet.toString());
				if (logDevData) {
					for (int i = 0; i < packet.getCaptureHeader().caplen(); i++) {
						if ((i % 16) == 0) {
							System.out.printf("\n 0x%03X: ", i);
						}
						System.out.printf(" %02X", packet.getByte(i));
					}
					System.out.printf("\n\n");
				}
				
				if (!sender.sendPacket(packet)) {
					System.out.printf("## failed to send received packet to hub => shutting down gateway\n");
					pcap.breakloop();
				}
			}
		};

		// enter PCap receiving loop and forward ingoing XNS packets to NetHub
		pcap.loop(Pcap.LOOP_INFINITE, jpacketHandler, worker);

		// done -> close the pcap handle
		pcap.close();
	}
}
