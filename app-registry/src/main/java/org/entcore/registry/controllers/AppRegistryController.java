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

package org.entcore.registry.controllers;

import fr.wseduc.bus.BusAddress;
import fr.wseduc.rs.Delete;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.rs.Put;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.MfaProtected;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.Server;
import fr.wseduc.webutils.http.BaseController;

import fr.wseduc.webutils.http.Renders;
import io.vertx.core.Vertx;
import org.entcore.common.http.filter.AdminFilter;
import org.entcore.common.http.filter.AdmlOfStructure;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.user.UserUtils;
import org.entcore.common.utils.StringUtils;
import org.entcore.registry.filters.ApplicationFilter;
import org.entcore.registry.filters.LinkRoleGroupFilter;
import org.entcore.registry.filters.RoleFilter;
import org.entcore.registry.filters.RoleGroupFilter;
import org.entcore.registry.filters.SuperAdminFilter;
import org.entcore.registry.services.AppRegistryService;
import org.entcore.registry.services.impl.DefaultAppRegistryService;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.vertx.java.core.http.RouteMatcher;

import static fr.wseduc.webutils.Utils.handlerToAsyncHandler;
import static fr.wseduc.webutils.request.RequestUtils.bodyToJson;
import static org.entcore.common.appregistry.AppRegistryEvents.APP_REGISTRY_PUBLISH_ADDRESS;
import static org.entcore.common.appregistry.AppRegistryEvents.PROFILE_GROUP_ACTIONS_UPDATED;
import static org.entcore.common.bus.BusResponseHandler.busArrayHandler;
import static org.entcore.common.bus.BusResponseHandler.busResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.*;

import java.net.URL;
import java.util.List;
import java.util.Map;

public class AppRegistryController extends BaseController {

	private final AppRegistryService appRegistryService = new DefaultAppRegistryService();
	private JsonObject skinLevels;

	public void init(Vertx vertx, JsonObject config, RouteMatcher rm,
					 Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
		super.init(vertx, config, rm, securedActions);
		this.skinLevels = new JsonObject(vertx.sharedData().getLocalMap("skin-levels"));
	}

	@Get("/admin-console")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	@ResourceFilter(AdminFilter.class)
	@MfaProtected()
	public void staticAdmin(final HttpServerRequest request) {
		renderView(request);
	}

	@Get("/app-preview")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	@ResourceFilter(AdminFilter.class)
	@MfaProtected()
	public void appPreview(final HttpServerRequest request) {
		renderView(request);
	}

	@Get("/applications")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	@MfaProtected()
	public void listApplications(HttpServerRequest request) {
		String structureId = request.params().get("structureId");
		appRegistryService.listApplications(structureId, arrayResponseHandler(request));
	}

	@Get("/application/:name")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	@ResourceFilter(AdminFilter.class)
	@MfaProtected()
	public void listApplicationActions(HttpServerRequest request) {
		String name = request.params().get("name");
		if (name != null && !name.trim().isEmpty()) {
			appRegistryService.listActions(name, arrayResponseHandler(request));
		} else {
			badRequest(request, "invalid.application.name");
		}
	}

	@Get("/applications/actions")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	@MfaProtected()
	public void listApplicationsWithActions(HttpServerRequest request) {
		String structureId = request.params().get("structureId");
		String actionType = request.params().get("actionType");
		appRegistryService.listApplicationsWithActions(structureId, actionType, arrayResponseHandler(request));
	}

	@Get("/applications/roles")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	@MfaProtected()
	public void listApplicationsWithRoles(HttpServerRequest request) {
		String structureId = request.params().get("structureId");
		appRegistryService.listApplicationsWithRoles(structureId, arrayResponseHandler(request));
	}

	@Get("structure/:structureId/application/:appId/groups/roles")
	@SecuredAction(type = ActionType.RESOURCE, value = "")
	@MfaProtected()
	public void listApplicationRolesWithGroups(final HttpServerRequest request) {
		String structureId = request.params().get("structureId");
		String appId = request.params().get("appId");
		appRegistryService.listApplicationRolesWithGroups(structureId, appId, new Handler<Either<String, JsonArray>>() {
			@Override
			public void handle(Either<String, JsonArray> r) {
				if (r.isRight()) {
					JsonArray list = r.right().getValue();
					for (Object res : list) {
						UserUtils.translateGroupsNames(((JsonObject)res).getJsonArray("groups"), I18n.acceptLanguage(request));
					}
					renderJson(request, list);
				} else {
					leftToResponse(request, r.left());
				}
			}
		});
	}

	@Post("/role")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	@MfaProtected()
	public void createRole(final HttpServerRequest request) {
		bodyToJson(request, new Handler<JsonObject>() {
			@Override
			public void handle(JsonObject body) {
				final String roleName = body.getString("role");
				final JsonArray actions = body.getJsonArray("actions");
				if (actions != null && roleName != null &&
						actions.size() > 0 && !roleName.trim().isEmpty()) {
					final JsonObject role = new JsonObject().put("name", roleName);
					String structureId = request.params().get("structureId");
					appRegistryService.createRole(structureId, role, actions, notEmptyResponseHandler(request, 201, 409));
				} else {
					badRequest(request, "invalid.parameters");
				}
			}
		});
	}

	@Put("/role/:id")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	@ResourceFilter(RoleFilter.class)
	@MfaProtected()
	public void updateRole(final HttpServerRequest request) {
		bodyToJson(request, new Handler<JsonObject>() {
			@Override
			public void handle(JsonObject body) {
				final String roleId = request.params().get("id");
				if (roleId != null && !roleId.trim().isEmpty()) {
					final String roleName = body.getString("role");
					final JsonArray actions = body.getJsonArray("actions", new JsonArray());
					final JsonObject role = new JsonObject();
					if (roleName != null && !roleName.trim().isEmpty()) {
						role.put("name", roleName);
					}
					appRegistryService.updateRole(roleId, role, actions, notEmptyResponseHandler(request));
				} else {
					badRequest(request, "invalid.id");
				}
			}
		});
	}

	@Delete("/role/:id")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	@ResourceFilter(RoleFilter.class)
	@MfaProtected()
	public void deleteRole(final HttpServerRequest request) {
		String roleId = request.params().get("id");
		if (roleId != null && !roleId.trim().isEmpty()) {
			appRegistryService.deleteRole(roleId, defaultResponseHandler(request, 204));
		} else {
			badRequest(request, "invalid.id");
		}
	}

	@Put("/role/:roleId/distributions")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	@ResourceFilter(SuperAdminFilter.class)
	@MfaProtected()
	public void setRoleDistributions(final HttpServerRequest request) {
		bodyToJson(request, new Handler<JsonObject>() {
			@Override
			public void handle(JsonObject body) {
				JsonArray jsonDistributions = body.getJsonArray("distributions");
				List<String> distributions = jsonDistributions.getList();
				appRegistryService.setRoleDistributions(
						request.params().get("roleId"),
						distributions,
						defaultResponseHandler(request)
				);
			}
		});
	}

	@Post("/authorize/group")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	@ResourceFilter(LinkRoleGroupFilter.class)
	@MfaProtected()
	public void linkGroup(final HttpServerRequest request) {
		bodyToJson(request, new Handler<JsonObject>() {
			@Override
			public void handle(JsonObject body) {
				final JsonArray roleIds = body.getJsonArray("roleIds");
				final String groupId = body.getString("groupId");
				if (roleIds != null && groupId != null && !groupId.trim().isEmpty()) {
					appRegistryService.linkRolesToGroup(groupId, roleIds, new Handler<Either<String, JsonObject>>() {
						@Override
						public void handle(Either<String, JsonObject> event) {
							if (event.isRight()) {
								updatedProfileGroupActions(groupId);
								renderJson(request, event.right().getValue());
							} else {
								leftToResponse(request, event.left());
							}
						}
					});
				} else {
					badRequest(request, "invalid.parameters");
				}
			}
		});
	}

	@Put("/authorize/group/:groupId/role/:roleId")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	@ResourceFilter(RoleGroupFilter.class)
	@MfaProtected()
	public void addGroupLink(final HttpServerRequest request) {
		final String groupId = request.params().get("groupId");
		final String roleId = request.params().get("roleId");
		appRegistryService.addGroupLink(groupId, roleId, defaultResponseHandler(request));
	}

	@Delete("/authorize/group/:groupId/role/:roleId")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	@ResourceFilter(RoleGroupFilter.class)
	@MfaProtected()
	public void removeGroupLink(final HttpServerRequest request) {
		final String groupId = request.params().get("groupId");
		final String roleId = request.params().get("roleId");
		appRegistryService.deleteGroupLink(groupId, roleId, defaultResponseHandler(request, 204));
	}

	@Get("/roles")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	@MfaProtected()
	public void listRoles(HttpServerRequest request) {
		String structureId = request.params().get("structureId");
		appRegistryService.listRoles(structureId, arrayResponseHandler(request));
	}

	@Get("/roles/actions")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	@MfaProtected()
	public void listRolesWithActions(HttpServerRequest request) {
		String structureId = request.params().get("structureId");
		appRegistryService.listRolesWithActions(structureId, arrayResponseHandler(request));
	}

	@Get("/groups/roles")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	@MfaProtected()
	public void listGroupsWithRoles(final HttpServerRequest request) {
		String structureId = request.params().get("structureId");
		appRegistryService.listGroupsWithRoles(structureId, true, new Handler<Either<String, JsonArray>>() {
			@Override
			public void handle(Either<String, JsonArray> r) {
				if (r.isRight()) {
					JsonArray res = r.right().getValue();
					UserUtils.translateGroupsNames(res, I18n.acceptLanguage(request));
					renderJson(request, res);
				} else {
					leftToResponse(request, r.left());
				}
			}
		});
	}

	@Post("/application")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	@ResourceFilter(SuperAdminFilter.class)
	@MfaProtected()
	public void createApplication(final HttpServerRequest request){
		bodyToJson(request, pathPrefix + "createApplication", new Handler<JsonObject>() {
			@Override
			public void handle(final JsonObject body) {
				final String structureId = request.params().get("structureId");
				final boolean inherits = body.getBoolean("inherits", false);
				final String casType = body.getString("casType", "");
				final String statCasType = body.getString("statCasType", "");
				final String address = body.getString("address", "");
				final boolean updateCas = !StringUtils.isEmpty(casType);
				final URL addressURL = DefaultAppRegistryService.checkCasUrl(address);

				// don't check url for standard app or oauth connector
				if (!updateCas || addressURL != null) {
					appRegistryService.createApplication(structureId, body, null, new Handler<Either<String, JsonObject>>() {
						@Override
						public void handle(Either<String, JsonObject> event) {
							if (event.isLeft()) {
								JsonObject error = new JsonObject()
										.put("error", event.left().getValue());
								Renders.renderJson(request, error, 400);
								return;
							}

							if (event.right().getValue() != null && event.right().getValue().size() > 0) {
								sendPatternToCasConfiguration(updateCas, body, addressURL, casType, structureId, inherits, statCasType);
								Renders.renderJson(request, event.right().getValue(), 201);
							} else {
								JsonObject error = new JsonObject()
										.put("error", "appregistry.failed.app");
								Renders.renderJson(request, error, 400);
							}
						}
					});
				} else {
					badRequest(request, "appregistry.failed.app.url");
				}
			}
		});
	}

	private void sendPatternToCasConfiguration(boolean updateCas, JsonObject body, URL addressURL, String casType, String structureId, boolean inherits, String statCasType) {
		if (updateCas && addressURL != null) {
            String pattern = body.getString("pattern", "");
			boolean emptyPattern = pattern.isEmpty();
            if (pattern.isEmpty()) {
                pattern = "^\\Q" + addressURL.getProtocol() + "://" + addressURL.getHost() + (addressURL.getPort() > 0 ? ":" + addressURL.getPort() : "") + "\\E.*";
            }
            Server.getEventBus(vertx).publish("cas.configuration", new JsonObject()
                    .put("action", "add-patterns")
                    .put("service", casType)
                    .put("statCasType", statCasType)
					.put("structureId", structureId)
					.put("emptyPattern", emptyPattern)
					.put("inherits", inherits)
                    .put("patterns", new JsonArray().add(pattern)));
        }
	}

	@Get("/application/conf/:id")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	@ResourceFilter(ApplicationFilter.class)
	@MfaProtected()
	public void application(final HttpServerRequest request) {
		String id = request.params().get("id");
		if (id != null && !id.trim().isEmpty()) {
			appRegistryService.getApplication(id, notEmptyResponseHandler(request));
		} else {
			badRequest(request, "invalid.application.id");
		}
	}

	@Put("/application/conf/:id")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	@ResourceFilter(ApplicationFilter.class)
	@MfaProtected()
	public void applicationConf(final HttpServerRequest request) {
		bodyToJson(request, pathPrefix + "updateApplication", new Handler<JsonObject>() {
			@Override
			public void handle(final JsonObject body) {
				String applicationId = request.params().get("id");
				final String casType = body.getString("casType","");
				final String statCasType = body.getString("statCasType", "");
				final String address = body.getString("address", "");
				final boolean inherits = body.getBoolean("inherits", false);
				final boolean updateCas = !StringUtils.isEmpty(casType);

				if (applicationId != null && !applicationId.trim().isEmpty()) {
					final URL addressURL = DefaultAppRegistryService.checkCasUrl(address);

					// don't check url for standard app or oauth connector
					if (!updateCas ||  addressURL != null) {
						appRegistryService.updateApplication(applicationId, body, new Handler<Either<String, JsonObject>>() {
							public void handle(Either<String, JsonObject> event) {
								if (event.isLeft()) {
									JsonObject error = new JsonObject()
											.put("error", event.left().getValue());
									Renders.renderJson(request, error, 400);
									return;
								}
								final String structureId = event.right().getValue().getString("structureId");
								sendPatternToCasConfiguration(updateCas, body, addressURL, casType, structureId, inherits, statCasType);
								Renders.renderJson(request, event.right().getValue());
							}
						});
					} else {
						badRequest(request, "appregistry.failed.app.url");
					}
				} else {
					badRequest(request, "appregistry.failed.app");
				}
			}
		});
	}

	@Delete("/application/conf/:id")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	@ResourceFilter(ApplicationFilter.class)
	@MfaProtected()
	public void deleteApplication(final HttpServerRequest request) {
		String id = request.params().get("id");
		if (id != null && !id.trim().isEmpty()) {
			appRegistryService.deleteApplication(id, defaultResponseHandler(request, 204));
		} else {
			badRequest(request, "invalid.application.id");
		}
	}

	@Get("/cas-types")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	@ResourceFilter(AdminFilter.class)
	@MfaProtected()
	public void listCasTypes(final HttpServerRequest request) {
		Server.getEventBus(vertx).request("cas.configuration", new JsonObject().put("action", "list-services"),
				handlerToAsyncHandler(event -> {
          if ("ok".equals(event.body().getString("status"))) {
            renderJson(request, event.body().getJsonArray("result"));
          } else {
            log.error(event.body().getString("message"));
          }
        }));
	}

	@BusAddress("wse.app.registry")
	public void collectApps(final Message<JsonObject> message) {
		final JsonObject app = message.body().getJsonObject("application");
		final String application = app.getString("name");
		final JsonArray securedActions = message.body().getJsonArray("actions");
		if (application != null && securedActions != null && !application.trim().isEmpty()) {
			appRegistryService.createApplication(null, app, securedActions, new Handler<Either<String, JsonObject>>() {
				@Override
				public void handle(Either<String, JsonObject> event) {
					JsonObject j = new JsonObject();
					if (event.isRight()) {
						j.put("status", "ok");
					} else {
						j.put("status", "error").put("message", event.left().getValue());
					}
					message.reply(j);
				}
			});
		} else {
			message.reply(new JsonObject().put("status", "error").put("message", "invalid.parameters"));
		}
	}

	@Put("/application")
	public void recordApplication(final HttpServerRequest request) {
		if (("localhost:"+ config.getInteger("port", 8012))
				.equalsIgnoreCase(request.headers().get("Host"))) {
			bodyToJson(request, new Handler<JsonObject>() {
				@Override
				public void handle(JsonObject jo) {
					eb.request(config.getString("address", "wse.app.registry"), jo, handlerToAsyncHandler(new Handler<Message<JsonObject>>() {
						@Override
						public void handle(Message<JsonObject> reply) {
							renderJson(request, reply.body());
						}
					}));
				}
			});
		} else {
			forbidden(request, "invalid.host");
		}
	}

	@Put("/application/:applicationId/levels-of-education")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	@ResourceFilter(SuperAdminFilter.class)
	@MfaProtected()
	public void setLevelsOfEducation(final HttpServerRequest request) {
		bodyToJson(request, new Handler<JsonObject>() {
			@Override
			public void handle(JsonObject body) {
				JsonArray jsonLevelsOfEducation = body.getJsonArray("levelsOfEducation");
				List<Integer> levelsOfEducation = jsonLevelsOfEducation.getList();
				appRegistryService.setLevelsOfEducation(
						request.params().get("applicationId"),
						levelsOfEducation,
						defaultResponseHandler(request)
				);
			}
		});
	}

    @Put("/structures/:structureId/roles")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(AdminFilter.class)
	@MfaProtected()
    public void authorizeProfiles(final HttpServerRequest request) {
        bodyToJson(request, new Handler<JsonObject>() {
            @Override
            public void handle(JsonObject body) {
                final String structureId = request.params().get("structureId");
                List<String> profiles = body.getJsonArray("profiles").getList();
                List<String> roles = body.getJsonArray("roles").getList();

                if (structureId == null || structureId.trim().isEmpty() || profiles.isEmpty() || roles.isEmpty()) {
                    badRequest(request);
                    return;
                }

                appRegistryService.massAuthorize(structureId, profiles, roles, defaultResponseHandler(request));
            }
        });
    }

    @Delete("/structures/:structureId/roles")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(AdminFilter.class)
	@MfaProtected()
    public void unauthorizeProfiles(final HttpServerRequest request) {
        bodyToJson(request, new Handler<JsonObject>() {
            @Override
            public void handle(JsonObject body) {
                final String structureId = request.params().get("structureId");
                List<String> profiles = body.getJsonArray("profiles").getList();
                List<String> roles = body.getJsonArray("roles").getList();

                if (structureId == null || structureId.trim().isEmpty() || profiles.isEmpty() || roles.isEmpty()) {
                    badRequest(request);
                    return;
                }
                appRegistryService.massUnauthorize(structureId, profiles, roles, defaultResponseHandler(request));
            }
        });
    }

	@Get("/applications/:structureId/default-bookmarks")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	@ResourceFilter(AdmlOfStructure.class)
	@MfaProtected()
	public void getDefaultBookmarks(final HttpServerRequest request){
		final String structureId = request.params().get("structureId");
		appRegistryService.getDefaultBookmarks(structureId, defaultResponseHandler(request));
	}

	@Put("/applications/:structureId/default-bookmarks")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	@ResourceFilter(AdmlOfStructure.class)
	@MfaProtected()
	public void setDefaultBookmarks(final HttpServerRequest request){
		final String structureId = request.params().get("structureId");
		bodyToJson(request, body -> {
			JsonObject apps = body.getJsonObject("apps");
			if (structureId == null || structureId.trim().isEmpty() || apps == null) {
				badRequest(request);
				return;
			}
			appRegistryService.setDefaultBookmarks(structureId, apps, defaultResponseHandler(request));
		});
	}

	@BusAddress("wse.app.registry.applications")
	public void applications(final Message<JsonObject> message) {
		String application = message.body().getString("application");
		if (application != null && !application.trim().isEmpty()) {
			String action = message.body().getString("action", "");
			Handler<Either<String, JsonArray>> responseHandler = new Handler<Either<String, JsonArray>>() {
				@Override
				public void handle(Either<String, JsonArray> res) {
					if (res.isRight()) {
						message.reply(res.right().getValue());
					} else {
						message.reply(new JsonArray());
					}
				}
			};
			switch (action) {
				case "allowedUsers":
					appRegistryService.applicationAllowedUsers(application,
							message.body().getJsonArray("users"),
							message.body().getJsonArray("groups"),
							responseHandler);
					break;
				case "allowedProfileGroups":
					appRegistryService.applicationAllowedProfileGroups(application, responseHandler);
					break;
				default:
					message.reply(new JsonArray());
					break;
			}
		} else {
			message.reply(new JsonArray());
		}
	}

	@BusAddress("wse.app.registry.bus")
	public void registryEventBusHandler(final Message<JsonObject> message) {
		final String structureId = message.body().getString("structureId");
		switch (message.body().getString("action", "")) {
			case "setDefaultClassRoles" :
				appRegistryService.setDefaultClassRoles(message.body().getString("classId"),
						new Handler<Either<String, JsonObject>>() {
					@Override
					public void handle(Either<String, JsonObject> r) {
						if (r.isRight()) {
							message.reply(r.right().getValue());
						} else {
							message.reply(new JsonObject().put("status", "error")
									.put("message", "invalid.classId"));
						}
					}
				});
				break;
			case "create-application" :
				appRegistryService.createApplication(structureId,
						message.body().getJsonObject("application"), null, busResponseHandler(message));
				break;
			case "create-role" :
				final JsonObject role = message.body().getJsonObject("role");
				final JsonArray actions = message.body().getJsonArray("actions");
				appRegistryService.createRole(structureId, role, actions, busResponseHandler(message));
				break;
			case "link-role-group" :
				final String groupId = message.body().getString("groupId");
				final JsonArray roleIds = message.body().getJsonArray("roleIds");
				appRegistryService.linkRolesToGroup(groupId, roleIds, new Handler<Either<String, JsonObject>>() {
					@Override
					public void handle(Either<String, JsonObject> event) {
						if (event.isRight()) {
							updatedProfileGroupActions(groupId);
							message.reply(new JsonObject().put("status", "ok")
									.put("result", event.right().getValue()));
						} else {
							JsonObject error = new JsonObject()
									.put("status", "error")
									.put("message", event.left().getValue());
							message.reply(error);
						}
					}
				});
				break;
			case "list-groups-with-roles" :
				boolean classGroups = message.body().getBoolean("classGroups", false);
				appRegistryService.listGroupsWithRoles(structureId, classGroups, busArrayHandler(message));
				break;
			case "list-roles" :
				appRegistryService.listRoles(structureId, busArrayHandler(message));
				break;
			case "list-cas-connectors" :
				appRegistryService.listCasConnectors(busArrayHandler(message));
				break;
			case "set-roles-and-profiles-by-structureId" :
				final JsonArray data = message.body().getJsonArray("data");
				final Integer transactionId = message.body().getInteger("transactionId");
				final Boolean commit = message.body().getBoolean("commit", true);
				appRegistryService.massAuthorization(data, transactionId, commit, busResponseHandler(message));
				break;
			case "apply-default-bookmarks" :
				final String userId = message.body().getString("userId");
				final String userTheme = message.body().getString("theme");
				JsonArray userSkinLevels = null;
				if (userTheme != null) {
					userSkinLevels = this.skinLevels.getJsonArray(userTheme);
				} else if (this.skinLevels.size() == 1) {
					userSkinLevels = this.skinLevels.getJsonArray(this.skinLevels.iterator().next().getKey());
				}
				if (userSkinLevels != null && userSkinLevels.contains("2d")) {
					// We apply default bookmarks for 2D themes only
					appRegistryService.applyDefaultBookmarks(userId);
				}
				break;
			default:
				message.reply(new JsonObject().put("status", "error")
						.put("message", "invalid.action"));
		}
	}

	private void updatedProfileGroupActions(String groupId) {
		JsonObject message = new JsonObject().put("type", PROFILE_GROUP_ACTIONS_UPDATED);
		if (groupId != null && !groupId.trim().isEmpty()) {
			message.put("groups", new JsonArray().add(groupId));
		}
		eb.publish(APP_REGISTRY_PUBLISH_ADDRESS, message);
	}

}
