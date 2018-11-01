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

package dev.hawala.xns.level3.courier.tests;

import dev.hawala.xns.level3.courier.ARRAY;
import dev.hawala.xns.level3.courier.BOOLEAN;
import dev.hawala.xns.level3.courier.CARDINAL;
import dev.hawala.xns.level3.courier.CHOICE;
import dev.hawala.xns.level3.courier.CrEnum;
import dev.hawala.xns.level3.courier.CrProgram;
import dev.hawala.xns.level3.courier.ENUM;
import dev.hawala.xns.level3.courier.INTEGER;
import dev.hawala.xns.level3.courier.LONG_CARDINAL;
import dev.hawala.xns.level3.courier.LONG_INTEGER;
import dev.hawala.xns.level3.courier.RECORD;
import dev.hawala.xns.level3.courier.SEQUENCE;
import dev.hawala.xns.level3.courier.STRING;
import dev.hawala.xns.level3.courier.StreamOf;
import dev.hawala.xns.level3.courier.UNSPECIFIED;
import dev.hawala.xns.level3.courier.UNSPECIFIED2;
import dev.hawala.xns.level3.courier.UNSPECIFIED3;

/**
 * Definition of a Courier test program used for testing the
 * serialization and deserialization implementations.
 * <p>
 * Besides this, this class also shows the translation of definitions
 * in Courier syntax into the corresponding Java classes inside a
 * {@code CrProgram}.
 * </p>
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public class CrTestProgram extends CrProgram {
	
	public static final int PROGRAM = 3333;
	public static final int VERSION = 1;
	
	public int getProgramNumber() { return PROGRAM; }
	
	public int getVersionNumber() { return VERSION; }
	
	/*
	 * test enums
	 */
	
	// Courier:
	//   PacketType: TYPE = { request(1), response(2), error(128), reset(255) }; 
	public enum PacketType implements CrEnum {
		request(1),
		response(2),
		error(128),
		reset(255);
		
		private final int wireValue;
		
		private PacketType(int w) { this.wireValue = w; }

		@Override
		public int getWireValue() { return this.wireValue; }
	}
	public static final EnumMaker<PacketType> mkPacketType = buildEnum(PacketType.class).get();
	
	// Courier:
	//   TransmitType : TYPE = { wire(0), wifi(1), drums(2), paper(3), smoke(4) };
	public enum TransmitType { wire, wifi, drums, paper, smoke; }
	public static final EnumMaker<TransmitType> mkTransmitType = buildEnum(TransmitType.class).get();
	
	/*
	 * test CHOICE
	 * 
	 * Courier:
	 *   ContentType: TYPE = { empty(0), integer(1), string(2), complex(3) };
	 *   Content: TYPE = CHOICE ContentType OF {
	 *       empty   => RECORD [],
	 *       integer => RECORD [
	 *           integer:      INTEGER
	 *           ],
	 *       string  => RECORD [
	 *           string:       STRING
	 *           ],
	 *       complex => RECORD [
	 *           flags:        ARRAY [0..4) OF BOOLEAN,
	 *           string:       STRING,
	 *           longCardinal: LONG CARDINAL
	 *           ]
	 *       };
	 */
	
	public enum ContentType { empty, integer, string, complex };
	public static final EnumMaker<ContentType> mkContentType = buildEnum(ContentType.class).get();
	
	public static class Content_integer extends RECORD {
		public final INTEGER integer = mkINTEGER();
		
		private Content_integer() {}
		
		public static Content_integer make() { return new Content_integer(); }
	}
	
	public static class Content_string extends RECORD {
		public final STRING string = mkSTRING();
		
		private Content_string() {}
		
		public static Content_string make() { return new Content_string(); }
	}
	
	public static class Content_complex extends RECORD {
		public final ARRAY<BOOLEAN> flags = mkARRAY(4, BOOLEAN::make);
		public final STRING string = mkSTRING();
		public final LONG_CARDINAL longCardinal = mkLONG_CARDINAL();
		
		private Content_complex() {}
		
		public static Content_complex make() { return new Content_complex(); }
	}
	
	public static final ChoiceMaker<ContentType> mkContent = buildChoice(mkContentType)
			.choice(ContentType.empty, RECORD::empty)
			.choice(ContentType.integer, Content_integer::make)
			.choice(ContentType.string, Content_string::make)
			.choice(ContentType.complex, Content_complex::make)
			.get();
	
	/*
	 * RECORD for simple repeated data (ARRAY / SEQUENCE / StreamOf)
	 * 
	 * Courier:
	 *   Data: TYPE = RECORD [
	 *     userdata1: CARDINAL,
	 *     userdata2: LONG INTEGER
	 *     ];
	 */
	
	public static class Data extends RECORD {
		public final CARDINAL userdata1 = mkCARDINAL();
		public final LONG_INTEGER userdata2 = mkLONG_INTEGER();
		
		private Data() {}
		
		public static Data make() { return new Data(); }
	}
	
	/*
	 * test RECORD holding basic (atomic) data types
	 * 
	 * Courier:
	 *   CrBasics: TYPE = RECORD [
	 *     zeBool:         BOOLEAN,
	 *     zeCardinal:     CARDINAL,
	 *     zePackectType:  PacketType,
	 *     zeTransmitType: TransmitType,
	 *     zeInteger:      INTEGER,
	 *     zeLongCardinal: LONG CARDINAL,
	 *     zeLongInteger:  LONG INTEGER,
	 *     zeString:       STRING, -- unlimited ?? (is this allowed in Courier?)
	 *     zeUnspec:       UNSPECIFIED,
	 *     zeUnspec2:      UNSPECIFIED2,
	 *     zeUnspec3:      UNSPECIFIED3,
	 *     ];
	 */
	
	public static class CrBasics extends RECORD {
		public final BOOLEAN zeBool = mkBOOLEAN();
		public final CARDINAL zeCardinal = mkCARDINAL();
		public final ENUM<PacketType> zePacketType = mkENUM(mkPacketType);
		public final ENUM<TransmitType> zeTransmitType = mkENUM(mkTransmitType);
		public final INTEGER zeInteger = mkINTEGER();
		public final LONG_CARDINAL zeLongCardinal = mkLONG_CARDINAL();
		public final LONG_INTEGER zeLongInteger = mkLONG_INTEGER();
		public final STRING zeString = mkSTRING();
		public final UNSPECIFIED zeUnspec = mkUNSPECIFIED();
		public final UNSPECIFIED2 zeUnspec2 = mkMember(UNSPECIFIED2::make);
		public final UNSPECIFIED3 zeUnspec3 = mkMember(UNSPECIFIED3::make);
	}
	
	/*
	 * test RECORD having a sub-RECORD and a CHOICE
	 * 
	 * Courier:
	 *   CrSubstructures: TYPE = RECORD [
	 *     check1:  INTEGER,
	 *     data:    Data,
	 *     check2:  INTEGER,
	 *     content: Content,
	 *     check3:  INTEGER
	 *     ];
	 */
	
	public static class CrSubstructures extends RECORD {
		public final INTEGER check1 = mkINTEGER();
		public final Data data = mkRECORD(Data::make);
		public final INTEGER check2 = mkINTEGER();
		public final CHOICE<ContentType> content = mkCHOICE(mkContent);
		public final INTEGER check3 = mkINTEGER();
	}
	
	/*
	 * test RECORD with SEQUENCE and StreamOf
	 * 
	 * Courier:
	 *   StreamOfData: TYPE = CHOICE OF {
	 *     nextSegment (0) => RECORD [
	 *         segment: SEQUENCE OF Data,
	 *         restOfStream: StreamOfData
	 *         ],
	 *     lastSegment (1) => RECORD [
	 *         segment: SEQUENCE OF Data
	 *         ]
	 *     };
	 *   StreamOfContent: TYPE = CHOICE OF {
	 *     nextSegment (0) => RECORD [
	 *         segment: SEQUENCE OF Content,
	 *         restOfStream: StreamOfContent
	 *         ],
	 *     lastSegment (1) => RECORD [
	 *         segment: SEQUENCE OF Content
	 *         ]
	 *     };
	 *   CrSequences: TYPE = RECORD [
	 *     check1:     INTEGER,
	 *     seqInt:     SEQUENCE OF INTEGER,
	 *     check2:     INTEGER,
	 *     sOfData:    StreamOfData,
	 *     check3:     INTEGER,
	 *     seqString:  SEQUENCE OF STRING,
	 *     check4:     INTEGER,
	 *     sOfContent: StreamOfContent,
	 *     check5:     INTEGER
	 *     ];
	 */
	
	public static class CrSequences extends RECORD {
		public final INTEGER check1 = mkINTEGER();
		public final SEQUENCE<INTEGER> seqInt = mkSEQUENCE(INTEGER::make);
		public final INTEGER check2 = mkINTEGER();
		public final StreamOf<Data> sOfData = mkStreamOf(4, Data::make);
		public final INTEGER check3 = mkINTEGER();
		public final SEQUENCE<STRING> seqString = mkSEQUENCE(STRING::make);
		public final INTEGER check4 = mkINTEGER();
		public final StreamOf<CHOICE<ContentType>> sOfContent = mkStreamOf(3, mkContent);
		public final INTEGER check5 = mkINTEGER();
	}

}
