package org.entcore.directory.controllers;

import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.http.BaseController;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.entcore.directory.services.SubjectService;

import static fr.wseduc.webutils.request.RequestUtils.bodyToJson;
import static org.entcore.common.http.response.DefaultResponseHandler.*;

public class SubjectController extends BaseController {

    private SubjectService subjectService;

    public void setSubjectService(SubjectService subjectService) {
        this.subjectService = subjectService;
    }


    @Get("/subject/admin/list")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void listAdmin(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(UserInfos user) {
                if (user != null) {
                    String structureId = request.params().get("structureId");
                    subjectService.listAdmin(structureId, arrayResponseHandler(request));
                } else {
                    unauthorized(request);
                }
            }
        });
    }

    @Post("/subject")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void create(final HttpServerRequest request) {
        bodyToJson(request, pathPrefix + "createManualSubject", new Handler<JsonObject>() {
            @Override
            public void handle(JsonObject body) {
                subjectService.createOrUpdateManual(body, notEmptyResponseHandler(request, 201));
            }
        });

    }
}
