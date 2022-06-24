package dev.phonis.discordminecraftmulticlient.protocol;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.zip.DataFormatException;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;

public
class McSocket
{

	private final Socket       socket;
	private       InputStream  inputStream;
	private       OutputStream outputStream;
	private       int          compressionThreshold = 0;

	public
	McSocket(String serverIP, int port) throws IOException
	{
		this.socket = new Socket();

		this.socket.setReceiveBufferSize(1024 * 1024);
		this.socket.setSoTimeout(30000);
		this.socket.connect(new InetSocketAddress(serverIP, port));

		this.inputStream  = this.socket.getInputStream();
		this.outputStream = this.socket.getOutputStream();
	}

	public
	void sendPacket(Packet packet) throws IOException
	{
		this.sendPacket(packet.id, packet.inputStream.readAllBytes());
	}

	public
	void sendPacket(int id, byte[] packet) throws IOException
	{
		byte[] combined = DataTypes.concatBytes(DataTypes.varIntBytes(id), packet);

		if (this.compressionThreshold > 0)
		{
			if (combined.length >= this.compressionThreshold)
			{
				ByteArrayOutputStream memoryStream = new ByteArrayOutputStream(combined.length);
				OutputStream          zipper       = new DeflaterOutputStream(memoryStream);

				zipper.write(combined);
				zipper.close();

				combined = DataTypes.concatBytes(DataTypes.varIntBytes(combined.length), memoryStream.toByteArray());
			}
			else
			{
				combined = DataTypes.concatBytes(DataTypes.varIntBytes(0), combined);
			}
		}

		this.outputStream.write(DataTypes.concatBytes(DataTypes.varIntBytes(combined.length), combined));
		this.outputStream.flush();
	}

	public
	Packet readPacket() throws IOException, DataFormatException
	{
		int    length = DataTypes.getVarInt(this.inputStream);
		byte[] data   = DataTypes.getBytes(this.inputStream, length);

		if (this.compressionThreshold > 0)
		{
			InputStream raw                = new ByteArrayInputStream(data);
			int         uncompressedLength = DataTypes.getVarInt(raw);

			if (uncompressedLength != 0)
			{
				byte[]   compressed   = raw.readAllBytes();
				byte[]   output       = new byte[uncompressedLength];
				Inflater decompressor = new Inflater();

				decompressor.setInput(compressed, 0, compressed.length);
				decompressor.inflate(output);
				decompressor.end();

				data = output;
			}
			else
			{
				data = raw.readAllBytes();
			}
		}

		return new Packet(data);
	}

	public
	void switchToEncrypted(SecretKey secretKey)
		throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, InvalidAlgorithmParameterException
	{
		Cipher          encryptCipher = Cipher.getInstance("AES/CFB8/NoPadding");
		Cipher          decryptCipher = Cipher.getInstance("AES/CFB8/NoPadding");
		IvParameterSpec params        = new IvParameterSpec(secretKey.getEncoded());

		encryptCipher.init(Cipher.ENCRYPT_MODE, secretKey, params);
		decryptCipher.init(Cipher.DECRYPT_MODE, secretKey, params);

		this.inputStream  = new CipherInputStream(this.inputStream, decryptCipher);
		this.outputStream = new CipherOutputStream(this.outputStream, encryptCipher);
	}

	public
	void setCompressionThreshold(int compressionThreshold)
	{
		this.compressionThreshold = compressionThreshold;
	}

	public
	void close() throws IOException
	{
		this.socket.close();
	}

	public
	boolean isClosed()
	{
		return this.socket.isClosed();
	}

}
