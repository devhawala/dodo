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

package dev.hawala.xns.level1;

import dev.hawala.xns.EndpointAddress;
import dev.hawala.xns.level0.NetPacket;
import dev.hawala.xns.level0.Payload;

/**
 * Representation of an Internet Datagram Protocol (IDP) packet.
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2016
 */
public class IDP extends Payload {
	
	public enum PacketType {
		
		RIP("Rip", 1),
		ECHO("Echo", 2),
		ERROR("Error", 3),
		PEX("PEX", 4),
		SPP("SPP", 5),
		
		BOOT_SERVER_PACKET("BootServerPacket", 9),
		
		PUP("PUP", 12),
		
		UNKNOWN("unknown", 0);
		
		private final String name;
		private final byte packetType;
		
		public String getName() { return this.name; }
		public byte getPacketTypeCode() { return this.packetType; }
		
		private PacketType(String name, int packetType) {
			this.name = name;
			this.packetType = (byte)(packetType & 0xFF);
		}
		
		public static PacketType get(byte code) {
			for (PacketType p : PacketType.values()) {
				if (p.getPacketTypeCode() == code) { return p; }
			}
			return PacketType.UNKNOWN;
		}
	}
	
	public enum KnownSocket {
		
		ROUTING(1),
		ECHO(2),
		ERROR(3),
		ENVOY(4), // whatever this is...
		COURIER(5),
		
		CLEARINGHOUSE_OLD(7),
		TIME(8),
		
		BOOT(10),
		
		DIAG(19),
		CLEARINGHOUSE(20), // BFSClearinghouse(20),     // Broadcast for servers / Clearinghouse
		AUTH(21),          // BFSAuthentication(21);    // Broadcast for servers / Authentication
		MAIL(22),
		NET_EXEC(23),
		WS_INFO(24),
		
		BINDING(28),
		
		GERM(35),
		
		TELEDEBUG(48)
		;
		
		private final int socketNumber;
		
		public int getSocket() { return this.socketNumber; }
		
		private KnownSocket(int socketNo) {
			this.socketNumber = socketNo;
		}
	}
	
	/** the broadcast address */
	public static final long BROADCAST_ADDR = 0x0000FFFFFFFFFFFFL;
	
	/** the broadcast ('any') network */
	public static final long ANY_NETWORK = 0x00000000FFFFFFFFL;
	
	/** the "local' network number */
	public static final long LOCAL_NETWORK = 0;
	
	/**
	 * the size of the IDP protocol specific data as packet header,
	 * so this is also the offset of the first data byte in an IDP payload.
	 */
	public static final int IDP_DATA_START = 30;
	
	/** the resulting maximal size of the IDP payload */
	public static final int MAX_PAYLOAD_SIZE = NetPacket.MAX_PACKET_SIZE - IDP_DATA_START;
	
	/** the special checksum value meaning 'no checksum' */
	public static final int NO_CHECKSUM = 0xFFFF;
	
	/** the raw packet containing the IDP packet */
	public final NetPacket packet;
	
	/** the net payload of the IDP packet (payload of {@code packet} less the IDP header) */
	// XXXX public final Payload payload;
	
	/** the raw payload of {@code packet} */ 
	// XXXX private final Payload rawPayload;
	
	// the checksum and length fields group of the IDP header
	private int checksum = NO_CHECKSUM;
	private int length = 0;
	
	// the transportControl and type fields group of the IDP header
	private byte transportControl = 0;
	private byte packetTypeCode = 0;
	
	// the destination identification fields group of the IDP header
	private long dstNetwork = 0;
	private long dstHost = 0;
	private int dstSocket = 0;

	// the source identification fields group of the IDP header
	private long srcNetwork = 0;
	private long srcHost = 0;
	private int srcSocket = 0;
	
	/**
	 * Create an empty internet datagram packet with minimal length and yet
	 * no IDP transport or payload content.
	 */
	public IDP() {
		super(new NetPacket(), IDP_DATA_START, NetPacket.MAX_PACKET_SIZE - IDP_DATA_START);
		this.packet = (NetPacket)this.basePayload;
		
		this.setPayloadLength(0);
		this.resetChecksum();
	}
	
	/**
	 * Create an internet datagram packet from an existing net packet, loading the
	 * transport IDP data from this packet. This constructor is primary intended
	 * to access the IDP transport information from an incoming raw packet.
	 *  
	 * @param packet containing the internet datagram packet.
	 */
	public IDP(NetPacket packet) {
		super(packet, IDP_DATA_START, packet.rdCardinal(2) - IDP_DATA_START);
		
		this.packet = packet;
		this.checksum = this.packet.rdCardinal(0);
		this.length = this.packet.rdCardinal(2);
		this.transportControl = this.packet.rdByte(4);
		this.packetTypeCode = this.packet.rdByte(5);
		this.dstNetwork = this.packet.rdLongCardinal(6);
		this.dstHost = this.packet.rdAddress(10);
		this.dstSocket = this.packet.rdCardinal(16);
		this.srcNetwork = this.packet.rdLongCardinal(18);
		this.srcHost = this.packet.rdAddress(22);
		this.srcSocket = this.packet.rdCardinal(28);
		
		super.setPayloadLength(Math.min(this.getPayloadLength(), this.length - IDP_DATA_START));
	}
	
	/**
	 * Create an internet datagram packet with blank transport data but
	 * with the given (IDP-) payload data.
	 * 
	 * @param sourcePayloadData the data to copy from the IDP payload.
	 * @param offset the start offset of the payload in {@code sourcePacketData}
	 * @param length the length of the payload in {@code sourcePacketData}
	 */
	public IDP(byte[] sourcePayloadData, int offset, int length) {
		this();
		int copiedLength = this.writePayload(sourcePayloadData, offset, length);
		this.setPayloadLength(copiedLength);
	}
	
	/**
	 * Create an internet datagram packet with blank transport data but
	 * with the given (IDP-) payload data.
	 * 
	 * @param sourcePayloadData the data to copy from the IDP payload.
	 */
	public IDP(byte[] sourcePayloadData) {
		this(sourcePayloadData, 0, sourcePayloadData.length);
	}
	
	/**
	 * Copy the specified area of a byte array into the payload of the IDP packet.
	 * 
	 * @param sourcePayloadData the source byte array for the payload content.
	 * @param offset the first byte position in {@code sourcePayloadData} from
	 *   where to copy the payload content.
	 * @param length the number of bytes to copy to the payload.
	 * @return the number of bytes copied ({@code length} but possibly reduced
	 *   to the space available in the payload).
	 */
	public int writePayload(byte[] sourcePayloadData, int offset, int length) {
		return this.packet.wrBytes(IDP_DATA_START, MAX_PAYLOAD_SIZE, sourcePayloadData, offset, length);
	}
	
	/**
	 * Get the current checksum value of the IDP packet.
	 * 
	 * @return the last (explicitly) computed checksum or {@code NO_CHECKSUM}
	 *   if the checksum was not yet computed or has been reset.
	 */
	public int getChecksum() {
		return checksum;
	}
	
	/**
	 * Clear the checksum by setting it to {@code NO_CHECKSUM}.
	 * 
	 * @return this IDP instance (for command chaining).
	 */
	public IDP resetChecksum() {
		this.checksum = NO_CHECKSUM;
		this.packet.wrCardinal(0, this.checksum);
		return this;
	}
	
	/**
	 * Recompute the checksum of this IDP packet. 
	 * 
	 * @return this IDP instance (for command chaining).
	 */
	public IDP computeChecksum_A() {
		long newChecksum = 0;
		int wordCount = (this.length + 1) / 2;
		for (int i = 1; i < wordCount; i++) {
			newChecksum += this.packet.rdCardinal(i << 1);
			newChecksum <<= 1;
		}
		
		this.checksum = (int)(newChecksum & 0xFFFF);
		this.packet.wrCardinal(0, this.checksum);
		
		return this;
	}
	public IDP computeChecksum_B() {
		long newChecksum = 0;
		int wordCount = (this.length + 1) / 2;
		for (int i = 1; i < wordCount; i++) {
			newChecksum += this.packet.rdCardinal(i << 1);
			newChecksum <<= 1;
			if (newChecksum > 0xFFFF) {
				newChecksum += 1;
			}
			newChecksum &= 0xFFFF;
		}
		
		this.checksum = (int)(newChecksum & 0xFFFF);
		this.packet.wrCardinal(0, this.checksum);
		
		return this;
	}
	public IDP computeChecksum_C() {
		long newChecksum = 0;
		int limit = ((length % 2) != 0) ? length + 1 : length;
		for (int i = 2; i < limit; i++) {
			newChecksum += this.packet.rdByte(i) & 0xFF;
			newChecksum <<= 1;
			if (newChecksum > 0xFFFF) {
				newChecksum += 1;
			}
			newChecksum &= 0xFFFF;
		}
		
		this.checksum = (int)(newChecksum & 0xFFFF);
		this.packet.wrCardinal(0, this.checksum);
		
		return this;
	}
	
	public IDP computeChecksum_D() {
		long newChecksum = 0;
		int limit = ((length % 2) != 0) ? length + 1 : length;
		for (int i = 2; i < limit; i++) {
			newChecksum <<= 1;
			newChecksum += this.packet.rdByte(i) & 0xFF;
			if (newChecksum > 0xFFFF) {
				newChecksum += 1;
			}
			newChecksum &= 0xFFFF;
		}
		
		this.checksum = (int)(newChecksum & 0xFFFF);
		this.packet.wrCardinal(0, this.checksum);
		
		return this;
	}
	
	public IDP computeChecksum_E() {
		int newChecksum = 0;
		int limit = ((length % 2) != 0) ? length + 1 : length;
		for (int i = 0; i < limit; i++) {
			int b1 = this.packet.rdByte(i++) & 0xFF;
			int b2 = this.packet.rdByte(i) & 0xFF;
			int w = (b1 << 8) | b2;
			int tmp = newChecksum + w;
			if (tmp > 0xFFFF) { tmp += 1;}
			if ((tmp & 0x8000) != 0) {
				newChecksum = (tmp << 1) + 1;
			} else {
				newChecksum = tmp << 1;
			}
			newChecksum &= 0xFFFF;
		}

		this.checksum = newChecksum;
		this.packet.wrCardinal(0, this.checksum);
		
		return this;
	}
	
	/**
	 * Compute checksum for the current IDP content (xns payload
	 * except the checksum word), based on the CKSUM instruction.
	 * 
	 * @return this packet.
	 */
	public IDP updateChecksum() {		
		this.checksum = this.computeChecksum();
		this.packet.wrCardinal(0, this.checksum);
		return this;
	}
	
	public int computeChecksum() {
		int cksum = 0;
		int limit = ((length % 2) != 0) ? length + 1 : length;
		for (int i = 2; i < limit; i++) {
			int b1 = this.packet.rdByte(i++) & 0xFF;
			int b2 = this.packet.rdByte(i) & 0xFF;
			int w = (b1 << 8) | b2;
			cksum = checksum(cksum, w);
		}
		
		if (cksum == 0177777) { cksum = 0; }
		return cksum;
	}
	
	private static int checksum(int cksum, int data) {
		int temp = (cksum + (data & 0xFFFF)) & 0xFFFF;
		if (cksum > temp) { temp = temp + 1; }
		if (temp >= 0100000) {
			temp = (temp * 2) + 1;
		} else {
			temp = temp * 2;
		}
		return temp & 0xFFFF;
	}
	
	/**
	 * Get the IDP length header value last specified for this IDP packet. 
	 * 
	 * @return the current packet length value.
	 */
	public int getLength() {
		return length;
	}
	
	/**
	 * Set the IDP length header value for this IDP packet, also setting
	 * the {@code payloadLength} value of the underlying (wrapped)
	 * {@link NetPacket}. 
	 * 
	 * @param length the new length-field value (i.e. for the whole
	 *   packet, meaning this is NOT the length of the IDP-payload!).
	 */
	public void setPacketLength(int length) {
		this.length = Math.min(this.packet.getMaxPayloadLength(), Math.max(IDP_DATA_START, length));
		super.setPayloadLength(this.length - IDP_DATA_START);
		this.packet.wrCardinal(2, this.length);
	}
	
	@Override
	public void setPayloadLength(int newLength) {
		this.setPacketLength(newLength + IDP_DATA_START);
	}
	
	/**
	 * Get the transportControl header value of the IDP packet.
	 * 
	 * @return the current transportControl header value.
	 */
	public byte getTransportControl() {
		return transportControl;
	}
	
	/**
	 * Set the transportControl header value of the IDP packet.
	 * 
	 * @param transportControl the new transportControl header value.
	 */
	public void setTransportControl(byte transportControl) {
		this.transportControl = transportControl;
		this.packet.wrByte(4, transportControl);
	}
	
	/**
	 * Get the packet type field value of the IDP packet.
	 * 
	 * @return the current packet type field value. 
	 */
	public byte getPacketTypeCode() {
		return this.packetTypeCode;
	}
	
	/**
	 * Get the packet type value of the IDP packet.
	 * 
	 * @return the current packet type if valid or {@link PacketType.UNKNOWN}.
	 */
	public PacketType getPacketType() {
		return PacketType.get(this.packetTypeCode);
	}
	
	/**
	 * Set the packet type field value of the IDP packet.
	 * 
	 * @param packetType the new packet type field value.
	 */
	public void setPacketTypeCode(byte packetType) {
		this.packetTypeCode = packetType;
		this.packet.wrByte(5, packetType);
	}
	
	/**
	 * Set the packet type field value of the IDP packet.
	 * 
	 * @param packetType the new packet type field value.
	 */
	public void setPacketType(PacketType packetType) {
		this.setPacketTypeCode(packetType.getPacketTypeCode());
	}
	
	/**
	 * Get the destination network identification of this IDP packet.
	 * 
	 * @return the (unsigned) 16-bit destination network identification.
	 */
	public long getDstNetwork() {
		return dstNetwork;
	}
	
	/**
	 * Set the destination network identification of this IDP packet.
	 * 
	 * @param dstNetwork the new (unsigned) 16-bit destination network
	 *   identification of this IDP packet.
	 */
	public void setDstNetwork(long dstNetwork) {
		this.dstNetwork = dstNetwork;
		this.packet.wrLongCardinal(6, dstNetwork);
	}
	
	/**
	 * Get the destination host identification of this IDP packet.
	 * 
	 * @return the (unsigned) 48-bit destination host identification.
	 */
	public long getDstHost() {
		return dstHost;
	}
	
	/**
	 * Set the destination host identification of this IDP packet.
	 * 
	 * @param dstHost the new (unsigned) 48-bit destination host
	 *   identification of this IDP packet.
	 */
	public void setDstHost(long dstHost) {
		this.dstHost = dstHost;
		this.packet.wrAddress(10, dstHost);
	}
	
	/**
	 * Get the destination socket number of this IDP packet.
	 * 
	 * @return the (unsigned) 16-bit destination socket number.
	 */
	public int getDstSocket() {
		return dstSocket;
	}
	
	/**
	 * Set the destination socket number of this IDP packet.
	 * 
	 * @param dstSocket the new (unsigned) 16-bit destination socket
	 *   number of this IDP packet.
	 */
	public void setDstSocket(int dstSocket) {
		this.dstSocket = dstSocket;
		this.packet.wrCardinal(16, dstSocket);
	}
	
	/**
	 * Get the destination endpoint of this IDP packet;
	 * 
	 * @return the destination endpoint information;
	 */
	public EndpointAddress getDstEndpoint() {
		return new EndpointAddress(this.getDstNetwork(), this.getDstHost(), this.getDstSocket());
	}
	
	/**
	 * Get the source network identification of this IDP packet.
	 * 
	 * @return the (unsigned) 16-bit source network identification.
	 */
	public long getSrcNetwork() {
		return srcNetwork;
	}
	
	/**
	 * Set the source network identification of this IDP packet.
	 * 
	 * @param dstNetwork the new (unsigned) 16-bit source network
	 *   identification of this IDP packet.
	 */
	public void setSrcNetwork(long srcNetwork) {
		this.srcNetwork = srcNetwork;
		this.packet.wrLongCardinal(18, srcNetwork);
	}
	
	/**
	 * Get the source host identification of this IDP packet.
	 * 
	 * @return the (unsigned) 48-bit source host identification.
	 */
	public long getSrcHost() {
		return srcHost;
	}
	
	/**
	 * Set the source host identification of this IDP packet.
	 * 
	 * @param dstHost the new (unsigned) 48-bit source host
	 *   identification of this IDP packet.
	 */
	public void setSrcHost(long srcHost) {
		this.srcHost = srcHost;
		this.packet.wrAddress(22, srcHost);
	}
	
	/**
	 * Get the source socket number of this IDP packet.
	 * 
	 * @return the (unsigned) 16-bit source socket number.
	 */
	public int getSrcSocket() {
		return srcSocket;
	}
	
	/**
	 * Set the source socket number of this IDP packet.
	 * 
	 * @param dstSocket the new (unsigned) 16-bit source socket
	 *   number of this IDP packet.
	 */
	public void setSrcSocket(int srcSocket) {
		this.srcSocket = srcSocket;
		this.packet.wrCardinal(28, srcSocket);
	}
	
	/**
	 * Get the source endpoint of this IDP packet;
	 * 
	 * @return the source endpoint information;
	 */
	public EndpointAddress getSrcEndpoint() {
		return new EndpointAddress(this.getSrcNetwork(), this.getSrcHost(), this.getSrcSocket());
	}
	
	/**
	 * Set the 3 destination identification fields for this IDP packet.
	 * 
	 * @param dstNetwork the new (unsigned) 16-bit destination network
	 *   identification of this IDP packet.
	 * @param dstHost  the new (unsigned) 48-bit destination host
	 *   identification of this IDP packet.
	 * @param dstSocket the new (unsigned) 16-bit destination socket
	 *   number of this IDP packet.
	 * @return this IDP packet instance (for command chaining).
	 */
	public IDP withDestination(long dstNetwork, long dstHost, int dstSocket) {
		this.setDstNetwork(dstNetwork);
		this.setDstHost(dstHost);
		this.setDstSocket(dstSocket);
		return this;
	}
	
	/**
	 * Set the 3 destination identification fields for this IDP packet.
	 * 
	 * @param dst the destination endpoint.
	 * @return this IDP packet instance (for command chaining).
	 */
	public IDP withDestination(EndpointAddress dst) {
		this.setDstNetwork(dst.network);
		this.setDstHost(dst.host);
		this.setDstSocket(dst.socket);
		return this;
	}
	
	/**
	 * Set the 3 source identification fields for this IDP packet.
	 * 
	 * @param dstNetwork the new (unsigned) 16-bit source network
	 *   identification of this IDP packet.
	 * @param dstHost  the new (unsigned) 48-bit source host
	 *   identification of this IDP packet.
	 * @param dstSocket the new (unsigned) 16-bit source socket
	 *   number of this IDP packet.
	 * @return this IDP packet instance (for command chaining).
	 */
	public IDP withSource(long srcNetwork, long srcHost, int srcSocket) {
		this.setSrcNetwork(srcNetwork);
		this.setSrcHost(srcHost);
		this.setSrcSocket(srcSocket);
		return this;
	}
	
	/**
	 * Set the 3 source identification fields for this IDP packet.
	 * 
	 * @param dst the source endpoint.
	 * @return this IDP packet instance (for command chaining).
	 */
	public IDP withSource(EndpointAddress src) {
		this.setSrcNetwork(src.network);
		this.setSrcHost(src.host);
		this.setSrcSocket(src.socket);
		return this;
	}
	
	/**
	 * Set the source and destination identification triples of this IDP packet
	 * for a reply to the given IDP packet by copying the destination and
	 * source fields respectively.
	 * 
	 * @param sender the IDP packet for which this packet is intended to reply. 
	 * @return this IDP packet instance (for command chaining).
	 */
	public IDP asReplyTo(IDP sender) {
		return this
				.withDestination(sender.getSrcNetwork(), sender.getSrcHost(), sender.getSrcSocket())
				.withSource(sender.getDstNetwork(), sender.getDstHost(), sender.getDstSocket());
	}
	
	private String fmtSrcEndpoint() {
		return String.format("src: %04X.%06X.%04X", this.getSrcNetwork(), this.getSrcHost(), this.getSrcSocket());
	}
	
	private String fmtDstEndpoint() {
		return String.format("dst: %04X.%06X.%04X", this.getDstNetwork(), this.getDstHost(), this.getDstSocket());
	}
	
//	private String toHex2(int value) {
//		return String.format("%02X", value);
//	}
	
	private String toHex4(int value) {
		return String.format("%04X", value);
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder()
			.append("IDP[ ")
			.append("chksum: 0x").append(this.toHex4(this.checksum)).append(" ")
			.append("len: ").append(this.length).append(" ")
			.append("transportControl: ").append(this.transportControl).append(" ")
			.append("packetType: ").append(this.packetTypeCode).append(" [").append(this.getPacketType().getName()).append("] ")
			.append(this.fmtDstEndpoint()).append(" ")
			.append(this.fmtSrcEndpoint()).append(" ")
			.append(" ]")
			;
		return sb.toString();
	}
	
	@Override
	public long getPacketId() {
		return this.packet.getPacketId();
	}
}
