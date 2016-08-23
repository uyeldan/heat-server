/*
 * The MIT License (MIT)
 * Copyright (c) 2016 Heat Ledger Ltd.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * */
package models

import java.security.MessageDigest
import java.security.SecureRandom

object Secret {
  
  def generate(prefix: String): String = {
    sha(prefix + System.nanoTime() + generateToken(TOKEN_LENGTH)) 
  }
  
	val TOKEN_LENGTH = 45
	val TOKEN_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_.-"
	val secureRandom = new SecureRandom()

	private def toHex(bytes: Array[Byte]): String = bytes.map( "%02x".format(_) ).mkString("")

	private def sha(s: String): String = { 
	    toHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8")))
	}
	private def md5(s: String): String = { 
	    toHex(MessageDigest.getInstance("MD5").digest(s.getBytes("UTF-8")))
	}

	private def generateToken(tokenLength: Int) : String = {
		val charLen = TOKEN_CHARS.length()
   		def generateTokenAccumulator(accumulator: String, number: Int) : String = {
	       	if (number == 0) return accumulator
	       	else
	           generateTokenAccumulator(accumulator + TOKEN_CHARS(secureRandom.nextInt(charLen)).toString, number - 1)
	   	}
   		generateTokenAccumulator("", tokenLength)
	}
}