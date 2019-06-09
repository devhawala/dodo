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

package dev.hawala.xns.level3.courier;

import java.util.ArrayList;
import java.util.List;

import dev.hawala.xns.level3.courier.iWireStream.EndOfMessageException;
import dev.hawala.xns.level3.courier.iWireStream.NoMoreWriteSpaceException;

/**
 * Non-generic implementation equivalent for {@code StreamOf<UNSPECIFIED>} with
 * a much smaller heap footprint and probably faster.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2019)
 */
public class StreamOfUnspecified implements iWireData {

	private final int nextSegmentSelector;
	private final int lastSegmentSelector;
	
	private final int segmentLength;
	
	private final int CHUNK_SIZE = 512;
	
	private final List<short[]> contents = new ArrayList<>();
	private int contentsSize = 0;
	private short[] content = null;
	private int remainingInContent = 0;
	private int currIdx = -1;
	
	private StreamOfUnspecified(int nextSegmentSelector, int lastSegmentSelector, int segmentLength) {
		this.nextSegmentSelector = nextSegmentSelector;
		this.lastSegmentSelector = lastSegmentSelector;
		this.segmentLength = segmentLength;
	}
	
	public int size() {
		return this.contentsSize;
	}
	
	public StreamOfUnspecified add(int value) {
		if (this.remainingInContent < 1) {
			this.content = new short[CHUNK_SIZE];
			this.currIdx = -1;
			this.remainingInContent = this.content.length;
			this.contents.add(this.content);
		}
		this.currIdx++;
		this.content[this.currIdx] = (short)(value & 0xFFFF);
		this.remainingInContent--;
		this.contentsSize++;
		return this;
	}
	
	public StreamOfUnspecified setLast(int value) {
		if (this.currIdx < 0) {
			throw new IllegalStateException("no last element to overwrite");
		}
		this.content[this.currIdx] = (short)(value & 0xFFFF);
		return this;
	}
	
	public int get(int idx) {
		if (idx < 0 || idx >= this.contentsSize) {
			throw new IllegalArgumentException("idx out ot range");
		}
		int pageNo = idx / CHUNK_SIZE;
		int pageIdx = idx % CHUNK_SIZE;
		short[] page = this.contents.get(pageNo);
		return page[pageIdx] & 0xFFFF;
	}
	
	public StreamOfUnspecified clear() {
		this.contents.clear();
		this.contentsSize = 0;
		this.content = null;
		this.remainingInContent = 0;
		this.currIdx = -1;
		return this;
	}

	@Override
	public void serialize(iWireStream ws) throws NoMoreWriteSpaceException {
		int remainingElements = this.size();
		int currSegmentType = this.nextSegmentSelector;
		
		int pageNo = 0;
		int pageIdx = 0;
		short[] page = (this.contents.isEmpty()) ? null : this.contents.get(pageNo);
		
		while(currSegmentType == this.nextSegmentSelector) {
			int segmentSize = Math.min(remainingElements, this.segmentLength);
			if (segmentSize == remainingElements) {
				currSegmentType = this.lastSegmentSelector;
			}
			ws.writeI16(currSegmentType);
			ws.writeI16(segmentSize);
			while(segmentSize > 0) {
				if (pageIdx >= page.length) {
					pageNo++;
					page = this.contents.get(pageNo);
					pageIdx = 0;
				}
				short w = page[pageIdx++];
				ws.writeI16(w);
				segmentSize--;
				remainingElements--;
			}
		}
	}

	@Override
	public void deserialize(iWireStream ws) throws EndOfMessageException {
		this.clear();
		boolean done = false;
		while(!done) {
			int currSegmentType = ws.readI16();
			done = (currSegmentType == this.lastSegmentSelector);
			
			int segmentLength = ws.readI16();
			for (int i = 0; i < segmentLength; i++) {
				int w = ws.readI16();
				this.add(w);
			}
		}
	}

	@Override
	public StringBuilder append(StringBuilder to, String indent, String fieldName) {
		int count = this.size();
		to.append(indent).append(fieldName).append(": StreamOfUnspecified(").append(Integer.toString(count)).append(")[\n");
		String newIndent = indent + "    ";
		for (int i = 0; i < count; i++) {
			to.append(newIndent).append(String.format("(%s): %5d;\n", i, this.get(i)));
		}
		to.append(newIndent).append("]");
		return to;
	}
	
	public static StreamOfUnspecified make() { return new StreamOfUnspecified(0, 1, 256); }
}
