/*
Copyright (c) 2020, Dr. Hans-Walter Latz
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

package dev.hawala.xns.level3.courier;

/**
 * Special Courier type for getting the remote host-id, but with no effects on the
 * Courier communication.
 * 
 * @author Dr. Hans-Walter Latz / Berlin 2019,2020,2023
 */
public class RemoteHostId implements iWireData {
	
	private long hostId = 0L;
	
	public long get() { return hostId; }

	@Override
	public void serialize(iWireStream ws) { return; }

	@Override
	public void deserialize(iWireStream ws) {
		Long tmpHostId = ws.getPeerHostId();
		if (tmpHostId != null) {
			this.hostId = tmpHostId;
		}
	}

	@Override
	public StringBuilder append(StringBuilder to, String indent, String fieldName) { return to; }
	
	private RemoteHostId() {}
	public static RemoteHostId make() { return new RemoteHostId(); }

	@Override
	public void serialize(iJsonWriter wr) { return; }

	@Override
	public void deserialize(iJsonReader rd) { return ; /* ignored !! */ }
	
}
