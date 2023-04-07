/*
** This file is part of the external MECAFF process implementation.
** (MECAFF :: Multiline External Console And Fullscreen Facility 
**            for VM/370 R6 SixPack 1.2)
**
** This software is provided "as is" in the hope that it will be useful, with
** no promise, commitment or even warranty (explicit or implicit) to be
** suited or usable for any particular purpose.
** Using this software is at your own risk!
**
** Written by Dr. Hans-Walter Latz, Berlin (Germany), 2011,2012
** Released to the public domain.
*/

package dev.hawala.vm370.ebcdic;

import static dev.hawala.vm370.ebcdic.PlainHex.*;

/**
 * Definitions for the EBCDIC character set, with codes for typical displayable
 * characters, upper- and lowercase mapping as well as a (very basic) ASCII translation. 
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2011,2012
 */
public class Ebcdic {

	public static final byte _a = (byte) 0x81;
	public static final byte _b = (byte) 0x82;
	public static final byte _c = (byte) 0x83;
	public static final byte _d = (byte) 0x84;
	public static final byte _e = (byte) 0x85;
	public static final byte _f = (byte) 0x86;
	public static final byte _g = (byte) 0x87;
	public static final byte _h = (byte) 0x88; 
	public static final byte _i = (byte) 0x89;
	
	public static final byte _j = (byte) 0x91;
	public static final byte _k = (byte) 0x92;
	public static final byte _l = (byte) 0x93;
	public static final byte _m = (byte) 0x94;
	public static final byte _n = (byte) 0x95;
	public static final byte _o = (byte) 0x96;
	public static final byte _p = (byte) 0x97;
	public static final byte _q = (byte) 0x98; 
	public static final byte _r = (byte) 0x99;
	
	public static final byte _s = (byte) 0xA2;
	public static final byte _t = (byte) 0xA3;
	public static final byte _u = (byte) 0xA4;
	public static final byte _v = (byte) 0xA5;
	public static final byte _w = (byte) 0xA6;
	public static final byte _x = (byte) 0xA7;
	public static final byte _y = (byte) 0xA8; 
	public static final byte _z = (byte) 0xA9;

	public static final byte _A = (byte) 0xC1;
	public static final byte _B = (byte) 0xC2;
	public static final byte _C = (byte) 0xC3;
	public static final byte _D = (byte) 0xC4;
	public static final byte _E = (byte) 0xC5;
	public static final byte _F = (byte) 0xC6;
	public static final byte _G = (byte) 0xC7;
	public static final byte _H = (byte) 0xC8; 
	public static final byte _I = (byte) 0xC9;
	
	public static final byte _J = (byte) 0xD1;
	public static final byte _K = (byte) 0xD2;
	public static final byte _L = (byte) 0xD3;
	public static final byte _M = (byte) 0xD4;
	public static final byte _N = (byte) 0xD5;
	public static final byte _O = (byte) 0xD6;
	public static final byte _P = (byte) 0xD7;
	public static final byte _Q = (byte) 0xD8; 
	public static final byte _R = (byte) 0xD9;
	
	public static final byte _S = (byte) 0xE2;
	public static final byte _T = (byte) 0xE3;
	public static final byte _U = (byte) 0xE4;
	public static final byte _V = (byte) 0xE5;
	public static final byte _W = (byte) 0xE6;
	public static final byte _X = (byte) 0xE7;
	public static final byte _Y = (byte) 0xE8; 
	public static final byte _Z = (byte) 0xE9;
	
	public static final byte _0 = (byte) 0xF0;
	public static final byte _1 = (byte) 0xF1;
	public static final byte _2 = (byte) 0xF2;
	public static final byte _3 = (byte) 0xF3;
	public static final byte _4 = (byte) 0xF4;
	public static final byte _5 = (byte) 0xF5;
	public static final byte _6 = (byte) 0xF6;
	public static final byte _7 = (byte) 0xF7;
	public static final byte _8 = (byte) 0xF8; 
	public static final byte _9 = (byte) 0xF9;
	
	public static final byte _Blank = (byte) 0x40;
	public static final byte _Point = (byte) 0x4B;
	public static final byte _Dot = (byte) 0x4B;
	public static final byte _LT = (byte) 0x4C;
	public static final byte _ParenOpen = (byte) 0x4D;
	public static final byte _Plus = (byte) 0x4E;
	public static final byte _Pipe = (byte) 0x4F;
	
	public static final byte _Ampersand = (byte) 0x50;
	public static final byte _Exclamation = (byte) 0x5A;
	public static final byte _Dollar = (byte) 0x5B;
	public static final byte _Star = (byte) 0x5C;
	public static final byte _Semicolon = (byte) 0x5E;
	public static final byte _Not = (byte) 0x5F;
	
	public static final byte _Dash = (byte) 0x60;
	public static final byte _Slash = (byte) 0x61;
	public static final byte _PipeBroken = (byte) 0x6A;
	public static final byte _Comma = (byte) 0x6B;
	public static final byte _Percent = (byte) 0x6C;
	public static final byte _Underline = (byte) 0x6D;
	public static final byte _GT = (byte) 0x6E;
	public static final byte _Question = (byte) 0x6F;
	
	public static final byte _Backtick = (byte) 0x79;
	public static final byte _Colon = (byte) 0x7A;
	public static final byte _Hash = (byte) 0x7B;
	public static final byte _At = (byte) 0x7C;
	public static final byte _SingleQuote = (byte) 0x7D;
	public static final byte _Equal = (byte) 0x7E;
	public static final byte _DoubleQuote = (byte) 0x7F;
		
	public static final byte _Circumflex = (byte) 0xB0;
	
	public static final byte _CurlyOpen = (byte) 0xC0;
	public static final byte _CurlyClose = (byte) 0xD0;
	
	public static final byte _SquareOpen = (byte) 0xAD;
	public static final byte _SquareClose = (byte) 0xBD;
	
	public static final byte _Backslash = (byte) 0xE0;
	
	private static final byte[] uppercaseMap = {
		_00, _01, _02, _03, _04, _05, _06, _07, _08, _09, _0A, _0B, _0C, _0D, _0E, _0F,
		_10, _11, _12, _13, _14, _15, _16, _17, _18, _19, _1A, _1B, _1C, _1D, _1E, _1F,
		_20, _21, _22, _23, _24, _25, _26, _27, _28, _29, _2A, _2B, _2C, _2D, _2E, _2F,
		_30, _31, _32, _33, _34, _35, _36, _37, _38, _39, _3A, _3B, _3C, _3D, _3E, _3F,
		_40, _41, _62, _63, _64, _65, _66, _67, _68, _69, _4A, _4B, _4C, _4D, _4E, _4F,
		_50, _71, _72, _73, _74, _75, _76, _77, _78, _59, _5A, _5B, _5C, _5D, _5E, _5F,
		_60, _61, _62, _63, _64, _65, _66, _67, _68, _69, _6A, _6B, _6C, _6D, _6E, _6F,
		_80, _71, _72, _73, _74, _75, _76, _77, _78, _79, _7A, _7B, _7C, _7D, _7E, _7F,
		_80, _C1, _C2, _C3, _C4, _C5, _C6, _C7, _C8, _C9, _8A, _8B, _AC, _BA, _8E, _8F,
		_90, _D1, _D2, _D3, _D4, _D5, _D6, _D7, _D8, _D9, _9A, _9B, _9E, _9D, _9E, _9F,
		_A0, _A1, _E2, _E3, _E4, _E5, _E6, _E7, _E8, _E9, _AA, _AB, _AC, _AD, _8E, _AF,
		_B0, _B1, _B2, _B3, _B4, _B5, _B6, _B7, _B8, _B9, _BA, _BB, _BC, _BD, _BE, _BF,
		_C0, _C1, _C2, _C3, _C4, _C5, _C6, _C7, _C8, _C9, _CA, _EB, _EC, _ED, _EE, _EF,
		_D0, _D1, _D2, _D3, _D4, _D5, _D6, _D7, _D8, _D9, _DA, _FB, _FC, _FD, _FE, _DF,
		_E0, _E1, _E2, _E3, _E4, _E5, _E6, _E7, _E8, _E9, _EA, _EB, _EC, _ED, _EE, _EF,
		_F0, _F1, _F2, _F3, _F4, _F5, _F6, _F7, _F8, _F9, _FA, _FB, _FC, _FD, _FE, _FF
	};
	
	private static final byte[] lowercaseMap = {
		_00, _01, _02, _03, _04, _05, _06, _07, _08, _09, _0A, _0B, _0C, _0D, _0E, _0F,
		_10, _11, _12, _13, _14, _15, _16, _17, _18, _19, _1A, _1B, _1C, _1D, _1E, _1F,
		_20, _21, _22, _23, _24, _25, _26, _27, _28, _29, _2A, _2B, _2C, _2D, _2E, _2F,
		_30, _31, _32, _33, _34, _35, _36, _37, _38, _39, _3A, _3B, _3C, _3D, _3E, _3F,
		_40, _41, _42, _43, _44, _45, _46, _47, _48, _49, _4A, _4B, _4C, _4D, _4E, _4F,
		_50, _51, _52, _53, _54, _55, _56, _57, _58, _59, _5A, _5B, _5C, _5D, _5E, _5F,
		_60, _61, _42, _43, _44, _45, _46, _47, _48, _49, _6A, _6B, _6C, _6D, _6E, _6F,
		_70, _51, _52, _53, _54, _55, _56, _57, _58, _79, _7A, _7B, _7C, _7D, _7E, _7F,
		_70, _81, _82, _83, _84, _85, _86, _87, _88, _89, _8A, _8B, _8C, _8D, _AE, _8F,
		_90, _91, _92, _93, _94, _95, _96, _97, _98, _99, _9A, _9B, _9C, _9D, _9C, _9F,
		_A0, _A1, _A2, _A3, _A4, _A5, _A6, _A7, _A8, _A9, _AA, _AB, _8C, _AD, _AE, _AF,
		_B0, _B1, _B2, _B3, _B4, _B5, _B6, _B7, _B8, _B9, _8D, _BB, _BC, _BD, _BE, _BF,
		_C0, _81, _82, _83, _84, _85, _86, _87, _88, _89, _CA, _CB, _CC, _CD, _CE, _CF,
		_D0, _91, _92, _93, _94, _95, _96, _97, _98, _99, _DA, _DB, _DC, _DD, _DE, _DF,
		_E0, _E1, _A2, _A3, _A4, _A5, _A6, _A7, _A8, _A9, _EA, _CB, _CC, _CD, _CE, _CF,
		_F0, _F1, _F2, _F3, _F4, _F5, _F6, _F7, _F8, _F9, _FA, _DB, _DC, _DD, _DE, _FF
	};
	
	/**
	 * Convert an EBCDIC character to upper case.
	 * @param b the EBCDIC character to convert.
	 * @return the uppercase character for <code>b</code>.
	 */
	public static byte uppercase(byte b) {
		if (b < 0) {
			return uppercaseMap[b + 256];
		} else {
			return uppercaseMap[b];
		}
	}
	
	/**
	 * Convert an EBCDIC character to lower case.
	 * @param b the EBCDIC character to convert.
	 * @return the lowercase character for <code>b</code>.
	 */
	public static byte lowercase(byte b) {
		if (b < 0) {
			return lowercaseMap[b + 256];
		} else {
			return lowercaseMap[b];
		}
	}
	
	/* see: http://ascii-table.com/ebcdic-table.php */
	/* with one correction: 
	 *      | == 0x4F ("logical OR") 
	 *           and not 0x6A ("vertical split" == Ascii-0xA6 => outside valid range 0x00..0x7F)  
	 */
	
	private static final String BaseTable = 
		/* 0123456789ABCDEF */
		  "                "  /* 0 */
		/* 0123456789ABCDEF */
	    + "                "  /* 1 */
		/* 0123456789ABCDEF */
	    + "                "  /* 2 */
		/* 0123456789ABCDEF */
	    + "                "  /* 3 */
		/* 0123456789ABCDEF */
	    + "           .<(+|"  /* 4 */
		/* 0123456789ABCDEF */
	    + "&         !$*); "  /* 5 */
		/* 0123456789ABCDEF */
	    + "-/         ,%_>?"  /* 6 */
		/* 0123456789ABCDEF */
	    + "          :#@'=\""  /* 7 */
		/* 0123456789ABCDEF */
	    + " abcdefghi      "  /* 8 */
		/* 0123456789ABCDEF */
	    + " jklmnopqr      "  /* 9 */
		/* 0123456789ABCDEF */
	    + " ~stuvwxyz   [  "  /* A */
		/* 0123456789ABCDEF */
	    + "         `   ]  "  /* B */
		/* 0123456789ABCDEF */
	    + "{ABCDEFGHI      "  /* C */
		/* 0123456789ABCDEF */
	    + "}JKLMNOPQR      "  /* D */
		/* 0123456789ABCDEF */
	    + "\\ STUVWXYZ      "  /* E */
		/* 0123456789ABCDEF */
	    + "0123456789      "  /* F */ 
	;

	private static final byte[] A2E;
	private static final byte[] E2A;
	
	static {
	  byte[] baseBytes = BaseTable.getBytes();
	  
	  A2E = new byte[256];
	  for (int i = 0; i < A2E.length; i++) { A2E[i] = (byte)0x40; }
	  E2A = new byte[256];
	  for (int i = 0; i < E2A.length; i++) { E2A[i] = (byte)0x20; }
	  
	  for (int i = 0; i < baseBytes.length; i++) {
		  byte b = baseBytes[i];
		  if (b != (byte)0x20) {
			  A2E[b] = (byte)i;
			  E2A[i] = b;
		  }
	  }
	}
	
	/**
	 * Convert a Java string to an equivalent EBCDIC byte sequence.
	 * <p>
	 * The string to convert should contain only ASCII characters.
	 * @param s the string to convert.
	 * @return the resulting EBCDIC byte sequence.
	 */
	public static byte[] toEbcdic(String s) {
		return toEbcdic(s.getBytes());
	}
	
	/**
	 * Convert an ASCII byte sequence to an equivalent EBCDIC byte sequence.
	 * @param aBytes the ASCII byte sequence to convert. 
	 * @return the resulting EBCDIC byte sequence.
	 */
	public static byte[] toEbcdic(byte[] aBytes) {
		 byte[] bBytes = new byte[aBytes.length];
		 for (int i = 0; i < aBytes.length; i++) {
			 bBytes[i] = A2E[aBytes[i]];
		 }
		 return bBytes;
	}
	
	/**
	 * Convert an EBCDIC byte sequence to a Java (unicode) string. 
	 * @param bBytes the byte buffer where to get the byte sequence to convert.
	 * @param offset the start offset in the buffer of the byte sequence to convert.
	 * @param length the length of the byte sequence to convert.
	 * @return the resulting Java string for the EBCDIC string.
	 */
	public static String toAscii(byte[] bBytes, int offset, int length) {
		 byte[] aBytes = new byte[length];
		 for (int i = offset; i < offset + length; i++) {
			 byte b = bBytes[i];
			 aBytes[i-offset] = E2A[(b < 0) ? 256 + b : b];
		 }
		 return new String(aBytes);
	}
	
	public static byte from(byte b) {
		return E2A[(b < 0) ? 256 + b : b];
	}
}
