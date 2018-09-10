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

package dev.hawala.xns.level2;

import dev.hawala.xns.level0.Payload;
import dev.hawala.xns.level1.IDP;
import dev.hawala.xns.level1.IDP.PacketType;

/**
 * Representation of a Sequenced Packet Protocol (SPP) packet.
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2016
 */
public class SPP extends Payload {
	
	/**
	 * the size of the SPP protocol specific data as packet header,
	 * so this is also the offset of the first data byte in a SPP payload.
	 */
	public final static int SPP_DATA_START = 12;
	
	/** the maximum payload length of a single SPP packet */
	public final static int SPP_MAX_PAYLOAD_LENGTH = IDP.MAX_PAYLOAD_SIZE - SPP_DATA_START;
	
	/** the IDP packet containing this SPP packet */
	public final IDP idp;
	
	/** connection control flag: system-packet */
	public final static byte SPP_SYSTEMPACKET = (byte)0x80;
	
	/** connection control flag: send-acknowledge */
	public final static byte SPP_SENDACKNOWLEDGE = (byte)0x40;
	
	/** connection control flag: attention */
	public final static byte SPP_ATTENTION = (byte)0x20;
	
	/** connection control flag: end-of-message */
	public final static byte SPP_ENDOFMESSAGE = (byte)0x10;
	
	// the connection control and datastream type fields group of the SPP header
	private byte connectionControl = 0;
	private byte datastreamType = 0;
	
	// the connection id fields group of the SPP header
	private int srcConnectionId = 0;
	private int dstConnectionId = 0;
	
	// the sequence number of this SPP packet in the outgoing packet stream
	private int sequenceNumber = 0;
	
	// the flow control fields group of the SPP header
	private int acknowledgeNumber = 0;
	private int allocationNumber = 0;
	
	/**
	 * Construct a new SPP packet with blank header fields (SPP and IDP)
	 * and payload.
	 */
	public SPP() {
		super(new IDP(), SPP_DATA_START, IDP.MAX_PAYLOAD_SIZE - SPP_DATA_START);
		this.idp = (IDP)this.basePayload;
		this.idp.setPacketType(PacketType.SPP);
	}
	
	/**
	 * Create a SPP packet from an existing IDP packet, loading the
	 * SPP header data from this packet. This constructor is primary intended
	 * to access the SPP transport information from an incoming packet (as IDP
	 * packet already wrapping a raw packet).
	 *  
	 * @param idp the internet datagram packet to be wrapped.
	 */
	public SPP(IDP idp) {
		super(idp, SPP_DATA_START, idp.getLength() - SPP_DATA_START);
		this.idp = idp;
		
		this.connectionControl = this.idp.rdByte(0);
		this.datastreamType = this.idp.rdByte(1);
		this.srcConnectionId = this.idp.rdCardinal(2);
		this.dstConnectionId = this.idp.rdCardinal(4);
		this.sequenceNumber = this.idp.rdCardinal(6);
		this.acknowledgeNumber = this.idp.rdCardinal(8);
		this.allocationNumber = this.idp.rdCardinal(10);
	}
	
	/**
	 * Create a SPP packet with blank header data but with the given
	 * (SPP-) payload data.
	 * 
	 * @param sourcePayloadData the data to copy from the SPP payload.
	 * @param offset the start offset of the payload in {@code sourcePacketData}
	 * @param length the length of the payload in {@code sourcePacketData}
	 */
	public SPP(byte[] sourcePacketData, int offset, int length) {
		this();
		int copiedLength = this.wrBytes(0, SPP.SPP_MAX_PAYLOAD_LENGTH, sourcePacketData, offset, length);
		this.setPayloadLength(copiedLength);
	}
	
	/**
	 * Create a SPP packet with blank header data but with the given
	 * (SPP-) payload data.
	 * 
	 * @param sourcePayloadData the data to copy from the SPP payload.
	 */
	public SPP(byte[] sourcePacketData) {
		this(sourcePacketData, 0, sourcePacketData.length);
	}

	/**
	 * Get the connection control flags of this SPP packet.
	 * 
	 * @return the current connection control flags.
	 */
	public byte getConnectionControl() {
		return connectionControl;
	}

	/**
	 * Set the connection control flags of this SPP packet.
	 * 
	 * @param connectionControl the new connection control flags.
	 */
	public SPP setConnectionControl(byte connectionControl) {
		this.connectionControl = connectionControl;
		this.idp.wrByte(0, connectionControl);
		return this;
	}
	
	public boolean isSystemPacket() {
		return (this.connectionControl & SPP_SYSTEMPACKET) != 0;
	}
	
	public SPP asSystemPacket() {
		this.setPayloadLength(0);
		return this.setConnectionControl((byte)(this.connectionControl | SPP_SYSTEMPACKET));
	}
	
	public boolean isAttention() {
		return (this.connectionControl & SPP_ATTENTION) != 0;
	}
	
	public SPP asAttention() {
		return this.setConnectionControl((byte)(this.connectionControl | SPP_ATTENTION));
	}
	
	public boolean isSendAcknowledge() {
		return (this.connectionControl & SPP_SENDACKNOWLEDGE) != 0;
	}
	
	public SPP asSendAcknowledge() {
		return this.setConnectionControl((byte)(this.connectionControl | SPP_SENDACKNOWLEDGE));
	}
	
	public boolean isEndOfMessage() {
		return (this.connectionControl & SPP_ENDOFMESSAGE) != 0;
	}
	
	public SPP asEndOfMessage() {
		return this.setConnectionControl((byte)(this.connectionControl | SPP_ENDOFMESSAGE));
	}

	/**
	 * get the data-stream-type header field of this SPP packet.
	 * 
	 * @return the data-stream-type header field.
	 */
	public byte getDatastreamType() {
		return datastreamType;
	}

	/**
	 * Set the data-stream-type header field of this SPP packet.
	 * 
	 * @param connectionControl the new data-stream-type header field value.
	 */
	public SPP setDatastreamType(byte datastreamType) {
		this.datastreamType = datastreamType;
		this.idp.wrByte(1, datastreamType);
		return this;
	}

	/**
	 * Get the source connection id field of this SPP packet.
	 * 
	 * @return the source connection id field
	 */
	public int getSrcConnectionId() {
		return srcConnectionId;
	}

	/**
	 * Set the source connection id field of this SPP packet.
	 * 
	 * @param srcConnectionId the new source connection id field value.
	 */
	public SPP setSrcConnectionId(int srcConnectionId) {
		this.srcConnectionId = srcConnectionId;
		this.idp.wrCardinal(2, srcConnectionId);
		return this;
	}

	/**
	 * Get the destination connection id field of this SPP packet.
	 * 
	 * @return the destination connection id field
	 */
	public int getDstConnectionId() {
		return dstConnectionId;
	}

	/**
	 * Set the destination connection id field of this SPP packet.
	 * 
	 * @param dstConnectionId the new destination connection id field value.
	 */
	public SPP setDstConnectionId(int dstConnectionId) {
		this.dstConnectionId = dstConnectionId;
		this.idp.wrCardinal(4, dstConnectionId);
		return this;
	}

	/**
	 * Get the sequence number of this SPP packet in the outgoing packet stream.
	 * 
	 * @return the sequence number of this SPP packet.
	 */
	public int getSequenceNumber() {
		return sequenceNumber;
	}

	/**
	 * Set the sequence number of this SPP packet in the packet stream.
	 * 
	 * @param sequenceNumber the new sequence number for this SPP packet.
	 */
	public SPP setSequenceNumber(int sequenceNumber) {
		this.sequenceNumber = sequenceNumber;
		this.idp.wrCardinal(6, sequenceNumber);
		return this;
	}

	/**
	 * Get the acknowledge number header field of this SPP packet.
	 * 
	 * @return the acknowledge number.
	 */
	public int getAcknowledgeNumber() {
		return acknowledgeNumber;
	}

	/**
	 * Set the acknowledge number header field of this SPP packet.
	 * 
	 * @param acknowledgeNumber the new acknowledge number.
	 */
	public SPP setAcknowledgeNumber(int acknowledgeNumber) {
		this.acknowledgeNumber = acknowledgeNumber;
		this.idp.wrCardinal(8, acknowledgeNumber);
		return this;
	}

	/**
	 * Get the allocation number header field of this SPP packet.
	 * 
	 * @return the allocation number.
	 */
	public int getAllocationNumber() {
		return allocationNumber;
	}

	/**
	 * Set the allocation number header field of this SPP packet.
	 * 
	 * @param allocationNumber the new allocation number.
	 */
	public SPP setAllocationNumber(int allocationNumber) {
		this.allocationNumber = allocationNumber;
		this.idp.wrCardinal(10, allocationNumber);
		return this;
	}

	private String toHex2(long value) {
		return String.format("%02X", value & 0xFF);
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder()
			.append(this.idp.toString())
			.append("\n")
			.append("SPP[ connectioncontrol: 0x").append(this.toHex2(this.connectionControl))
				.append(" [")
				.append(this.isSystemPacket() ? " SysPacket" : "")
				.append(this.isSendAcknowledge() ? " SendAck" : "")
				.append(this.isEndOfMessage() ? " EndOfMsg" : "")
				.append(this.isAttention() ? " Attention" : "")
				.append(" ]")
			.append(" streamtype: 0x").append(this.toHex2(this.datastreamType))
			.append(" srcConnId: ").append(this.srcConnectionId)
			.append(" dstConnId: ").append(this.dstConnectionId)
			.append(" seqNo: ").append(this.sequenceNumber)
			.append(" ackNo: ").append(this.acknowledgeNumber)
			.append(" allocNo: ").append(this.allocationNumber)
			.append(" ]");
		return sb.toString();
	}
	
	@Override
	public long getPacketId() {
		return this.idp.getPacketId();
	}
	
}
