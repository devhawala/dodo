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
 * Representation of the Courier ARRAY datatype.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public class ARRAY<T extends iWireData> implements iWireData {

	private final int elemCount;
	private final iWireDynamic<T> elemBuilder;
	
	private final List<T> elems = new ArrayList<T>();
	
	ARRAY(int count, iWireDynamic<T> builder) {
		this.elemCount = count;
		this.elemBuilder = builder;
		for(int i = 0; i < this.elemCount; i++) {
			this.elems.add(this.elemBuilder.create());
		}
	}
	
	public T get(int idx) {
		if (idx < 0 || idx >= elemCount) {
			throw new IndexOutOfBoundsException();
		}
		return this.elems.get(idx);
	}

	@Override
	public void serialize(iWireStream ws) throws NoMoreWriteSpaceException {
		for (int i = 0; i < this.elemCount; i++) {
			this.elems.get(i).serialize(ws);
		}
	}

	@Override
	public void deserialize(iWireStream ws) throws EndOfMessageException {
		for (int i = 0; i < this.elemCount; i++) {
			this.elems.get(i).deserialize(ws);
		}
	}

	@Override
	public StringBuilder append(StringBuilder to, String indent, String fieldName) {
		to.append(indent).append(fieldName).append(": ARRAY(").append(Integer.toString(this.elemCount)).append(")[\n");
		String newIndent = indent + "    ";
		for (int i = 0; i < this.elemCount; i++) {
			this.elems.get(i).append(to, newIndent, String.format("(%s)", i)).append(";\n");
		}
		to.append(newIndent).append("]");
		return to;
	}
	
}
