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

package dev.hawala.xns.level3.courier;

import dev.hawala.xns.level3.courier.iWireStream.EndOfMessageException;
import dev.hawala.xns.level3.courier.iWireStream.NoMoreWriteSpaceException;

/**
 * Representation of the Courier STRING datatype.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public class STRING implements iWireData {
	
	private final int maxLength; // TODO: correctly use maxLength with XNS Character Encoding Standard !!
	
	private String str;
	
	protected STRING(int maxLen) {
		this.maxLength = maxLen;
	}
	
	protected STRING() { this(0xFFFF); }

	public STRING set(String value) {
		this.str = (value != null && value.length() > this.maxLength) ? value.substring(0, this.maxLength) :value;
		return this;
	}
	
	public String get() {
		return this.str;
	}

	@Override
	public void serialize(iWireStream ws) throws NoMoreWriteSpaceException {
		if (this.str == null || this.str.length() == 0) {
			ws.writeI16(0);
			return;
		}
		
		byte[] bytes = this.str.getBytes();
		// TODO: recode UNICODE => XNS Character Encoding Standard !!
		
		ws.writeI16(this.str.length());
		for (byte b : bytes) {
			ws.writeI8(b);
		}
		
	}

	@Override
	public void deserialize(iWireStream ws) throws EndOfMessageException {
		int len = ws.readI16() & 0xFFFF;
		if (len == 0) {
			this.str = "";
			return;
		}
		
		byte[] bytes = new byte[len];
		for (int i = 0; i < len; i++) {
			bytes[i] = (byte)(ws.readI8() & 0xFF);
		}
		
		// TODO: recode XNS Character Encoding Standard => UNICODE !!
		this.str = new String(bytes);
	}
	
	@Override
	public StringBuilder append(StringBuilder to, String indent, String fieldName) {
		String valueStr = (this.str != null) ? "\"" + this.str + "\"" : "<null>";
		to.append(indent).append(fieldName).append(": ").append(valueStr);
		return to;
	}
	
	public static STRING make() { return new STRING(); }
	
}
