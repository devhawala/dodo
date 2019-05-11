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

package dev.hawala.xns.level4.filing.tests;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.Test;

import dev.hawala.xns.level4.filing.fs.AccessEntry;
import dev.hawala.xns.level4.filing.fs.UninterpretedAttribute;
import dev.hawala.xns.level4.filing.fs.FileEntry;
import dev.hawala.xns.level4.filing.fs.FsConstants;

public class FileEntryTests {

	@Test
	public void testFeSimple() {
		FileEntry fe = new FileEntry(1033, 255, false, "test simple FileEntry", 2, 0, "simple:dev:hawala");
		
		System.out.println();
		System.out.println(fe.toString());
		
		FileEntry result = outIn(fe);
		System.out.println(result.toString());
		
		assertEquals("fe -> outIn() -> result", fe.toString(), result.toString());
	}
	
	@Test
	public void testFeAttributes() {
		FileEntry fe = new FileEntry(2033, 255, true, "test complex FileEntry", 1, 0, "complex:dev:hawala");
		
		fe.setAccessListDefaulted(false);
		fe.getAccessList().add(new AccessEntry("user1:dev:hawala", FsConstants.fullAccess));
		fe.getAccessList().add(new AccessEntry("*:sys:tu-berlin", FsConstants.readAccess));
		
		fe.setDefaultAccessListDefaulted(false);
		fe.getDefaultAccessList().add(new AccessEntry("user2:dev:hawala", FsConstants.readAccess | FsConstants.writeAccess));
		fe.getDefaultAccessList().add(new AccessEntry("userX:zrz:tu-berlin", FsConstants.ownerAccess));
		
		fe.getUninterpretedAttributes().add(new UninterpretedAttribute(4433).add(0xFFFF).add(0).add(0x1234));
		fe.getUninterpretedAttributes().add(new UninterpretedAttribute(1122).add(0).add(0x4321).add(0xFFFF).add(0xFFFF));
		
		fe.setType(FsConstants.tDirectory);
		fe.setChecksum(0xFEDC);
		fe.setDataSize(4567);
		fe.setReadBy("user1:dev:hawala");
		fe.setReadOn(0x4255);
		fe.setModifiedBy("userX:zrz:tu-berlin");
		fe.setModifiedOn(0x4242);
		fe.setChildrenUniquelyNamed(false);
		fe.setOrderingKey(3); // createdOn
		fe.setOrderingAscending(false);
		fe.setOrderingInterpretation(FsConstants.intrTime);

		System.out.println();
		System.out.println(fe.toString());
		
		FileEntry result = outIn(fe);
		System.out.println(result.toString());
		
		assertEquals("fe -> outIn() -> result", fe.toString(), result.toString());
	}
	
	
	private static FileEntry outIn(FileEntry fe) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		PrintStream ps = new PrintStream(bos);
		fe.externalize(ps);
		
		String line = new String(bos.toByteArray());
		FileEntry res = new FileEntry(line);
		
		return res;
	}
}
