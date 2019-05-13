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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Stack;

/**
 * Utility for converting a binary Interpress stream into a human-readable
 * Interpress version, similar to the Interpress example texts found in
 * Xerox documentation or specifications or into a PostScript stream if
 * a GVWin 2.1 ip-to-postscript conversion module is provided.
 * <p>
 * This module provides both a library conversion method and the corresponding
 * command line utility for reading an IP file and dumping the disassembled
 * content or the generated PostScript stream to stdout.
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
	
	/** Representation of a single IP operaand */
	private static class Op {
		/** keyword of the operand */
		public final String keyword;
		/** change of indentation, different of 0 for skeleton operands like { } BEGIN END */
		public final int indentDelta;
		public Op(String keyword, int indentDelta) {
			this.keyword = keyword;
			this.indentDelta = indentDelta;
		}
	}
	
	// masks and bit for interpreting ip token bytes
	private static final int OPMASK     = 0x00E0; // 111.....
	private static final int OPSHORT    = 0x0080; // 100.....
	private static final int OPLONG     = 0x00A0; // 101.....
//	private static final int OPSHORTSEQ = 0x00C0; // 110....
	private static final int OPLONGSEQ  = 0x00E0; // 111.....
	private static final int OPVALUE    = 0x001F; // ...11111
	private static final int SHORTNUM   = 0x007F; // 01111111
	
	// sequence types
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
	
	// number of bytes to indent in or out per level
	private static final int INDENT = 2;
	
	// maps ip operand code => operands
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
	
	// exception for signaling end-of-file forthe ip stream
	private static class EOF extends Exception {
		private static final long serialVersionUID = 3208783020555331322L;
	}
	
	// exception used for signaling that an interpress token seems invalid
	// or is not supported in this context
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

	/**
	 * Disassemble an IP binary stream into human readable text, in a similar
	 * format as in Xerox documentation and examples for Interpress.
	 * 
	 * @param ip the IP binary data input stream
	 * @param dest destination stream for the disassembled interpress master
	 * @param forPsConv produce restructured output for PS conversion if {@code true}
	 * @throws InterpressException if the IP master seems unplausible
	 * @throws IOException
	 */
	public void readableFromBinary(InputStream ip, PrintStream dest) throws InterpressException, IOException {
		this.innerReadableFromBinary(ip, dest, false);
	}
	
	/**
	 * Transform an interpress master into postscript like GSWin 2.1
	 * does it, relying on the IP2PS procedure of GVWin 2.1 provided
	 * externally.
	 *  
	 * @param ip the IP binary data input streanm
	 * @param dest destination stream for the generated postscript
	 * @param ip2psProc the content of the ip-to-ps conversion program module
	 * 		of GVWin 2.1 or a compatible module
	 * @param jobName the name of the print job to be recorded in the PS header
	 * @param title the title of the printed document to be recorded in the PS header
	 * @param creator the creators name of the printed document to be recorded in the PS header
	 * @throws InterpressException if the IP master seems unplausible or if the master contains
	 * 		constructs unsupported by this disassembler as PS conversion output
	 * @throws IOException
	 */
	public void generatePostscript(
			InputStream ip,
			PrintStream dest,
			byte[] ip2psProc,
			String jobName,
			String title,
			String creator
			) throws InterpressException, IOException {
		
		// write our header with a minimum "N-up" printing definition required deep in ip2psProc 
		dest.println("%!PS-Adobe-2.0");
		dest.printf("%%XRXJob: %s\n", jobName);
		dest.printf("%%%%Title: %s\n", title);
		dest.printf("%%%%Creator: %s\n", creator);
		dest.println("%%Pages: (atend)");
		dest.println("%%PageOrder: Ascend");
		dest.println("%%EndComments");
		dest.println("%%BeginProlog");
		dest.println("%%BeginResource: ProcSet N-up defs (simplified)");
		dest.println("/N-updict 20 dict def N-updict begin");
		dest.println("/Page 1 def");
		dest.println("end");
		dest.println("/showpage load /oldshowpage exch def");
		dest.println("/showpage {");
		dest.println("  save /SaveObj exch def");
		dest.println("  oldshowpage");
		dest.println("  0 0 initmatrix");
		dest.println("  transform");
		dest.println("  SaveObj restore");
		dest.println("  itransform translate");
		dest.println("} def");
		dest.println("%%EndResource");
		
		// include the ip-to-ps conversion resource
		if (ip2psProc[ip2psProc.length - 1] == (byte)0x1A) {
			dest.write(ip2psProc, 0, ip2psProc.length - 1);
		} else {
			dest.write(ip2psProc);
		}
		dest.println(); // just to be sure
		dest.println("%%EndProlog");
		
		// start the postscript content
		dest.println("%%BeginSetup");
		dest.println("IPdict begin");
		dest.println("SetIPMatrics");
		
		this.innerReadableFromBinary(ip, dest, true);
	}

	/**
	 * Disassemble an IP binary stream into human readable text, either
	 * in a similar format as in Xerox documentation and examples for
	 * Interpress or in a slightly restructured format suitable to be
	 * postpended to an GVWin 2.1 compatible ip-to-ps conversion module 
	 * to convert the Interpress master to PostScript.
	 * 
	 * @param ip the IP binary data input stream
	 * @param dest destination stream for the dissassembled interpress master
	 * @param forPsConv produce restructured output for PS conversion if {@code true}
	 * @throws InterpressException if the IP master seems unplausible or in
	 * 		case of PS conversion output if the master contains constructs
	 * 		unsupported by this disassembler as PS conversion output
	 * @throws IOException
	 */
	private void innerReadableFromBinary(InputStream ip, PrintStream dest, boolean forPsConv) throws InterpressException, IOException {
		int level = 0;       // skeleton nesting level
		int pageNo = -1;     // current page (-1 = before or in preamble, 0 = begin preamble)
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
			if (forPsConv) {
				dest.printf("%%%%Format: %s\n", prefix);
			} else {
				dest.printf("Header \"%s\"\n", prefix);
			}
		} catch(EOF eof) {
			throw new IOException("EOF in interpress prefix");
		}
		
		// disassemble the binary interpress stream
		boolean lastWasDosaveSimpleBody = false;
		Stack<Integer> doSaveSimpleBodyLevels = new Stack<>();
		doSaveSimpleBodyLevels.push(-1);
		boolean lastWasCorrect = false;
		Stack<Integer> correctLevels = new Stack<>();
		correctLevels.push(-1);
		String lastPackedPixelVector = null;
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
					
					String keyword = op.keyword;
					
					if (forPsConv) {
						/*
						 *  transform some ip structures into specific equivalent postscript structures
						 */
						boolean isBraceOpen = "{".equals(keyword);
						boolean isBraceClose = "}".equals(keyword);
						
						// CORRECT { .. } -> { .. } CORRECT
						if (isBraceOpen && lastWasCorrect) {
							// CORRECT {
							// -> output { and push level for transforming matching } to } CORRECT
							dest.printf("%s{\n", indent);
							level++;
							correctLevels.push(level);
							indent = blanks.substring(0, level * INDENT);
							sep = indent;
							b = this.getByte(ip);
							continue;
						}
						if (isBraceClose && correctLevels.peek() == level) {
							correctLevels.pop();
							level--;
							indent = blanks.substring(0, level * INDENT);
							sep = indent;
							dest.printf("%s}\n", indent);
							dest.printf("%sCORRECT\n", indent);
							b = this.getByte(ip);
							continue;
						}
						
						
						// DOSAVESIMPLEBODY { .. } -> DOSAVESIMPLEBODY .. DORESTORESIMPLEBODY
						if (isBraceOpen && lastWasDosaveSimpleBody) {
							// DOSAVESIMPLEBODY {
							// -> dont't output { and push level for transforming matching } to DORESTORESIMPLEBODY
							level++;
							doSaveSimpleBodyLevels.push(level);
							indent = blanks.substring(0, level * INDENT);
							sep = indent;
							b = this.getByte(ip);
							continue;
						}
						if (isBraceClose && doSaveSimpleBodyLevels.peek() == level) {
							doSaveSimpleBodyLevels.pop();
							level--;
							indent = blanks.substring(0, level * INDENT);
							sep = indent;
							dest.printf("%sDORESTORESIMPLEBODY\n", indent);
							b = this.getByte(ip);
							continue;
						}

						// PackedPixelArray: output the cached pixel string for tokens using it
						if ( ("MAKESAMPLEDBLACK".equals(keyword) || "MASKPIXEL".equals(keyword))
							 && lastPackedPixelVector != null) {
							dest.printf("%s%s", sep, keyword); // the cached pixel string starts with a newline
							dest.println(lastPackedPixelVector);
							lastPackedPixelVector = null;
							sep = indent;
							b = this.getByte(ip);
							continue;
						}
						
						// preamble body
						if (isBraceOpen && level == 1 && pageNo == -1) {
							// begin preamble
							dest.println("%BeginPreamble");
							level = 2;
							indent = blanks.substring(0, level * INDENT);
							b = this.getByte(ip);
							continue;
						}
						if (isBraceClose && level == 2 && pageNo == -1) {
							// end preamble
							dest.println("%EndPreamble");
							dest.println("%%EndSetup");
							level = 1;
							indent = "";
							pageNo = 0;
							b = this.getByte(ip);
							continue;
						}
						
						
						// page bodies
						if (isBraceOpen && level == 1 && pageNo >= 0) {
							// start new page
							pageNo++;
							dest.printf("%%%%Page: %d %d\n", pageNo, pageNo); // 2x pageNo ??
							dest.printf("%sPAGEBEGIN\n", indent);
							level = 2;
							indent = blanks.substring(0, level * INDENT);
							b = this.getByte(ip);
							continue;
							
						}
						if (isBraceClose && level == 2 && pageNo >= 0) {
							// end of current pagedest.println("%EndPreamble");
							dest.println("%PageEnd");
							dest.printf("%sPAGEEND\n", indent);
							level = 1;
							indent = "";
							b = this.getByte(ip);
							continue;
						}
						
						// document end
						if ("END".equals(keyword) && level == 1) {
							dest.println("%%Trailer");
							dest.println("END");
							level = 0;
							indent = "";
							break; // last interpress token for postscript output
						}
						
						// handle ps rendering specialities
						if ("SHOW".equals(keyword)) {
							keyword = "0 setcs SHOW";
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
					
					lastWasDosaveSimpleBody = keyword.equals("DOSAVESIMPLEBODY");
					lastWasCorrect = keyword.equals("CORRECT");
					if (!forPsConv || !lastWasCorrect) {
						dest.printf("%s%s\n", sep, keyword);
					}
					
					if (op.indentDelta > 0) {
						level += op.indentDelta;
						indent = blanks.substring(0, level * INDENT);
					}
					
					sep = indent;
				} else if (b <= SHORTNUM) {
					// short number
					int biased = ((b & SHORTNUM) << 8) | this.getByte(ip);
					dest.printf("%s%d", sep, biased - 4000);
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
							if (forPsConv) {
								dest.printf("%s(%s)\n", sep, mapXString(seqData));
								sep = indent;
							} else {
								sep = this.dumpString("String", sep, indent, seqData, dest);
							}
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
							if (forPsConv) {
								throw new InterpressException("PS conversion: unsupported sequence-type 'InsertMaster'");
							}
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
							
							if (forPsConv) {
								dest.printf("%s%d %d DIV", sep, num, denom);
							} else {
								float value = (float)num / (float)denom;
								dest.printf(Locale.ROOT, "%s%1.8f", sep, value);
							}
							sep = " ";
						}
						break;
					case SEQ_IDENTIFIER: {
							if (forPsConv) {
								String identifier = new String(seqData);
								if ("XC82-0-0".equals(identifier)) {
									identifier = "XC1-1-1"; // map XDE's preferred charset to one known by the IP-to-PS processor
								}
								dest.printf("%s/%s\n", indent, identifier);
								sep = indent;
							} else {
								sep = this.dumpString("Identifier", sep, indent, seqData, dest);
							}
						}
						break;
					case SEQ_COMMENT: {
							if (!forPsConv) {
								sep = this.dumpString("Comment", sep, indent, seqData, dest);
							}
						}
						break;
					case SEQ_CONTINUED: {
						if (forPsConv) {
							throw new InterpressException("PS conversion: unsupported sequence-type 'Continued'");
						}
							sep = this.dumpSequence("Continued", sep, indent, seqData, dest);
						}
						break;
					case SEQ_LARGEVECTOR: {
							if (forPsConv) {
								throw new InterpressException("PS conversion: unsupported sequence-type 'LargeVector'");
							}
						
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
							if (forPsConv) {
								if (seqData.length < 8) {
									throw new InterpressException("invalid PackedPixelVector (too short, length: " + seqData.length + ")");
								}
								int bitsPerPixel = ((seqData[0] << 8) & 0xFF00) | (seqData[1] & 0x00FF);
								int pixelsPerLine = ((seqData[2] << 8) & 0xFF00) | (seqData[3] & 0x00FF);
								int psBytesPerLine = ((bitsPerPixel * pixelsPerLine) + 7) / 8;
								int ipExtraBytesPerLine = (4 - (psBytesPerLine % 4)) % 4;
								
								// output the ps replacement for PacketPixelVector
								dest.printf("%s[%d %d [<> () ] ]\n", sep, bitsPerPixel, pixelsPerLine);
								sep = indent;
								
								// create and buffer the pixel string for later usage
								StringBuilder sb = new StringBuilder();
								int lineByte = 0;
								int pixelBytes = psBytesPerLine;
								int skipBytes = 0;
								for (int i = 4; i < seqData.length; i++) {
									if (skipBytes > 0) {
										skipBytes--;
										continue;
									}
									
									if ((lineByte % 50) == 0) {
										sb.append("\n");
										lineByte = 0;
									}
									
									sb.append(String.format("%02X", seqData[i] & 0xFF));
									lineByte++;
									pixelBytes--;
									if (pixelBytes <= 0) {
										skipBytes = ipExtraBytesPerLine;
										pixelBytes = psBytesPerLine;
										if ((seqData.length - i) < pixelBytes) {
											// last pixel line would be incomplete
											break;
										}
									}
								}
								lastPackedPixelVector = sb.toString();
							} else {
								sep = this.dumpSequence("PackedPixelVector", sep, indent, seqData, dest);
							}
						}
						break;
					case SEQ_COMPRESSEDPIXELVECTOR: {
						if (forPsConv) {
							throw new InterpressException("PS conversion: unsupported sequence-type 'CompressedPixelVector'");
						}
							sep = this.dumpSequence("CompressedPixelVector", sep, indent, seqData, dest);
						}
						break;
					case SEQ_INSERTFILE: {
						if (forPsConv) {
							throw new InterpressException("PS conversion: unsupported sequence-type 'InsertFile'");
						}
							sep = this.dumpString("InsertFile", sep, indent, seqData, dest);
						}
						break;
					case SEQ_ADAPTIVEPIXELVECTOR: {
						if (forPsConv) {
							throw new InterpressException("PS conversion: unsupported sequence-type 'AdaptivePixelVector'");
						}
							sep = this.dumpSequence("Adaptive4PixelVector", sep, indent, seqData, dest);
						}
						break;
					case SEQ_CCITT4PIXELVECTOR: {
						if (forPsConv) {
							throw new InterpressException("PS conversion: unsupported sequence-type 'Ccitt4PixelVector'");
						}
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
		
		if (forPsConv) {
			// close postscript skeleton started at the ip header
			dest.printf("%%%%Pages: %d\n", pageNo);
			dest.println("%%EOF");
		}
	}
	
	private String dumpString(String typeName, String sep, String indent, byte[] seqData, PrintStream dest) {
		if (sep.length() == 1) { dest.println(); }
		dest.printf("%s%s \"", indent, typeName);
		for (int i = 0; i < seqData.length; i++) {
			byte b = seqData[i];
			if (b >= 20 && b < 127) {
				char chr = (char)b;
				if (chr == '\\' || chr == '\"') {
					dest.printf("\\%c", chr);
				} else {
					dest.print((char)chr);
				}
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
		int reqArgs = 1;
		byte[] ip2ps = null;
		if (args.length > 0 && args[0].startsWith("-ip2ps:")) {
			String[] parts = args[0].split(":");
			if (parts.length != 2) {
				System.err.printf("** invalid arg '%s'\n", args[0]);
				System.err.println("InterpressUtils [-ip2ps:<ip2ps-proc.ps>] <ip-file>");
				return;
			}
			
			File ip2PsFile = new File(parts[1]);
			if (!ip2PsFile.exists() || !ip2PsFile.canRead()) {
				System.err.printf("** file not found: %s\n", parts[1]);
				System.err.println("InterpressUtils [-ip2ps:<ip2ps-proc.ps>] <ip-file>");
				return;
			}
			FileInputStream fis = new FileInputStream(ip2PsFile);
			int remaining = (int)ip2PsFile.length();
			ip2ps = new byte[remaining];
			while(remaining > 0) {
				remaining -= fis.read(ip2ps, ip2ps.length - remaining, ip2ps.length);
			}
			fis.close();
			
			reqArgs = 2;
		}
		if (args.length != reqArgs) {
			System.err.println("InterpressUtils [-ip2ps:<ip2ps-proc.ps>] <ip-file>");
			return;
		}
		
		InterpressUtils utils = new InterpressUtils();
		InputStream ip = new BufferedInputStream(new FileInputStream(args[reqArgs - 1]));
		
		if (ip2ps != null) {
			utils.generatePostscript(ip, System.out, ip2ps, "The-Print-Job-Name", "The-Title", "The-Creator");
		} else {
			utils.innerReadableFromBinary(ip, System.out, false);
		}
	}
	
	/*
	 * definitions for interpress and xerox extended strings
	 */
	
	// map {xerox-charset,xerox-char} -> representation as single char in postscript
	private static final Map<Integer,Integer> xcharmap = new HashMap<>();
	
	// map an string from interpress to ps string with octal encoding,
	// based on the xchar encoding schema and the xchar-mapping hard-coded here
	private static String mapXString(byte[] xs) {
		StringBuilder sb = new StringBuilder();
		
		int currCharset = 0;
		boolean lastWasFF = false;
		boolean twoByteMode = false;
		boolean twoByteFirstByte = false;
		for (int i = 0; i < xs.length; i++) {
			int b = xs[i] & 0xFF;
			if (twoByteMode && twoByteFirstByte && b == 0xFF) {
				twoByteMode = false;
				lastWasFF = true;
			} else if (twoByteMode && twoByteFirstByte) {
				currCharset = b;
			} else if (twoByteMode && !twoByteFirstByte) {
				sb.append(mapXChar(currCharset, b));
			} else if (b == 0xFF && lastWasFF) {
				lastWasFF = false;
				twoByteMode = true;
				twoByteFirstByte = false;
			} else if (lastWasFF) {
				currCharset = b;
				lastWasFF = false;
			} else if (b == 0xFF) {
				lastWasFF = true;
			} else {
				sb.append(mapXChar(currCharset, b));
				lastWasFF = false;
			}
		}
		
		return sb.toString();
	}
	
	private static String mapXChar(int charset, int code) {
		if (charset == 0 && code >= 0x20 && code < 0x7F) {
			return getSingleCharPsString(code);
		}
		
		int key = ((charset << 8) & 0x0000FF00) | (code & 0x00FF);
		Integer mapped = xcharmap.get(key);
		if (mapped != null) {
			return getSingleCharPsString(mapped);
		}
		return "\\267"; // bullet
	}
	
	private static String getSingleCharPsString(int code) {
		if (code >= 0x20 && code < 0x7F) {
			if (code == '(') {
				return "\\(";
			} else if (code == ')') {
				return "\\)";
			} else if (code == '\\') {
				return "\\\\";
			}
			return Character.toString((char)code);
		} else {
			return String.format("\\%03o", code);
		}
	}
	
	private static void defXChar(int charset, int xchar, int psChar) {
		int key = ((charset << 8) & 0x0000FF00) | (xchar & 0x00FF);
		xcharmap.put(key, psChar);
	}

	// definition of interpress codes and xchar mappings
	static {
		// interpress operators
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
		
		// interpress skeleton
		op(+1, "BEGIN", 102);
		op("CONTENTINSTRUCTIONS", 105);
		op(-1, "END", 103);
		op(+1, "{", 106);
		op(-1, "}", 107);
		
		// mapping xns char (charset & char-code, preferably hex) => ps single char (preferably octal)
		// (8-bit chars outside 0x20..0x7F (ascii) are used to map the most important accented or the like chars)
		// (however no support for other characters sets like asian charsets, sorry about that)
		// (mappings reconstructed by heavy experimenting with a GVWin document and virtual keyboards)
		// (so some possible mapped codes may be missing)
		
		// o000
		defXChar(0xF1, (int)'"', 0001);
		defXChar(0xF1, (int)'#', 0002);
		defXChar(0xF1, (int)'\'', 0003);
		defXChar(0xF1, (int)'!', 0004);
		defXChar(0xF1, (int)'(', 0005);
		defXChar(0xF1, (int)'$', 0006);
		defXChar(0xF1, (int)'-', 0007);

		defXChar(0xF1, (int)'1', 0010);
		defXChar(0xF1, (int)'2', 0011);
		defXChar(0xF1, (int)'5', 0012);
		defXChar(0xF1, (int)'0', 0013);
		defXChar(0xF1, (int)'?', 0014);
		defXChar(0xF1, (int)'@', 0015);
		defXChar(0xF1, (int)'D', 0016);
		defXChar(0xF1, (int)'>', 0017);

		defXChar(0xF1, (int)'L', 0020);
		defXChar(0xF1, (int)'P', 0021);
		defXChar(0xF1, (int)'5', 0022);
		defXChar(0xF1, (int)'T', 0023);
		defXChar(0xF1, (int)'0', 0024);
		defXChar(0xF1, (int)'R', 0025);
		defXChar(0xF1, (int)'\\', 0026);
		defXChar(0xF1, (int)'`', 0027);

		defXChar(0xF1, (int)'a', 0030);
		defXChar(0xF1, (int)'e', 0031);
		defXChar(0xF1, (int)'_', 0032);
		defXChar(0xF1, (int)'m', 0033);
		defXChar(0xF1, (int)'p', 0034);
		defXChar(0xF1, (int)'k', 0035);
		// o036
		// o037
		
		defXChar(0xF1, 0xA2, 0201);
		defXChar(0xF1, 0xA3, 0202);
		defXChar(0xF1, 0xA7, 0203);
		defXChar(0xF1, 0xA1, 0204);
		defXChar(0xF1, 0xA8, 0205);
		defXChar(0xF1, 0xA4, 0206);
		defXChar(0xF1, 0xAD, 0207);
		
		defXChar(0xF1, 0xB1, 0210);
		defXChar(0xF1, 0xB2, 0211);
		defXChar(0xF1, 0xB5, 0212);
		defXChar(0xF1, 0xB0, 0213);
		defXChar(0xF1, 0xBF, 0214);
		defXChar(0xF1, 0xC0, 0215);
		defXChar(0xF1, 0xC4, 0216);
		defXChar(0xF1, 0xBE, 0217);

		defXChar(0xF1, 0xCC, 0220);
		defXChar(0xF1, 0xD0, 0221);
		defXChar(0xF1, 0xD1, 0222);
		defXChar(0xF1, 0xD4, 0223);
		defXChar(0xF1, 0xCF, 0224);
		defXChar(0xF1, 0xD2, 0225);
		defXChar(0xF1, 0xDC, 0226);
		defXChar(0xF1, 0xE0, 0227);

		defXChar(0xF1, 0xE1, 0230);
		defXChar(0xF1, 0xE5, 0231);
		defXChar(0xF1, 0xDF, 0232);
		defXChar(0xF1, 0xF0, 0234);
		defXChar(0xF1, 0xEB, 0235);
		// o236
		// o237
		
		// o240
		// o241
		defXChar(0x00, 0xA1, 0242);
		defXChar(0x00, 0xA2, 0243);
		// o244
		defXChar(0x00, 0xA5, 0245);
		defXChar(0xEF, 0xA2, 0246);
		defXChar(0x00, 0xA7, 0247);

		defXChar(0xEE, (int)'F', 0250);
		defXChar((int)'!', (int)'l', 0251);
		defXChar(0x00, 0xAA, 0252);
		defXChar(0x00, 0xAB, 0253);
		defXChar(0xEF, (int)'*', 0254);
		defXChar(0xEF, (int)'+', 0255);
		defXChar(0xF0, (int)'$', 0256);
		defXChar(0xF0, (int)'%', 0257);

		defXChar(0x00, 0xB0, 0260);
		defXChar(0xEF, (int)'$', 0261);
		defXChar(0xEF, (int)'0', 0262);
		defXChar(0xEF, (int)'1', 0263);
		defXChar(0x00, 0xB7, 0264);
		// o265
		defXChar(0x00, 0xB6, 0266);
		defXChar(0xEF, (int)'f', 0267);
		
		// o270
		// o271
		defXChar(0x00, 0xBA, 0272);
		defXChar(0x00, 0xBB, 0273);
		defXChar((int)'!', (int)'D', 0274);
		defXChar(0xEF, (int)'A', 0275);
		// o276
		defXChar(0x00, 0xBF, 0277);
		
		// o300
		defXChar(0x00, 0xC1, 0301);
		defXChar(0x00, 0xC2, 0302);
		defXChar(0x00, 0xC3, 0303);
		defXChar(0x00, 0xC4, 0304);
		defXChar(0x00, 0xC5, 0305);
		defXChar(0x00, 0xC6, 0306);
		defXChar(0x00, 0xC7, 0307);

		defXChar(0x00, 0xC8, 0310);
		// o311 <-> 0xC9 ??
		defXChar(0x00, 0xCA, 0312);
		defXChar(0x00, 0xCB, 0313);
		// 0314
		defXChar((int)'!', (int)'m', 0315);
		defXChar(0x00, 0xCE, 0316);
		defXChar(0x00, 0xCF, 0317);

		defXChar(0xEF, (int)'%', 0320);
		// o321
		defXChar(0x00, 0xFC, 0322);
		defXChar(0x00, 0xEC, 0323);
		defXChar(0xEF, (int)'k', 0324);
		defXChar(0x00, 0xF3, 0325);
		defXChar(0xF0, (int)'p', 0326);
		defXChar(0x00, 0xBD, 0327);

		defXChar(0xF0, (int)'r', 0330);
		defXChar(0x00, 0xD2, 0331);
		defXChar(0x00, 0xD4, 0332);
		defXChar(0x00, 0xD1, 0333);
		defXChar(0x00, 0xB2, 0334);
		defXChar(0x00, 0xB3, 0335);
		defXChar((int)'&', (int)'o', 0336);
		// o337
		
		// o340
		defXChar(0x00, 0xE1, 0341);
		// o342 <-> 0xE2 ??
		defXChar(0x00, 0xE3, 0343);
		// o344
		// o345
		// o346
		// o347

		defXChar(0x00, 0xE7, 0350);
		defXChar(0x00, 0xE9, 0351);
		defXChar(0x00, 0xEA, 0352);
		defXChar(0x00, 0xEB, 0353);
		defXChar(0xEF, (int)'j', 0354);
		defXChar(0x00, 0xB1, 0355);
		defXChar(0xEF, (int)'$', 0356);
		defXChar(0x00, 0xD3, 0357);

		defXChar(0x00, 0xB4, 0360);
		defXChar(0x00, 0xF1, 0361);
		defXChar(0x00, 0xB8, 0362);
		// o363
		// o364
		// o365
		// o366
		// o367

		defXChar(0x00, 0xF7, 0370);
		defXChar(0x00, 0xF9, 0371);
		defXChar(0x00, 0xFA, 0372);
		defXChar(0x00, 0xFB, 0373);
		// o374
		// o375
		// o376
		// o377
		
		// chars not found / not discernable in globalviews virtual keyboard
		defXChar((int)'!', (int)'>', 0x2D); // mapped to simple 'minus' ('dash')
		defXChar(0xEF, (int)'(', 0271); // lower-"guillemets"
	}
}
