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

package dev.hawala.xns.level4.printing;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Locale;

/**
 * Utility for converting a binary Interpress stream into a human-readable
 * Interpress version, similar to the Interpress example texts found in
 * Xerox documentation or specifications.
 * <p>
 * This module provides both a library conversion method and the corresponding
 * command line utility for reading an IP file and dumping the disassembled
 * content to stdout.
 * </p>
 * <p>
 * For a documentation of the binary encoding, see Appendix B in:
 * </p><p>
 * Interpress - The Source Book
 * <br/>
 * Steven J. Harrington and Robert R. Buckley
 * <br/>
 * A Brady book, Simon & Schuster, 1988
 *  </p>
 * 
 * @author Dr. Hans-Walter Latz / Berlin 2019
 */
public class InterpressUtils {
	
	private static class Op {
		public final String keyword;
		public final int indentDelta;
		public Op(String keyword, int indentDelta) {
			this.keyword = keyword;
			this.indentDelta = indentDelta;
		}
	}
	
	private static final int OPMASK     = 0x00E0; // 111.....
	private static final int OPSHORT    = 0x0080; // 100.....
	private static final int OPLONG     = 0x00A0; // 101.....
//	private static final int OPSHORTSEQ = 0x00C0; // 110....
	private static final int OPLONGSEQ  = 0x00E0; // 111.....
	private static final int OPVALUE    = 0x001F; // ...11111
	
	private static final int SEQMASK = 0x001F;
	private static final int SEQ_STRING = 1;
	private static final int SEQ_INTEGER = 2;
	private static final int SEQ_INSERTMASTER = 3;
	private static final int SEQ_RATIONAL = 4;
	private static final int SEQ_IDENTIFIER = 5;
	private static final int SEQ_COMMENT = 6;
	private static final int SEQ_CONTINUED = 7;
	private static final int SEQ_LARGEVECTOR = 8;
	private static final int SEQ_PACKEDPIXELVECTOR = 9;
	private static final int SEQ_COMPRESSEDPIXELVECTOR = 10;
	private static final int SEQ_INSERTFILE = 11;
	private static final int SEQ_ADAPTIVEPIXELVECTOR = 12;
	private static final int SEQ_CCITT4PIXELVECTOR = 13;
	
	private static final int INDENT = 2;
	
	private static final Op[] shortOps = new Op[32];
	private static final Op[] longOps = new Op[0x0259]; // 0x0258 == 600 is the last allowed/documented op code
	
	private static void op(int indentDelta, String keyword, int longCode) {
		Op opcode = new Op(keyword, indentDelta);
		longOps[longCode] = opcode;
	}
	
	private static void op(String keyword, int longCode) {
		Op opcode = new Op(keyword, 0);
		longOps[longCode] = opcode;
	}
	
	private static void op(String keyword, int longCode, int shortCode) {
		Op opcode = new Op(keyword, 0);
		longOps[longCode] = opcode;
		shortOps[shortCode] = opcode;
	}
	
	private static class EOF extends Exception {
		private static final long serialVersionUID = 3208783020555331322L;
	}
	
	public static class InterpressException extends Exception {
		private static final long serialVersionUID = -1896789874604364224L;
		public InterpressException(String msg) { super(msg); }
	}
	
	private int getByte(InputStream ip) throws EOF, IOException {
		int b = ip.read();
		if (b < 0) { throw new EOF(); }
		return b;
	}
	
	private final String blanks = "                                                                                        ";

	public void readableFromBinary(InputStream ip, PrintStream dest) throws InterpressException, IOException {
		int level = 0;       // skeleton nesting level
		String indent = "";  // current output indent
		String sep = indent; // separator (indent at line start or single blank inside the line
		int b = 0;
		
		// read prefix up to the first blank (binary data starts after the blank)
		try {
			byte[] prefixBytes = new byte[256];
			int pos = 0;
			while(b != 32) {
				if (pos >= prefixBytes.length) {
					throw new InterpressException("unplausible interpress prefix (> 256 bytes)");
				}
				b = this.getByte(ip);
				prefixBytes[pos++] = (byte)b;
			}
			String prefix = new String(prefixBytes, 0, pos);
			if (!prefix.startsWith("Interpress/")) {
				throw new InterpressException("unplausible interpress prefix (not: 'Interpress/...')");
			}
			dest.printf("Header \"%s\"\n", prefix);
		} catch(EOF eof) {
			throw new IOException("EOF in interpress prefix");
		}
		
		// disassemble the binary interpress stream
		try {
			b = this.getByte(ip);
			while(true) {
				int opSelector = b & OPMASK;
				if (opSelector == OPSHORT || opSelector == OPLONG) {
					// operator or skeleton
					Op op = null;
					int code = b & OPVALUE;
					if (opSelector == OPLONG) {
						code = (code << 8) | this.getByte(ip);
						if (code >= longOps.length || longOps[code] == null) {
							throw new InterpressException("Invalid long opcode: " + code);
						}
						op = longOps[code];
					} else {
						op = shortOps[code];
						if (op == null) {
							throw new InterpressException("Invalid short opcode: " + code);
						}
					}
					
					if (op.indentDelta < 0) {
						level += op.indentDelta;
						indent = blanks.substring(0, level * INDENT);
						if (sep.length() != 1) {
							sep = indent;
						}
					}
					
					if (op.indentDelta != 0 && sep.length() == 1) {
						dest.println();
						sep = indent;
					}
					
					dest.printf("%s%s\n", sep, op.keyword);
					
					if (op.indentDelta > 0) {
						level += op.indentDelta;
						indent = blanks.substring(0, level * INDENT);
					}
					
					sep = indent;
				} else if (b <= 0x7F) {
					// short number
					int biased = ((b & OPVALUE) << 8) | this.getByte(ip);
					dest.printf("%s%s", sep, biased - 4000);
					sep = " ";
				} else {
					// sequence (short or long)
					int seqType = b & SEQMASK;
					int seqLen = this.getByte(ip);
					if (opSelector == OPLONGSEQ) {
						seqLen = (seqLen << 16)
							   | (this.getByte(ip) << 8)
							   | this.getByte(ip);
					}
					
					// load the sequence data
					byte[] seqData = new byte[seqLen];
					for (int i = 0; i < seqLen; i++) {
						seqData[i] = (byte)this.getByte(ip);
					}
					
					// interpret the sequence type and read+interpret the bytes accordingly
					switch(seqType) {
					case SEQ_STRING: {
							sep = this.dumpString("String", sep, indent, seqData, dest);
						}
						break;
					case SEQ_INTEGER: {
							if (seqLen == 0) {
								dest.printf("%s0", sep);
								break;
							}
							int value = (seqData[0] < 0) ? -1 : 0;
							for (int i = 0; i < seqLen; i++) {
								value = (value << 8) | (seqData[i] & 0xFF);
							}
							dest.printf("%s%d", sep, value);
							sep = " ";
						}
						break;
					case SEQ_INSERTMASTER: {
							sep = this.dumpString("InsertMaster", sep, indent, seqData, dest);
						}
						break;
					case SEQ_RATIONAL: {
							int numLen = seqLen / 2;
							if (numLen == 0) {
								dest.printf("%s0.0", sep);
								break;
							}
							
							int num = (seqData[0] < 0) ? -1 : 0;
							for (int i = 0; i < numLen; i++) {
								num = (num << 8) | (seqData[i] & 0xFF);
							}
							
							int denom = (seqData[numLen] < 0) ? -1 : 0;
							for (int i = numLen; i < seqLen; i++) {
								denom = (denom << 8) | (seqData[i] & 0xFF);
							}
							if (denom == 0) {
								this.dumpSequence("sequenceRational!invalid", sep, indent, seqData, dest);
								throw new InterpressException("invalid sequenceRational[" + seqLen + "]: denominator == 0");
							}
							
							float value = num / denom;
							dest.printf(Locale.ROOT, "%s%1.3f", sep, value);
							sep = " ";
						}
						break;
					case SEQ_IDENTIFIER: {
							sep = this.dumpString("Identifier", sep, indent, seqData, dest);
						}
						break;
					case SEQ_COMMENT: {
							sep = this.dumpString("Comment", sep, indent, seqData, dest);
						}
						break;
					case SEQ_CONTINUED: {
							sep = this.dumpSequence("Continued", sep, indent, seqData, dest);
						}
						break;
					case SEQ_LARGEVECTOR: {
							if (sep.length() == 1) { dest.println(); }
							sep = indent;
							if (seqLen == 0) {
								dest.printf("%sLargeVector[]\n", indent);
								break;
							}
							dest.printf("%sLargeVector[", indent);
							int elemLen = seqData[0] & 0x00FF;
							int elemCount = (seqLen - 1) / elemLen;
							
							int elemPos = 1;
							for (int elemNo = 0; elemNo < elemCount; elemNo++) {
								int value = (seqData[elemPos] < 0) ? -1 : 0;
								for (int i = 0; i < elemLen; i++) {
									value = (value << 8) | (seqData[elemPos + i] & 0xFF);
								}
								elemPos += elemLen;
								
								if ((elemNo % 8) == 0) {
									dest.printf("\n%s   ", indent);
								}
								dest.printf(" %04X", value);
							}
							
							dest.printf("\n%s  ]\n", indent);
						}
						break;
					case SEQ_PACKEDPIXELVECTOR: {
							sep = this.dumpSequence("PackedPixelVector", sep, indent, seqData, dest);
						}
						break;
					case SEQ_COMPRESSEDPIXELVECTOR: {
							sep = this.dumpSequence("CompressedPixelVector", sep, indent, seqData, dest);
						}
						break;
					case SEQ_INSERTFILE: {
							sep = this.dumpString("InsertFile", sep, indent, seqData, dest);
						}
						break;
					case SEQ_ADAPTIVEPIXELVECTOR: {
							sep = this.dumpSequence("Adaptive4PixelVector", sep, indent, seqData, dest);
						}
						break;
					case SEQ_CCITT4PIXELVECTOR: {
							sep = this.dumpSequence("Ccitt4PixelVector", sep, indent, seqData, dest);
						}
						break;
					default:
						throw new InterpressException("invalid sequence type code: " + seqType);
					}
				}
				b = this.getByte(ip);
			}
		} catch (EOF eof) {
			// normal end of the ip file
		}
	}
	
	private String dumpString(String typeName, String sep, String indent, byte[] seqData, PrintStream dest) {
		if (sep.length() == 1) { dest.println(); }
		dest.printf("%s%s \"", indent, typeName);
		for (int i = 0; i < seqData.length; i++) {
			byte b = seqData[i];
			if (b >= 20 && b < 127) {
				dest.print((char)b);
			} else {
				dest.printf("\\x%02X", b);
			}
		}
		dest.print("\"\n");
		return indent;
	}
	
	private String dumpSequence(String typeName, String sep, String indent, byte[] seqData, PrintStream dest) {
		if (sep.length() == 1) { dest.println(); }
		
		if (seqData.length == 0) {
			dest.printf("%s%s[]\n", indent, typeName);
			return indent;
		}
		dest.printf("%s%s[", indent, typeName);
		
		for (int i = 0; i < seqData.length; i++) {
			if ((i % 16) == 0) {
				dest.printf("\n%s    ", indent);
			}
			dest.printf("%02X", seqData[i] & 0x00FF);
		}
		
		dest.printf("\n%s  ]\n", indent);
		return indent;
	}
	
	public static void main(String[] args) throws InterpressException, IOException {
		if (args.length != 1) {
			System.err.println("InterpressUtils <ip-file>");
			return;
		}
		
		InterpressUtils utils = new InterpressUtils();
		InputStream ip = new BufferedInputStream(new FileInputStream(args[0]));
		
		utils.readableFromBinary(ip, System.out);
	}

	// definition of interpress codes
	static {
		// operators
		op("ABS", 200);
		op("ADD", 201);
		op("AND", 202);
		op("ARCTO", 403);
		op("CEILING", 203);
		op("CLIPOUTLINE", 418);
		op("CLIPRECTANGLE", 419);
		op("CONCAT", 165);
		op("CONCATT", 168);
		op("CONICTO", 404);
		op("COPY", 183);
		op("CORRECT", 110);
		op("CORRECTMASK", 156);
		op("CORRECTSPACE", 157);
		op("COUNT", 188);
		op("CURVETO", 402);
		op("DIV", 204);
		op("DO", 231);
		op("DOSAVE", 232);
		op("DOSAVEALL", 233);
		op("DOSAVESIMPLEBODY", 120);
		op("DUP", 181);
		op("EQ", 205);
		op("ERROR", 600);
		op("EXCH", 185);
		op("EXTRACTPIXELARRAY", 451);
		op("FGET", 20, 20);
		op("FINDCOLOR", 423);
		op("FINDCOLORMODELOPERATOR", 422);
		op("FINDCOLOROPERATOR", 421);
		op("FINDDECOMPRESSOR", 149);
		op("FINDFONT", 147);
		op("FINDOPERATOR", 116);
		op("FLOOR", 206);
		op("FSET", 21, 21);
		op("GE", 207);
		op("GET", 17, 17);
		op("GETCP", 159);
		op("GETP", 286);
		op("GETPROP", 287);
		op("GT", 208);
		op("IF", 239);
		op("IFCOPY", 240);
		op("IFELSE", 241);
		op("IGET", 18, 18);
		op("ISET", 19, 19);
		op("LINETO", 23, 23);
		op("LINETOX", 14, 14);
		op("LINETOY", 15, 15);
		op("MAKEFONT", 150);
		op("MAKEGRAY", 425);
		op("MAKEOUTLINE", 417);
		op("MAKEOUTLINEODD", 416);
		op("MAKEPIXELARRAY", 450);
		op("MAKESAMPLEDBLACK", 426);
		op("MAKESAMPLEDCOLOR", 427);
		op("MAKESIMPLECO", 114);
		op("MAKET", 160);
		op("MAKEVEC", 283);
		op("MAKEVECLU", 282);
		op("MARK", 186);
		op("MASKCHAR", 140);
		op("MASKDASHEDSTROKE", 442);
		op("MASKFILL", 409);
		op("MASKPIXEL", 452);
		op("MASKRECTANGLE", 410);
		op("MASKSTROKE", 24, 24);
		op("MASKSTROKECLOSED", 440);
		op("MASKSTROKE", 24, 24);
		op("MASKSTROKECLOSED", 440);
		op("MASKTRAPEZOIDX", 411);
		op("MASKTRAPEZOIDY", 412);
		op("MASKUNDERLINE", 414);
		op("MASKVECTOR", 441);
		op("MERGEPROP", 288);
		op("MOD", 209);
		op("MODIFYFONT", 148);
		op("MOVE", 169);
		op("MOVETO", 25, 25);
		op("MUL", 210);
		op("NEG", 211);
		op("NOP", 1, 1);
		op("NOT", 212);
		op("OR", 213);
		op("POP", 180);
		op("REM", 216);
		op("ROLL", 184);
		op("ROTATE", 163);
		op("ROUND", 217);
		op("SCALE", 164);
		op("SCALE2", 166);
		op("SETCORRECTMEASURE", 154);
		op("SETCORRECTTOLERANCE", 155);
		op("SETFONT", 151);
		op("SETGRAY", 424);
		op("SETSAMPLEDBLACK", 428);
		op("SETSAMPLEDCOLOR", 429);
		op("SETXREL", 12, 12);
		op("SETXY", 10, 10);
		op("SETXYREL", 11, 11);
		op("SETYREL", 13, 13);
		op("SHAPE", 285);
		op("SHOW", 22, 22);
		op("SHOWANDFIXEDXREL", 145);
		op("SHOWANDXREL", 146);
		op("SPACE", 16, 16);
		op("STARTUNDERLINE", 413);
		op("SUB", 214);
		op("TRANS", 170);
		op("TRANSLATE", 162);
		op("TRUNC", 215);
		op("TYPE", 220);
		op("UNMARK", 187);
		op("UNMARK0", 192);
		
		// skeleton
		op(+1, "BEGIN", 102);
		op("CONTENTINSTRUCTIONS", 105);
		op(-1, "END", 103);
		op(+1, "{", 106);
		op(-1, "}", 107);
	}
}
