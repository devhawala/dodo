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
package dev.hawala.xns.level3.courier;

/**
 * Definition of source and sink objects for Courier (de)serializations.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public interface iWireStream {
	
	/*
	 * possible exceptions
	 */
	class EndOfMessageException extends Exception {
		private static final long serialVersionUID = 5305442387833815969L;
	};
	
	class AbortTransmissionException extends RuntimeException {
		private static final long serialVersionUID = -621513879061435486L;
	}
	
	class NoMoreWriteSpaceException extends Exception {
		private static final long serialVersionUID = -3671310520944364701L;
	}
	
	class SerializeException extends IllegalStateException {
		private static final long serialVersionUID = 8974283590937547915L;
		public SerializeException() { super() ; }
		public SerializeException(String msg) { super(msg) ; }
	}
	
	class DeserializeException extends IllegalStateException {
		private static final long serialVersionUID = -1491729973419132818L;
		public DeserializeException() { super() ; }
		public DeserializeException(String msg) { super(msg) ; }
	}
	
	
	/*
	 * input methods for deserializing
	 */
	
	/**
	 * Write an 48 bit long integer value.
	 * @param value
	 */
	void writeI48(long value) throws NoMoreWriteSpaceException;

	/**
	 * Write an 32 bit long value.
	 * @param value
	 */
	void writeI32(int value) throws NoMoreWriteSpaceException;
	
	/**
	 * Write a 16 bit long value.
	 * @param value
	 */
	void writeI16(int value) throws NoMoreWriteSpaceException;
	
	/**
	 * Write a 16 bit long value.
	 * @param value
	 */
	void writeS16(short value) throws NoMoreWriteSpaceException;
	
	/**
	 * Write a 8 bit long value. Writing a continuous sequence of 8 bit long
	 * quantities will alternatively write the upper resp. lower word of
	 * a 16 bit word to the stream, writing a not 8 bit quantity will possibly
	 * align to a 16t bit boundary by inserting an arbitrary 8 bit value.
	 * @param value
	 */
	void writeI8(int value) throws NoMoreWriteSpaceException;
	
	/**
	 * Write a 8 bit long value. Writing a continuous sequence of 8 bit long
	 * quantities will alternatively write the upper resp. lower word of
	 * a 16 bit word to the stream, writing a not 8 bit quantity will possibly
	 * align to a 16t bit boundary by inserting an arbitrary 8 bit value.
	 * @param value
	 */
	void writeS8(short value) throws NoMoreWriteSpaceException;
	
	/**
	 * Write a end-of-message to the stream 
	 */
	void writeEOM() throws NoMoreWriteSpaceException;
	
	/**
	 * Transmit the data written so far, not closing the current message
	 * but forcing the stream to a word boundary.
	 */
	void flush() throws NoMoreWriteSpaceException;
	
	/**
	 * Switch to stream type, implicitly sending an EOM of the stream type
	 * changes (resp. ignoring the request if already using this stream type).
	 */
	void beginStreamType(byte datastreamType) throws NoMoreWriteSpaceException;
	
	/**
	 * Reset the half-word synchronization mechanism for writing
	 * single bytes (@code writeI8()} resp. {@code writeS8()}). This
	 * method is intended for clients writing bulk-data directly
	 * at byte level for ensuring that next courier operations
	 * will not be misleaded if an odd-count of bytes were transmitted.
	 */
	void resetWritingToWordBoundary();
	
	
	/*
	 * input methods for deserializing
	 */
	
	/**
	 * Read a 48 bit integer value.
	 * @return
	 */
	long readI48() throws EndOfMessageException;
	
	/**
	 * Read a 32 bit integer value.
	 * @return
	 */
	int readI32() throws EndOfMessageException;
	
	/**
	 * Read a 16 bit integer value.
	 * @return
	 */
	int readI16() throws EndOfMessageException;
	
	/**
	 * Read a 16 bit integer value.
	 * @return
	 */
	short readS16() throws EndOfMessageException;
	
	/**
	 * Read a 8 bit integer value.  Reading a continuous sequence of 8 bit long
	 * quantities will alternatively read the upper resp. lower word of
	 * a 16 bit word to the stream, the first next not 8 bit quantity will possibly
	 * align to a 16 bit boundary by skipping 8 bit value.
	 * @return
	 */
	int readI8() throws EndOfMessageException;
	
	/**
	 * Read a 8 bit integer value.  Reading a continuous sequence of 8 bit long
	 * quantities will alternatively read the upper resp. lower word of
	 * a 16 bit word to the stream, the first next not 8 bit quantity will possibly
	 * align to a 16 bit boundary by skipping 8 bit value.
	 * @return
	 */
	short readS8() throws EndOfMessageException;
	
	/**
	 * Did the last read operation reach an end-of-message mark? If the 
	 * mark was present, it is consumed, allowing further read operations
	 * to wait for data coming from the remote end.
	 * @return {@code true} if the last read operation ended just before an
	 *    end-of-message.
	 */
	boolean isAtEnd();
	
	/**
	 * Did the last read operation reach an end-of-message mark? Even if
	 * at end, the mark is not consumed, allowing it to be rechecked
	 * elsewhere if necessary.
	 * @return {@code true} if the last read operation ended just before an
	 *    end-of-message.
	 */
	boolean checkIfAtEnd();
	
	/**
	 * Consume all data until the next EOM is received in the required byte datastreamType.
	 * 
	 *  @param reqDatastreamType the byte datastreamType in which the EOM must arrive.
	 */
	void dropToEOM(byte reqDatastreamType) throws EndOfMessageException;
	
	/**
	 * Get the current stream type.
	 * @return
	 */
	byte getStreamType();
	
	/**
	 * Transmit an abort request to the sending side as reading
	 * the current message resulted in an error so receiving more
	 * data makes no sense any more. 
	 */
	void sendAbort();
	
	/**
	 * Reset the half-word synchronization mechanism for reading
	 * single bytes (@code readI8()} resp. {@code readS8()}). This
	 * method is intended for clients reading bulk-data directly
	 * at byte level for ensuring that next courier operations
	 * will not be misleaded if an odd-count of bytes were transmitted.
	 */
	void resetReadingToWordBoundary();
	
	/*
	 * misc. functionality
	 */
	
	/**
	 * Return the host machine id of the communication partner on the
	 * other end of the wire.
	 *  
	 * @return the host id of the communication partner or {@code null}
	 * 		if this is not a network based wire connection or the remote
	 *      host-id is not known. 
	 */
	Long getPeerHostId();
}
