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
 * Representation of a Packet EXchange protocol (PEX) packet.
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2016
 */
public class PEX extends Payload {
	
	public enum ClientType {
		UNSPECIFIED(0),
		TIME(1),
		CLEARINGHOUSE(2),
		TELEDEBUG(8);
		
		private final int typeValue;
		
		private ClientType(int typeValue) {
			this.typeValue = typeValue;
		}
		
		public int getTypeValue() {
			return this.typeValue;
		}
	}
	
	/**
	 * the size of the PEX protocol specific data as packet header,
	 * so this is also the offset of the first data byte in a PEX payload.
	 */
	public final static int PEX_DATA_START = 6;
	
	/** the max. payload length in bytes that a PEX packet can transport. */
	public final static int PEX_MAX_PAYLOAD_SIZE = IDP.MAX_PAYLOAD_SIZE - PEX_DATA_START;
	
	/** the IDP packet containing this SPP packet */
	public final IDP idp;
	
	// identification header fields
	private long identification = 0;
	
	// client-type header fields
	private int clientType = 0;
	
	public PEX() {
		super(new IDP(), PEX_DATA_START, IDP.MAX_PAYLOAD_SIZE - PEX_DATA_START);
		this.idp = (IDP)this.basePayload;
		
		this.idp.setPacketTypeCode(PacketType.PEX.getPacketTypeCode());
	}
	
	public PEX(IDP idp) {
		super(idp, PEX_DATA_START, idp.getLength() - PEX_DATA_START);
		this.idp = idp;
		
		this.identification = this.idp.rdLongCardinal(0);
		this.clientType = this.idp.rdCardinal(4);
	}
	
	public PEX(byte[] sourcePacketData, int offset, int length) {
		this();
		int copiedBytes = this.wrBytes(0, this.getMaxPayloadLength(), sourcePacketData, offset, length);
		this.setPayloadLength(copiedBytes);
	}
	
	public PEX(byte[] sourcePacketData) {
		this(sourcePacketData, 0, sourcePacketData.length);
	}

	public long getIdentification() {
		return identification;
	}

	public void setIdentification(long identification) {
		this.identification = identification;
		this.idp.wrLongCardinal(0, this.identification);
	}

	public int getClientType() {
		return clientType;
	}

	public void setClientType(int clientType) {
		this.clientType = clientType;
		this.idp.wrCardinal(4, this.clientType);
	}

	private String toHex8(long value) {
		return String.format("%08X", value);
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder()
			.append(this.idp.toString())
			.append("\n")
			.append("PEX[ id: 0x").append(this.toHex8(this.identification)).append(" ")
			.append("clienttype: ").append(this.clientType).append(" ]");
		return sb.toString();
	}
	
	@Override
	public long getPacketId() {
		return this.idp.getPacketId();
	}
	
}
