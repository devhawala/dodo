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

//import java.io.IOException;
//import java.io.OutputStream;

//import dev.hawala.vm370.Log;
import dev.hawala.vm370.ebcdic.EbcdicHandler;

/**
 * Utility class for for step-by-step construction of 3270 output
 * data streams.
 * <p>
 * The methods of this class allow a more or less high level creation
 * of 3270 output streams without having to manipulate the bytes of
 * the stream or with the coordinate to buffer address conversions. 
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2011,2012
 */
public class DataOutStream3270 {
	
//	private static Log logger = Log.getLogger();

	private int maxLength;
	private byte[] buffer;
	private int currLength = 0;
	
	private final int alternateCols;
	private final int alternateRows;
	private final boolean canExtended;
	
	private final EbcdicHandler ebcdicString = new EbcdicHandler();
	
	private BufferAddress ba = new BufferAddress();
	
	/**
	 * Construct the instance for a default 3270 terminal
	 * (24x80, no extended highlighting) without an alternate screen.
	 */
	public DataOutStream3270() {
		this(80, 24, false);
	}
	
	/**
	 * Construct the instance for a 3270 terminal possibly supporting 
	 * extended highlighting and with the given alternative screen size.
	 * @param alternateCols number of columns in the alternative screen geometry.
	 * @param alternateRows number of columns in the alternative screen geometry.
	 * @param canExtended flag indicating f the terminal supports extended highlighting.
	 */
	public DataOutStream3270(int alternateCols, int alternateRows, boolean canExtended){
		this.maxLength = 16384;
		this.buffer = new byte[this.maxLength];
		
		this.alternateCols = alternateCols;
		this.alternateRows = alternateRows;
		this.canExtended = canExtended;
	}
	
	private DataOutStream3270 appendByte(OrderCode3270 code) {
		return this.appendByte(code.getCode());
	}
	
	private DataOutStream3270 appendByte(byte b) {
		if (this.currLength >= this.maxLength) { return this; }
		this.buffer[this.currLength++] = b;
		return this;
	}
	
	/*
	 * ---------------------------- generals
	 */
	
	/**
	 * Reset the 3270 output stream to empty.
	 * @return this instance for function call chaining.
	 */
	public DataOutStream3270 clear() { 
		this.currLength = 0; 
		return this;
	}
	
	/**
	 * Reset the 3270 output stream to empty and start the buffer with the ESC character.
	 * @return this instance for function call chaining.
	 */
	public DataOutStream3270 clearAndESC() { 
		this.currLength = 0;
		this.appendByte((byte)0x27);
		return this;
	}
	
	/**
	 * Append the telnet end-of-record byte sequence.
	 * @return this instance for function call chaining.
	 */
	public DataOutStream3270 telnetEOR() {
		this.appendByte((byte)0xFF);
		this.appendByte((byte)0xEF);
		return this;
	}
	
	/*
	 * ---------------------------- 3270 Commands
	 */
	
	/**
	 * Append the Write-CCW with the given flags for the WCC-byte. 
	 * @param sound Sound-flag for the WCC-byte.
	 * @param kbdReset KbdReset for the WCC-byte.
	 * @param resetMDT ResetMDT-Flag for the WCC-byte.
	 * @return this instance for function call chaining.
	 */
	public DataOutStream3270 cmdWrite(boolean sound, boolean kbdReset, boolean resetMDT) {
		this.appendByte(CommandCode3270.W);
		this.appendByte(CommandCode3270.encodeWccFlags(sound, kbdReset, resetMDT));
		return this;
	}
	
	/**
	 * Append the EraseWrite-CCW with the given flags for the WCC-byte. 
	 * @param sound Sound-flag for the WCC-byte.
	 * @param kbdReset KbdReset for the WCC-byte.
	 * @param resetMDT ResetMDT-Flag for the WCC-byte.
	 * @return this instance for function call chaining.
	 */
	public DataOutStream3270 cmdEraseWrite(boolean sound, boolean kbdReset, boolean resetMDT) {
		this.appendByte(CommandCode3270.EW);
		this.appendByte(CommandCode3270.encodeWccFlags(sound, kbdReset, resetMDT));
		this.ba.setTermCols(80);
		this.ba.setTermRows(24);
		this.ba.moveTo(1,1);
		return this;
	}
	
	/**
	 * Append the EraseWriteAlternate-CCW with the given flags for the WCC-byte. 
	 * @param sound Sound-flag for the WCC-byte.
	 * @param kbdReset KbdReset for the WCC-byte.
	 * @param resetMDT ResetMDT-Flag for the WCC-byte.
	 * @return this instance for function call chaining.
	 */
	public DataOutStream3270 cmdEraseWriteAlternate(boolean sound, boolean kbdReset, boolean resetMDT) {
		this.appendByte(CommandCode3270.EWA);
		this.appendByte(CommandCode3270.encodeWccFlags(sound, kbdReset, resetMDT));
		this.ba.setTermCols(this.alternateCols);
		this.ba.setTermRows(this.alternateRows);
		this.ba.moveTo(1,1);
		return this;
	}
	
	/**
	 * Append the EraseAllUnprotected-CCW.
	 * @return this instance for function call chaining.
	 */
	public DataOutStream3270 cmdEraseAllUnprotected() {
		this.appendByte(CommandCode3270.EAU);
		return this;
	}
	
	/**
	 * Append the ReadBuffer-CCW.
	 * @return this instance for function call chaining.
	 */
	public DataOutStream3270 cmdReadBuffer() {
		this.appendByte(CommandCode3270.RB);
		return this;
	}
	
	/**
	 * Append the ReadModified-CCW.
	 * @return this instance for function call chaining.
	 */
	public DataOutStream3270 cmdReadModified() {
		this.appendByte(CommandCode3270.RM);
		return this;
	}
	
	/**
	 * Append the ReadModifiedAll-CCW.
	 * @return this instance for function call chaining.
	 */
	public DataOutStream3270 cmdReadModifiedAll() {
		this.appendByte(CommandCode3270.RMA);
		return this;
	}
	
	/*
	 * ---------------------------- 3270 Orders
	 */
	
	/**
	 * Append a SetBufferAddress order for the given screen position.
	 * @param row 1-based row number for the screen position.
	 * @param col 1-based column number for the screen position.
	 * @return this instance for function call chaining.
	 */
	public DataOutStream3270 setBufferAddress(int row, int col) {
		if ((this.currLength + 3) > this.maxLength) { return this; }
		this.appendByte(OrderCode3270.SBA);
		this.currLength = this.ba.encode(this.buffer, this.currLength, row, col, true);
		return this;
	}
	
	/**
	 * Append a RepeatToAddress order for the given screen position.
	 * @param row 1-based row number for the screen position.
	 * @param col 1-based column number for the screen position.
	 * @return this instance for function call chaining.
	 */
	public DataOutStream3270 repeatToAddress(int row, int col, byte repeatByte) {
		if ((this.currLength + 4) > this.maxLength) { return this; }
		this.appendByte(OrderCode3270.RA);
		this.currLength = this.ba.encode(this.buffer, this.currLength, row, col, true);
		this.appendByte(repeatByte);
		return this;
	}
	
	/**
	 * Append a EraseUnprotectedToAddress order for the given screen position.
	 * @param row 1-based row number for the screen position.
	 * @param col 1-based column number for the screen position.
	 * @return this instance for function call chaining.
	 */
	public DataOutStream3270 eraseUnprotectedToAddress(int row, int col) {
		if ((this.currLength + 3) > this.maxLength) { return this; }
		this.appendByte(OrderCode3270.EUA);
		this.currLength = this.ba.encode(this.buffer, this.currLength, row, col, true);
		return this;
	}
	
	/**
	 * Append an InsertCursor order.
	 * @return this instance for function call chaining.
	 */
	public DataOutStream3270 insertCursor() {
		this.appendByte(OrderCode3270.IC);
		this.ba.moveNext();
		return this;
	}
	
	private byte encodeFieldAttr(
			boolean isProtected, 
			boolean isNumeric, 
			boolean isIntensified, 
			boolean isInvisible, 
			boolean isModified) {
		byte attrValue = (byte)0x80;
		if (isProtected) { attrValue |= (byte)0x20; }
		if (isNumeric) { attrValue |= (byte)0x10; }
		if (isInvisible) { attrValue |= (byte)0x0C; } else if (isIntensified) { attrValue |= (byte)0x08; }
		if (isModified) { attrValue |= (byte)0x01; }
		return Ebcdic6BitEncoding.encode6BitValue(attrValue);
	}
	
	/**
	 * Append a StartField order with the given field attributes.
	 * @param isProtected Protected-flag for the field attribute.
	 * @param isNumeric Numeric-flag for the field attribute.
	 * @param isIntensified Intensified-flag for the field attribute.
	 * @param isInvisible Invisible-flag for the field attribute.
	 * @param isModified Modified-flag for the field attribute.
	 * @return this instance for function call chaining.
	 */
	public DataOutStream3270 startField(
			boolean isProtected, 
			boolean isNumeric, 
			boolean isIntensified, 
			boolean isInvisible, 
			boolean isModified) {
		byte attrs = this.encodeFieldAttr(isProtected, isNumeric, isIntensified, isInvisible, isModified);
		this.appendByte(OrderCode3270.SF);
		this.appendByte(attrs);
		this.ba.moveNext();
		
		return this;
	}
	
	/**
	 * Append a StartFieldExtended order with the given field attributes.
	 * <p>
	 * If the terminal does not support extended attributes, a StartField order is
	 * appended.
	 * @param isProtected Protected-flag for the field attribute.
	 * @param isNumeric Numeric-flag for the field attribute.
	 * @param isIntensified Intensified-flag for the field attribute.
	 * @param isInvisible Invisible-flag for the field attribute.
	 * @param isModified Modified-flag for the field attribute.
	 * @param highlight the highlight value to set for the field.
	 * @param color the color to set for the field.
	 * @return this instance for function call chaining.
	 */
	public DataOutStream3270 startFieldExtended(
			boolean isProtected, 
			boolean isNumeric, 
			boolean isIntensified, 
			boolean isInvisible, 
			boolean isModified,
			ExtHighlight3270 highlight,
			Color3270 color) {
		if (!this.canExtended) {
			return this.startField(isProtected, isNumeric, isIntensified, isInvisible, isModified);
		}
		byte pairCount = 1; // min. attrs!
		if (highlight != null) { pairCount++; }
		if (color != null) { pairCount++; }
		
		this.appendByte(OrderCode3270.SFE);
		this.appendByte(pairCount);

		byte attrs = this.encodeFieldAttr(isProtected, isNumeric, isIntensified, isInvisible, isModified);
		this.appendByte((byte)0xC0); // basic field attributes
		this.appendByte(attrs);
		
		if (highlight != null) {
			this.appendByte((byte)0x41); // extended highlighting
			this.appendByte(highlight.getCode());
		}
		
		if (color != null) {
			this.appendByte((byte)0x42); // extended color
			this.appendByte(color.getCode());
		}

		this.ba.moveNext();
		
		return this;
	}
	
	/**
	 * Append a SetAttribute order for the given highlight attribute.
	 * <p>
	 * The order is not added if  the terminal does not support extended
	 * attributes.
	 * @param highlight the extended highlight attribute to set.
	 * @return this instance for function call chaining.
	 */
	public DataOutStream3270 setAttributeHighlight(ExtHighlight3270 highlight) {
		if (!this.canExtended) { return this; }
		if (highlight == null) { return this; }
		this.appendByte(OrderCode3270.SA);
		this.appendByte((byte)0x41); // extended highlighting
		this.appendByte(highlight.getCode());
		return this;
	}
	
	/**
	 * Append a SetAttribute order for the given color attribute.
	 * <p>
	 * The order is not added if  the terminal does not support extended
	 * attributes.
	 * @param color the extended color attribute to set.
	 * @return this instance for function call chaining.
	 */
	public DataOutStream3270 setAttributeColor(Color3270 color) {
		if (!this.canExtended) { return this; }
		if (color == null) { return this; }
		this.appendByte(OrderCode3270.SA);
		this.appendByte((byte)0x42); // extended color
		this.appendByte(color.getCode());
		return this;
	}
	
	/**
	 * Append a SetAttribute order for the given background color attribute.
	 * <p>
	 * The order is not added if  the terminal does not support extended
	 * attributes.
	 * @param color the extended background color attribute to set.
	 * @return this instance for function call chaining.
	 */
	public DataOutStream3270 setAttributeBgColor(Color3270 color) {
		if (!this.canExtended) { return this; }
		if (color == null) { return this; }
		this.appendByte(OrderCode3270.SA);
		this.appendByte((byte)0x45); // extended background color
		this.appendByte(color.getCode());
		return this;
	}
	
	/**
	 * Append a SetAttribute order for resetting all extended attributes.
	 * <p>
	 * The order is not added if  the terminal does not support extended
	 * attributes.
	 * @return this instance for function call chaining.
	 */
	public DataOutStream3270 setAttributesToDefault() {
		if (!this.canExtended) { return this; }
		this.appendByte(OrderCode3270.SA);
		this.appendByte((byte)0x00);
		this.appendByte((byte)0x00);
		return this;
	}
	
	/*
	 * ---------------------------- text output
	 */
	
	/**
	 * The 3270 character set shift byte.
	 */
	public static final byte GraphicsEscape = (byte)0x08;
	
	/**
	 * Write an EBCDIC string at the current screen position. 
	 * @param eString the string to write.
	 * @return this instance for function call chaining.
	 */
	public DataOutStream3270 appendEbcdic(EbcdicHandler eString) {
		this.ba.moveNext(eString.getLength());
		this.currLength = eString.addTo(this.buffer, this.currLength);
		return this;
	}
	
	/**
	 * Write an EBCDIC character at the current screen position.
	 * @param ebcdicChar the character to write.
	 * @return this instance for function call chaining.
	 */
	public DataOutStream3270 appendEbcdic(byte ebcdicChar) {
		this.appendByte(ebcdicChar);
		return this;
	}
	
	/**
	 * Write an EBCDIC byte sequence at the current screen position
	 * @param fromBytes the byte array with the EBDDIC characers.
	 * @param offset first position in the byte sequence to write.
	 * @param length number of characters to write.
	 * @return this instance for function call chaining.
	 */
	public DataOutStream3270 appendEbcdic(byte[] fromBytes, int offset, int length) {
		if (offset < 0) { offset = 0; }
		if (offset >= fromBytes.length) { return this; }
		length = Math.min(fromBytes.length - offset, length);
		this.ba.moveNext(length);
		while(this.currLength < this.maxLength && length > 0) {
			this.buffer[this.currLength++] = fromBytes[offset++];
			length--;
		}
		return this;
	}
	
	/**
	 * Write EBCDIC bytes at the current screen position. 
	 * @param fromBytes the bytes to write.
	 * @return this instance for function call chaining.
	 */
	public DataOutStream3270 appendEbcdic(byte[] fromBytes) {
		return this.appendEbcdic(fromBytes, 0, fromBytes.length);
	}
	
	/**
	 * Write a Java (unicode) string at the current position.
	 * @param unicodeString the string to write.
	 * @return this instance for function call chaining.
	 */
	public DataOutStream3270 appendUnicode(String unicodeString) {
		this.currLength = this.ebcdicString
			.reset()
		    .appendUnicode(unicodeString)
			.addTo(this.buffer, this.currLength);
		this.ba.moveNext(unicodeString.length());
		return this;
	}
	
//	/*
//	 * ---------------------------- sending to terminal
//	 */
//	
//	/**
//	 * Write the 3270 output stream to the given output stream.
//	 * @param sink the output stream to write to.
//	 * @param reset if <code>true</code>, the internal buffer is cleared after
//	 * sending the 3270 output stream.
//	 * @throws IOException
//	 */
//	public void writeToSink(OutputStream sink, boolean reset) throws IOException {
//		if (this.currLength == 0) { return; }
//		logger.logHexBuffer("  sending 3270 data stream", null, this.buffer, this.currLength);
//		sink.write(this.buffer, 0, this.currLength);
//		sink.flush();
//		if (reset) { this.currLength = 0; }
//	}
//	
//	/**
//	 * Write the 3270 output stream to the given output stream.
//	 * @param sink the output stream to write to.
//	 * @throws IOException
//	 */
//	public void writeToSink(OutputStream sink) throws IOException {
//		this.writeToSink(sink, true);
//	}
	
	/*
	 * ---------------------------- consuming the data stream constructed so far
	 */
	
	@FunctionalInterface
	public interface Consumer {
		/**
		 * Process a 3270 data stream buffer and return if the buffer is to be cleared
		 * @param buffer buffer holding 3270 data
		 * @param length number of valid bytes
		 * @return reset the buffer?
		 * @throws Exception
		 */
		boolean process(byte[] buffer, int length) throws Exception;
	}
	
	public void consumeStream(Consumer consumer) throws Exception {
		if (consumer.process(this.buffer, this.currLength)) {
			this.currLength = 0;
		}
	}
	
}
