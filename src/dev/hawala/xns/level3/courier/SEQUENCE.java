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

import java.util.ArrayList;
import java.util.List;

import dev.hawala.xns.level3.courier.iWireStream.EndOfMessageException;
import dev.hawala.xns.level3.courier.iWireStream.NoMoreWriteSpaceException;

/**
 * Representation of the Courier SEQUENCE datatype.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public class SEQUENCE<T extends iWireData> implements iWireData {
	
	private final int maxElemCount;
	private final iWireDynamic<T> elemBuilder;
	
	private final List<T> elems = new ArrayList<T>();
	
	public SEQUENCE(int maxCount, iWireDynamic<T> builder) {
		this.maxElemCount = maxCount;
		this.elemBuilder = builder;
	}
	
	public SEQUENCE(iWireDynamic<T> builder) {
		this(0xFFFF, builder);
	}
	
	public int size() {
		return this.elems.size();
	}
	
	public T get(int idx) {
		return this.elems.get(idx);
	}
	
	public SEQUENCE<T> clear() {
		this.elems.clear();
		return this;
	}
	
	public SEQUENCE<T> add(T value) {
		if (value == null) {
			throw new IllegalArgumentException("elements for SEQUENCE.add() may not be null");
		}
		if (this.elems.size() >= this.maxElemCount) {
			throw new IllegalStateException("attempt to add element beyond maxCount (" + this.maxElemCount + ")");
		}
		this.elems.add(value);
		return this;
	}
	
	public T add() {
		T elem = this.elemBuilder.make();
		this.add(elem);
		return elem;
	}

	@Override
	public void serialize(iWireStream ws) throws NoMoreWriteSpaceException {
		ws.writeI16(this.elems.size());
		for (T elem : this.elems) {
			elem.serialize(ws);
		}
	}

	@Override
	public void deserialize(iWireStream ws) throws EndOfMessageException {
		int elemCount = ws.readI16() & 0xFFFF;
		this.elems.clear();
		for (int i = 0; i < elemCount; i++) {
			T elem = this.elemBuilder.make();
			elem.deserialize(ws);
			if (this.elems.size() < this.maxElemCount) {
				this.elems.add(elem);
			}
		}
	}

	@Override
	public StringBuilder append(StringBuilder to, String indent, String fieldName) {
		int count = this.elems.size();
		to.append(indent).append(fieldName).append(": SEQUENCE(").append(Integer.toString(count)).append(")[\n");
		String newIndent = indent + "    ";
		for (int i = 0; i < count; i++) {
			this.elems.get(i).append(to, newIndent, String.format("(%s)", i)).append(";\n");
		}
		to.append(newIndent).append("]");
		return to;
	}
	
}