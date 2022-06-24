package dev.phonis.discordminecraftmulticlient.protocol;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public
class DataTypes
{

	public static
	int getVarInt(InputStream inputStream) throws IOException
	{
		int  decodedInt = 0;
		int  bitOffset  = 0;
		byte currentByte;

		do
		{
			currentByte = (byte) inputStream.read();
			decodedInt |= (currentByte & 0b01111111) << bitOffset;

			if (bitOffset == 35)
			{
				throw new RuntimeException("VarInt is too big");
			}

			bitOffset += 7;
		}
		while ((currentByte & 0b10000000) != 0);

		return decodedInt;
	}

	public static
	byte[] varIntBytes(int value)
	{
		List<Byte> bytes = new ArrayList<>();

		do
		{
			byte currentByte = (byte) (value & 0b01111111);
			value >>>= 7;

			if (value != 0)
			{
				currentByte |= 0b10000000;
			}

			bytes.add(currentByte);
		}
		while (value != 0);

		byte[] ret = new byte[bytes.size()];

		for (int i = 0; i < ret.length; i++)
		{
			ret[i] = bytes.get(i);
		}

		return ret;
	}

	public static
	boolean getBoolean(InputStream inputStream) throws IOException
	{
		return inputStream.read() != 0x00;
	}

	public static
	byte[] booleanBytes(boolean value)
	{
		return new byte[]{(byte) (value ? 0x01 : 0x00)};
	}

	public static
	byte[] uShortBytes(int value)
	{
		byte[] ret = new byte[2];
		ret[0] = (byte) value;
		ret[1] = (byte) (value >> 8);

		return ret;
	}

	public static
	int getUShort(InputStream inputStream) throws IOException
	{
		int firstByte  = inputStream.read();
		int secondByte = inputStream.read();

		return secondByte << 8 | firstByte;
	}

	public static
	byte[] stringBytes(String value)
	{
		byte[] stringBytes = value.getBytes(StandardCharsets.UTF_8);

		return DataTypes.concatBytes(DataTypes.varIntBytes(stringBytes.length), stringBytes);
	}

	public static
	byte[] getBytes(InputStream inputStream, int length) throws IOException
	{
		byte[] ret     = new byte[length];
		int    current = 0;

		while (current != ret.length)
		{
			int numRead = inputStream.read(ret, current, ret.length - current);

			if (numRead == -1)
			{
				throw new EOFException();
			}

			current += numRead;
		}

		return ret;
	}

	public static
	String getString(InputStream inputStream) throws IOException
	{
		return new String(DataTypes.getByteArray(inputStream), StandardCharsets.UTF_8);
	}

	public static
	byte[] byteArrayBytes(byte[] data)
	{
		return DataTypes.concatBytes(DataTypes.varIntBytes(data.length), data);
	}

	public static
	byte[] getByteArray(InputStream inputStream) throws IOException
	{
		int length = DataTypes.getVarInt(inputStream);

		return DataTypes.getBytes(inputStream, length);
	}

	public static
	byte[] concatBytes(byte[]... byteArrays)
	{
		int totalLength = 0;

		for (byte[] bytes : byteArrays)
		{
			totalLength += bytes.length;
		}

		byte[] ret     = new byte[totalLength];
		int    current = 0;

		for (byte[] bytes : byteArrays)
		{
			System.arraycopy(bytes, 0, ret, current, bytes.length);

			current += bytes.length;
		}

		return ret;
	}

}
