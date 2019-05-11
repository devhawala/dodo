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

package dev.hawala.xns.level4.common;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * DES encryption/decryption utilities for XNS strong authentication.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public class StrongAuthUtils {
	
	/** DES "Electronic Code Book" (ECB) encoder/decoder for a 64 bit data block with a 64 bit key block */
	public static class ECBProcessor {
		
		private final Cipher cipher;
		
		private ECBProcessor(int mode, byte[] keyBytes) throws Exception {
			if (mode != Cipher.ENCRYPT_MODE && mode != Cipher.DECRYPT_MODE) {
				throw new IllegalArgumentException("Argument 'mode must be either Cipher.ENCRYPT_MODE or Cipher.DECRYPT_MODE");
			}
			if (keyBytes == null || keyBytes.length != 8) {
				throw new IllegalArgumentException("Argument 'key' must be 8 bytes (64 bits)");
			}
			SecretKeySpec key = new SecretKeySpec(keyBytes, "DES");
			this.cipher = Cipher.getInstance("DES/ECB/NoPadding"); // padding to 64 bit multiples is done externally!
			cipher.init(mode, key);
		}
		
		/**
		 * Process a single 64 bit data block.
		 * @param data the data block to be processed (must be 8 bytes)
		 * @return the transformed data (8 bytes)
		 * @throws Exception
		 */
		public byte[] process(byte[] data) throws Exception {
			if (data == null || data.length != 8) {
				throw new IllegalArgumentException("Argument 'data' must be 8 bytes (64 bits)");
			}
			int transformedLength = cipher.getOutputSize(data.length);
			byte[] transformedData = new byte[transformedLength];
			int tl1 = cipher.update(data,  0, data.length, transformedData, 0);
			int tl2 = tl1 + cipher.doFinal(transformedData, tl1);
			return transformedData;
		}
		
		/**
		 * Process a single 64 bit data block.
		 * @param data the data block to be processed (must be 4 ints interpreted as 16 bit unsigned quantities, i.e. the lower 16 bits will be used)
		 * @return the transformed data (8 bytes)
		 * @throws Exception
		 */
		public byte[] process(int[] data) throws Exception {
			return this.process(toBytes(data));
		}
	}
	
	/**
	 * Create an DES "Electronic Code Book" (ECB) encrypter for a 64 bit data blocks with a 64 bit key block
	 * 
	 * @param keyBytes the key for processing (must be 8 bytes)
	 * @return the encryption processor
	 * @throws Exception
	 */
	public static ECBProcessor getEncrypter(byte[] keyBytes) throws Exception {
		return new ECBProcessor(Cipher.ENCRYPT_MODE, keyBytes);
	}

	/**
	 * Create an DES "Electronic Code Book" (ECB) encrypter for a 64 bit data blocks with a 64 bit key block
	 * 
	 * @param keyBytes the key for processing (must be 4 ints interpreted as 16 bit unsigned quantities, i.e. the lower 16 bits will be used)
	 * @return the encryption processor
	 * @throws Exception
	 */
	public static ECBProcessor getEncrypter(int[] keyWords) throws Exception {
		return new ECBProcessor(Cipher.ENCRYPT_MODE, toBytes(keyWords));
	}
	
	/**
	 * Create an DES "Electronic Code Book" (ECB) decrypter for a 64 bit data blocks with a 64 bit key block
	 * 
	 * @param keyBytes the key for processing (must be 8 bytes)
	 * @return the encryption processor
	 * @throws Exception
	 */
	public static ECBProcessor getDecrypter(byte[] keyBytes) throws Exception {
		return new ECBProcessor(Cipher.DECRYPT_MODE, keyBytes);
	}
	
	/**
	 * Create an DES "Electronic Code Book" (ECB) decrypter for a 64 bit data blocks with a 64 bit key block
	 * 
	 * @param keyBytes the key for processing (must be 4 ints interpreted as 16 bit unsigned quantities, i.e. the lower 16 bits will be used)
	 * @return the encryption processor
	 * @throws Exception
	 */
	public static ECBProcessor getDecrypter(int[] keyWords) throws Exception {
		return new ECBProcessor(Cipher.DECRYPT_MODE, toBytes(keyWords));
	}

	/**
	 * Generate the strong key for a password.
	 * <p>
	 * Requirement: only ascii chars, or it will fail as Xerox Character set is not supported.
	 * </p>
	 * 
	 * @param password
	 * 		the password to encode
	 * @param asSpecified
	 * 		if {@code true} encode each 4 char-block with the password to produce the next
	 * 		password (as specified, but this does match <i>not</i> the data in the example), else (if
	 * 		{@code false}) exchange the encryption parameters, i.e. use each 4 char-block to
	 * 		encrypt the password of the last iteration to produce the new password (this
	 * 		contradicts the specification, but creates the data in the example...)   
	 * @return the 8 bytes strong key for the password
	 * @throws Exception
	 */
	public static byte[] getStrongKey(String password, boolean asSpecified) throws Exception {
		if (password == null || password.isEmpty()) {
			throw new IllegalArgumentException("Password may not be null or empty");
		}
		
		byte[] key = { 0, 0, 0, 0, 0, 0, 0, 0 }; // initial strong key
		
		int pwLen = password.length();
		int pwPos = 0;
		while(pwPos < pwLen) {
			// get next unified 4 character chunk
			int[] data = { 0, 0, 0, 0 };
			int usedCount = 0;
			for (int i = pwPos; i < Math.min(pwPos + 4, pwLen); i++) {
				char c = password.charAt(i);
				if (c >= 'A' && c <= 'Z') { c = Character.toLowerCase(c); }
				data[usedCount] = c & 0xFF;
				usedCount++;
			}
			pwPos += 4;
			
			// create next key with this chunk
			// ==> question: what is wrong in section 5.3: specification part or example part ??
			if (asSpecified) {
				key = getEncrypter(key).process(data); // <= encryption as specified in 5.3 of the Authentication document, but does NOT produce the values in the example!
			} else {
				key = getEncrypter(data).process(key); // <= parameters swapped (i.e. != specification), but this DOES return the values in the example!
			}
		}
		
		// set parities of each byte to odd
		for (int i = 0; i < key.length; i++) {
			key[i] = setOddParityByte(key[i]);
		}
		
		// done
		return key;
	}
	
	/**
	 * Get the 8 bytes for 4 words.
	 * @param data 4 (16 bit) words
	 * @return 8 bytes for {@code data}
	 */
	public static byte[] toBytes(int[] data) {
		if (data == null || data.length != 4) {
			throw new IllegalArgumentException("Argument 'data' must be 4 words (64 bits)");
		}
		byte[] bdata = new byte[8];
		int b = 0;
		for (int i = 0; i < 4; i++) {
			int w = data[i];
			bdata[b++] = (byte)((w >> 8) & 0xFF);
			bdata[b++] = (byte)(w & 0xFF);
		}
		return bdata;
	}
	
	/**
	 * Get the 4 words for 8 bytes.
	 * @param data 8 bytes to convert
	 * @return 4 (16 bit) words for {@code data}
	 */
	public static int[] toWords(byte[] data) {
		if (data == null || data.length != 8) {
			throw new IllegalArgumentException("Argument 'data' must be 4 words (64 bits)");
		}
		int[] wData = new int[4];
		int b = 0;
		for (int i = 0; i < 4; i++) {
			int hi = (data[b++] & 0xFF) << 8;
			int lo = data[b++] & 0xFF;
			wData[i] = hi | lo;
		}
		return wData;
	}
	
	private static long seed = System.currentTimeMillis();
	
	/**
	 * Create a pseudo-random conversation key (4 words) with the
	 * parity-bits a byte level.
	 *  
	 * @return new conversation key
	 */
	public static synchronized int[] createConversationKey() {
		long millisecs = seed++;
		long nanosecs = System.nanoTime();
		long k = millisecs * nanosecs * (nanosecs + millisecs);
		
		int[] key = new int[4];
		key[0] = setOddParityWord((int)((k >>> 48) & 0xFFFFL));
		key[1] = setOddParityWord((int)((k >>> 32) & 0xFFFFL));
		key[2] = setOddParityWord((int)((k >>> 16) & 0xFFFFL));
		key[3] = setOddParityWord((int)(k & 0xFFFFL));
		return key;
	}
	
	/**
	 * Return byte value with the parity bit set as specified.
	 *  
	 * @param b input byte
	 * @return value with odd number of 1's by changing the lowest bit
	 */
	public static byte setOddParityByte(byte b) {
		if ((countOnes(b) % 2) == 0) {
			// even number of 1's => invert least significant bit
			b ^= 0x01;
		}
		return b;
	}
	
	/**
	 * Return word value with parity bits set for each byte in the word.
	 * 
	 * @param w 16 bit input value
	 * @return word value with parity bits set for each byte in the word
	 */
	public static int setOddParityWord(int w) {
		byte hi = (byte)((w >> 8) & 0xFF);
		byte lo = (byte)(w & 0xFF);
		hi = setOddParityByte(hi);
		lo = setOddParityByte(lo);
		return ((hi << 8) & 0xFF00) | (lo & 0xFF); 
	}
	
	/**
	 * Check if the number of 1's in the byte is odd.
	 * 
	 * @param b the byte to test
	 * @return {@code true} if the number of 1's is odd.
	 */
	public static boolean isOddParityOk(byte b) {
		return ((countOnes(b) % 2) == 1);
	}
	
	/**
	 * Encrypt a block of data with the given key using the Xerox DES variant.
	 * 
	 * @param key the encryption key
	 * @param data the data block
	 * @return the encrypted data
	 * @throws Exception
	 */
	public static int[] xnsDesEncrypt(int[] key, int[] data) throws Exception {
		return xnsDesEncrypt(toBytes(key), data);
	}
	
	/**
	 * Encrypt a block of data with the given key using the Xerox DES variant.
	 * 
	 * @param key the encryption key
	 * @param data the data block
	 * @return the encrypted data
	 * @throws Exception
	 */
	public static int[] xnsDesEncrypt(byte[] key, int[] data) throws Exception {
		if (data == null || data.length == 0) {
			throw new IllegalArgumentException("Argument 'data' may not be null or empty");
		}
		
		ECBProcessor processor = getEncrypter(key);
		int[] checksum = { 0, 0, 0, 0 };
		int[] inBlock = { 0, 0, 0, 0 };
		int[] lastOutBlock = { 0, 0, 0, 0 }; // this is the initialization vector in case of the first block
		byte[] outBlock;
		
		int[] result = new int[((data.length + 3) / 4) * 4];
		int resultPos = 0;
		
		int dataPos = 0;
		int remaining = data.length;
		while(remaining > 0) {
			// build input block
			for (int i = 0; i < 4; i++, dataPos++) {
				if (dataPos < data.length) {
					inBlock[i] = data[dataPos];
				} else {
					inBlock[i] = 0;
				}
			}
			
			// handle checksum: cumulate if not the last block, else xor the last block with the checksum
			if (remaining > 4) {
				// not the last block
				xor(inBlock, checksum); // inBlock ^ checksum -> checksum
			} else {
				// last block: apply current checksum to inBlock before processing it 
				xor(checksum, inBlock); // checksum ^ inBlock -> inBlock
			}
			
			// block chaining xor
			xor(lastOutBlock, inBlock);
			
			// encrypt the current block and append it to the result
			outBlock = processor.process(inBlock);
			lastOutBlock = toWords(outBlock);
			for (int i = 0; i < lastOutBlock.length; i++) {
				result[resultPos++] = lastOutBlock[i];
			}
			
			// this block is done
			remaining -= 4;
		}
		
		return result;
	}
	
	/**
	 * Decrypt a block of data with the given key using the Xerox DES variant.
	 * 
	 * @param key the encryption key
	 * @param data the data block
	 * @return the decrypted data
	 * @throws Exception
	 */
	public static int[] xnsDesDecrypt(int[] key, int[] data) throws Exception {
		if (data == null || data.length == 0) {
			throw new IllegalArgumentException("Argument 'data' may not be null or empty");
		}
		
		ECBProcessor processor = getDecrypter(key);
		int[] checksum = { 0, 0, 0, 0 };
		int[] inBlock = { 0, 0, 0, 0 };
		int[] lastInBlock = { 0, 0, 0, 0 }; // this is the initialization vector in case of the first block
		int[] outBlock;
		
		int[] result = new int[((data.length + 3) / 4) * 4]; // data.length should always be a multiple of 4 for encrypted data, but who knows
		int resultPos = 0;
		
		int dataPos = 0;
		int remaining = data.length;
		while(remaining > 0) {
			// build input block
			for (int i = 0; i < 4; i++, dataPos++) {
				if (dataPos < data.length) {
					inBlock[i] = data[dataPos];
				} else {
					inBlock[i] = 0;
				}
			}
			
			// decrypt the block
			outBlock = toWords(processor.process(inBlock));
			
			// block chaining xor and append it to the result
			xor(lastInBlock, outBlock); // lastInBlock ^ outBlock -> ouBlock
			
			// remember the inBlock just decrypted for next block
			for (int i = 0; i < 4; i++) {
				lastInBlock[i] = inBlock[i];
			}
			
			// handle checksum: cumulate if not the last block, else xor the last block with the checksum
			if (remaining > 4) {
				// not the last block
				xor(outBlock, checksum); // outBlock ^ checksum -> checksum
			} else {
				// last block: apply current checksum to inBlock before processing it 
				xor(checksum, outBlock); // checksum ^ outBlock -> outBlock
			}
			
			// append current outBlock to the result
			for (int i = 0; i < outBlock.length; i++) {
				result[resultPos++] = outBlock[i];
			}
			
			// this block is done
			remaining -= 4;
		}
		
		return result;	
	}
	
	
	/*
	 * internal procedures
	 */
	
	private static void xor(int[] from, int[] into) {
		for (int i = 0; i < into.length; i++) {
			into[i] ^= from[i];
		}
	}
	
	private static int countOnes(byte b) {
		int ones = 0;
		if ((b & 0x80) != 0) { ones++; }
		if ((b & 0x40) != 0) { ones++; }
		if ((b & 0x20) != 0) { ones++; }
		if ((b & 0x10) != 0) { ones++; }
		if ((b & 0x08) != 0) { ones++; }
		if ((b & 0x04) != 0) { ones++; }
		if ((b & 0x02) != 0) { ones++; }
		if ((b & 0x01) != 0) { ones++; }
		return ones;
	}
	
}
