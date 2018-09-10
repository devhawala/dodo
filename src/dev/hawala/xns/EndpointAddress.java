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

/**
 * Representation of an XNS address with 3 components: network (32 bit),
 * host (48 bit) and socket (16 bit);
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2016-2018
 *
 */
public class EndpointAddress {
	
	public final long network;
	public final long host;
	public final int socket;
	
	public EndpointAddress(long network, long host, int socket) {
		this.network = network & 0x00000000FFFFFFFFL;
		this.host = host & 0x0000FFFFFFFFFFFFL;
		this.socket = socket & 0x0000FFFF;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (host ^ (host >>> 32));
		result = prime * result + (int) (network ^ (network >>> 32));
		result = prime * result + socket;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) { return true; }
		if (obj == null) { return false; }
		if (this.getClass() != obj.getClass()) { return false; }
		
		EndpointAddress other = (EndpointAddress) obj;
		return this.host == other.host
			   && this.network == other.network
			   && this.socket == other.socket;
	}

	@Override
	public String toString() {
		return this.string();
	}
	
	public String string() {
		return String.format("[%d-%02X.%02X.%02X.%02X.%02X.%02X:%d]", 
				this.network,
				(this.host >> 40) & 0xFF,
				(this.host >> 32) & 0xFF,
				(this.host >> 24) & 0xFF,
				(this.host >> 16) & 0xFF,
				(this.host >> 8) & 0xFF,
				this.host & 0xFF,
				this.socket
				);
	}

}
