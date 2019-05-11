package dev.hawala.xns.level3.courier.tests;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import dev.hawala.xns.level3.courier.STRING;
import dev.hawala.xns.level3.courier.iWireStream;
import dev.hawala.xns.level3.courier.iWireStream.EndOfMessageException;
import dev.hawala.xns.level3.courier.iWireStream.NoMoreWriteSpaceException;

public class TestXStringEncodings {

	private MockWireStream mkStringStream(byte[] content) {
		MockWireStream s = new MockWireStream();
		s.writeI16(content.length);
		for (int i = 0; i < content.length; i++) {
			s.writeI8(content[i]);
		}
		s.writeEOM();
		return s;
	}
	
	private void assertStreamHasBytes(iWireStream stream, byte[] bytes) throws EndOfMessageException {
		assertEquals("bytes length", bytes.length, stream.readI16());
		for (int i = 0; i < bytes.length; i++) {
			assertEquals("byte[" + i + "]", bytes[i], stream.readI8());
		}
	}
	
	@Test
	public void testEmpty() throws EndOfMessageException, NoMoreWriteSpaceException {
		byte[] bytes = {};
		MockWireStream in = mkStringStream(bytes);
		
		STRING xs = STRING.make();
		xs.deserialize(in);
		System.out.printf("deserialized string: '%s'\n", xs.get());
		assertEquals("deserialized", "", xs.get());
		
		MockWireStream out = new MockWireStream();
		xs.serialize(out);
		
		out.dumpWritten();
		
		assertStreamHasBytes(out, bytes);
	}
	
	@Test
	public void testPlainAscii() throws EndOfMessageException, NoMoreWriteSpaceException {
		byte[] bytes = { 'a', 'b', 'c', 'd', 'e', 'f' };
		MockWireStream in = mkStringStream(bytes);
		
		STRING xs = STRING.make();
		xs.deserialize(in);
		assertEquals("deserialized", "abcdef", xs.get());
		
		MockWireStream out = new MockWireStream();
		xs.serialize(out);
		
		assertStreamHasBytes(out, bytes);
	}
	
	@Test
	public void testSingleCodes() throws EndOfMessageException, NoMoreWriteSpaceException {
		byte[] bytes = { 'G', 'r', (byte)0xFF, (byte)0xF1, (byte)0xE5, (byte)0xFF, (byte)0x00, (byte)0xFB, 'e', 'n' };
		MockWireStream in = mkStringStream(bytes);
		
		STRING xs = STRING.make();
		xs.deserialize(in);
		System.out.printf("deserialized string: '%s'\n", xs.get());
		
		MockWireStream out = new MockWireStream();
		xs.serialize(out);
		
		out.dumpWritten();
		
		assertStreamHasBytes(out, bytes);
	}
	
	@Test
	public void testStartsAndEndsWithNonAscii8bit() throws EndOfMessageException, NoMoreWriteSpaceException {
		byte[] bytes = { (byte)0xFB, 'a', 'b', (byte)0xAB };
		MockWireStream in = mkStringStream(bytes);
		
		STRING xs = STRING.make();
		xs.deserialize(in);
		System.out.printf("deserialized string: '%s'\n", xs.get());
		
		MockWireStream out = new MockWireStream();
		xs.serialize(out);
		
		out.dumpWritten();
		
		assertStreamHasBytes(out, bytes);
	}
	
	@Test
	public void test16bitsInMiddle() throws EndOfMessageException, NoMoreWriteSpaceException {
		byte[] bytes = { 'a', 'B', (byte)0xFF, (byte)0xFF, 0x11, 0x22, 0x33, 0x44, (byte)0xFF, 0x00, 'c', 'D' };
		MockWireStream in = mkStringStream(bytes);
		
		STRING xs = STRING.make();
		xs.deserialize(in);
		System.out.printf("deserialized string: '%s'\n", xs.get());
		
		MockWireStream out = new MockWireStream();
		xs.serialize(out);
		
		out.dumpWritten();
		
		assertStreamHasBytes(out, bytes);
	}
	
	@Test
	public void test16bitsAtStart() throws EndOfMessageException, NoMoreWriteSpaceException {
		byte[] bytes = { (byte)0xFF, (byte)0xFF, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, (byte)0xFF, 0x00, 'x', 'y' };
		MockWireStream in = mkStringStream(bytes);
		
		STRING xs = STRING.make();
		xs.deserialize(in);
		System.out.printf("deserialized string: '%s'\n", xs.get());
		
		MockWireStream out = new MockWireStream();
		xs.serialize(out);
		
		out.dumpWritten();
		
		assertStreamHasBytes(out, bytes);
	}
	
	@Test
	public void test16bitsOnly() throws EndOfMessageException, NoMoreWriteSpaceException {
		byte[] bytes = { (byte)0xFF, (byte)0xFF, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66 };
		MockWireStream in = mkStringStream(bytes);
		
		STRING xs = STRING.make();
		xs.deserialize(in);
		System.out.printf("deserialized string: '%s'\n", xs.get());
		
		MockWireStream out = new MockWireStream();
		xs.serialize(out);
		
		out.dumpWritten();
		
		assertStreamHasBytes(out, bytes);
	}
	
}
