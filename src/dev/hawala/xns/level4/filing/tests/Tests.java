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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import dev.hawala.xns.level4.filing.fs.PathElement;
import dev.hawala.xns.level4.filing.fs.Volume;
import dev.hawala.xns.level4.filing.fs.Volume.Session;
import dev.hawala.xns.level4.filing.fs.Volume.VolumeLockedException;
import dev.hawala.xns.level4.filing.fs.iValueSetter;

public class Tests {

	@Test
	public void testPatterns() {
		innerTestFilePattern("0123456789ABCDEF.full-meta", true);
		innerTestFilePattern("0123456789ABCDEF.delta-meta", true);
		innerTestFilePattern("0123456789ABCDEF.test-meta", false);
		innerTestFilePattern("0123456789ABCD.full-meta", false);
		innerTestFilePattern("0123456789ABCDEF.delta-metax", false);
	}
	
	private void innerTestFilePattern(String s, boolean expected) {
		boolean actual = s.matches("^[0-9A-Za-z]{16}\\.(full|delta)-meta$");
		if (expected) {
			assertTrue(s + " - matches", actual);
		} else {
			assertFalse(s + " - matches", actual);
		}
	}
	
	@Test
	public void testPathElement_nopath_name() {
		List<PathElement> p = PathElement.parse("abc^3f:xx");
		assertEquals("p.size()", 1, p.size());
		assertEquals("p[0].name", "abc^3f:xx", p.get(0).getName());
		assertEquals("p[0].version", PathElement.HIGHEST_VERSION, p.get(0).getVersion());
	}
	
	@Test
	public void testPathElement_nopath_nameAndVersion() {
		List<PathElement> p = PathElement.parse("abc^3f:xx!33");
		assertEquals("p.size()", 1, p.size());
		assertEquals("p[0].name", "abc^3f:xx", p.get(0).getName());
		assertEquals("p[0].version", 33, p.get(0).getVersion());
	}
	
	@Test
	public void testPathElement_path() {
		List<PathElement> p = PathElement.parse("abc^3f:xx!33/XY'/Z!-/1245'!56!+/qwertz/asdfg.mesa!2");
		assertEquals("p.size()", 5, p.size());
		
		assertEquals("p[0].name", "abc^3f:xx", p.get(0).getName());
		assertEquals("p[0].version", 33, p.get(0).getVersion());
		
		assertEquals("p[1].name", "XY/Z", p.get(1).getName());
		assertEquals("p[1].version", PathElement.LOWEST_VERSION, p.get(1).getVersion());
		
		assertEquals("p[2].name", "1245!56", p.get(2).getName());
		assertEquals("p[2].version", PathElement.HIGHEST_VERSION, p.get(2).getVersion());
		
		assertEquals("p[3].name", "qwertz", p.get(3).getName());
		assertEquals("p[3].version", PathElement.HIGHEST_VERSION, p.get(3).getVersion());
		
		assertEquals("p[4].name", "asdfg.mesa", p.get(4).getName());
		assertEquals("p[4].version", 2, p.get(4).getVersion());
	}
	
	@Test
	public void testOpenCloseVolume() throws IOException, VolumeLockedException {
		Volume vol = Volume.openVolume("testdata/fs1/merkur", new TestErrorRaiser());
		assertNotNull("opened volume", vol);
		
		vol.close();
	}
	
	@Test
	public void testSession() throws IOException, VolumeLockedException {
		// TODO: initialize volume content (i.e. remove results of previous run)
		Volume vol = Volume.openVolume("testdata/fs1/merkur", new TestErrorRaiser());
		
		vol.dumpHierarchy(System.out);
		
		try (Session session = vol.startModificationSession()) {
			
			// TODO: use the session to perform changes on the volume
			
			List<iValueSetter> valueSetters = new ArrayList<>();
			valueSetters.add( (fe) -> {
				fe.setType(4433);
			});
			session.createFile(2, true, "Test-directory", 1, null, "hans:dev:hawala", valueSetters, null);
			
			vol.dumpHierarchy(System.out);
		
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
}
