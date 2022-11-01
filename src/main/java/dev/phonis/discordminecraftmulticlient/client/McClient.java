package dev.phonis.discordminecraftmulticlient.client;

import dev.phonis.discordminecraftmulticlient.DiscordMinecraftMultiClient;
import dev.phonis.discordminecraftmulticlient.auth.AuthUtil;
import dev.phonis.discordminecraftmulticlient.auth.Authenticator;
import dev.phonis.discordminecraftmulticlient.auth.SessionToken;
import dev.phonis.discordminecraftmulticlient.chat.ParseUtils;
import dev.phonis.discordminecraftmulticlient.protocol.DataTypes;
import dev.phonis.discordminecraftmulticlient.protocol.McSocket;
import dev.phonis.discordminecraftmulticlient.protocol.Packet;
import dev.phonis.discordminecraftmulticlient.util.LockUtils;
import dev.phonis.discordminecraftmulticlient.util.NameChangeHandler;
import dev.phonis.discordminecraftmulticlient.util.SafelyStoppableThread;

import javax.crypto.*;
import java.io.IOException;
import java.net.SocketException;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.DataFormatException;

public
class McClient
{

	public final  String                username;
	public final  String                password;
	public final  String                serverIP;
	public final  int                   port;
	private       boolean               validSession  = false;
	// used for atomic boolean toggle, sysOut%2 determining the boolean value
	private final AtomicLong            sysOut        = new AtomicLong(1);
	private final NameChangeHandler     nameChangeHandler;
	private       McSocket              socket;
	private       boolean               loginPhase;
	private       SafelyStoppableThread mainThread;
	private       SafelyStoppableThread senderThread;
	private final BlockingQueue<Packet> sendingQueue  = new LinkedBlockingQueue<>();
	private       SessionToken          sessionToken;
	private       String                playerName;
	private final ReentrantLock         nameLock      = new ReentrantLock();
	private final Condition             nameReady     = this.nameLock.newCondition();
	// prevents race conditions during the shutdown process
	private final ReentrantLock         shutdownLock  = new ReentrantLock();
	// prevents starting/stopping at same time
	private final ReentrantLock         startStopLock = new ReentrantLock();

	public
	McClient(String username, String password, String serverIP, int port, NameChangeHandler nameChangeHandler)
	{
		this.username          = username;
		this.password          = password;
		this.serverIP          = serverIP;
		this.port              = port;
		this.nameChangeHandler = nameChangeHandler;
	}

	public
	void toggleOut()
	{
		this.sysOut.incrementAndGet();
	}

	public
	String getPlayerName() throws InterruptedException
	{
		return LockUtils.getWithLockInterruptable(this.nameLock, () ->
		{
			while (this.playerName == null)
			{
				this.nameReady.await();
			}

			return this.playerName;
		});
	}

	public
	void queueMessage(String message) throws InterruptedException
	{
		DiscordMinecraftMultiClient.log(
			"Not yet implemented in > 1.19 since chat messages must be cryptographically signed.");
		// this.sendingQueue.put(new Packet(0x04, DataTypes.stringBytes(message)));
	}

	public
	void restartClient() throws InterruptedException
	{
		LockUtils.withLockInterruptable(this.startStopLock, () ->
		{
                /*
                stopClient joins with previous main thread, establishing happens-before relationship,
                meaning whatever internal state modified by the previous main thread, IE sessionToken,
                will be visible to the new main thread since it will be, transitively, in a happens-before
                relationship with the previous main thread by way of the calling thread.
                (old main -> calling thread -> new main)

                If stopClient and startClient are instead called separately, and by two different threads, the
                happens-before relationship will still be in place since startStopLock's release happens-before
                the startStopLock's subsequent acquisition.
                (old main -> stopClient's calling thread -> startClient's calling thread -> new main)

                This means no effort needs to be made to keep memory consistent across different lifetimes of
                the McClient, it will happen inherently by control of the client being restricted to the
                stopClient, startClient, and restartClient methods.
                 */
			this.stopClient();
			this.startClient();
		});
	}

	public
	void startClient()
	{
		LockUtils.withLock(this.startStopLock, () ->
		{
			this.mainThread = new SafelyStoppableThread(this::start);

			this.mainThread.start();
		});
	}

	public
	void stopClient() throws InterruptedException
	{
        /*
        Thread Closing
            ----shutdownSocket() ----------------> mainThread.interrupt()
                A                                  B
        Thread Main
            ------socket = new McSocket(..) -> socket.readPacket() -------
                  C                            D
        In this case (ACDB), where the main thread creates a new socket after the closing
        thread closes the current one, the next call to socket.readPacket() will block
        indefinitely (up to socket timeout) since neither an InterruptedException nor a SocketException will be thrown.
        To fix this, we need to lock the creation/closing of the socket and the setting/getting
        of the interrupted flag, before continuing with the creation of the socket.
        */
		LockUtils.withLockInterruptable(this.startStopLock, () ->
		{
			if (this.mainThread == null)
			{
				return; // client has not been started yet
			}

			LockUtils.withLockInterruptable(this.shutdownLock, () ->
			{
				this.shutdown();
				this.mainThread.interrupt();
			});

			this.mainThread.waitForCompletion();
		});
	}

	// should only return if thread is interrupted
	private
	void start()
	{
		try
		{
			this.mainLoop();
		}
		catch (InterruptedException ignored)
		{
		}
	}

	private
	void mainLoop() throws InterruptedException
	{
		boolean shouldRetry = true;

		while (!Thread.currentThread().isInterrupted() && shouldRetry)
		{
			try
			{
				this.connect();
			}
			catch (InterruptedException ignored)
			{
				throw new InterruptedException();
			}
			catch (SocketException ignored) // likely from shutdownSocket(), expected if McClient is stopped
			{
			}
			catch (CancellationException e)
			{
				e.printStackTrace();
				shouldRetry = false;
			}
			catch (Throwable e)
			{
				e.printStackTrace();
			}

			// throw error if interrupted, else shutdown resources before restarting
			LockUtils.withLockInterruptable(this.shutdownLock, () ->
			{
				this.checkInterrupted();
				this.shutdown();
			});
			Thread.sleep(10000);
		}
	}

	private
	void shutdown() throws InterruptedException
	{
		SafelyStoppableThread.stopAll(this.senderThread);
		this.shutdownSocket();
	}

	private
	void checkInterrupted() throws InterruptedException
	{
		if (Thread.currentThread().isInterrupted())
		{
			throw new InterruptedException();
		}
	}

	private
	void shutdownSocket()
	{
		try
		{
			if (this.socket != null && !this.socket.isClosed())
			{
				this.socket.close();
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	private
	void connect() throws ExecutionException, InterruptedException, IOException, DataFormatException,
						  InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException,
						  NoSuchAlgorithmException, InvalidKeySpecException, BadPaddingException, InvalidKeyException
	{
		this.loginPhase   = true;
		this.sessionToken = (this.validSession) ? this.sessionToken
												: Authenticator.getOrRefreshSession(this.sessionToken, this.username,
													this.password);
		String old = this.playerName;

		// let everyone know who is waiting on what this client's player name is
		LockUtils.withLock(this.nameLock, () ->
		{
			this.playerName = this.sessionToken.playerName;

			this.nameReady.signalAll();
		});
		if ((old == null || !old.equals(this.playerName)) && this.playerName != null)
		{
			this.nameChangeHandler.onNameChange(this, this.playerName, old);
		}

		DiscordMinecraftMultiClient.embedWithPlayer(this.sessionToken.playerName, "Auth",
			"Logged in as: " + this.sessionToken.playerName);
		LoginQueue.waitForTurn(); // avoid connection throttled
        /*
        Thread Closing
            ----shutdownSocket() ----------------> mainThread.interrupt()
                A                                  B
        Thread Main
            ------socket = new McSocket(..) -> socket.readPacket() -------
                  C                            D
        In this case (ACDB), where the main thread creates a new socket after the closing
        thread closes the current one, the next call to socket.readPacket() will block
        indefinitely since neither an InterruptedException nor a SocketException will be thrown.
        To fix this, we need to lock the creation/closing of the socket and the setting/getting
        of the interrupted flag, before continuing with the creation of the socket.
        */
		LockUtils.withLockInterruptableIO(this.shutdownLock, () ->
		{
			this.checkInterrupted();

			this.socket = new McSocket(this.serverIP, this.port);
		});

		if (!this.login(this.sessionToken))
		{
			return;
		}

		while (this.handlePacket(this.socket.readPacket()))
		{
		}
	}

	private
	boolean login(SessionToken sessionToken)
		throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException,
			   IllegalBlockSizeException, BadPaddingException, InvalidKeyException, InvalidAlgorithmParameterException,
			   DataFormatException, InterruptedException
	{
		byte[] protocolVersion = DataTypes.varIntBytes(759); // Protocol version number
		byte[] serverPort      = DataTypes.uShortBytes(this.port); // Port
		byte[] nextState       = DataTypes.varIntBytes(2); // 2 to initiate login phase, 1 for status phase
		byte[] handshakePacket = DataTypes.concatBytes(protocolVersion, DataTypes.stringBytes(this.serverIP),
			serverPort, nextState);

		this.socket.sendPacket(0x00, handshakePacket);

		byte[] playerName   = DataTypes.stringBytes(sessionToken.playerName);
		byte[] hasSigData   = DataTypes.booleanBytes(false);
		byte[] login_packet = DataTypes.concatBytes(playerName, hasSigData);

		this.socket.sendPacket(0x00, login_packet);

		while (true)
		{
			Packet packet = this.socket.readPacket();

			switch (packet.id)
			{
				case 0x00 ->
				{
					DiscordMinecraftMultiClient.log(
						"Login rejected " + this.playerName + ": " + DataTypes.getString(packet.inputStream));

					return false;
				}

				case 0x01 ->
				{
					String serverID  = DataTypes.getString(packet.inputStream);
					byte[] serverKey = DataTypes.getByteArray(packet.inputStream);
					byte[] token     = DataTypes.getByteArray(packet.inputStream);

					return this.initEncryption(serverID, serverKey, token, sessionToken);
				}

				case 0x02 ->
				{
					DiscordMinecraftMultiClient.log("Login successful " + this.playerName);

					this.loginPhase = false;

					return true;
				}

				default -> this.handlePacket(packet);
			}
		}
	}

	private
	void startSenderThread() throws InterruptedException
	{
		LockUtils.withLockInterruptable(this.shutdownLock, // don't start sender thread while client shutting down
			() ->
			{
				this.checkInterrupted();
				this.sendingQueue.clear();

				this.senderThread = new SafelyStoppableThread(() ->
				{
					while (!Thread.currentThread().isInterrupted())
					{
						try
						{
							Packet sendPacket = this.sendingQueue.take();

							if (!Thread.currentThread().isInterrupted())
							{
								this.socket.sendPacket(sendPacket);
							}
						}
						catch (IOException e)
						{
							e.printStackTrace();
						}
						catch (InterruptedException e)
						{
							break;
						}
					}
				});

				this.senderThread.start();
			});
	}

	private
	boolean handlePacket(Packet packet) throws IOException, InterruptedException
	{
		if (this.loginPhase)
		{
			switch (packet.id)
			{
				// Set compression
				case 0x03 -> this.socket.setCompressionThreshold(DataTypes.getVarInt(packet.inputStream));

				// Login plugin request
				case 0x04 ->
				{
					int messageID = DataTypes.getVarInt(packet.inputStream);

					this.socket.sendPacket(0x02,
						DataTypes.concatBytes(DataTypes.varIntBytes(messageID), DataTypes.booleanBytes(false)));
				}

				default -> DiscordMinecraftMultiClient.log("Unknown packet of ID: " + packet.id);
			}
		}
		else // play phase
		{
			switch (packet.id)
			{
				// Keep alive
				case 0x1E -> this.sendingQueue.put(new Packet(0x11, packet.inputStream.readAllBytes()));

				// Join game
				case 0x23 -> this.startSenderThread();

				// System chat message
				case 0x5F ->
				{
					String rawJson = DataTypes.getString(packet.inputStream);
					this.handleMessage(rawJson);
				}

				// Chat message
				case 0x30 ->
				{
					String  toProcess;
					String  rawJsonSigned = DataTypes.getString(packet.inputStream);
					boolean hasUnsigned   = DataTypes.getBoolean(packet.inputStream);
					if (hasUnsigned) // Prefer the unsigned, server modifiable message
					{
						toProcess = DataTypes.getString(packet.inputStream);
					}
					else
					{
						toProcess = rawJsonSigned;
					}
					if (!this.handleMessage(toProcess)) return false;
				}

				// Disconnect (play)
				case 0x17 ->
				{
					DiscordMinecraftMultiClient.log(DataTypes.getString(packet.inputStream));

					return false;
				}

				default ->
				{
					//                    DiscordMinecraftMultiClient.log(
					//                        "Unknown packet of ID: " + new BigInteger(String.valueOf(packet.id)).toString(16));
				}
			}
		}

		return true;
	}

	private
	boolean handleMessage(String rawJson) throws InterruptedException
	{
		String rawMessage = ParseUtils.getRawMessage(rawJson);
		if (rawMessage == null)
		{
			return true;
		}
		// if system output enabled
		if (this.sysOut.get() % 2 == 0)
		{
			DiscordMinecraftMultiClient.log(rawMessage);
		}
		// if (packet.inputStream.read() == 2 && rawMessage.contains("sleep")) return false;
		if (this.playerName.equals("RecallerBot") && (rawMessage.contains("recall") || rawMessage.contains("b"))) return false;
		if (rawMessage.contains("respawn"))
		{
			// Client status respawn request
			this.sendingQueue.put(new Packet(0x06, DataTypes.varIntBytes(0)));
		}
		return true;
	}

	private
	boolean initEncryption(String serverID, byte[] serverKey, byte[] token, SessionToken sessionToken)
		throws NoSuchAlgorithmException, InvalidKeySpecException, IOException, InvalidKeyException,
			   NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException,
			   InvalidAlgorithmParameterException, DataFormatException, InterruptedException
	{
		PublicKey    publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(serverKey));
		KeyGenerator keyGen    = KeyGenerator.getInstance("AES");

		keyGen.init(128);

		SecretKey secretKey  = keyGen.generateKey();
		String    serverHash = AuthUtil.getServerHash(serverID, publicKey.getEncoded(), secretKey.getEncoded());

		// don't want to do a session check if we don't have to since it is uninterruptible
		this.checkInterrupted();

		if (!AuthUtil.sessionCheck(serverHash, sessionToken.playerID, sessionToken.id))
		{
			DiscordMinecraftMultiClient.log("Failed to check session " + this.playerName);

			this.validSession = false;

			return false;
		}

		DiscordMinecraftMultiClient.log("Session checked " + this.playerName);

		this.validSession = true;
		Cipher encryptCipher = Cipher.getInstance("RSA");

		encryptCipher.init(Cipher.ENCRYPT_MODE, publicKey);

		byte[] keyEnc         = DataTypes.byteArrayBytes(encryptCipher.doFinal(secretKey.getEncoded()));
		byte[] hasVerifyToken = DataTypes.booleanBytes(true);
		byte[] tokenEnc       = DataTypes.byteArrayBytes(encryptCipher.doFinal(token));

		this.socket.sendPacket(0x01, DataTypes.concatBytes(keyEnc, hasVerifyToken, tokenEnc));
		this.socket.switchToEncrypted(secretKey);

		while (true)
		{
			Packet packet = this.socket.readPacket();

			if (packet.id < 0)
			{
				DiscordMinecraftMultiClient.log("LOGIN FAILED " + this.playerName);

				return false;
			}
			// Disconnect (login)
			else if (packet.id == 0x00)
			{
				DiscordMinecraftMultiClient.log(
					"LOGIN REJECTED " + this.playerName + ": " + DataTypes.getString(packet.inputStream));

				return false;
			}
			// Login success
			else if (packet.id == 0x02)
			{
				DiscordMinecraftMultiClient.log("LOGIN SUCCESS " + this.playerName);

				this.loginPhase = false;

				return true;
			}
			/*
			It seems that these packets may be received while initializing encryption, or later on.
			We delegate them to the normal handler.
			 */
			else
			{
				this.handlePacket(packet);
			}
		}
	}


}
