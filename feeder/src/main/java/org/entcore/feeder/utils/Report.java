/*
 * Copyright © WebServices pour l'Éducation, 2016
 *
 * This file is part of ENT Core. ENT Core is a versatile ENT engine based on the JVM.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 *
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with ENT Core is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of ENT Core, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package org.entcore.feeder.utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.entcore.common.email.EmailFactory;
import org.entcore.common.http.request.JsonHttpServerRequest;
import org.entcore.feeder.exceptions.TransactionException;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;

import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.webutils.I18n;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class Report {

	public static final Logger log = LoggerFactory.getLogger(Report.class);
	public static final String FILES = "files";
	public static final String PROFILES = "profiles";
	public final JsonObject result;
	private final I18n i18n = I18n.getInstance();
	public final String acceptLanguage;
	private long endTime;
	private long startTime;
	private String source;
	private Set<String> loadedFiles = new HashSet<>();

	public enum State {
		NEW, UPDATED, DELETED
	}

	public Report(String acceptLanguage) {
		this.acceptLanguage = acceptLanguage;
		final JsonObject errors = new JsonObject();
		final JsonObject files = new JsonObject();
		JsonObject ignored = new JsonObject();
		result = new JsonObject().put("errors", errors).put("files", files).put("ignored", ignored);
	}

	public Report addError(String error) {
		addErrorWithParams(error);
		return this;
	}

	public void addError(String file, String error) {
		addErrorByFile(file, error);
	}

	public void addErrorWithParams(String key, String... errors) {
		addErrorByFile("global", key, errors);
	}

	public void addFailedUser(String filename, String key, JsonObject props, String... errors) {
		final String file = "error." + filename;
		JsonArray f = result.getJsonObject("errors").getJsonArray(file);
		if (f == null) {
			f = new fr.wseduc.webutils.collections.JsonArray();
			result.getJsonObject("errors").put(file, f);
		}
		String error = i18n.translate(key, I18n.DEFAULT_DOMAIN, acceptLanguage, errors);
		props.put("error", error);
		f.add(props);
		log.error(error + " :\n" + Arrays.asList(props));
	}

	public void addErrorByFile(String filename, String key, String... errors) {
		final String file = "error." + filename;
		JsonArray f = result.getJsonObject("errors").getJsonArray(file);
		if (f == null) {
			f = new fr.wseduc.webutils.collections.JsonArray();
			result.getJsonObject("errors").put(file, f);
		}
		String error = i18n.translate(key, I18n.DEFAULT_DOMAIN, acceptLanguage, errors);
		f.add(error);
		log.error(error);
	}

	public void addSoftErrorByFile(String file, String key, String... errors) {
		JsonObject softErrors = result.getJsonObject("softErrors");
		if (softErrors == null) {
			softErrors = new JsonObject();
			result.put("softErrors", softErrors);
		}
		JsonArray f = softErrors.getJsonArray(file);
		if (f == null) {
			f = new fr.wseduc.webutils.collections.JsonArray();
			softErrors.put(file, f);
		}
		String error = i18n.translate(key, I18n.DEFAULT_DOMAIN, acceptLanguage, errors);
		f.add(error);
		log.error(error);
	}

	public void addUser(String file, JsonObject props) {
		JsonArray f = result.getJsonObject("files").getJsonArray(file);
		if (f == null) {
			f = new fr.wseduc.webutils.collections.JsonArray();
			result.getJsonObject("files").put(file, f);
		}
		f.add(props);
	}

	public void addProfile(String profile) {
		JsonArray f = result.getJsonArray(PROFILES);
		if (f == null) {
			f = new fr.wseduc.webutils.collections.JsonArray();
			result.put(PROFILES, f);
		}
		f.add(profile);
	}

	public void addIgnored(String file, String reason, JsonObject object) {
		JsonArray f = result.getJsonObject("ignored").getJsonArray(file);
		if (f == null) {
			f = new fr.wseduc.webutils.collections.JsonArray();
			result.getJsonObject("ignored").put(file, f);
		}
		f.add(new JsonObject().put("reason", reason).put("object", object));
	}

	public String translate(String key, String... params) {
		return i18n.translate(key, I18n.DEFAULT_DOMAIN, acceptLanguage, params);
	}

	public JsonObject getResult() {
		return result;
	}

	public void setUsersExternalId(JsonArray usersExternalIds) {
		result.put("usersExternalIds", usersExternalIds);
	}

	public JsonArray getUsersExternalId() {
		final JsonArray res = new fr.wseduc.webutils.collections.JsonArray();
		for (String f : result.getJsonObject("files").fieldNames()) {
			JsonArray a = result.getJsonObject("files").getJsonArray(f);
			if (a != null) {
				for (Object o : a) {
					if (!(o instanceof JsonObject))
						continue;
					final String externalId = ((JsonObject) o).getString("externalId");
					if (externalId != null) {
						res.add(externalId);
					}
				}
			}
		}
		return res;
	}

	public boolean containsErrors() {
		return result.getJsonObject("errors", new JsonObject()).size() > 0;
	}

	public void persist(Handler<Message<JsonObject>> handler) {
		cleanKeys();
		MongoDb.getInstance().save("imports", this.getResult(), handler);
	}

	protected void cleanKeys() {
	}

	public void setEndTime(long endTime) {
		this.endTime = endTime;
	}

	public void setStartTime(long startTime) {
		this.startTime = startTime;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public void loadedFile(String file) {
		loadedFiles.add(file);
	}

	private JsonObject cloneAndFilterResults(Optional<String> prefixAcademy) {
		JsonObject results = this.result.copy();
		if (prefixAcademy.isPresent()) {
			// filter each ignored object by externalId starting with academy name
			String prefix = prefixAcademy.get();
			JsonObject ignored = results.getJsonObject("ignored");
			Set<String> domains = ignored.fieldNames();
			for (String domain : domains) {
				JsonArray filtered = ignored.getJsonArray(domain, new JsonArray()).stream().filter(ig -> {
					if (ig instanceof JsonObject && ((JsonObject) ig).containsKey("object")) {
						JsonObject object = ((JsonObject) ig).getJsonObject("object");
						String externalId = object.getString("externalId");
						return StringUtils.startsWithIgnoreCase(externalId, prefix);
					} else {
						// keep in list because it is not a concerned object
						return true;
					}
				}).collect(JsonArray::new, JsonArray::add, JsonArray::addAll);//
				ignored.put(domain, filtered);
			}
			// userExternalIds FIltered
			JsonArray usersExternalIdsFiltered = results.getJsonArray("usersExternalIds", new JsonArray()).stream()
					.filter(value -> {
						return (value instanceof String && StringUtils.startsWithIgnoreCase((String) value, prefix));
					}).collect(JsonArray::new, JsonArray::add, JsonArray::addAll);//
			results.put("usersExternalIds", usersExternalIdsFiltered);
		}
		return results;
	}

	private JsonArray cloneAndFilterFiles(Optional<String> academyPrefix) {
		List<String> filtered = null;
		if (academyPrefix.isPresent()) {
			String pattern = academyPrefix.get();
			filtered = loadedFiles.stream().filter(file -> StringUtils.contains(file, "/" + pattern + "/")).sorted()
					.collect(Collectors.toList());
		} else {
			filtered = loadedFiles.stream().sorted().collect(Collectors.toList());
		}
		return new fr.wseduc.webutils.collections.JsonArray(filtered);
	}

	private void countDiff(Optional<String> prefixAcademy, final Handler<JsonObject> handler) {
		try {
			TransactionHelper tx = TransactionManager.getTransaction();
			JsonObject params = new JsonObject().put("source", source).put("start", startTime).put("end", endTime)
					.put("startTime", new DateTime(startTime).toString())
					.put("endTime", new DateTime(endTime).toString());
			if (prefixAcademy.isPresent()) {
				params.put("prefixAcademy", prefixAcademy.get());
			}
			tx.add("MATCH (u:User {source:{source}}) "
					+ "WHERE HAS(u.created) AND u.created >= {startTime} AND u.created < {endTime} "
					+ (prefixAcademy.isPresent() ? " AND u.externalId STARTS WITH {prefixAcademy} " : "")//
					+ "RETURN count(*) as createdCount", params);
			tx.add("MATCH (u:User {source:{source}}) "
					+ "WHERE HAS(u.modified) AND u.modified >= {startTime} AND u.modified < {endTime} "
					+ (prefixAcademy.isPresent() ? " AND u.externalId STARTS WITH {prefixAcademy} " : "")//
					+ "RETURN count(*) as modifiedCount", params);
			tx.add("MATCH (u:User {source:{source}}) "
					+ "WHERE HAS(u.disappearanceDate) AND u.disappearanceDate >= {start} AND u.disappearanceDate < {end} "
					+ (prefixAcademy.isPresent() ? " AND u.externalId STARTS WITH {prefixAcademy} " : "")//
					+ "RETURN count(*) as disappearanceCount", params);
			tx.commit(new Handler<Message<JsonObject>>() {
				@Override
				public void handle(Message<JsonObject> event) {
					JsonArray results = event.body().getJsonArray("results");
					if ("ok".equals(event.body().getString("status")) && results != null && results.size() == 3) {
						try {
							final JsonObject result = cloneAndFilterResults(prefixAcademy);
							int created = results.getJsonArray(0).getJsonObject(0).getInteger("createdCount");
							int modified = results.getJsonArray(1).getJsonObject(0).getInteger("modifiedCount");
							int disappearance = results.getJsonArray(2).getJsonObject(0)
									.getInteger("disappearanceCount");
							result.put("userCount", new JsonObject().put("created", created)
									.put("modified", (modified - created)).put("disappearance", disappearance));
							result.put("source", source);
							result.put("startTime", new DateTime(startTime).toString());
							result.put("endTime", new DateTime(endTime).toString());
							result.put("loadedFiles", cloneAndFilterFiles(prefixAcademy));
							handler.handle(result);
//							persist(new Handler<Message<JsonObject>>() {
//								@Override
//								public void handle(Message<JsonObject> event) {
//									if (!"ok".equals(event.body().getString("status"))) {
//										log.error("Error persist report : " + event.body().getString("message"));
//									}
//								}
//							});
						} catch (RuntimeException e) {
							log.error("Error parsing count diff response.", e);
							handler.handle(null);
						}
					} else {
						log.error("Error in count diff transaction.");
						handler.handle(null);
					}
				}
			});
		} catch (TransactionException e) {
			log.error("Exception in count diff transaction.", e);
			if (handler != null) {
				handler.handle(null);
			}
		}
	}

	private void emailReport(final Vertx vertx, final EmailFactory emailFactory, final JsonObject sendReport,
			final JsonObject result) {
		final JsonObject reqParams = new JsonObject().put("headers",
				new JsonObject().put("Accept-Language", acceptLanguage));
		emailFactory.getSender().sendEmail(new JsonHttpServerRequest(reqParams),
				sendReport.getJsonArray("to").getList(),
				sendReport.getJsonArray("cc") != null ? sendReport.getJsonArray("cc").getList() : null,
				sendReport.getJsonArray("bcc") != null ? sendReport.getJsonArray("bcc").getList() : null,
				sendReport.getString("project", "")
						+ i18n.translate("import.report", I18n.DEFAULT_DOMAIN, acceptLanguage) + " - "
						+ DateTime.now().toString(DateTimeFormat.forPattern("yyyy-MM-dd")),
				"email/report.html", result, false, ar -> {
					if (ar.failed()) {
						log.error("Error sending report email.", ar.cause());
					}
				});
	}

	public void sendEmails(final Vertx vertx, final JsonObject config) {
		final JsonArray sendReport = config.getJsonArray("sendReport");
		if (sendReport == null) {
			return;
		}
		int count = sendReport.size();
		EmailFactory emailFactory = new EmailFactory(vertx, config);
		for (Object o : sendReport) {
			JsonObject currentSendReport = (JsonObject) o;
			if (currentSendReport.getJsonArray("to") == null //
					|| currentSendReport.getJsonArray("to").size() == 0 //
					|| currentSendReport.getJsonArray("sources") == null//
					|| !currentSendReport.getJsonArray("sources").contains(source)) {
				break;
			}
			if (count == 1) {
				this.countDiff(Optional.empty(), countEvent -> {
					if (countEvent != null) {
						this.emailReport(vertx, emailFactory, currentSendReport, countEvent);
					}
				});
			} else {
				String prefixAcademy = currentSendReport.getString("academyPrefix");
				this.countDiff(Optional.ofNullable(prefixAcademy), countEvent -> {
					if (countEvent != null) {
						this.emailReport(vertx, emailFactory, currentSendReport, countEvent);
					}
				});
			}
		}
	}

}
