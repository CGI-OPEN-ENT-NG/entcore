/*
 * Copyright © "Open Digital Education", 2017
 *
 * This program is published by "Open Digital Education".
 * You must indicate the name of the software and the company in any production /contribution
 * using the software and indicate on the home page of the software industry in question,
 * "powered by Open Digital Education" with a reference to the website: https://opendigitaleducation.com/.
 *
 * This program is free software, licensed under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, version 3 of the License.
 *
 * You can redistribute this application and/or modify it since you respect the terms of the GNU Affero General Public License.
 * If you modify the source code and then use this modified source code in your creation, you must make available the source code of your modifications.
 *
 * You should have received a copy of the GNU Affero General Public License along with the software.
 * If not, please see : <http://www.gnu.org/licenses/>. Full compliance requires reading the terms of this license and following its directives.

 */

package org.entcore.workspace.service.impl;

import static fr.wseduc.webutils.Utils.handlerToAsyncHandler;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.vertx.core.json.JsonArray;
import org.entcore.common.bus.WorkspaceHelper;
import org.entcore.common.storage.Storage;
import org.entcore.common.storage.StorageFactory;
import org.entcore.common.user.UserUtils;
import org.vertx.java.busmods.BusModBase;

import com.sun.jna.Platform;

import fr.wseduc.webutils.collections.PersistantBuffer;
import fr.wseduc.webutils.data.ZLib;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import net.sf.lamejb.BladeCodecFactory;
import net.sf.lamejb.LamejbCodec;
import net.sf.lamejb.LamejbCodecFactory;
import net.sf.lamejb.LamejbConfig;
import net.sf.lamejb.impl.std.StreamEncoderWAVImpl;
import net.sf.lamejb.std.LameConfig;
import net.sf.lamejb.std.StreamEncoder;


public class AudioRecorderWorker extends BusModBase implements Handler<Message<JsonObject>> {

	private Storage storage;
	private WorkspaceHelper workspaceHelper;
	private final Map<String, Integer> sampleRates = new HashMap<>();
	private final Map<String, PersistantBuffer> buffers = new HashMap<>();
	private final Map<String, MessageConsumer<byte[]>> consumers = new HashMap<>();
	private final Set<String> disabledCompression = new HashSet<>();

	@Override
	public void start() {
		super.start();
		storage = new StorageFactory(vertx, config).getStorage();
		workspaceHelper = new WorkspaceHelper(vertx.eventBus(), storage);
		vertx.eventBus().localConsumer(AudioRecorderWorker.class.getSimpleName(), this);
	}

	@Override
	public void handle(Message<JsonObject> message) {
		final String action = message.body().getString("action", "");
		final String id = message.body().getString("id");
		switch (action) {
			case "open" :
				final String sampleRate = message.body().getString("sampleRate", "44100");
				sampleRates.put(id, Integer.valueOf(sampleRate));
				open(id, message);
				break;
			case "cancel":
				cancel(id, message);
				break;
			case "save":
				save(id, message);
				break;
			case "rawdata":
				disableCompression(id, message);
				break;
		}
	}

	private void disableCompression(String id, Message<JsonObject> message) {
		disabledCompression.add(id);
		sendOK(message);
	}

	private void save(final String id, final Message<JsonObject> message) {
		final JsonObject session = message.body().getJsonObject("session");
		final String name = message.body().getString("name", "Capture " + System.currentTimeMillis()) + ".mp3";
		final PersistantBuffer buffer = buffers.get(id);
		if (buffer != null) {
			buffer.getBuffer(new Handler<AsyncResult<Buffer>>() {
				@Override
				public void handle(AsyncResult<Buffer> buf) {
					try {
						final Integer sampleRate = sampleRates.getOrDefault(id,44100);
						storage.writeBuffer(id, toMp3(toWav(buf.result(),sampleRate), sampleRate), "audio/mp3", name, new Handler<JsonObject>() {
							@Override
							public void handle(JsonObject f) {
								if ("ok".equals(f.getString("status"))) {
									workspaceHelper.addDocument(f,
											UserUtils.sessionToUserInfos(session), name, "mediaLibrary",
											true, new JsonArray(), handlerToAsyncHandler(new Handler<Message<JsonObject>>() {
												@Override
												public void handle(Message<JsonObject> event) {
													if ("ok".equals(event.body().getString("status"))) {
														sendOK(message, event.body());
													} else {
														sendError(message, "workspace.add.error");
													}
												}
											}));
								} else {
									sendError(message, "write.file.error");
								}
								cancel(id, null);
							}
						});
					} catch (Exception e) {
						sendError(message, "encoding.file.error");
						cancel(id, null);
						logger.error("Error writing audio capture.", e);
					}
				}
			});
		} else {
			sendError(message, "missing.buffer.error");
		}
	}

	private void cancel(String id, Message<JsonObject> message) {
		disabledCompression.remove(id);
		sampleRates.remove(id);
		PersistantBuffer buffer = buffers.remove(id);
		if (buffer != null) {
			buffer.clear();
		}
		MessageConsumer<byte[]> consumer = consumers.remove(id);
		if (consumer != null) {
			consumer.unregister();
		}
		if (message != null) {
			sendOK(message);
		}
	}

	private void open(final String id, final Message<JsonObject> message) {
		Handler<Message<byte[]>> handler = new Handler<Message<byte[]>>() {
			@Override
			public void handle(Message<byte[]> chunk) {
				try {
					final PersistantBuffer buf = buffers.get(id);
					final Buffer tmp;
					if (disabledCompression.contains(id)) {
						tmp = Buffer.buffer(chunk.body());
					} else {
						tmp = Buffer.buffer(ZLib.decompress(chunk.body()));
					}
					if (buf != null) {
						buf.appendBuffer(tmp);
					} else {
						PersistantBuffer pb = new PersistantBuffer(vertx, tmp, id);
						pb.exceptionHandler(new Handler<Throwable>() {
							@Override
							public void handle(Throwable event) {
								logger.error("Error with PersistantBuffer " + id, event);
							}
						});
						buffers.put(id, pb);
					}
					chunk.reply(new JsonObject().put("status", "ok"));
				} catch (Exception e) {
					logger.error("Error receiving chunk.", e);
					chunk.reply(new JsonObject().put("status", "error")
							.put("message", "audioworker.chunk.error"));
				}
			}
		};
		MessageConsumer<byte[]> consumer = vertx.eventBus().localConsumer(AudioRecorderWorker.class.getSimpleName() + id, handler);
		consumers.put(id, consumer);
		sendOK(message);
	}


	private static Buffer toWav(Buffer data, Integer sampleRate) {
		Buffer wav = Buffer.buffer();
		wav.appendString("RIFF");
		wav.appendBytes(intToByteArray(44 + data.length()));
		wav.appendString("WAVE");
		wav.appendString("fmt ");
		wav.appendBytes(intToByteArray(16));
		wav.appendBytes(shortToByteArray((short) 1));
		wav.appendBytes(shortToByteArray((short) 2));
		wav.appendBytes(intToByteArray(sampleRate));
		wav.appendBytes(intToByteArray(sampleRate * 4));
		wav.appendBytes(shortToByteArray((short) 4));
		wav.appendBytes(shortToByteArray((short) 16));
		wav.appendString("data");
		wav.appendBytes(intToByteArray(data.length()));
		wav.appendBuffer(data);
		return wav;
	}

	private static byte[] shortToByteArray(short data) {
		return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(data).array();
	}

	private static byte[] intToByteArray(int i) {
		return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(i).array();
	}


	private static Buffer toMp3(Buffer wav, Integer sampleRate) {
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final ByteArrayInputStream bais = new ByteArrayInputStream(wav.getBytes());
		final LamejbConfig config = new LamejbConfig(sampleRate, 64, LamejbConfig.MpegMode.STEREO, true);
		if (Platform.isWindows()) {
			LamejbCodecFactory codecFactory = new BladeCodecFactory();
			LamejbCodec codec = codecFactory.createCodec();
			codec.encodeStream(bais, baos, config);
		} else {
			StreamEncoder encoder = new StreamEncoderWAVImpl(new BufferedInputStream(bais));
			LameConfig conf = encoder.getLameConfig();
			conf.setInSamplerate(config.getSampleRate());
			conf.setBrate(config.getBitRate());
			conf.setBWriteVbrTag(config.isVbrTag());
			conf.setMode(config.getMpegMode().lameMode());
			encoder.encode(new BufferedOutputStream(baos));
		}
		return Buffer.buffer(baos.toByteArray());
	}

}
