/*
Copyright (c) 2019, Dr. Hans-Walter Latz
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

package dev.hawala.xns.level4.filing.fs;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Representation of an uninterpreted file attribute, i.e. for an arbitrary
 * (vendor-) defined attribute-type not pre-defined by the Filing Courier protocol.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2019)
 */
public class UninterpretedAttribute {
	
	private final long type;     // lower 64 bits <-> long cardinal
	private final List<Short> value = new ArrayList<>();
	
	public UninterpretedAttribute(long type) {
		this.type = type;
	}
	
	public UninterpretedAttribute(String externalized) {
		String[] parts = externalized.split(";");
		if (parts.length < 2) {
			throw new IllegalArgumentException("Invalid externalized attribute (< 2 components)");
		}
		int elementCount = Integer.parseInt(parts[1]);
		if (elementCount != (parts.length - 2)) {
			throw new IllegalArgumentException("Invalid externalized attribute (elementCount != parts.length - 2)");
		}
		
		this.type = Long.parseLong(parts[0]);
		for (int i = 0; i < elementCount; i++) {
			this.add(Short.parseShort(parts[i + 2]));
		}
	}
	
	public void externalize(PrintStream to) {
		to.printf("%d;%d", this.type, this.value.size());
		for (short v : this.value) { to.printf(";%d", v); }
	}

	public long getType() {
		return this.type;
	}
	
	public int size() {
		return this.value.size();
	}
	
	public void clear() {
		this.value.clear();
	}
	
	public UninterpretedAttribute add(short v) {
		this.value.add(v);
		return this;
	}
	
	public UninterpretedAttribute add(int v) {
		this.value.add((short)(v & 0xFFFF));
		return this;
	}
	
	public short get(int index) {
		return this.value.get(index);
	}

	@Override
	public String toString() {
		return "[type=" + type + ", value=" + value + "]";
	}
	
}
