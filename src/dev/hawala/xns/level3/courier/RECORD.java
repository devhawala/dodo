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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.hawala.xns.level3.courier.iWireStream.EndOfMessageException;
import dev.hawala.xns.level3.courier.iWireStream.NoMoreWriteSpaceException;

/**
 * Representation of the Courier RECORD datatype
 * as base class for real RECORDs.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public abstract class RECORD implements iWireData {
	
	private final List<iWireData> wireSequence = new ArrayList<>();
	
	private static Map<String,String[]> memberNamesMap = new HashMap<>();;
	
	private void wire(iWireData data) {
		this.wireSequence.add(data);
	}
	
	public BOOLEAN mkBOOLEAN() {
		BOOLEAN val = new BOOLEAN();
		this.wire(val);
		return val;
	}
	
	public UNSPECIFIED mkUNSPECIFIED() {
		UNSPECIFIED val = new UNSPECIFIED();
		this.wire(val);
		return val;
	}
	
	public CARDINAL mkCARDINAL() {
		CARDINAL val = new CARDINAL();
		this.wire(val);
		return val;
	}
	
	public LONG_CARDINAL mkLONG_CARDINAL() {
		LONG_CARDINAL val = new LONG_CARDINAL();
		this.wire(val);
		return val;
	}
	
	public INTEGER mkINTEGER() {
		INTEGER val = new INTEGER();
		this.wire(val);
		return val;
	}
	
	public LONG_INTEGER mkLONG_INTEGER() {
		LONG_INTEGER val = new LONG_INTEGER();
		this.wire(val);
		return val;
	}
	
	public STRING mkSTRING() {
		STRING str = new STRING();
		this.wire(str);
		return str;
	}
	
	public STRING mkSTRING(int maxLen) {
		STRING str = new STRING(maxLen);
		this.wire(str);
		return str;
	}
	
	public <T extends Enum<T>> ENUM<T> mkENUM(iWireDynamic<ENUM<T>> enumMaker) {
		ENUM<T> en = enumMaker.make();
		this.wire(en);
		return en;
	}
	
	public <T extends Enum<T>> CHOICE<T> mkCHOICE(iWireDynamic<CHOICE<T>> choiceMaker) {
		CHOICE<T> en = choiceMaker.make();
		this.wire(en);
		return en;
	}
	
	public <T extends iWireData> ARRAY<T> mkARRAY(int count, iWireDynamic<T> elemMaker) {
		ARRAY<T> array = new ARRAY<T>(count, elemMaker);
		this.wire(array);
		return array;
	}
	
	public <T extends iWireData> SEQUENCE<T> mkSEQUENCE(int maxCount, iWireDynamic<T> elemMaker) {
		SEQUENCE<T> seq = new SEQUENCE<T>(maxCount, elemMaker);
		this.wire(seq);
		return seq;
	}
	
	public <T extends iWireData> SEQUENCE<T> mkSEQUENCE(iWireDynamic<T> elemMaker) {
		SEQUENCE<T> seq = new SEQUENCE<T>(elemMaker);
		this.wire(seq);
		return seq;
	}
	
	public <T extends RECORD> T mkRECORD(iWireDynamic<T> recordMaker) {
		T record = recordMaker.make();
		this.wire(record);
		return record;
	}
	
	public <T extends iWireData> StreamOf<T> mkStreamOf(
			int nextSegmentSelector,
			int lastSegmentSelector,
			int segmentLength,
			iWireDynamic<T> elemBuilder) {
		StreamOf<T> sof = new StreamOf<T>(nextSegmentSelector, lastSegmentSelector, segmentLength, elemBuilder);
		this.wire(sof);
		return sof;
	}
	
	public <T extends iWireData> StreamOf<T> mkStreamOf(int segmentLength, iWireDynamic<T> elemBuilder) {
		return this.mkStreamOf(0, 1, segmentLength, elemBuilder);
	}
	
	public <T extends iWireData> StreamOf<T> mkStreamOf(iWireDynamic<T> elemBuilder) {
		return this.mkStreamOf(0, 1, 32, elemBuilder);
	}
	
	public <T extends iWireData> T mkMember(iWireDynamic<T> maker) {
		T member = maker.make();
		this.wire(member);
		return member;
	}

	@Override
	public void serialize(iWireStream ws) throws NoMoreWriteSpaceException {
		for (iWireData member : this.wireSequence) {
			member.serialize(ws);
		}
	}

	@Override
	public void deserialize(iWireStream ws) throws EndOfMessageException {
		for (iWireData member : this.wireSequence) {
			member.deserialize(ws);
		}
	}
	
	private String[] getMemberNames() {
		Class<?> clazz = this.getClass();
		String clazzName = clazz.getName();
		if (memberNamesMap.containsKey(clazzName)) {
			return memberNamesMap.get(clazzName);
			
		}
		int wireCount = this.wireSequence.size();
		String[] memberNames = new String[wireCount];
		for (int i = 0; i < memberNames.length; i++) {
			memberNames[i] = "<anon>";
		}
		for(Field field: clazz.getFields()) {
			if (!java.lang.reflect.Modifier.isFinal(field.getModifiers())) {
				continue;
			}
			if (!iWireData.class.isAssignableFrom(field.getType())) {
				continue;
			}
			String fieldName = field.getName();
			field.setAccessible(true);
			try {
				iWireData fieldValue = (iWireData)field.get(this);
				for (int i = 0; i < wireCount; i++) {
					if (fieldValue == this.wireSequence.get(i)) {
						memberNames[i] = fieldName;
						break;
					}
				}
			} catch (IllegalArgumentException | IllegalAccessException e) {
				System.err.printf("** %s.initMemberNames() : unable to access content for field '%s'",
						this.getClass().getName(), fieldName);
			}
		}
		
		memberNamesMap.put(clazzName, memberNames);
		return memberNames;
	}
	
	@Override
	public StringBuilder append(StringBuilder to, String indent, String fieldName) {
		String[] memberNames = this.getMemberNames();
		int wireCount = this.wireSequence.size();
		
		to.append(indent).append(fieldName).append(": RECORD [");
		if (wireCount == 0) {
			to.append(" ]");
		} else {
			to.append("\n");
			String newIndent = indent + "    ";
			for (int i = 0; i < wireCount; i++) {
				this.wireSequence.get(i).append(to, newIndent, memberNames[i]).append("\n");
			}
			to.append(newIndent).append("]");
		}
		return to;
	}
	
	public static final RECORD EMPTY = new RECORD() {}; // empty record as constant
	
	public static final RECORD empty() { return EMPTY; } // iWireDynamic for empty record
		
}
