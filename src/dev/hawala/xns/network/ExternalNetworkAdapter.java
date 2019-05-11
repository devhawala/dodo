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

package dev.hawala.xns.network;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.jnetpcap.Pcap;
import org.jnetpcap.PcapIf;
import org.jnetpcap.packet.PcapPacket;
import org.jnetpcap.packet.PcapPacketHandler;

import dev.hawala.xns.level0.NetPacket;
import dev.hawala.xns.level1.IDP;
import dev.hawala.xns.level1.IDP.PacketType;
import dev.hawala.xns.level2.Error;
import dev.hawala.xns.level2.PEX;
import dev.hawala.xns.level2.SPP;

/**
 * (obsolete)
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public class ExternalNetworkAdapter implements Runnable {

	private final String adapterName;
	
	private final PcapIf device;
	private final Pcap pcap;
	
	public ExternalNetworkAdapter(String devName) {
		this.adapterName = (devName != null && !devName.isEmpty()) ? devName : "tap0";
		
		List<PcapIf> alldevs = new ArrayList<PcapIf>(); // Will be filled with NICs
		StringBuilder errbuf = new StringBuilder(); // For any error msgs
		
		int r = Pcap.findAllDevs(alldevs, errbuf);
		if (r == Pcap.NOT_OK || alldevs.isEmpty()) {
			System.err.printf("Can't read list of devices, error is %s", errbuf.toString());
			this.device = null;
			this.pcap = null;
			return;
		}
		
		PcapIf matchedDev = null;
		for (PcapIf device : alldevs) {
			if (this.adapterName.equals(device.getDescription())) {
				matchedDev = device;
			}
			if (this.adapterName.equals(device.getName())) {
				matchedDev = device;
			}
		}
		this.device = matchedDev;
		
		int snaplen = 64 * 1024;           // Capture all packets, no trucation
		int flags = Pcap.MODE_PROMISCUOUS; // capture all packets
		int timeout = 1; // 10 * 1000;           // 10 seconds in millis
		this.pcap = Pcap.openLive(this.device.getName(), snaplen, flags, timeout, errbuf);
	}
	
	/*
	 * returns the localToExternal pipeline
	 */
	public iIDPSender startTransmissions(iIDPSender externalToLocal) {

		AsyncIdpPipeline localToExternal = new AsyncIdpPipeline();
		new Thread(() -> {
			byte[] buffer = new byte[NetPacket.MAX_PACKET_SIZE + 14];
			try {
				while(true) {
						IDP idp = localToExternal.get();
						NetPacket p = idp.packet;
						
						// set the ethernet payload
						p.rdBytes(0, NetPacket.MAX_PACKET_SIZE, buffer, 14, NetPacket.MAX_PACKET_SIZE);
						
						// set ethernet destination mac address
						buffer[0] = p.rdByte(10);
						buffer[1] = p.rdByte(11);
						buffer[2] = p.rdByte(12);
						buffer[3] = p.rdByte(13);
						buffer[4] = p.rdByte(14);
						buffer[5] = p.rdByte(15);
						
						// set ethernet source mac address
						buffer[6] = p.rdByte(18);
						buffer[7] = p.rdByte(19);
						buffer[8] = p.rdByte(20);
						buffer[9] = p.rdByte(21);
						buffer[10] = p.rdByte(22);
						buffer[11] = p.rdByte(23);
						
						// set ethernet packet type
						buffer[12] = 0x06;
						buffer[13] = 0x00;
						
						// send the packet
						this.pcap.sendPacket(buffer,  0, p.getPayloadLength() + 14);
				}
			} catch (InterruptedException e) {
				// ignored => terminate 
			}
		}).start();
		return localToExternal;
	}

	/*
	 * Implementation of packet transfer: external network to local network 
	 */
	@Override
	public void run() {
		
	}
	
	PcapPacketHandler<String> jpacketHandler = new PcapPacketHandler<String>() {

		public void nextPacket(PcapPacket packet, String user) {
			
			if (packet.getByte(12) != (byte)0x06 || packet.getByte(13) != (byte)0x00) {
				// System.out.println("packet skipped...");
				return;
			}

			System.out.printf("\n\nReceived packet at %s caplen=%-4d len=%-4d %s\n",
			    new Date(packet.getCaptureHeader().timestampInMillis()), 
			    packet.getCaptureHeader().caplen(),  // Length actually captured
			    packet.getCaptureHeader().wirelen(), // Original length 
			    user                                 // User supplied object
			    );
			// System.out.println(packet);
			
			int payloadStart = 14;
			int payloadLength = packet.getCaptureHeader().caplen() - payloadStart;
			NetPacket np = new NetPacket(packet.getByteArray(payloadStart, payloadLength));
			IDP idp = new IDP(np);
			PacketType packetType = idp.getPacketType();
			if (packetType == PacketType.SPP) {
				SPP spp = new SPP(idp);
				System.out.printf("%s\n", spp.toString());
				System.out.printf("payload: %s\n", spp.payloadToString());
			} else if (packetType == PacketType.ERROR) {
				Error err = new Error(idp);
				System.out.printf("%s\n", err.toString());
				System.out.printf("payload: %s\n", err.payloadToString());
			} else if (packetType == PacketType.PEX) {
				PEX pex = new PEX(idp);
				System.out.printf("%s\n", pex.toString());
				System.out.printf("payload: %s\n", pex.payloadToString());
			} else {
				System.out.printf("%s\n", np.toString());
				System.out.printf("payload: %s\n", np.payloadToString());
			}
		}
	};
}
