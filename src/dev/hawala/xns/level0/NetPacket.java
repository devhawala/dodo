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

package dev.hawala.xns.level0;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Most basic kind of network packet providing access to components of
 * the payload encompassing the whole packet.
 * <p>
 * This packet type does not provide any internal representations of
 * a protocol substructures: these "higher-level" structures are provided
 * by other classes wrapping a {@link NetPacket}.
 * </p>
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2016
 */
public class NetPacket extends Payload {
	
	private static final AtomicLong packetIds = new AtomicLong();

	// rationale: an internet datagram packet has max. 546 bytes payload + 30 bytes header
	public static final int MAX_PACKET_SIZE = 576;
	
	/** the payload of this packet, i.e. the raw data in the packet. */
	// XXXX public final Payload payload;
	
	// the currently assigned "nominal" length of the packet as it was (input) or will be
	// (output) transmitted
	// XXXX private int packetLength = 0;
	
	private final long packetId = packetIds.incrementAndGet();
	
	/**
	 * Construct an empty packet.
	 */
	public NetPacket() {
		super(MAX_PACKET_SIZE);
	}
	
	/**
	 * Construct a packet using the passed source data as packet content.
	 * 
	 * @param sourcePacketData the source data from where to copy the packets raw payload.
	 * @param offset position of the first packet content byte in {@code sourcePacketData}
	 * @param length byte count of the packet content byte in {@code sourcePacketData}
	 */
	public NetPacket(byte[] sourcePacketData, int offset, int length) {
		super(MAX_PACKET_SIZE);
		this.setPayloadLength(this.wrBytes(0, MAX_PACKET_SIZE, sourcePacketData, offset, length));
	}
	
	/**
	 * Construct a packet using the passed source data as packet content.
	 * 
	 * @param sourcePacketData the source data from where to copy the packets raw payload.
	 */
	public NetPacket(byte[] sourcePacketData) {
		this(sourcePacketData, 0, sourcePacketData.length);
	}
	
	/**
	 * Create a packet from an other packet, sharing the same packet content data as the
	 * other packet, but only using a subset of its packet content.
	 * <p>
	 * As both packets share their content, modifying one packets data in the common
	 * (shared) portion modifies both packets. 
	 * </p>
	 * 
	 * @param source the source packet from which to reuse the content.
	 * @param basePosition the first content byte of the source packet to be part of this new packet.
	 * @param visibleLength the amount of bytes to share with the source packet.
	 */
	public NetPacket(NetPacket source, int basePosition, int visibleLength) {
		super(source, basePosition, visibleLength);
		if (basePosition < 0) {
			throw new IllegalArgumentException("Invalid negative basePosition for NetPacket(NetPacket source, int basePosition, int visibleLength)");
		}
		if (visibleLength < 1) {
			throw new IllegalArgumentException("Invalid visibleLength for NetPacket(NetPacket source, int basePosition, int visibleLength)");
		}
	}
	
	/**
	 * Create a packet from an other packet, sharing the same packet content data as the
	 * other packet, but only using the subset of its packet content starting at the given
	 * position.
	 * <p>
	 * As both packets share their content, modifying one packets data in the common
	 * (shared) portion modifies both packets. 
	 * </p>
	 * 
	 * @param source the source packet from which to reuse the content.
	 * @param basePosition the first content byte of the source packet to be part of this new packet,
	 *   with the shared packet content encompassing the rest of the source packet.
	 */
	public NetPacket(NetPacket source, int basePosition) {
		this(source, basePosition, MAX_PACKET_SIZE);
	}
	
	/**
	 * Create a packet for an existing (raw) payload.
	 * @param payload
	 */
	public NetPacket(Payload payload) {
		super(payload, 0);
	}
	
	/**
	 * Set the content length of the packet for later transport.
	 * <p>
	 * <b>ATTENTION</b>: when altering the packets content, the packet length
	 * must always be explicitly set, as modifying the payload (or its sub-payloads)
	 * has no effect on the packet itself.
	 * </p>
	 * 
	 * @param newLength the new packet length, which will be reduces by the base position
	 *   of this packet.
	 */
	@Override
	public void setPayloadLength(int newLength) {
		int packetLength = ((newLength % 2) != 0) ? newLength + 1 : newLength;
		super.setPayloadLength(packetLength);
	}
	
	/**
	 * Copy the content and the packet length of the {@code other} packet into this packet,
	 * reducing the length and the byte amount copied according to the respective payload base
	 * and sizes.
	 * 
	 * @param other the packet from which to copy payload data and packet length.
	 * @return this packet (for command chaining)
	 */
	public NetPacket copy(NetPacket other) {
		this.setPayloadLength(Math.min(other.getPayloadLength(), super.copy(other)));
		return this;
	}
	
	@Override
	public long getPacketId() {
		return this.packetId;
	}
}
