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
 * Representation of a mutable 3270 buffer address, supporting the translation between
 * (row,column)-positions and the absolute 3270 terminal buffer position, based
 * on the current geometry of the terminal (default size or size of the alternate
 * screen specified as screen rows and columns) and automatically switching between
 * the 12-bit and 14-bit addressing modes. 
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2011,2012
 */
public class BufferAddress extends Ebcdic6BitEncoding {

	private int row = 1;
	private int col = 1;
	
	private int termCols = 80;
	private int termRows = 24;
	private int termPositionCount = 1920; 
	
	private static final int buf12bitMax = 4095;
	private static final int buf14bitMax = 16383;
	
	private boolean mode14bit = false; // 14-bit addressing if 'termCols' * 'termRows' > 4095
	
	/**
	 * Encode the given (row,col) buffer position for the current screen size
	 * of the instance and append the resulting bytes to the buffer.
	 * <p>
	 * The current position of this instance is not affected by this method.
	 * @param buffer the byte buffer where to append the encoded buffer address.
	 * @param atOffset the offset position in the buffer where to append the encoded buffer address.
	 * @param row the row-part of the buffer address to encode.
	 * @param col the column-part of the buffer address to encode.
	 * @return the offset position in the buffer where to continue to append.
	 */
	public int encode(byte[] buffer, int atOffset, int row, int col) {
		return this.encode(buffer, atOffset, row, col, false);
	}
	
	/**
	 * Encode the given (row,col) buffer position for the current screen size
	 * of the instance and append the resulting bytes to the buffer, optionally setting
	 * the current (screen)buffer address of this instance.
	 * @param buffer the byte buffer where to append the encoded buffer address.
	 * @param atOffset the offset position in the buffer where to append the encoded buffer address.
	 * @param row the row-part of the buffer address to encode.
	 * @param col the column-part of the buffer address to encode.
	 * @return the offset position in the buffer where to continue to append.
	 */
	public int encode(byte[] buffer, int atOffset, int row, int col, boolean setToCurrent) {
		if (row < 1) { row = 1; }
		if (col < 1) { col = 1; }
		int position = ((row - 1) * this.termCols) + (col - 1);
		
		byte b0;
		byte b1;
		
		if (this.mode14bit) {
			if (position > buf14bitMax) { position = buf14bitMax; }
			b0 = (byte)((position & 0x3F00) >> 8);
			b1 = (byte) (position & 0xFF);
		} else {
			if (position > buf12bitMax) { position = buf12bitMax; }
			b0 = codes3270[position / 64];
			b1 = codes3270[position % 64];
		}
		
		buffer[atOffset] = b0;
		buffer[atOffset+1] = b1;
		
		if (setToCurrent) {
			this.row = row;
			this.col = col;
		}
		
		return atOffset + 2; // new offset in buffer
	}
	
	/**
	 * Decode the buffer address encoded by the 2 bytes passed and set the current
	 * (screen)buffer address of this instance.
	 * @param b0 first byte of the encoded address.
	 * @param b1 second byte of the encoded address.
	 */
	public void decode(byte b0, byte b1) {
		int position;
		if ((b0 & 0xC0) != 0) {
			position = (Ebcdic6BitEncoding.valueOf(b0) * 64) + Ebcdic6BitEncoding.valueOf(b1);
		} else {
			position = ((int)b0 << 8) | b1;
		}
		this.row = (position / this.termCols) + 1;
		this.col = (position % this.termCols) + 1;
	}
	
	/**
	 * Get the current terminal column-count of this instance.
	 * @return the column count used to encode buffer addresses.
	 */
	public int getTermCols() { 
		return this.termCols; 
	}
	
	/**
	 * Set the current terminal column-count used to encode buffer addresses
	 * for this instance.
	 * @param value the new column count to use.
	 */
	public void setTermCols(int value) { 
		this.termCols = value; 
		this.termPositionCount = this.termCols * this.termRows;
		this.mode14bit = (this.termPositionCount) > buf12bitMax;
	}
	
	/**
	 * Get the current terminal row-count of this instance.
	 * @return the row count used to encode buffer addresses.
	 */
	public int getTermRows() { 
		return this.termRows; 
	}
	
	/**
	 * Set the current terminal row-count used to encode buffer addresses
	 * for this instance.
	 * @param value the new row count to use.
	 */
	public void setTermRows(int value) { 
		this.termRows = value; 
		this.termPositionCount = this.termCols * this.termRows;
		this.mode14bit = (this.termPositionCount) > buf12bitMax;
	}
	
	/**
	 * Get the row number of the current (screen)buffer address of this instance.
	 * @return the current row position.
	 */
	public int getRow() {
		return this.row;
	}
	
	/**
	 * Get the column number of the current (screen)buffer address of this instance.
	 * @return the current column position.
	 */
	public int getCol() {
		return this.col;
	}
	
	/**
	 * Get the string representing the current (screen)buffer address of the instance, with
	 * a prepended prefix string.
	 * @param prefix the prefix to prepend.
	 * @return the string representation of the current address.
	 */
	public String getString(String prefix) {
		String str = (prefix != null) ? prefix : "";
		str += "[" + this.row + "," + this.col + "]";
		return str;
	}
	
	/**
	 * Get the string representing the current (screen)buffer address of the instance.
	 * @return the string representation of the current address.
	 */
	public String getString() {
		return this.getString(null);
	}
	
	/**
	 * Move the current (screen)buffer address to the next display position
	 * to the right along the display line, wrapping to the next line(s) or even to the
	 * screen's start (left upper corner) if necessary.
	 */
	public void moveNext() {
		this.col++;
		if (this.col > this.termCols) {
			this.col = 1;
			this.row++;
		}
		if (this.row > this.termRows) {
			this.row = 1;
		}
	}
	
	/**
	 * Move the current (screen)buffer address by the given display position number
	 * to the right along the display line, wrapping to the next line(s) or even to the
	 * screen's start (left upper corner) if necessary.
	 * @param positions the number of display positions to move the buffer address. 
	 */
	public void moveNext(int positions) {
		int currPos = ((this.row - 1) * this.termRows) + this.col - 1;
		int newPos = (currPos + positions) % (this.termPositionCount);
		if (newPos < 0) { newPos += (this.termRows * this.termCols); }

		this.row = (newPos / this.termCols) + 1;
		this.col = (newPos % this.termCols) + 1;
	}
	
	/**
	 * Place the buffer address at the given (row,col) position bound at the
	 * screen boundaries.
	 * @param col the column where to place the current buffer address. 
	 * @param row the row where to place the current buffer address.
	 */
	public void moveTo(int col, int row) {
		this.col = Math.min(1, Math.max(this.termCols, col));
		this.row = Math.min(1, Math.max(this.termRows, row));
	}
}
