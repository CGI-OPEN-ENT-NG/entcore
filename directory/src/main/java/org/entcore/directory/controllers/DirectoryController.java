/* Copyright © "Open Digital Education", 2014
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

 *
 */

package org.entcore.directory.controllers;

import fr.wseduc.bus.BusAddress;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.MfaProtected;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.http.BaseController;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.security.BCrypt;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.appregistry.ApplicationUtils;
import org.entcore.common.bus.BusResponseHandler;
import org.entcore.common.events.EventStore;
import org.entcore.common.events.EventStoreFactory;
import org.entcore.common.http.filter.AdminFilter;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.http.filter.SuperAdminFilter;
import org.entcore.common.neo4j.Neo;
import org.entcore.common.user.position.UserPositionService;
import org.entcore.directory.security.AdmlOfStructuresByExternalId;
import org.entcore.directory.services.*;
import org.vertx.java.core.http.RouteMatcher;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static fr.wseduc.webutils.Utils.getOrElse;
import static fr.wseduc.webutils.Utils.handlerToAsyncHandler;
import static fr.wseduc.webutils.request.RequestUtils.bodyToJson;
import static org.entcore.common.bus.BusResponseHandler.busArrayHandler;
import static org.entcore.common.bus.BusResponseHandler.busResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.*;

public class DirectoryController extends BaseController {

	private Neo neo;
	private JsonObject config;
	private JsonObject admin;
	private SchoolService schoolService;
	private ClassService classService;
	private UserService userService;
	private GroupService groupService;
	private SlotProfileService slotProfileService;
	private EventStore eventStore;

	public void init(Vertx vertx, JsonObject config, RouteMatcher rm,
			Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
		super.init(vertx, config, rm, securedActions);
		this.neo = new Neo(vertx, eb,log);
		this.config = config;
		this.admin = new JsonObject(vertx.fileSystem().readFileBlocking("super-admin.json").toString());
		eventStore = EventStoreFactory.getFactory().getEventStore(UserBookController.ANNUAIRE_MODULE);
	}

	@Get("/admin-console")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	@ResourceFilter(AdminFilter.class)
	@MfaProtected()
	public void adminConsole(HttpServerRequest request) {
		renderView(request, new JsonObject());
	}

	@Get("/class-admin")
	@SecuredAction("classadmin.address")
	@MfaProtected()
	public void classAdmin(HttpServerRequest request) {
		renderView(request);
		eventStore.createAndStoreEvent(UserBookController.DirectoryEvent.ACCESS.name(), request, new JsonObject().put("override-module", "ClassParam"));
	}

	@Get("/class-admin/:userId")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	public void classAdminUsers(HttpServerRequest request) {
		String userId = request.params().get("userId");
		if (userId == null || userId.trim().isEmpty()) {
			badRequest(request);
			return;
		}
		userService.getUserInfos(userId, new Handler<Either<String, JsonObject>>() {
			@Override
			public void handle(final Either<String, JsonObject> either) {
				if (either.isRight()) {
					if (either.right().getValue() != null && either.right().getValue().size() > 0) {
						renderJson(request, either.right().getValue());
					} else {
						request.response().setStatusCode(404).end();
					}
				} else {
					leftToResponse(request, either.left());
				}
			}
		});
	}

	@Get("/gar/config")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	@ResourceFilter(SuperAdminFilter.class)
	@MfaProtected()
	public void garConfig(HttpServerRequest request) {
		Renders.renderJson(request, config.getJsonArray("gar-config", new JsonArray()));
	}

	@Post("/import")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	@ResourceFilter(AdmlOfStructuresByExternalId.class)
	@MfaProtected()
	public void launchImport(final HttpServerRequest request) {
		final JsonObject json = new JsonObject()
				.put("action", "import")
				.put("feeder", request.params().get("feeder"))
				.put("profile", request.params().get("profile"))
				.put("charset", request.params().get("charset"))
				.put("structureExternalId", request.params().get("structureExternalId"));

		eb.request("entcore.feeder", json);
		request.response().end();
	}

	@Post("/transition")
	@SecuredAction("directory.transition")
	public void launchTransition(final HttpServerRequest request) {
		JsonObject t = new JsonObject().put("action", "transition");
		callTransition(request, t);
	}

	private void callTransition(HttpServerRequest request, JsonObject t) {
		String structureId = request.params().get("structureExternalId");
		if (structureId != null) {
			t.put("structureExternalId", structureId);
		}
		eb.request("entcore.feeder", t, new DeliveryOptions().setSendTimeout(getOrElse(config.getLong("transitionTimeout"), 300000l)),
				handlerToAsyncHandler(new Handler<Message<JsonObject>>() {

			@Override
			public void handle(Message<JsonObject> event) {
				renderJson(request, event.body());
			}
		}));
	}

	@Post("/removeclassgroupshare/:structureExternalId")
	@SecuredAction("directory.removeclassgroupshare")
	public void launchRemoveGroupShare(final HttpServerRequest request) {
		JsonObject t = new JsonObject().put("action", "transition").put("onlyRemoveShare", true);
		callTransition(request, t);
	}

	@Post("/duplicates/mark")
	@SecuredAction("directory.duplicates.mark")
	public void markDuplicates(final HttpServerRequest request) {
		eb.request("entcore.feeder", new JsonObject().put("action", "mark-duplicates"),
				new DeliveryOptions().setSendTimeout(getOrElse(config.getLong("markDuplicatesTimeout"), 300000l)), handlerToAsyncHandler(new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				renderJson(request, event.body());
			}
		}));
	}

	@Post("/autogroups/link")
	@SecuredAction("directory.autogroups.link")
	public void linkAutogroups(final HttpServerRequest request) {
		eb.request("entcore.feeder", new JsonObject().put("action", "manual-link-autogroups"), handlerToAsyncHandler(new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				renderJson(request, event.body());
			}
		}));
	}

	@Post("/export")
	@SecuredAction("directory.export")
	public void launchExport(HttpServerRequest request) {
		eb.request("entcore.feeder", new JsonObject().put("action", "export"));
		request.response().end();
	}

	@Post("/reinitLogins")
	@SecuredAction("directory.reinit.login")
	public void reinitLogins(HttpServerRequest request) {
		eb.request("entcore.feeder", new JsonObject().put("action", "reinit-logins"));
		request.response().end();
	}

	@Get("/annuaire")
	@SecuredAction(value = "directory.search.view", type = ActionType.AUTHENTICATED)
	public void annuaire(HttpServerRequest request) {
		renderView(request, null, "annuaire.html", null);
	}

	@Get("/schools")
	@SecuredAction(value = "directory.schools", type = ActionType.AUTHENTICATED)
	public void schools(HttpServerRequest request) {
		renderView(request);
	}

	@Get("/api/ecole")
	@SecuredAction("directory.authent")
	public void school(HttpServerRequest request) {
		neo.send("MATCH (n:Structure) RETURN distinct n.name as name, n.id as id", request.response());
	}

	@Post("/school")
	@SecuredAction("directory.school.create")
	public void createSchool(final HttpServerRequest request) {
		bodyToJson(request, new Handler<JsonObject>() {
			@Override
			public void handle(JsonObject school) {
				schoolService.create(school, new Handler<Either<String, JsonObject>>() {
					@Override
					public void handle(final Either<String, JsonObject> r) {
						if (r.isRight()) {
							if (r.right().getValue() != null && r.right().getValue().size() > 0) {
								JsonObject j = new JsonObject()
										.put("action", "initDefaultCommunicationRules")
										.put("schoolIds", new JsonArray().add(
												r.right().getValue().getString("id")));
								eb.request("wse.communication", j, handlerToAsyncHandler(new Handler<Message<JsonObject>>() {
									@Override
									public void handle(Message<JsonObject> message) {
										renderJson(request, r.right().getValue(), 201);
									}
								}));
							} else {
								request.response().setStatusCode(404).end();
							}
						} else {
							leftToResponse(request, r.left());
						}
					}
				});
			}
		});
	}

	@Get("/school/:id")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	public void getSchool(final HttpServerRequest request) {
		String schoolId = request.params().get("id");
		schoolService.get(schoolId, notEmptyResponseHandler(request));
	}

	@Post("/class/:schoolId")
	@SecuredAction("directory.class.create")
	public void createClass(final HttpServerRequest request) {
		final String schoolId = request.params().get("schoolId");
		if (schoolId == null || schoolId.trim().isEmpty()) {
			badRequest(request);
			return;
		}
		bodyToJson(request, new Handler<JsonObject>() {
			@Override
			public void handle(JsonObject c) {
				classService.create(schoolId, c, new Handler<Either<String, JsonObject>>() {
					@Override
					public void handle(final Either<String, JsonObject> event) {
						if (event.isRight()) {
							if (event.right().getValue() != null && event.right().getValue().size() > 0) {
								JsonObject j = new JsonObject()
										.put("action", "initDefaultCommunicationRules")
										.put("schoolIds", new JsonArray().add(schoolId));
								eb.request("wse.communication", j);
								String classId = event.right().getValue().getString("id");
								if (classId != null && !classId.trim().isEmpty() &&
										request.params().contains("setDefaultRoles") &&
										config.getBoolean("classDefaultRoles", false)) {
									ApplicationUtils.setDefaultClassRoles(eb, classId,
											handlerToAsyncHandler(new Handler<Message<JsonObject>>() {
												@Override
												public void handle(Message<JsonObject> message) {
													renderJson(request, event.right().getValue(), 201);
												}
											}));
								} else {
									renderJson(request, event.right().getValue(), 201);
								}
							} else {
								request.response().setStatusCode(404).end();
							}
						} else {
							JsonObject error = new JsonObject()
									.put("error", event.left().getValue());
							renderJson(request, error, 400);
						}
					}
				});
			}
		});
	}

	@Get("/api/classes")
	@SecuredAction("directory.classes")
	public void classes(HttpServerRequest request) {
		Map<String, Object> params = new HashMap<>();
		params.put("id",request.params().get("id"));
		neo.send("MATCH (n:Structure)<-[:BELONGS]-(m:Class) " +
				"WHERE n.id = {id} " +
				"RETURN distinct m.name as name, m.id as classId, n.id as schoolId",
				params, request.response());
	}

	@Get("/api/personnes")
	@SecuredAction("directory.authent")
	public void people(HttpServerRequest request) {
		List<String> expectedTypes = request.params().getAll("type");
		JsonObject params = new JsonObject();
		params.put("classId", request.params().get("id"));
		String types = "";
		if (expectedTypes != null && !expectedTypes.isEmpty()) {
			types = "AND p.name IN {expectedTypes} ";
			params.put("expectedTypes", new JsonArray(expectedTypes));
		}
		neo.send("MATCH (n:Class)<-[:DEPENDS]-(g:ProfileGroup)<-[:IN]-(m:User), "
				+ "g-[:DEPENDS]->(pg:ProfileGroup)-[:HAS_PROFILE]->(p:Profile) "
				+ "WHERE n.id = {classId} " + types
				+ "RETURN distinct m.id as userId, p.name as type, "
				+ "m.activationCode as code, m.firstName as firstName,"
				+ "m.lastName as lastName, n.id as classId "
				+ "ORDER BY type DESC ", params.getMap(), request.response());
	}

	@Get("/users")
	@SecuredAction("directory.list.users")
	public void users(HttpServerRequest request) {
		String structureId = request.params().get("structureId");
		String classId = request.params().get("classId");
		List<String> profiles = request.params().getAll("profile");
		userService.list(structureId, classId, new JsonArray(profiles), arrayResponseHandler(request));
	}

	@Get("/api/details")
	@SecuredAction("directory.authent")
	public void details(HttpServerRequest request) {
		Map<String, Object> params = new HashMap<>();
		params.put("id", request.params().get("id"));
		neo.send("MATCH (n:User) " +
				"WHERE n.id = {id} " +
				"RETURN distinct n.id as id, n.login as login, n.address as address, n.activationCode as code;"
				, params, request.response());
	}

	@Post("/api/user")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	@MfaProtected()
	public void createUser(final HttpServerRequest request) {
		request.setExpectMultipart(true);
		request.endHandler(new Handler<Void>() {

			@Override
			public void handle(Void v) {
				final String classId = request.formAttributes().get("classId");
				final String structureId = request.formAttributes().get("structureId");
				if ((classId == null || classId.trim().isEmpty()) &&
						(structureId == null || structureId.trim().isEmpty())) {
					badRequest(request);
					return;
				}
				JsonObject user = new JsonObject()
						.put("firstName", request.formAttributes().get("firstname"))
						.put("lastName", request.formAttributes().get("lastname"))
						.put("type", request.formAttributes().get("type"));
				String birthDate = request.formAttributes().get("birthDate");
				if (birthDate != null && !birthDate.trim().isEmpty()) {
					user.put("birthDate", birthDate);
				}
				List<String> childrenIds = request.formAttributes().getAll("childrenIds");
				user.put("childrenIds", new JsonArray(childrenIds));
				// Get UserPosition IDs and remove duplicates.
				List<String> userPositionIds = request.formAttributes().getAll("positionIds")
				.stream().distinct().collect(Collectors.toList());

				user.put("userPositionIds", new fr.wseduc.webutils.collections.JsonArray(userPositionIds));
				if (classId != null && !classId.trim().isEmpty()) {
					userService.createInClass(classId, user, null, new Handler<Either<String, JsonObject>>() {
						@Override
						public void handle(Either<String, JsonObject> res) {
							if (res.isRight() && res.right().getValue().size() > 0) {
								JsonObject r = res.right().getValue();
								JsonArray a = new JsonArray().add(r.getString("id"));
								ApplicationUtils.sendModifiedUserGroup(eb, a, handlerToAsyncHandler(new Handler<Message<JsonObject>>() {
									@Override
									public void handle(Message<JsonObject> message) {
										schoolService.getByClassId(classId, new Handler<Either<String, JsonObject>>() {
											@Override
											public void handle(Either<String, JsonObject> s) {
												if (s.isRight()) {
													JsonObject j = new JsonObject()
															.put("action", "setDefaultCommunicationRules")
															.put("schoolId", s.right().getValue().getString("id"));
													eb.request("wse.communication", j);
												}
											}
										});
									}
								}));
								renderJson(request, r);
							} else {
								final Either.Left<String, JsonObject> left = res.left();
								final String value = left.getValue();
								if("user.profiles.not.allowed.for.profile.at.creation".equals(value)) {
									renderError(request, new JsonObject().put("error", value), 403, "Forbidden");
								} else {
									leftToResponse(request, res.left());
								}
							}
						}
					});
				} else {
					userService.createInStructure(structureId, user, null, new Handler<Either<String, JsonObject>>() {
						@Override
						public void handle(Either<String, JsonObject> res) {
							if (res.isRight() && res.right().getValue().size() > 0) {
								JsonObject r = res.right().getValue();
								JsonArray a = new JsonArray().add(r.getString("id"));
								ApplicationUtils.sendModifiedUserGroup(eb, a, handlerToAsyncHandler(new Handler<Message<JsonObject>>() {
									@Override
									public void handle(Message<JsonObject> message) {
										JsonObject j = new JsonObject()
												.put("action", "setDefaultCommunicationRules")
												.put("schoolId", structureId);
										eb.request("wse.communication", j);
									}
								}));
								renderJson(request, r);
							} else {
								final Either.Left<String, JsonObject> left = res.left();
								final String value = left.getValue();
								if("user.profiles.not.allowed.for.profile.at.creation".equals(value)) {
									renderError(request, new JsonObject().put("error", value), 403, "Forbidden");
								} else {
									leftToResponse(request, res.left());
								}
							}
						}
					});
				}
			}
		});
	}

	@Get("/api/export")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	@MfaProtected()
	public void export(final HttpServerRequest request) {
		String neoRequest = "";
		Map<String, Object> params = new HashMap<>();
		if (request.params().get("id").equals("all")){
			neoRequest =
					"MATCH (m:User)-[:IN]->g " +
					"WHERE NOT(m.activationCode IS NULL) " +
					"OPTIONAL MATCH g-[:DEPENDS*0..1]->(pg:ProfileGroup)-[:HAS_PROFILE]->(p:Profile) " +
					"RETURN distinct m.lastName as lastName, m.firstName as firstName, " +
					"m.login as login, m.activationCode as activationCode, " +
					"p.name as type " +
					"ORDER BY type, login ";
		} else if (request.params().get("id") != null) {
			neoRequest =
					"MATCH (m:User)-[:IN]->g-[:DEPENDS]->n " +
					"WHERE (n:Structure OR n:Class) AND n.id = {id} AND NOT(m.activationCode IS NULL) " +
					"OPTIONAL MATCH g-[:DEPENDS*0..1]->(pg:ProfileGroup)-[:HAS_PROFILE]->(p:Profile) " +
					"RETURN distinct m.lastName as lastName, m.firstName as firstName, " +
					"m.login as login, m.activationCode as activationCode, " +
					"p.name as type " +
					"ORDER BY type, login ";
			params.put("id", request.params().get("id"));
		} else {
			notFound(request);
		}
		neo.send(neoRequest, params, new Handler<Message<JsonObject>>() {

			@Override
			public void handle(Message<JsonObject> res) {
				if ("ok".equals(res.body().getString("status"))) {
					JsonArray r = Neo.resultToJsonArray(res.body().getJsonObject("result"));
					processTemplate(request, "text/export.txt",
							new JsonObject().put("list", r), new Handler<String>() {
						@Override
						public void handle(String export) {
							if (export != null) {
								request.response().putHeader("Content-Type", "application/csv");
								request.response().putHeader("Content-Disposition",
										"attachment; filename=activation_de_comptes.csv");
								request.response().end(export);
							} else {
								renderError(request);
							}
						}
					});
				} else {
					renderError(request);
				}
			}
		});
	}

	public void createSuperAdmin(){
		log.info("Create super admin");
		neo.send("MATCH (n:User)-[:HAS_FUNCTION]->(f:Function { externalId : 'SUPER_ADMIN'}) "
			+ "WHERE n.id = '" + admin.getString("id") + "' "
			+ "WITH count(*) AS exists "
			+ "WHERE exists=0 "
			+ "CREATE (m:User {id:'" + admin.getString("id") + "', "
			+ "externalId:'" + UUID.randomUUID().toString() + "', "
			+ "manual:true, "
			+ "lastName:'" + admin.getString("firstname") + "', "
			+ "firstName:'" + admin.getString("lastname") + "', "
			+ "login:'" + admin.getString("login") + "', "
			+ "displayName:'" + admin.getString("firstname") + " " + admin.getString("lastname") + "', "
			+ "needRevalidateTerms:false, "
			+ "password:'" + BCrypt.hashpw(admin.getString("password"), BCrypt.gensalt()) + "'})-[:HAS_FUNCTION]->" +
			"(f:Function { externalId : 'SUPER_ADMIN', name : 'SuperAdmin' })", event -> {
				if (!"ok".equals(event.body().getString("status"))) {
					log.error("Create default super admin : " + event.body().getString("message"));
				}
			});
		neo.execute("MERGE (u:User {id:'OAuthSystemUser'}) ON CREATE SET u.manual = true", (JsonObject) null, event -> {
			if (!"ok".equals(event.body().getString("status"))) {
				log.error("Create OAuthSystemUser : " + event.body().getString("message"));
			}
		});
	}

	@BusAddress("directory")
	public void directoryHandler(final Message<JsonObject> message) {
		String action = message.body().getString("action", "");
		String userId = message.body().getString("userId");
		switch (action) {
			case "usersInProfilGroup":
				boolean itSelf2 = message.body().getBoolean("itself", false);
				String excludeUserId = message.body().getString("excludeUserId");
				userService.list(userId, itSelf2, excludeUserId, responseHandler(message));
				break;
			case "getUser" :
				boolean withClasses = message.body().getBoolean("withClasses", false);
				userService.get(userId, false, false, withClasses, BusResponseHandler.busResponseHandler(message));
				break;
			case "getUserGoups" :
				userService.getGroups(userId, BusResponseHandler.busArrayHandler(message));
				break;
			case "getUserInfos" :
				userService.getInfos(userId, BusResponseHandler.busResponseHandler(message));
				break;
			case "getUserStructuresClasses":
				userService.getUserStructuresClasses(userId, BusResponseHandler.busResponseHandler(message));
				break;
			case "getMainStructure" :
				JsonArray structuresToExclude = message.body().getJsonArray("structures-to-exclude");
				userService.getMainStructure(userId, structuresToExclude, BusResponseHandler.busResponseHandler(message));
				break;
			case "getUsersStructures" : {
				JsonArray userIds = message.body().getJsonArray("userIds", new JsonArray());
				JsonArray fields = message.body().getJsonArray("fields");
				userService.getUsersStructures(userIds, fields, busArrayHandler(message));
				break;
			}
			case "list-users":
				JsonArray userIds = message.body().getJsonArray("userIds", new JsonArray());
				JsonArray groupIds = message.body().getJsonArray("groupIds", new JsonArray());
				boolean itSelf = message.body().getBoolean("itself", false);
				String excludeId = message.body().getString("excludeUserId");
				userService.list(groupIds, userIds, itSelf, excludeId, busArrayHandler(message));
				break;
			case "list-structures" :
				schoolService.list(message.body().getJsonArray("fields"), busArrayHandler(message));
				break;
			case "list-groups" :
				String structureId = message.body().getString("structureId");
				String type = message.body().getString("type");
				boolean subGroups = message.body().getBoolean("subGroups", false);
				groupService.list(structureId, type, subGroups, busArrayHandler(message));
				break;
			case "list-adml" :
				String sId = message.body().getString("structureId");
				userService.listAdml(sId, responseHandler(message));
				break;
			case "list-slotprofiles" :
				String structId = message.body().getString("structureId");
				slotProfileService.listSlotProfilesByStructure(structId, busArrayHandler(message));
				break;
			case "list-slots" :
				String slotProfileId = message.body().getString("slotProfileId");
				slotProfileService.listSlots(slotProfileId, busResponseHandler(message));
				break;
			case "set-distrib-and-education-by-structureId" :
				final JsonArray data = message.body().getJsonArray("data");
				final Integer transactionId = message.body().getInteger("transactionId");
				final Boolean commit = message.body().getBoolean("commit", true);
				schoolService.massDistributionEducationMobileApp(data, transactionId, commit, busResponseHandler(message));
				break;
			case "getActivationInfos" :
				JsonArray structureIds = message.body().getJsonArray("structureIds");
				schoolService.getActivationInfos(structureIds, busArrayHandler(message));
				break;
			case "getUsersActivity" :
				JsonArray usersIds = message.body().getJsonArray("userIds");
				schoolService.getUsersActivity(usersIds, busArrayHandler(message));
				break;
			case "getUserStructuresGroup":
				userService.getUserStructuresGroup(userId, BusResponseHandler.busResponseHandler(message));
				break;
			case "getGroupsInfos": 
				JsonArray batchIds = message.body().getJsonArray("groupIds");
				int fieldMask = message.body().getInteger("fieldMask", GroupService.Field.ALL.value());
				groupService.getBatchInfos(batchIds, fieldMask, BusResponseHandler.busArrayHandler(message));
				break;
		default:
			message.reply(new JsonObject()
				.put("status", "error")
				.put("message", "Invalid action."));
		}
	}

	public void setSchoolService(SchoolService schoolService) {
		this.schoolService = schoolService;
	}

	public void setClassService(ClassService classService) {
		this.classService = classService;
	}

	public void setUserService(UserService userService) {
		this.userService = userService;
	}

	private Handler<Either<String, JsonArray>> responseHandler(final Message<JsonObject> message) {
		return new Handler<Either<String, JsonArray>>() {

			@Override
			public void handle(Either<String, JsonArray> res) {
				JsonArray j;
				if (res.isRight()) {
					j = res.right().getValue();
				} else {
					log.warn(res.left().getValue());
					j = new JsonArray();
				}
				message.reply(j);
			}
		};
	}

	public void setGroupService(GroupService groupService) {
		this.groupService = groupService;
	}

	public void setSlotProfileService (SlotProfileService slotProfileService) {
		this.slotProfileService = slotProfileService;
	}

	// Methods used to create Workflow rights

	@SecuredAction("classadmin.add.users")
	public void allowClassAdminAddUsers(){}

	@SecuredAction("classadmin.reset.password")
	public void allowClassAdminResetPassword(){}

	@SecuredAction("classadmin.block.users")
	public void allowClassAdminBlockUsers(){}

	@SecuredAction("classadmin.delete.users")
	public void allowClassAdminDeleteUsers(){}

	@SecuredAction("classadmin.unlink.users")
	public void allowClassAdminUnlinkUsers(){}

	@SecuredAction("classadmin.csv.import")
	public void allowClassAdminCSVImport(){}
}
