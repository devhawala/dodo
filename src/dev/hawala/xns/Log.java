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

package dev.hawala.xns;

import dev.hawala.xns.level0.Payload;

/**
 * Simple logging for the Dodo XNS components.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2016-2018)
 */
public class Log {
	
	private static final Object lock = new Object();
	
	private static final String INDENT_SEP = "    ";

	public static class Logger {
		
		private final String nullIntro;
		private final String indent;
		
		private boolean silenced = false;
		
		private Logger(String indent) {
			this(indent, "#----#");
		}
		
		private Logger(String indent, String nullIntro) {
			this.indent = indent;
			this.nullIntro = nullIntro;
		}
		
		public Logger printf(Object ref, String format, Object... args) {
			synchronized(lock) {
				if (this.silenced) { return this; }
				Payload pl = (ref instanceof Payload) ? (Payload)ref : null;
				String sref = (ref == null) ? this.nullIntro : "#" + ref.toString() + "#";
				String id = (pl != null) ? String.format("[%4d]", pl.getPacketId()) : sref;  
				String line = String.format(format,  args);
				System.out.printf("%s %s%s", id, this.indent, line);
				return this;
			}
		}
		
		public void doLog(boolean enabled) {
			synchronized(lock) {
				this.silenced = !enabled;
			}
		}
		
//		public Logger append(String txt) {
//			System.out.printf(txt);
//			return this;
//		}
		
//		public Logger append(String format, Object... args) {
//			System.out.printf(format, args);
//			return this;
//		}
		
	}
	
	public final static Logger E = new Logger("", "#ERROR");
	
	public final static Logger C = new Logger("", "COURIER");
	
	public final static Logger X = new Logger("", "CLIENT");
	
	public final static Logger I = new Logger("", "-INFO-");
	
	public final static Logger CHS = new Logger("", "#CHS##");
	
	public final static Logger AUTH = new Logger("", "#AUTH");
	
	public final static Logger L0 = new Logger("");
	
	public final static Logger L1 = new Logger(INDENT_SEP);
	
	public final static Logger L2 = new Logger(INDENT_SEP + INDENT_SEP);
	
	public final static Logger L3 = new Logger(INDENT_SEP + INDENT_SEP + INDENT_SEP);
	
	public final static Logger L4 = new Logger(INDENT_SEP + INDENT_SEP + INDENT_SEP + INDENT_SEP);
	
	public static void reset() {
		Log.E.doLog(true);
		Log.X.doLog(true);
		Log.I.doLog(true);
		Log.L0.doLog(true);
		Log.L1.doLog(true);
		Log.L2.doLog(true);
		Log.L3.doLog(true);
		Log.L4.doLog(true);
	}
}
