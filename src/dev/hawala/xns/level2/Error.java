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

import dev.hawala.xns.level0.NetPacket;
import dev.hawala.xns.level0.Payload;
import dev.hawala.xns.level1.IDP;
import dev.hawala.xns.level1.IDP.PacketType;

/**
 * Representation of an Error Protocol packet. 
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2016
 */
public class Error extends Payload {

	/** Known error codes for the XNS Error protocol */
	public enum ErrorCode {
		
		/** Unspecified Error detected at destination */
		UNSPECIFIED(0),
		
		/** Bad Checksum detected at destination */
		BAD_CHECKSUM(1),
		
		/** Specified socket does not exist at destination */
		NO_SOCKET(2),
		
		/** Destination refuses packet due to resource limitation */
		RESOURCE_LIMIT(3),
		
		/** Listener rejected this packet */
		LISTEN_REJECT(4),
		
		/** Packet type unknown / unsupported */
		INVALID_PACKET_TYPE(5),
		
		/** Protocol error */
		PROTOCOL_VIOLATION(6),
		
		/** Unspecified error occured before reaching destination */
		UNSPECIFIED_IN_ROUTE(0x200),
		
		/** Bad Checksum detected in transit */
		BADSUM_TRANSIT(0x201),
		
		/** Destination cannot be reached from here */
		UNREACHABLE_HOST(0x202),
		
		/** Packet passed 15 routers without delivery */
		TOO_OLD(0x203),
		
		/**
		 * Packet too large to be forwarded through some intermediate gateway.
		 * The error parameter field contains the max packet size that can
		 * be accommodated
		 */
		TOO_BIG(0x204),
		
		/** growing backlog at some bottleneck, packed dropped */
		CONGESTION_WARNING(0x205),
		
		/** packet dropped at bottle neck */
		CONGESTION_DISCARD(0x0206)
		;
		
		private final int number;
		
		/**
		 * Get the numeric value of the error.
		 *  
		 * @return the (unsigned) 16-bit value of the error code.
		 */
		public int getNumber() {
			return this.number;
		}
		
		private ErrorCode(int number) {
			this.number = number;
		}
		
		/**
		 * find the {@link ErrorCode} for an error number.
		 *  
		 * @param num the error number to look up.
		 * @return the {@link ErrorCode} having the given numeric value
		 *   or {@code UNSPEC} if no code has this error number.
		 */
		public static ErrorCode forNumber(int num) {
			for (ErrorCode code : ErrorCode.values()) {
				if (code.getNumber() == num) {
					return code;
				}
			}
			return ErrorCode.UNSPECIFIED;
		}
	}
	
	public final static int ERROR_DATA_START = 4;
	
	private final static int OFFENDING_PACKET_COPYLENGTH = 96;

	public final IDP idp;
	
	private final ErrorCode errorCode;
	
	private final int errorParam;
	
	private final NetPacket offendingPacket;
	
	private final IDP offendingIdpPacket;
	
	/**
	 * Create the error packet wrapping a received IDP packet of
	 * type ERROR. 
	 * 
	 * @param source the IDP packet of type ERROR to be reinterpreted
	 * 		as error packet.
	 */
	public Error(IDP source) {
		super(source, ERROR_DATA_START, source.getLength() - ERROR_DATA_START);
		
		this.idp = source;
		this.offendingPacket = new NetPacket(this.idp.packet, IDP.IDP_DATA_START + ERROR_DATA_START);
		this.offendingIdpPacket = new IDP(this.offendingPacket);
		this.errorCode = ErrorCode.forNumber(this.idp.rdCardinal(0));
		this.errorParam = this.idp.rdCardinal(2);
	}
	
	/**
	 * Create an error reply packet for an offending IDP packet, setting
	 * the error payload and the source/destination addresses for replying
	 * based on the given offending packet.
	 * 
	 * @param code the error code for the error packet
	 * @param param the additional information parameter for the error code
	 * @param forIdp the offending IDP for which the error was recognized, 
	 */
	public Error(ErrorCode code, int param, IDP forIdp) {
		// create the Error packet proper
		super(new IDP(), ERROR_DATA_START, IDP.MAX_PAYLOAD_SIZE - ERROR_DATA_START);
		this.idp = (IDP)this.basePayload;
		
		this.idp.setPacketTypeCode(PacketType.ERROR.getPacketTypeCode());
		
		// create a copy of 'forIdp' inside out payload,
		// putting the first OFFENDING_PACKET_COPYLENGTH bytes of the offending packet, 42 bytes is the minimum :-)
		this.offendingPacket = new NetPacket(this);
		int copiedLength = this.offendingPacket.copy(forIdp.packet, OFFENDING_PACKET_COPYLENGTH);
		this.offendingIdpPacket = new IDP(this.offendingPacket); // this will scramble this packets length as it will interpret the length-field in the offending packet 
		this.setPayloadLength(copiedLength);
		
		// set the error information
		this.errorCode = code;
		this.idp.wrCardinal(0, this.errorCode.getNumber());
		this.errorParam = param;
		this.idp.wrCardinal(2, this.errorParam);
		
		// fill the source / destination fields
		this.idp.asReplyTo(forIdp);
		this.idp.setDstSocket(IDP.KnownSocket.ERROR.getSocket());
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}

	public int getErrorParam() {
		return errorParam;
	}

	public IDP getOffendingIdpPaket() {
		return this.offendingIdpPacket;
	}
	
	@Override
	public long getPacketId() {
		return this.idp.getPacketId();
	}
	
}
