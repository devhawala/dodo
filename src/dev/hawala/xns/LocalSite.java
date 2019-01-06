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

package dev.hawala.xns;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;

import dev.hawala.xns.level0.NetPacket;
import dev.hawala.xns.level1.IDP;
import dev.hawala.xns.network.AsyncIdpPipeline;
import dev.hawala.xns.network.NetMachine;

/**
 * Factory class for creating the single XNS machine at the local site
 * (current JVM), connecting it to a virtual network provided by a
 * {@code NetHub} for communication with other emulated XNS systems.
 * <p>
 * The instance of {@code localSite} is mainly the interface to the
 * {@code NetHub}, transferring packets at the IDP level. The implementation of
 * the higher level protocols (above IDP) is delegated to a {@code NetMachine}
 * which dispatches the received packets to the handlers registered for the
 * corresponding ports and protocols, providing these handlers to send
 * generated data packets.  
 * </p>
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2016-2018)
 */
public class LocalSite {
	
	// 60 bytes seems the minimum length accepted by Pilot (Ethernet requirement?)
	private static final int MIN_PACKET_LEN = 60;
	
	private static final Object lock = new Object();
	
	// configuration with defaults
	private static long networkId = 2273; // 0x0001_0120; // arbitrary
	private static long machineId = 0x0000_1000_FF12_3401L; // 10-00-FF-12-34-01
	private static boolean enforceChecksums = false; // false; // true;
	
	private static String machineName = "DwarfSvc:domain:org";
	
	private static NetMachine theMachine = null;
	
	private static String hubHost = "localhost";
	private static int hubSocket = 3333;
	private static final int HUB_CONNECT_RETRY_INTERVAL = 2000; // 2 seconds
	
	private static Socket sock = null;
	private static InputStream istream = null;
	private static OutputStream ostream = null;
	
	private static final AsyncIdpPipeline local2remotePipeline = new AsyncIdpPipeline();
	private static final AsyncIdpPipeline remote2localPipeline = new AsyncIdpPipeline();
	
	private static Thread machine2remoteThread = null;;
	private static Thread remote2localThread = null;;
	private static Thread local2machineThread = null;
	
	private static void connectToHub() throws InterruptedException {
		while(sock == null) {
			try {
				Log.L0.printf(null, "opening connection to hub\n");
				sock = new Socket(hubHost, hubSocket);
				sock.setTcpNoDelay(true);
				istream = sock.getInputStream();
				ostream = sock.getOutputStream();
			} catch(UnknownHostException uhe) {
				Log.L0.printf(null, "** Unknown host: '%s', network hub unreachable\n", hubHost);
				lock.wait(); // wait forever resp. until interrupted
			} catch(IOException ioe) {
				Log.L0.printf(null, "IOException while connecting: %s\n", ioe.getMessage());
				dropHubConnection(); // cleanup the possibly partial connect
				lock.wait(HUB_CONNECT_RETRY_INTERVAL);
			}
		}
	}
	
	private static void dropHubConnection() {
		if (sock != null || ostream != null || istream != null) {
			Log.L0.printf(null, "closing connection to hub\n");
		}
		if (istream != null) {
			try {istream.close(); } catch(Exception e) { }
			istream = null;
		}
		if (ostream != null) {
			try {ostream.close(); } catch(Exception e) { }
			ostream = null;
		}
		if (sock != null) {
			try {sock.close(); } catch(Exception e) { }
			sock = null;
		}
	}
	
	private static final byte[] sendBuffer = new byte[2048];
	private static final byte[] recvBuffer = new byte[2048];
	
	private static void sendIdp(IDP idp) throws InterruptedException {
		OutputStream stream = null;
		boolean failed = false;
		while(true) {
			synchronized(lock) {
				if (ostream == null || (failed && stream == ostream)) {
					dropHubConnection();
					connectToHub();
				}
				stream = ostream;
				failed = false;
			}
			try {
				Log.L0.printf(idp, "transmitting packet for %s to hub\n", idp.getDstEndpoint());
				
				// transfer the IDP packet into the ethernet payload area (starts at offset 14 bytes, plus 2 bytes for length)
				NetPacket p = idp.packet;
				int packetLength = p.rdBytes(0, p.getPayloadLength(), sendBuffer, 16, sendBuffer.length - 16);
				
				// add trailing bytes to pad the content up to minimal length 
				if (packetLength < MIN_PACKET_LEN) {
					for (int i = packetLength; i < MIN_PACKET_LEN; i++) {
						sendBuffer[i + 16] = 0;
					}
					packetLength = MIN_PACKET_LEN;
				}
				
				// set ethernet destination mac address
				sendBuffer[2] = p.rdByte(10);
				sendBuffer[3] = p.rdByte(11);
				sendBuffer[4] = p.rdByte(12);
				sendBuffer[5] = p.rdByte(13);
				sendBuffer[6] = p.rdByte(14);
				sendBuffer[7] = p.rdByte(15);
				
				// set ethernet source mac address
				sendBuffer[8] = p.rdByte(22);
				sendBuffer[9] = p.rdByte(23);
				sendBuffer[10] = p.rdByte(24);
				sendBuffer[11] = p.rdByte(25);
				sendBuffer[12] = p.rdByte(26);
				sendBuffer[13] = p.rdByte(27);
				
				// set ethernet packet type for XNS
				sendBuffer[14] = 0x06;
				sendBuffer[15] = 0x00;
				
				// correct the packetlength (add ethernet header length)
				packetLength += 14;
				
				// send the ethernet packet to the hub
				sendBuffer[0] = (byte)((packetLength >> 8) & 0xFF);
				sendBuffer[1] = (byte)(packetLength & 0xFF);
				stream.write(sendBuffer, 0, packetLength + 2);
				return;
			} catch (IOException e) {
				System.err.printf("IOException while sending: %s\n", e.getMessage());
				// continue sending after reconnecting to hub
				failed = true;
			}
		}
	}

	private static int readPacket(InputStream nis) throws IOException {		
		int b0 = (byte)nis.read();
		int b1 = (byte)nis.read();
		int netLen = ((b0 & 0xFF) << 8) | (b1 & 0xFF);
		if (netLen > recvBuffer.length) { throw new IOException(); }
		int remaining = netLen;
		while(remaining > 0) {
			int chunkSize = nis.read(recvBuffer, (netLen - remaining), remaining);
			remaining -= chunkSize;
		}
		return netLen;
	}
	
	private static IDP receiveIdp() throws InterruptedException {
		InputStream stream = null;
		boolean failed = false;
		while(true) {
			synchronized(lock) {
				if (istream == null || (failed && stream == istream)) {
					dropHubConnection();
					connectToHub();
				}
				stream = istream;
				failed = false;
			}
			try {
				// receive packet from hub
				int contentLength = readPacket(stream);
				
				// check ethernet header
				if (contentLength < 14) {
					// no ethernet header
					Log.L0.printf(null, "packet from hub ignored, no ethernet header (contentLength[%d] < 14)\n", contentLength);
					continue;
				}
				if (recvBuffer[12] != 0x06 || recvBuffer[13] != 0x00) {
					// not ethernet packet type XNS
					Log.L0.printf(null, "packet from hub ignored, not XNS (type: 0x%02X%02X)\n", recvBuffer[12], recvBuffer[13]);
					continue;
				}
				long destAddress
						= ((long)(recvBuffer[0] & 0xFF) << 40)
						| ((long)(recvBuffer[1] & 0xFF) << 32)
						| ((long)(recvBuffer[2] & 0xFF) << 24)
						| ((long)(recvBuffer[3] & 0xFF) << 16)
						| ((long)(recvBuffer[4] & 0xFF) << 8)
						| (long)(recvBuffer[5] & 0xFF);
				if (destAddress != machineId && destAddress != IDP.BROADCAST_ADDR) {
					// packet not relevant (directed to us or broadcasted)
					Log.L0.printf(null, "packet from hub ignored, not for us (to: 0x%06X)\n", destAddress);
					continue;
				}
				
				// return the ethernet payload as IDP packet (removing the 14 bytes ethernet header)
				NetPacket rawPacket = new NetPacket(recvBuffer, 14, contentLength - 14);
				IDP idp = new IDP(rawPacket);
				Log.L0.printf(idp, "received XNS packet from hub, source: %s\n", idp.getSrcEndpoint());
				return idp;
			} catch (IOException e) {
				System.err.printf("IOException while receiving: %s\n", e.getMessage());
				// continue receiving after reconnecting to hub
				failed = true;
			}
		}
	}
	
	/**
	 * Configure the parameters of the local XNS machine. This is possible
	 * as long as the (singleton) machine has not yet been instantiated.
	 * 
	 * @param network the XNS network identification where the machine is located
	 * 		<br/>Default: {@code 0x0001_0120}
	 * @param machine the machine-id (MAC address) for the machine
	 * 		<br/>Default: {@code 0x0000_1000_FF12_3401L} (10-00-FF-12-34-01)
	 * @param name the symbolic name of the machine
	 * 		<br/>Default: {@code DwarfSvc:domain:org}
	 * @param doChecksums are checksums to be checked for received packets and
	 * 		checksums to be generated for outgoing packets?
	 * 		<br/>WARNING: using {@code true} slows down communications!
	 * 		<br/>Default: {@code true}
	 * @return {@code true} if the parameters where set.
	 */
	public static boolean configureLocal(long network, long machine, String name, boolean doChecksums) {
		synchronized(lock) {
			if (theMachine != null) { return false; }
			networkId = network;
			machineId = machine;
			machineName = name;
			enforceChecksums = doChecksums;
			return true;
		}
	}
	
	/**
	 * Configure the connection parameters to the {@code NetHub} serving
	 * as network backbone. 
	 * 
	 * @param host
	 * 		hostname for the {@code NetHub} machine (default: "localhost").
	 * 		Setting this value to {@code null} disables using the {@code NetHub},
	 * 		i.e. only local connections inside the machine are possible.  
	 * @param sockNo socket used by the {@code NetHub} service (default: 3333)
	 * @return {@code true} if the parameters where set.
	 */
	public static boolean configureHub(String host, int sockNo) {
		synchronized(lock) {
			if (theMachine != null) { return false; }
			hubHost = host;
			hubSocket = sockNo;
			return true;
		}
	}
	
	/**
	 * Possibly instantiate and get the one (single) XNS machine
	 * for the local site (this JVM instance).
	 * 
	 * @return the singleton XNS machine.
	 */
	public static iNetMachine getInstance() {
		synchronized(lock) {
			// is the singleton already there?
			if (theMachine == null) {
				// create the machine
				theMachine = new NetMachine(networkId, machineId, machineName, local2remotePipeline, enforceChecksums);
				
				// create and start the threads, either with hub connection or local only 
				if (hubHost != null && hubSocket > 0 && hubSocket < 65536) {
					machine2remoteThread = new Thread(() -> {
						while(true) {
							try {
								IDP idp = local2remotePipeline.get();
								
								// force correct source information of outgoing packet
								idp.setSrcHost(machineId);
								idp.setSrcNetwork(networkId);
								
								// if not sent as broadcast:
								// correct network for responses to requests sent to 'any' or 'local'network
								// to inform the sender about the network it is directly attached to
								long destNetwork = idp.getDstNetwork();
								if (idp.getDstHost() != IDP.BROADCAST_ADDR
									&& (destNetwork == IDP.LOCAL_NETWORK || destNetwork == IDP.ANY_NETWORK)) {
									idp.setDstNetwork(networkId);
								}
								
								// force checksum to reflect the content, if requested
								// (packets arriving here still have checksum = 0xFFFF = no_checksum,
								// but computing the checksum requires the final content, which is only
								// ensured here after the last content changes above)
								if (enforceChecksums) {
									idp.updateChecksum();
								} else {
									idp.resetChecksum();
								}
								
								// transfer the packet to local if target is this site and to to hub
								Log.L0.printf(idp, "local2remotePipeline(hub) -> packet to: 0x%06X\n", idp.getDstHost());
								if ((idp.getDstHost() == machineId || idp.getDstHost() == IDP.BROADCAST_ADDR)
										&& (idp.getDstNetwork() == networkId || idp.getDstNetwork() == IDP.LOCAL_NETWORK)) {
									Log.L0.printf(idp, "local2remotePipeline(hub) -> feeding back to local (into remote2localPipeline)\n");
									remote2localPipeline.send(idp);
									if (idp.getDstHost() == machineId) {
										return; // no need to forward to others if explicitly directed to this machine
									}
								}
								sendIdp(idp);
							} catch (InterruptedException e) {
								// shutting down
								return;
							}
						}
					});
					remote2localThread = new Thread(() -> {
						while(true) {
							try {
								IDP idp = receiveIdp();
								Log.L0.printf(idp, "remote2localPipeline -> new packet\n");
								remote2localPipeline.send(idp);
							} catch (InterruptedException e) {
								// shutting down
								return;
							}
						}
					});
				} else {
					machine2remoteThread = new Thread(() -> {
						while(true) {
							try {
								IDP idp = local2remotePipeline.get();
								
								// force correct source information of outgoing packet
								idp.setSrcHost(machineId);
								idp.setSrcNetwork(networkId);
								
								// no need for checksums if local only communication
								
								// transfer the packet to local if target is this site (here we have no hub to transfer to)
								Log.L0.printf(idp, "local2remotePipeline(local) -> packet to: 0x%06X\n", idp.getDstHost());
								if ((idp.getDstHost() == machineId || idp.getDstHost() == IDP.BROADCAST_ADDR)
										&& (idp.getDstNetwork() == networkId || idp.getDstNetwork() == IDP.LOCAL_NETWORK)) {
									Log.L0.printf(idp, "local2remotePipeline(local) -> feeding back to local (into remote2localPipeline)\n");
									remote2localPipeline.send(idp);
								}
							} catch (InterruptedException e) {
								// shutting down
								return;
							}
						}
					});
				}
				local2machineThread = new Thread(() -> {
					while(true) {
						try {
							IDP idp = remote2localPipeline.get();
							Log.L0.printf(idp, "local2machine -> theMachine.handlePacket(idp)\n");
							theMachine.handlePacket(idp);
						} catch (InterruptedException e) {
							// shutting down
							return;
						} 
					}
				});
				
				machine2remoteThread.start();
				if (remote2localThread != null) {remote2localThread.start(); }
				local2machineThread.start();
			}
			
			// return the singleton
			return theMachine;
		}
	}
	
	public static void shutdown() {
		if (theMachine != null) {
			theMachine.shutdown();
		}
		local2remotePipeline.abort();
		remote2localPipeline.abort();
	}
	
	public static long getNetworkId() { return networkId; }
	
	public static long getMachineId() { return machineId; }
	
}
