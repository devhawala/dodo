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

import dev.hawala.xns.level3.courier.iWireStream;
import dev.hawala.xns.level3.courier.iWireStream.EndOfMessageException;
import dev.hawala.xns.level3.courier.iWireStream.NoMoreWriteSpaceException;

/**
 * Tests for the test wire stream.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public class TestMockWireStream {

	@Test
	public void testBasics() throws EndOfMessageException, NoMoreWriteSpaceException {
		iWireStream iws = new MockWireStream();
		
		iws.beginStreamType((byte)1);
		iws.writeI48(0x0000112233445566L);
		iws.writeI32(0x77665544);
		iws.writeI16(20000);
		iws.writeS16((short)-27);
		iws.writeI8((byte)33);
		iws.writeS8((byte)-44);
		iws.writeI8((byte)55);
		iws.writeI16(0xFFEE);
		iws.writeEOM();
		
		assertEquals((byte)1, iws.getStreamType());
		assertEquals(0x0000112233445566L, iws.readI48());
		assertEquals(0x77665544, iws.readI32());
		assertEquals(20000, iws.readI16());
		assertEquals((short)-27, iws.readS16());
		assertEquals((byte)33, iws.readI8());
		assertEquals((byte)-44, iws.readS8());
		assertEquals((byte)55, iws.readI8());
		assertEquals(0xFFEE, iws.readI16() & 0xFFFF);
		assertTrue(iws.isAtEnd());
	}
	
	@Test
	public void testChars() throws EndOfMessageException, NoMoreWriteSpaceException {
		iWireStream iws = new MockWireStream();
		
		iws.writeI32(0x33445566);
		iws.writeI8(1);
		iws.writeI32(0x33445566);
		iws.writeI8(1);
		iws.writeI8(2);
		iws.writeI32(0x33445566);
		iws.writeI8(1);
		iws.writeI8(2);
		iws.writeI8(3);
		iws.writeI32(0x33445566);
		iws.writeI8(1);
		iws.writeI8(2);
		iws.writeI8(3);
		iws.writeI8(4);
		iws.writeI32(0x33445566);
		iws.writeI8(1);
		iws.writeI8(2);
		iws.writeI8(3);
		iws.writeI8(4);
		iws.writeI8(5);
		iws.writeI32(0x33445566);
		iws.writeI8(1);
		iws.writeI8(2);
		iws.writeI8(3);
		iws.writeI8(4);
		iws.writeI8(5);
		iws.writeI8(6);
		iws.writeI32(0x33445566);
		
		assertEquals((byte)0, iws.getStreamType());
		assertEquals(0x33445566, iws.readI32());
		assertEquals(1, iws.readI8());
		assertEquals(0x33445566, iws.readI32());
		assertEquals(1, iws.readI8());
		assertEquals(2, iws.readI8());
		assertEquals(0x33445566, iws.readI32());
		assertEquals(1, iws.readI8());
		assertEquals(2, iws.readI8());
		assertEquals(3, iws.readI8());
		assertEquals(0x33445566, iws.readI32());
		assertEquals(1, iws.readI8());
		assertEquals(2, iws.readI8());
		assertEquals(3, iws.readI8());
		assertEquals(4, iws.readI8());
		assertEquals(0x33445566, iws.readI32());
		assertEquals(1, iws.readI8());
		assertEquals(2, iws.readI8());
		assertEquals(3, iws.readI8());
		assertEquals(4, iws.readI8());
		assertEquals(5, iws.readI8());
		assertEquals(0x33445566, iws.readI32());
		assertEquals(1, iws.readI8());
		assertEquals(2, iws.readI8());
		assertEquals(3, iws.readI8());
		assertEquals(4, iws.readI8());
		assertEquals(5, iws.readI8());
		assertEquals(6, iws.readI8());
		assertEquals(0x33445566, iws.readI32());
		assertTrue(iws.isAtEnd());
	}
	
	@Test
	public void testEomSst() throws EndOfMessageException, NoMoreWriteSpaceException {
		iWireStream iws = new MockWireStream();
		
		iws.beginStreamType((byte)1);
		iws.writeI32(0x33445566);
		iws.beginStreamType((byte)17);
		iws.writeI32(0x88221133);
		iws.beginStreamType((byte)243);
		iws.writeI32(0x12345678);
		iws.writeEOM();
		
		assertEquals((byte)1, iws.getStreamType());
		assertEquals(0x33445566, iws.readI32());
		assertTrue(iws.isAtEnd());
		assertEquals((byte)17, iws.getStreamType());
		assertEquals(0x88221133, iws.readI32());
		assertTrue(iws.isAtEnd());
		assertEquals((byte)243, iws.getStreamType());
		assertEquals(0x12345678, iws.readI32());
		assertTrue(iws.isAtEnd());
	}
	
}
