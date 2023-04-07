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

package dev.hawala.vm370.stream3270;

import java.util.Hashtable;

/**
 * Definition of the AID-codes that can be sent by 3270 terminals, including 
 * mapping to transmission codes, symbolic names and key-index values. 
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2011,2012
 */
public enum AidCode3270 {

	/** Enter-Aid, index: 0. */
	Enter((byte)0x7D, 0, "Enter"),
	
	
	/** PF01-Aid, index: 1. */
	PF01((byte)0xF1, 1, "PF01"),
	
	/** PF02-Aid, index: 2. */
	PF02((byte)0xF2, 2, "PF02"),
	
	/** PF03-Aid, index: 3. */
	PF03((byte)0xF3, 3, "PF03"),
	
	/** PF04-Aid, index: 4. */
	PF04((byte)0xF4, 4, "PF04"),
	
	/** PF05-Aid, index: 5. */
	PF05((byte)0xF5, 5, "PF05"),
	
	/** PF06-Aid, index: 6. */
	PF06((byte)0xF6, 6, "PF06"),
	
	/** PF07-Aid, index: 7. */
	PF07((byte)0xF7, 7, "PF07"),
	
	/** PF08-Aid, index: 8. */
	PF08((byte)0xF8, 8, "PF08"),
	
	/** PF09-Aid, index: 9. */
	PF09((byte)0xF9, 9, "PF09"),
	
	/** PF10-Aid, index: 10. */
	PF10((byte)0x7A, 10, "PF10"),
	
	/** PF11-Aid, index: 11. */
	PF11((byte)0x7B, 11, "PF11"),
	
	/** PF12-Aid, index: 12. */
	PF12((byte)0x7C, 12, "PF12"),
	
	/** PF13-Aid, index: 13. */
	PF13((byte)0xC1, 13, "PF13"),
	
	/** PF14-Aid, index: 14. */
	PF14((byte)0xC2, 14, "PF14"),
	
	/** PF15-Aid, index: 15. */
	PF15((byte)0xC3, 15, "PF15"),
	
	/** PF16-Aid, index: 16. */
	PF16((byte)0xC4, 16, "PF16"),
	
	/** PF17-Aid, index: 17. */
	PF17((byte)0xC5, 17, "PF17"),
	
	/** PF18-Aid, index: 18. */
	PF18((byte)0xC6, 18, "PF18"),
	
	/** PF19-Aid, index: 19. */
	PF19((byte)0xC7, 19, "PF19"),
	
	/** PF20-Aid, index: 20. */
	PF20((byte)0xC8, 20, "PF20"),
	
	/** PF21-Aid, index: 21. */
	PF21((byte)0xC9, 21, "PF21"),
	
	/** PF22-Aid, index: 22. */
	PF22((byte)0x4A, 22, "PF22"),
	
	/** PF23-Aid, index: 23. */
	PF23((byte)0x4B, 23, "PF23"),
	
	/** PF24-Aid, index: 24. */
	PF24((byte)0x4C, 24, "PF24"),

	
	/** PA1-Aid, index: 31. */
	PA01((byte)0x6C, 31, "PA01"),
	
	/** PA2-Aid, index: 32. */
	PA02((byte)0x6E, 32, "PA02"),
	
	/** PA3-Aid, index: 33. */
	PA03((byte)0x6B, 33, "PA03"),
	

	
	/** Clear-Aid, index: 34. */
	Clear((byte)0x6D, 34, "Clear"),

	
	/** SysReq/TestReq-Aid, index: 35. */
	SysReq((byte)0xF0, 35, "SysReq/TestReq"),
	

	
	/** Structured Field Aid, index: 36. */
	StructF((byte)0x88, 36,  "Structured field"),
	
	/** Read Partition Aid, index: 37. */
	ReadPartition((byte)0x61, 37, "Read partition"),
	
	/** Trigger Action Aid, index: 38. */
	TriggerAction((byte)0x7F, 38, "Trigger action"),
	
	/** Clear Partition Aid, index: 39. */
	ClearPartition((byte)0x6A, 39, "Clear partition"),

	
	/** Select pen attention Aid, index: 40. */
	SelectPen((byte)0x7E, 40, "Select pen attention"),

	
	/** NoAid-Aid, index: 41. */
	NoAID((byte)0x60, 41, "No AID generated"),

	
	/** Unknown-Aid (default for <code>map()</code>), index: 42. */
	Unknown((byte)0xFF, 42, "Unknown AID");
	
	// ---------------------------
	
	private final byte code;
	private final int keyIndex;
	private final String name;
	
	private static Hashtable<Byte,AidCode3270> codesMap = null;
	
	AidCode3270(byte code, int keyIndex, String name) {
		this.code = code;
		this.keyIndex = keyIndex;
		this.name = name;		
	}
	
	/**
	 * Get the 3270 encoding byte code for this Aid.
	 * @return byte transmission code of this Aid.
	 */
	public byte getCode() { return this.code; }
	
	/**
	 * Get the index value of the Aid.
	 * @return the index value.
	 */
	public int getKeyIndex() { return this.keyIndex; }
	
	/**
	 * Get the displayable/printable name of this Aid.
	 * @return display text for this Aid.
	 */
	public String getName() { return this.name; }
	
	/**
	 * Interpret the given transmitted byte and map it to the enum-instance
	 * representing the Aid
	 * @param code the byte value to map to an Aid.
	 * @return the enum-instance that the transmitted byte is mapped to.
	 */
	public static AidCode3270 map(byte code) {
		if (codesMap == null) {
			codesMap = new Hashtable<Byte,AidCode3270>();
			for(AidCode3270 aid : AidCode3270.values()) {
				codesMap.put(aid.getCode(), aid);
			}
		}
		if (codesMap.containsKey(code)) {
			return codesMap.get(code);
		}
		return AidCode3270.Unknown;
	}
}
