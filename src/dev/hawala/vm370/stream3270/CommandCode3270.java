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

/**
 * Definition of the 3270 data stream CCW-command bytes including the
 * WCC encoding/decoding for CCW-commands requiring a WCC byte.
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2011,2012
 */
public final class CommandCode3270 {

	/** EraseAllUnprotected command. */
	public final static byte EAU = (byte)0x6F;
	
	/** EraseWrite command. */
	public final static byte EW = (byte)0xF5;
	
	/** EraseWriteAlternate command. */
	public final static byte EWA = (byte)0x7E;
	
	/** ReadBuffer command */
	public final static byte RB = (byte)0xF2;
	
	/** ReadModified command. */
	public final static byte RM = (byte)0xF6;
	
	/** ReadModifiedAll command. */
	public final static byte RMA = (byte)0x6E;
	
	/** Write command. */
	public final static byte W = (byte)0xF1;
	
	/** WriteStructuredField command. */
	public final static byte WSF = (byte)0xF3;
	
	private String commandName = "noCCW"; 
	private boolean flagSoundAlarm = false;
	private boolean flagKbdRestore = false;
	private boolean flagResetMDT = false;
	
	private boolean checkCCW(byte code, byte checkCode, String commandName) {
		if (code == checkCode) {
			this.commandName = commandName;
			return true;
		}
		return false;
	}
	
	/**
	 * Verify the a byte in a buffer is a valid 3270-CCW and if so remember
	 * the command name internally.
	 * @param buffer the byte buffer where to check for the command byte.
	 * @param offset the offset position in the buffer for the byte to check.
	 * @return <code>true</code> if the specified byte is a valid CCW byte.
	 */
	public boolean isCommand(byte[] buffer, int offset) {
		this.commandName = "noCommand";
		
		byte b = buffer[offset];
		return this.checkCCW(b, EAU, "Erase-All-Unprotected(EAU)")
			|| this.checkCCW(b, EW, "Erase/Write(EW)")
			|| this.checkCCW(b, EWA, "Erase/Write-Alternate(EWA)")
			|| this.checkCCW(b, RB, "Read-Buffer(RB)")
			|| this.checkCCW(b, RM, "Read-Modified(RM)")
			|| this.checkCCW(b, RMA, "Read-Modified-All(RMA)")
			|| this.checkCCW(b, W, "Write(W)")
			|| this.checkCCW(b, WSF, "Write-Structured-Field(WSF)");
	}
	
	/**
	 * Verify the a byte in a buffer is a valid 3270-CCW and if so remember
	 * the command name internally and interpret the WCC-byte if the CCW-command
	 * requires a WCC.
	 * @param buffer the byte buffer where to check for the command byte.
	 * @param offset the offset position in the buffer for the byte to check.
	 * @return <code>true</code> if the specified byte is a valid CCW byte.
	 */
	public int checkCommand(byte[] buffer, int offset) {
		if (!this.isCommand(buffer, offset)) { return offset; }
		
		this.flagSoundAlarm = false;
		this.flagKbdRestore = false;
		this.flagResetMDT = false;
		
		byte command = buffer[offset];
		if (command == WSF) {
			// find the End-of-Record in the buffer after this CCW and place us a its start
			// i.e.: skip the whole structured field
			offset++;
			while (offset < buffer.length) {
				if (buffer[offset] == (byte)0xFF && buffer[offset+1] == (byte)0xFF) {
					// telnet escaped 0xFF
					offset += 2;
				} else if (buffer[offset] == (byte)0xFF && buffer[offset+1] == (byte)0xEF) {
					// telnet EOR
					return offset;
				}
				offset++;
			}
			return offset;
		} else if (command == W || command == EW || command == EWA){
			// interpret WCC byte
			byte wcc = buffer[offset+1];
			this.flagSoundAlarm = ((wcc & 0x04) != 0);
			this.flagKbdRestore = ((wcc & 0x02) != 0);
			this.flagResetMDT = ((wcc & 0x01) != 0);
			// position after WCC byte
			return offset + 2;
		} else {
			// no additional data for CCW
			return offset + 1;
		}
	}
	
	/**
	 * Encode the passed WCC-flags into a valid WCC-byte.
	 * @param soundAlarm Sound-flag for the WCC-byte.
	 * @param kbdReset KbdReset for the WCC-byte.
	 * @param resetMDT ResetMDT-Flag for the WCC-byte.
	 * @return the WCC-byte for the given flags.
	 */
	public static byte encodeWccFlags(boolean soundAlarm, boolean kbdReset, boolean resetMDT) {
		byte wcc = (byte)0x00; // parity? bit reset (like hercules start screen)...
		if (soundAlarm) { wcc |= ( byte)0x04; }
		if (kbdReset) { wcc |= ( byte)0x02; }
		if (resetMDT) { wcc |= ( byte)0x01; }
		return Ebcdic6BitEncoding.encode6BitValue(wcc);
	}
	
	/**
	 * Get the command name for the last byte checked with <code>isCommand</code>
	 * or <code>checkCommand</code>.
	 * @return the name of the last verified command byte. 
	 */
	public String getCommandName() { return this.commandName; }
	
	/**
	 * Get the Sound-flag for the WCC-byte of the last command verified with
	 * <code>checkCommand</code>.
	 * @return the Sound-flag.
	 */
	public boolean isSoundAlarm() { return this.flagSoundAlarm; }
	
	/**
	 * Get the KbdRestore-flag for the WCC-byte of the last CCW verified with
	 * <code>checkCommand</code>.
	 * @return the KbdRestore-flag.
	 */
	public boolean isKbdRestore() { return this.flagKbdRestore; }
	
	/**
	 * Get the ResetMDT-flag for the WCC-byte of the last command verified with
	 * <code>checkCommand</code>.
	 * @return the ResetMDT-flag.
	 */
	public boolean isResetMDT() { return this.flagResetMDT; }
	
	/**
	 * Get a displayable/printable string for the WCC-byte flags of the last CCW verified with
	 * <code>checkCommand</code>.
	 * @return the string representing the last verified WCC-flags.
	 */
	public String getAttrString() {
		return "["
		  + ((this.flagSoundAlarm) ? " SoundAlarm" : "")
		  + ((this.flagKbdRestore) ? " KeyboardRestore" : "")
		  + ((this.flagResetMDT) ? " ResetMDT" : "")
		  + " ]";
	}
	
	/**
	 * Get the displayable/printable string for the last CCW verified with
	 * <code>checkCommand</code> including the WCC-byte. 
	 * @return the string representing the last verified CCW.
	 */
	public String getCcwString() {
		return this.commandName + " " + this.getAttrString();
	}
}
