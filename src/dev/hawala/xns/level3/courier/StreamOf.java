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
 * Utility representation of the Courier SEQUENCE datatype containing
 * CHOICE elements for transporting an arbitrary count of specific content
 * elements, each CHOICE specifying if it is the non-last or the last
 * element in the SEQUENCE. 
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public class StreamOf<T extends iWireData> implements iWireData {

	private final int nextSegmentSelector;
	private final int lastSegmentSelector;
	
	private final int segmentLength;
	
	private final iWireDynamic<T> elemBuilder;
	
	private final List<T> elems = new ArrayList<T>();
	
	public StreamOf(
				int nextSegmentSelector,
				int lastSegmentSelector,
				int segmentLength,
				iWireDynamic<T> elemBuilder) {
		this.nextSegmentSelector = nextSegmentSelector;
		this.lastSegmentSelector = lastSegmentSelector;
		this.segmentLength = segmentLength;
		this.elemBuilder = elemBuilder;
	}
	
	public int size() {
		return this.elems.size();
	}
	
	public T get(int idx) {
		return this.elems.get(idx);
	}
	
	public StreamOf<T> clear() {
		this.elems.clear();
		return this;
	}
	
	public StreamOf<T> add(T value) {
		if (value == null) {
			throw new IllegalArgumentException("elements for StreamOf.add() may not be null");
		}
		this.elems.add(value);
		return this;
	}
	
	public T add() {
		T elem = this.elemBuilder.make();
		this.elems.add(elem);
		return elem;
	}

	@Override
	public void serialize(iWireStream ws) throws NoMoreWriteSpaceException {
		int remainingElements = this.elems.size();
		int currElement = 0;
		int currSegmentType = this.nextSegmentSelector;
		while(currSegmentType == this.nextSegmentSelector) {
			int segmentSize = Math.min(remainingElements, this.segmentLength);
			if (segmentSize == remainingElements) {
				currSegmentType = this.lastSegmentSelector;
			}
			ws.writeI16(currSegmentType);
			ws.writeI16(segmentSize);
			while(segmentSize > 0) {
				this.elems.get(currElement).serialize(ws);
				currElement++;
				segmentSize--;
				remainingElements--;
			}
		}
	}

	@Override
	public void deserialize(iWireStream ws) throws EndOfMessageException {
		this.elems.clear();
		boolean done = false;
		while(!done) {
			int currSegmentType = ws.readI16();
			done = (currSegmentType == this.lastSegmentSelector);
			
			int segmentLength = ws.readI16();
			for (int i = 0; i < segmentLength; i++) {
				T elem = this.elemBuilder.make();
				elem.deserialize(ws);
				this.elems.add(elem);
			}
		}
	}

	@Override
	public StringBuilder append(StringBuilder to, String indent, String fieldName) {
		int count = this.elems.size();
		to.append(indent).append(fieldName).append(": StreamOf(").append(Integer.toString(count)).append(")[\n");
		String newIndent = indent + "    ";
		for (int i = 0; i < count; i++) {
			this.elems.get(i).append(to, newIndent, String.format("(%s)", i)).append(";\n");
		}
		to.append(newIndent).append("]");
		return to;
	}
	
}
