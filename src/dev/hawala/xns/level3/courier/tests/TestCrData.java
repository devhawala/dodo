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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import dev.hawala.xns.level3.courier.INTEGER;
import dev.hawala.xns.level3.courier.STRING;
import dev.hawala.xns.level3.courier.iWireStream;
import dev.hawala.xns.level3.courier.iWireStream.EndOfMessageException;
import dev.hawala.xns.level3.courier.iWireStream.NoMoreWriteSpaceException;
import dev.hawala.xns.level3.courier.tests.CrTestProgram.ContentType;
import dev.hawala.xns.level3.courier.tests.CrTestProgram.Content_complex;
import dev.hawala.xns.level3.courier.tests.CrTestProgram.Content_integer;
import dev.hawala.xns.level3.courier.tests.CrTestProgram.Content_string;
import dev.hawala.xns.level3.courier.tests.CrTestProgram.CrBasics;
import dev.hawala.xns.level3.courier.tests.CrTestProgram.CrSequences;
import dev.hawala.xns.level3.courier.tests.CrTestProgram.CrSubstructures;
import dev.hawala.xns.level3.courier.tests.CrTestProgram.Data;
import dev.hawala.xns.level3.courier.tests.CrTestProgram.PacketType;
import dev.hawala.xns.level3.courier.tests.CrTestProgram.TransmitType;

/**
 * Test for Courier (de)serializations.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public class TestCrData {
	
	/*
	 * Basic encoding tests
	 */

	@Test
	public void testBasics1() throws EndOfMessageException, NoMoreWriteSpaceException {
		CrBasics rec = new CrBasics();
		rec.zeBool.set(true);
		rec.zeCardinal.set(65000);
		rec.zePacketType.set(PacketType.error);
		rec.zeTransmitType.set(TransmitType.paper);
		rec.zeInteger.set(-23);
		rec.zeLongCardinal.set(4000000000L);
		rec.zeLongInteger.set(-2000000000);
		rec.zeString.set("abcdefg"); // 7 chars long
		rec.zeUnspec.set(44000);
		rec.zeUnspec2.set(1550000000);
		rec.zeUnspec3.set(66000000000L);
		
		StringBuilder sb = new StringBuilder();
		String recStr = rec.append(sb, "", "rec").toString();
		// System.out.printf("-------\n%s\n-------\n", recStr);
		
		iWireStream iws = new MockWireStream();
		
		rec.serialize(iws);
		
		CrBasics recReread = new CrBasics();
		recReread.deserialize(iws);
		sb.setLength(0);
		String recRereadStr = recReread.append(sb, "", "rec").toString();
		
		assertEquals("recStr eq recRereadStr", recStr, recRereadStr);
		assertTrue(iws.isAtEnd());
	}

	@Test
	public void testBasics2() throws EndOfMessageException, NoMoreWriteSpaceException {
		CrBasics rec = new CrBasics();
		rec.zeBool.set(false);
		rec.zeCardinal.set(0);
		rec.zePacketType.set(PacketType.reset);
		rec.zeTransmitType.set(TransmitType.wire);
		rec.zeInteger.set(+32000);
		rec.zeLongCardinal.set(4L);
		rec.zeLongInteger.set(-2);
		rec.zeString.set("abcdefghijkl"); // 12 chars long
		rec.zeUnspec.set(-44000);
		rec.zeUnspec2.set(-2130000000);
		rec.zeUnspec3.set(77000000000L);
		
		StringBuilder sb = new StringBuilder();
		String recStr = rec.append(sb, "", "rec").toString();
		// System.out.printf("-------\n%s\n-------\n", recStr);
		
		iWireStream iws = new MockWireStream();
		
		rec.serialize(iws);
		
		CrBasics recReread = new CrBasics();
		recReread.deserialize(iws);
		sb.setLength(0);
		String recRereadStr = recReread.append(sb, "", "rec").toString();
		
		assertEquals("recStr eq recRereadStr", recStr, recRereadStr);
		assertTrue(iws.isAtEnd());
	}
	
	/*
	 * test embedded RECORD and CHOICE structures
	 */
	
	@Test
	public void testSubstructures1() throws EndOfMessageException, NoMoreWriteSpaceException {
		CrSubstructures rec = new CrSubstructures();
		rec.check1.set(0x1111);
		rec.check2.set(0x2222);
		rec.check3.set(0x3333);
		
		rec.data.userdata1.set(11111);
		rec.data.userdata2.set(222222222);
		
		rec.content.setChoice(ContentType.empty);
		
		StringBuilder sb = new StringBuilder();
		String recStr = rec.append(sb, "", "rec").toString();
		// System.out.printf("-------\n%s\n-------\n", recStr);
		
		iWireStream iws = new MockWireStream();
		
		rec.serialize(iws);
		
		CrSubstructures recReread = new CrSubstructures();
		recReread.deserialize(iws);
		sb.setLength(0);
		String recRereadStr = recReread.append(sb, "", "rec").toString();
		
		assertEquals("recStr eq recRereadStr", recStr, recRereadStr);
		assertTrue(iws.isAtEnd());
	}
	
	@Test
	public void testSubstructures2() throws EndOfMessageException, NoMoreWriteSpaceException {
		CrSubstructures rec = new CrSubstructures();
		rec.check1.set(0x1111);
		rec.check2.set(0x2222);
		rec.check3.set(0x3333);
		
		rec.data.userdata1.set(11111);
		rec.data.userdata2.set(222222222);
		
		rec.content.setChoice(ContentType.integer);
		Content_integer ci = (Content_integer)rec.content.getContent();
		ci.integer.set(-9999);
		
		StringBuilder sb = new StringBuilder();
		String recStr = rec.append(sb, "", "rec").toString();
		// System.out.printf("-------\n%s\n-------\n", recStr);
		
		iWireStream iws = new MockWireStream();
		
		rec.serialize(iws);
		
		CrSubstructures recReread = new CrSubstructures();
		recReread.deserialize(iws);
		sb.setLength(0);
		String recRereadStr = recReread.append(sb, "", "rec").toString();
		
		assertEquals("recStr eq recRereadStr", recStr, recRereadStr);
		assertTrue(iws.isAtEnd());
	}
	
	@Test
	public void testSubstructures3() throws EndOfMessageException, NoMoreWriteSpaceException {
		CrSubstructures rec = new CrSubstructures();
		rec.check1.set(0x1111);
		rec.check2.set(0x2222);
		rec.check3.set(0x3333);
		
		rec.data.userdata1.set(11111);
		rec.data.userdata2.set(222222222);
		
		rec.content.setChoice(ContentType.string);
		Content_string cs = (Content_string)rec.content.getContent();
		cs.string.set("the string content in rec.content.string");
		
		StringBuilder sb = new StringBuilder();
		String recStr = rec.append(sb, "", "rec").toString();
		// System.out.printf("-------\n%s\n-------\n", recStr);
		
		iWireStream iws = new MockWireStream();
		
		rec.serialize(iws);
		
		CrSubstructures recReread = new CrSubstructures();
		recReread.deserialize(iws);
		sb.setLength(0);
		String recRereadStr = recReread.append(sb, "", "rec").toString();
		
		assertEquals("recStr eq recRereadStr", recStr, recRereadStr);
		assertTrue(iws.isAtEnd());
	}
	
	@Test
	public void testSubstructures4() throws EndOfMessageException, NoMoreWriteSpaceException {
		CrSubstructures rec = new CrSubstructures();
		rec.check1.set(0x1111);
		rec.check2.set(0x2222);
		rec.check3.set(0x3333);
		
		rec.data.userdata1.set(11111);
		rec.data.userdata2.set(222222222);
		
		Content_complex cc = (Content_complex)rec.content.setChoice(ContentType.complex);
		cc.flags.get(0).set(true);
		cc.flags.get(1).set(false);
		cc.flags.get(2).set(false);
		cc.flags.get(3).set(true);
		cc.string.set("the string content in rec.content.complex");
		cc.longCardinal.set(4000000000L);
		
		StringBuilder sb = new StringBuilder();
		String recStr = rec.append(sb, "", "rec").toString();
		// System.out.printf("-------\n%s\n-------\n", recStr);
		
		iWireStream iws = new MockWireStream();
		
		rec.serialize(iws);
		
		CrSubstructures recReread = new CrSubstructures();
		recReread.deserialize(iws);
		sb.setLength(0);
		String recRereadStr = recReread.append(sb, "", "rec").toString();
		
		assertEquals("recStr eq recRereadStr", recStr, recRereadStr);
		assertTrue(iws.isAtEnd());
	}
	
	/*
	 * Test embedded SEQUENCE and StreamOf 
	 */
	
	@Test
	public void testSequences() throws EndOfMessageException, NoMoreWriteSpaceException {
		CrSequences rec = new CrSequences();
		rec.check1.set(0x1111);
		rec.check2.set(0x2222);
		rec.check3.set(0x3333);
		rec.check4.set(0x4444);
		rec.check5.set(0x5555);
		
		for (int i = 0; i < 5; i++) {
			INTEGER integer = rec.seqInt.add();
			integer.set(5000 + i);
		}
		
		for (int i = 0; i < 11; i++) {
			Data data = rec.sOfData.add();
			data.userdata1.set(7000 + i);
			data.userdata2.set(1001000 + i);
		}
		
		for (int i = 0; i < 7; i++) {
			STRING string = rec.seqString.add();
			string.set("value of rec.seqString :: " + i);
		}
		
		Content_complex cc = (Content_complex)rec.sOfContent.add().setChoice(ContentType.complex);
		cc.flags.get(0).set(true);
		cc.flags.get(1).set(false);
		cc.flags.get(2).set(true);
		cc.flags.get(3).set(false);
		cc.string.set("a test string");
		cc.longCardinal.set(1234567890L);
		
		rec.sOfContent.add().setChoice(ContentType.empty);
		
		Content_integer ci = (Content_integer)rec.sOfContent.add().setChoice(ContentType.integer);
		ci.integer.set(42);
		
		StringBuilder sb = new StringBuilder();
		String recStr = rec.append(sb, "", "rec").toString();
		// System.out.printf("-------\n%s\n-------\n", recStr);
		
		iWireStream iws = new MockWireStream();
		
		rec.serialize(iws);
		
		CrSequences recReread = new CrSequences();
		recReread.deserialize(iws);
		sb.setLength(0);
		String recRereadStr = recReread.append(sb, "", "rec").toString();
		
		assertEquals("recStr eq recRereadStr", recStr, recRereadStr);
		assertTrue(iws.isAtEnd());
	}
	
}
